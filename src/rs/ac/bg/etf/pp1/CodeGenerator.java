package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import org.apache.log4j.Logger;

import com.sun.org.apache.bcel.internal.generic.DUP2_X1;

import rs.ac.bg.etf.pp1.SemanticAnalyzer.Method;
import rs.ac.bg.etf.pp1.CounterVisitor.*;
import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {

	private int mainPc;
	private LinkedList<Method> methods;
	private Stack<Integer> numberOfActualArgumentsStack = new Stack<Integer>();
	private Stack<Loop> insideLoop = new Stack<Loop>();
	
	enum Loop {
		DO_WHILE, WHILE, FOR
	}
	
	private static final int MAX_DATA_SIZE = 8192;
	
	Logger log = Logger.getLogger(getClass());
	
	public void report_error(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0)
			msg.append(" na liniji ").append(line);
		log.error(msg.toString());
		System.err.println(msg.toString());
	}
	
	public CodeGenerator(LinkedList<Method> methods) {
		this.methods = methods;
	}
	
	private CodeGenerator() {}
	
	public int getMainPc() {
		return mainPc;
	}
	
	public void visit(Program program) {
		if(Code.pc > MAX_DATA_SIZE) {
			report_error("Izvorni kod programa ne sme biti veci od 8 KB.", null);
		}
	}
	
	public void visit(MethodTypeName methodTypeName) {
		if("main".equals(methodTypeName.getMethodName())) {
			mainPc = Code.pc;
		}
		
		methodTypeName.obj.setAdr(Code.pc);
		
		// Collect arguments and local variables
		SyntaxNode methodNode = methodTypeName.getParent();
		
		FormalParamCounter formalParamCounter = new FormalParamCounter();
		methodNode.traverseTopDown(formalParamCounter);
		
		OptArgsCounter optArgsCounter = new OptArgsCounter();
		methodNode.traverseTopDown(optArgsCounter);
		
		VarCounter varCounter = new VarCounter();
		methodNode.traverseTopDown(varCounter);
		
		// Generate the entry
		Code.put(Code.enter);
		Code.put(formalParamCounter.getCount() + optArgsCounter.getCount());
		Code.put(formalParamCounter.getCount() + optArgsCounter.getCount() + varCounter.getCount());
	}
	
	public void visit(MethodDecl methodDecl) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	public void visit(ReadStatement readStatement) {
		Designator designator = readStatement.getDesignator();
		
		if(designator instanceof DesignatorIdent)
			Code.put(Code.pop); // The designatorIdent is already on stack
		
		if(designator.obj.getType() == Tab.intType || designator.obj.getType() == SymbolTable.boolType) {
			Code.put(Code.read);
			if(designator instanceof DesignatorIdent) {
				Code.store(designator.obj);
				
			} else {
				Code.put(Code.astore);
			}
			
		} else if(designator.obj.getType() == Tab.charType) {
			if(designator instanceof DesignatorIdent) {
				Code.put(Code.bread);
				Code.store(designator.obj);
				
			} else {
				Code.put(Code.read);
				Code.put(Code.bastore);
			}
		}
	}
	
	public void visit(PrintStatement printStatement) {
		Struct exprType = printStatement.getExpr().struct;
		
		if(exprType == Tab.intType || exprType == SymbolTable.boolType) {
			Code.loadConst(5);
			Code.put(Code.print);
			
		} else if(exprType == Tab.charType) {
			Code.loadConst(1);
			Code.put(Code.bprint);
			
		}
	}
	
	public void visit(PrintStatementNumConst printStatementNumConst) {
		Struct exprType = printStatementNumConst.getExpr().struct;
		int width = printStatementNumConst.getN2();
		
		Code.loadConst(width);
		if(exprType == Tab.intType || exprType == SymbolTable.boolType) {
			Code.put(Code.print);
			
		} else if(exprType == Tab.charType) {
			Code.put(Code.bprint);
			
		}
	}
	
	public void visit(BreakStatement breakStatement) {
		Code.putJump(0); 		
		breakStatementAddressesToPatch.peek().add(Code.pc - 2);
	}
	
	public void visit(ContinueStatement continueStatement) {
		if(insideLoop.peek() == Loop.DO_WHILE) {
			Code.putJump(0); 		
			continueStatementAddressesToPatch.peek().add(Code.pc - 2);
			
		} else if(insideLoop.peek() == Loop.WHILE) {
			Code.putJump(whileStatementAddressesToPatch.peek());
		
		} else if(insideLoop.peek() == Loop.FOR) {
			Code.putJump(forLoopEndAddressesToPatch.peek());
			
		}
	}
	
	public void visit(ReturnStatement returnStatement) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	public void visit(ReturnExprStatement returnExprStatement) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	/* Do-while statement */
	
	private Stack<Integer> doWhileStatementAddressesToPatch = new Stack<Integer>();
	private Stack<Integer> whileStatementAddressesToPatch = new Stack<Integer>();
	private Stack<List<Integer>> breakStatementAddressesToPatch = new Stack<List<Integer>>();
	private Stack<List<Integer>> continueStatementAddressesToPatch = new Stack<List<Integer>>(); 	
	
	public void visit(DoWhileStatementBegin doWhileStatementBegin) {
		insideLoop.push(Loop.DO_WHILE);
		
		orConditionAddressesToPatch.push(new ArrayList<Integer>());
		andConditionAddressesToPatch.push(new ArrayList<Integer>());
		
		/* Save the address which refers to the beginning of the `do` block.
		 * A stack data structure is used to support nested `do-while` loops.
		 */
		doWhileStatementAddressesToPatch.push(Code.pc);

		breakStatementAddressesToPatch.push(new ArrayList<Integer>());
		continueStatementAddressesToPatch.push(new ArrayList<Integer>());
	}
	
	public void visit(WhileStatementBegin whileStatementBegin) {
		insideLoop.push(Loop.WHILE);
		
		orConditionAddressesToPatch.push(new ArrayList<Integer>());
		andConditionAddressesToPatch.push(new ArrayList<Integer>());
		
		/* Save the address which refers to the beginning of the `while` block.
		 * A stack data structure is used to support nested `while` loops.
		 */
		whileStatementAddressesToPatch.push(Code.pc);

		breakStatementAddressesToPatch.push(new ArrayList<Integer>());
		continueStatementAddressesToPatch.push(new ArrayList<Integer>());
	}
	
	public void visit(WhileStatement whileStatement) {
		/* At the end of the `while` loop there are no more addresses 
		 * to be patched and the current scope can be restored to previous
		 * state. 
		 */
		andConditionAddressesToPatch.pop();
		orConditionAddressesToPatch.pop();
		
		whileStatementAddressesToPatch.pop();
		breakStatementAddressesToPatch.pop();
		continueStatementAddressesToPatch.pop();
	}
	
	public void visit(WhileBegin whileBegin) {
		/* The continue statement interrupts the current iteration of the 
		 * surrounding `do-while` loop and jumps to the condition check. 
		 * Therefore, address which that jump refers to needs to be patched.
		 */
		for(int addressToPatch: continueStatementAddressesToPatch.peek()) {
			Code.fixup(addressToPatch);
		}
		
		continueStatementAddressesToPatch.peek().clear();
	}
	
	public void visit(WhileStatementEnd whileStatementEnd) {
		insideLoop.pop();
		
		/* The condition consists of multiple conditions which are separated 
		 * with `or` operator, and if any of them is `true` there has to be
		 * a jump to the beginning of the `do-while` block which is achieved
		 * through the following unconditional jump.
		 */
		for(int addressToPatch: orConditionAddressesToPatch.peek()) {
			Code.fixup(addressToPatch);
		}
		
		orConditionAddressesToPatch.peek().clear();
		
		/* After the following instruction is executed, the PC will point to the 
		 * next address which essentially represents the block which comes after 
		 * the `do-while` loop.
		 */
		Code.putJump(this.whileStatementAddressesToPatch.peek());
		
		
		/* If there are no `or` conditions, that means that there are only `and` conditions,
		 * even if there is only one condition, which if they're not true need to jump to 
		 * the address right after `do-while` loop.
		 */
		for(int addressToPatch: andConditionAddressesToPatch.peek()) {
			Code.fixup(addressToPatch);
		}
		
		andConditionAddressesToPatch.peek().clear();

		
		for(int addressToPatch: breakStatementAddressesToPatch.peek()) {
			Code.fixup(addressToPatch);
		}
		
		breakStatementAddressesToPatch.peek().clear(); 
	}
	
	public void visit(DoWhileCondition doWhileCondition) {
		/* The condition consists of multiple conditions which are separated 
		 * with `or` operator, and if any of them is `true` there has to be
		 * a jump to the beginning of the `do-while` block which is achieved
		 * through the following unconditional jump.
		 */
		for(int addressToPatch: orConditionAddressesToPatch.peek()) {
			Code.fixup(addressToPatch);
		}
		
		orConditionAddressesToPatch.peek().clear();
		
		/* After the following instruction is executed, the PC will point to the 
		 * next address which essentially represents the block which comes after 
		 * the `do-while` loop.
		 */
		Code.putJump(this.doWhileStatementAddressesToPatch.peek());
		
		
		/* If there are no `or` conditions, that means that there are only `and` conditions,
		 * even if there is only one condition, which if they're not true need to jump to 
		 * the address right after `do-while` loop.
		 */
		for(int addressToPatch: andConditionAddressesToPatch.peek()) {
			Code.fixup(addressToPatch);
		}
		
		andConditionAddressesToPatch.peek().clear();

		
		for(int addressToPatch: breakStatementAddressesToPatch.peek()) {
			Code.fixup(addressToPatch);
		}
		
		breakStatementAddressesToPatch.peek().clear(); 
	}
	
	public void visit(DoWhileStatement doWhileStatement) {
		insideLoop.pop();
		
		/* At the end of the `do-while` loop there are no more addresses 
		 * to be patched and the current scope can be restored to previous
		 * state. 
		 */
		andConditionAddressesToPatch.pop();
		orConditionAddressesToPatch.pop();
		
		doWhileStatementAddressesToPatch.pop();
		breakStatementAddressesToPatch.pop();
		continueStatementAddressesToPatch.pop();
	}
	
	/* For Loop */
	 
	private Stack<Integer> forLoopEndAddressesToPatch = new Stack<Integer>();
	private Stack<Integer> forLoopIterationBlockAddressesToPatch = new Stack<Integer>();
	private Stack<List<Integer>> forLoopConditionAddressesToPatch = new Stack<List<Integer>>();
	
	public void visit(ForLoopBegin forLoopBegin) {
		insideLoop.push(Loop.FOR);
		
		breakStatementAddressesToPatch.push(new ArrayList<Integer>());
		continueStatementAddressesToPatch.push(new ArrayList<Integer>());
		
		orConditionAddressesToPatch.push(new ArrayList<Integer>());
		andConditionAddressesToPatch.push(new ArrayList<Integer>());
		forLoopConditionAddressesToPatch.push(new ArrayList<Integer>());
	}	
	
	public void visit(ForLoop forLoop) {
		for(int addressToPatch: andConditionAddressesToPatch.peek()) {
			Code.fixup(addressToPatch);
		}
		
		andConditionAddressesToPatch.pop();
		orConditionAddressesToPatch.pop();
		
		breakStatementAddressesToPatch.pop();
		continueStatementAddressesToPatch.pop();
		
		forLoopEndAddressesToPatch.pop();
		forLoopIterationBlockAddressesToPatch.pop();
		forLoopConditionAddressesToPatch.pop();
	}
	
	public void visit(ForConditionBegin forConditionBegin) {
		/* After each iteration of the `for` loop, iteration block is next
		 * to be executed, after which the condition needs to be checked again.
		 */
		forLoopIterationBlockAddressesToPatch.push(Code.pc);
	}
	
	public void visit(ForCondition forCondition) {
		/*  After the condition is checked there must be an
		 * unconditional jump to the beginning of the `for` loop
		 * body block. 
		 */
		Code.putJump(0);
		forLoopConditionAddressesToPatch.peek().add(Code.pc - 2);
	}
	
	public void visit(ForIterationBegin forIterationBegin) {
		/* At the end of the `for` loop block iteration block  
		 *  is next to be executed.
		 */
		forLoopEndAddressesToPatch.push(Code.pc);
	}
	
	public void visit(ForIteration forIteration) {
		/* After the iteration block is executed, there must an unconditional
		 * jump to the condition check.
		 */
		Code.putJump(forLoopIterationBlockAddressesToPatch.peek());
		
		/* This is the end of the iteration block and more importantly
		 * beginning of the `for` loop body.
		 */
		for(int addressToPatch: forLoopConditionAddressesToPatch.peek()) {
			Code.fixup(addressToPatch);
		}
		
		forLoopConditionAddressesToPatch.peek().clear();
	}
	
	public void visit(ForLoopEnd forLoopEnd) {
		insideLoop.pop();
		
		/* At the end of the `for` loop block iteration block  
		 *  is next to be executed.
		 */
		Code.putJump(forLoopEndAddressesToPatch.peek());
		
		for(int addressToPatch: breakStatementAddressesToPatch.peek()) {
			Code.fixup(addressToPatch);
		}
		
		breakStatementAddressesToPatch.peek().clear();
	}
	
	/* Designator Statements */
	
	public void visit(DesignatorAssignStatement designatorAssignStatement) {
		Designator designator =	designatorAssignStatement.getDesignator();
		
		if(designator instanceof DesignatorIdent) {
			Code.store(designatorAssignStatement.getDesignator().obj);
			
		} else {
			DesignatorIdentArray designatorIdentArray = (DesignatorIdentArray) designator;
			if(designatorIdentArray.getDesignator().obj.getType().getElemType() == Tab.intType || designatorIdentArray.getDesignator().obj.getType().getElemType() == SymbolTable.boolType) {
				Code.put(Code.astore);
				
				
			} else {
				Code.put(Code.bastore);
			}
		}
	}
	
	public void visit(DesignatorIncStatement designatorIncStatement) {
		Designator designator = designatorIncStatement.getDesignator();
		
		if(designator instanceof DesignatorIdent) {
			Code.put(Code.const_1);
			Code.put(Code.add);
			Code.store(designator.obj);
			
		} else if(designator instanceof DesignatorIdentArray) {
			Code.put(Code.dup2);
			Code.put(Code.aload);
			Code.put(Code.const_1);
			Code.put(Code.add);
			Code.put(Code.astore);
			
		}
	}
	
	public void visit(DesignatorDecStatement designatorDecStatement) {
		Designator designator = designatorDecStatement.getDesignator();
		
		if(designator instanceof DesignatorIdent) {
			Code.put(Code.const_1);
			Code.put(Code.sub);
			Code.store(designator.obj);
			
		} else if(designator instanceof DesignatorIdentArray) {
			Code.put(Code.dup2);
			Code.put(Code.aload);
			Code.put(Code.const_1);
			Code.put(Code.sub);
			Code.put(Code.astore);
			
		}
	}
	
	public void visit(DesignatorFunctionCall designatorFunctionCall) {		
		Obj functionObj = designatorFunctionCall.getDesignator().obj;
		
		int numberOfActualArguments = numberOfActualArgumentsStack.pop();
		
		if("len".equals(functionObj.getName())) {
			numberOfActualArguments = 0;
			Code.put(Code.arraylength);
			return;
			
		} else if("ord".equals(functionObj.getName())) {
			numberOfActualArguments = 0;
			Code.put(Code.pop);
			return;
			
		} else if("chr".equals(functionObj.getName())) {	
			numberOfActualArguments = 0;
			Code.put(Code.pop);
			return;
			
		}
		
		Method method = getMethodByName(functionObj.getName());
		if(method != null) {
			int numberOfFormalParameters = method.formalParameters.size();
			int numberOfOptionalArguments = method.optionalArguments.size();
			
			if(numberOfActualArguments < numberOfFormalParameters + numberOfOptionalArguments) {
				for(int i = numberOfActualArguments; i < numberOfOptionalArguments; i++) {
					Const constant = method.optionalArguments.get(i).getConst();
					
					if(constant instanceof NumConst) {
						int value = ((NumConst) constant).getN1();
						Code.loadConst(value);
						
					} else if(constant instanceof CharConst) {
						int value = ((CharConst) constant).getC1().charAt(1);
						Code.loadConst(value);
						
					} else if(constant instanceof BoolConst) {
						String stringValue = ((BoolConst) constant).getBool();
						if("true".equals(stringValue)) {
							Code.loadConst(1);
							
						} else {
							Code.loadConst(0);
							
						}
					}
				}
			}
		}
		
		int offset = functionObj.getAdr() - Code.pc;
		
		Code.put(Code.call);
		Code.put2(offset); // Offset contains 2 bytes
		
		if(designatorFunctionCall.getDesignator().obj.getType() != Tab.noType) {
			Code.put(Code.pop);
		}
	}
	
	public void visit(DesignatorArrayStatement designatorArrayStatement) {
		NumConst constant = (NumConst) designatorArrayStatement.getConst();
		Designator designator = designatorArrayStatement.getDesignator();
		int value = constant.getN1();
		
		Code.put(Code.pop);
		Code.put(Code.arraylength);
		
		int begginingOfLoop = Code.pc;
		Code.loadConst(1);
		Code.put(Code.sub);
		
		Code.put(Code.dup);
		Code.loadConst(0);
		Code.putFalseJump(Code.ge, 0);
		int addressToPatch = Code.pc - 2;
		
		Code.load(designator.obj);
		Code.put(Code.dup2);
		Code.put(Code.dup2);
		Code.put(Code.pop);
		Code.put(Code.aload);
		Code.loadConst(value);
		Code.put(Code.mul);
		Code.put(Code.astore);
		Code.putJump(begginingOfLoop);
		Code.fixup(addressToPatch);
		Code.put(Code.pop);
	}
	
	public void visit(DesignatorIdent designatorIdent) {
		SyntaxNode parent = designatorIdent.getParent();
		
		if(!(parent instanceof DesignatorAssignStatement) && !(parent instanceof FunctionCall) && !(parent instanceof DesignatorFunctionCall))  {
			Code.load(designatorIdent.obj);
		}
		
		if(designatorIdent.obj.getKind() == Obj.Meth) {
			numberOfActualArgumentsStack.push(new Integer(0));
		}
	}
	
	public void visit(DesignatorIdentArray designatorIdentArray) {
		SyntaxNode parent = designatorIdentArray.getParent();
		
		if(!(parent instanceof DesignatorAssignStatement) && !(parent instanceof DesignatorIncStatement) && !(parent instanceof DesignatorDecStatement)
				&& !(parent instanceof ReadStatement)) {
			Designator designator = designatorIdentArray.getDesignator();

			if(designator.obj.getType().getElemType() == Tab.intType || designator.obj.getType().getElemType() == SymbolTable.boolType) {
				Code.put(Code.aload);
				
			} else if(designator.obj.getType().getElemType() == Tab.charType) {
				Code.put(Code.baload);
			}
		}
	}
	
	public void visit(CoalesceExpression coalesceExpression) {
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		Code.put(Code.dup);
		Code.loadConst(0);
		
		Code.putFalseJump(Code.ne, 0);
		int addressToPatch = Code.pc - 2;
		
		// If the first expression is not equal to 0, then the second needs to be popped from the stack
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		Code.put(Code.pop);	
		Code.putJump(Code.pc + 4); // Size of the `jmp` instruction is 3 bytes, and syze of the `pop` instruction is 1 byte
		
		// If the first expression is equal to 0, then it needs to be popped from the stack
		Code.fixup(addressToPatch);
		Code.put(Code.pop);
	}
	
	public void visit(Ternary ternary) {
		// Get the condition on top of the stack
		Code.put(Code.dup_x2);
		Code.put(Code.pop);
		Code.put(Code.dup_x2);
		Code.put(Code.pop);
		Code.loadConst(1);
		
		Code.putFalseJump(Code.ne, 0);
		int addressToPatch = Code.pc - 2;
		
		// If the condition is false, then the first expression needs to be popped from the stack
		Code.put(Code.dup_x1);
		Code.put(Code.pop);
		Code.put(Code.pop);
		Code.putJump(Code.pc + 4); // Size of the `jmp` instruction is 3 bytes, and syze of the `pop` instruction is 1 byte
		
		// If the condition is true, then the second expression needs to be popped from the stack
		Code.fixup(addressToPatch);
		Code.put(Code.pop);			
	}
	
	public void visit(AddOpTermExpr addOpTermExpr) {
		Addop addop = addOpTermExpr.getAddop();
		
		if(addop instanceof AddopPlus) {
			Code.put(Code.add);
			
		} else {
			Code.put(Code.sub);
			
		}
	}
	
	public void visit(NegTermExpr negTermExpr) {
		Code.put(Code.neg);
	}
	
	public void visit(MulopFactor mulopFactor) {
		Mulop mulop = mulopFactor.getMulop();
		
		if(mulop instanceof MulopMul) {
			Code.put(Code.mul);
			
		} else if(mulop instanceof MulopDiv) {
			Code.put(Code.div);
			
		} else {
			Code.put(Code.rem);
			
		}
	}
	
	public void visit(NewArray newArray) {
		Code.put(Code.newarray);
		if (newArray.getType().struct == Tab.intType || newArray.getType().struct == SymbolTable.boolType) {
			Code.put(1);
		} else if(newArray.getType().struct == Tab.charType) {
			Code.put(0);
		}
	}
	
	public void visit(FunctionCall functionCall) {		
		Obj functionObj = functionCall.getDesignator().obj;
		
		int numberOfActualArguments = numberOfActualArgumentsStack.pop();
		
		if("len".equals(functionObj.getName())) {
			Code.put(Code.arraylength);
			numberOfActualArguments = 0;
			return;
			
		} else if("ord".equals(functionObj.getName())) {	
			numberOfActualArguments = 0;
			return;
			
		} else if("chr".equals(functionObj.getName())) {	
			numberOfActualArguments = 0;
			return;
			
		}
		
		Method method = getMethodByName(functionObj.getName());
		if(method != null) {
			int numberOfFormalParameters = method.formalParameters.size();
			int numberOfOptionalArguments = method.optionalArguments.size();
			
			if(numberOfActualArguments < numberOfFormalParameters + numberOfOptionalArguments) {
				int index = numberOfActualArguments - numberOfFormalParameters;
				for(int i = index; i < numberOfOptionalArguments; i++) {
					Const constant = method.optionalArguments.get(i).getConst();
					
					if(constant instanceof NumConst) {
						int value = ((NumConst) constant).getN1();
						Code.loadConst(value);
						
					} else if(constant instanceof CharConst) {
						int value = ((CharConst) constant).getC1().charAt(1);
						Code.loadConst(value);
						
					} else if(constant instanceof BoolConst) {
						String stringValue = ((BoolConst) constant).getBool();
						if("true".equals(stringValue)) {
							Code.loadConst(1);
							
						} else {
							Code.loadConst(0);
							
						}
					}
				}
			}
		}
		
		int offset = functionObj.getAdr() - Code.pc;
		
		Code.put(Code.call);
		Code.put2(offset); // Offset contains 2 bytes
	}
	
	public void visit(ActPar actPar) {
		int numberOfActualArguments = numberOfActualArgumentsStack.pop();
		numberOfActualArguments++;
		numberOfActualArgumentsStack.push(numberOfActualArguments);
	}
	
	/* Conditions */
	
	/* The names of the following variables suggest the source of the jump, not the destination. */
	private Stack<List<Integer>> orConditionAddressesToPatch = new Stack<List<Integer>>();
	private Stack<List<Integer>> andConditionAddressesToPatch = new Stack<List<Integer>>();
	private Stack<List<Integer>> thenBlockAddressesToPatch = new Stack<List<Integer>>();
	
	public void visit(UnmatchedIfElse unmatchedIfElse) {
		visitIfElseStatement();	
	}
	
	public void visit(MatchedStatement matchedStatement) {
		visitIfElseStatement();
	}
	
	private void visitIfElseStatement() {
		/* Patches the addresses which refer to the block which proceeds after
		 * `if-else` statement.
		 */
		for(int addressToPatch: thenBlockAddressesToPatch.peek()) {
			Code.fixup(addressToPatch);
		}
		
		thenBlockAddressesToPatch.peek().clear();
		
		thenBlockAddressesToPatch.pop();
		andConditionAddressesToPatch.pop();
		orConditionAddressesToPatch.pop();
	}
	
	public void visit(UnmatchedIf unmatchedIf) {
		/* At the end of the if statement, there are no more addresses 
		 * to be patched and the current scope can be restored to previous
		 * state. 
		 * 
		 * All of the addresses which refer to the block which comes
		 * after `if` statement have been processed in visit method of the 
		 * ThenStatementEnd.
		 */
		andConditionAddressesToPatch.pop();
		orConditionAddressesToPatch.pop();
		thenBlockAddressesToPatch.pop();
	}
	
	public void visit(IfConditionBegin ifConditionBegin) {
		/* At the beginning of the if statement, a new scope is open so 
		 * that the addresses can be patched in case of a forward jump to
		 * an unknown address.
		 */
		orConditionAddressesToPatch.push(new ArrayList<Integer>());
		andConditionAddressesToPatch.push(new ArrayList<Integer>());
		thenBlockAddressesToPatch.push(new ArrayList<Integer>());
	}
	
	public void visit(IfCond ifCond) {
		/* The condition consists of multiple conditions which are separated 
		 * with `or` operator, and if any of them is `true` there has to be
		 * a jump to the beginning of `then` block.
		 */
		for(int addressToPatch: orConditionAddressesToPatch.peek()) {
			Code.fixup(addressToPatch);
		}
		
		orConditionAddressesToPatch.peek().clear();
	}
	
	public void visit(ThenStatementEnd thenStatementEnd) {
		/* If there is an else block, at the end of `then` block there has to be an
		 * unconditional jump to the address right after `else` block. 
		 */
		if(thenStatementEnd.getParent() instanceof UnmatchedIfElse || thenStatementEnd.getParent() instanceof MatchedStatement) {
			Code.putJump(0); 		
			thenBlockAddressesToPatch.peek().add(Code.pc - 2); 
		}
		
		/* If there are no `or` conditions, that means that there are only `and` conditions,
		 * even if there is only one condition, which if they're not true need to jump to 
		 * the address right after `then` block, whatever proceeds, either `else` block, or if 
		 * there is no such, then whatever comes next after `then` block. 
		 */
		for(int addressToPatch: andConditionAddressesToPatch.peek()) {
			Code.fixup(addressToPatch);
		}
		
		andConditionAddressesToPatch.peek().clear();
	}
	
	public void visit(Or or) {
		/* If the condition on the left side of the `or` operator is true, then the rest of
		 * the condition is ignored.
		 */
		Code.putJump(0); 		
		orConditionAddressesToPatch.peek().add(Code.pc - 2);
				
		/* If the condition of the left side of the `or` operator is false, then there has to be
		 * a jump to the right side of the `or` operator and the rest of the condition needs to be 
		 * checked.
		 */
		for(int addressToPatch: andConditionAddressesToPatch.peek()) {
			Code.fixup(addressToPatch);
		}
		
		andConditionAddressesToPatch.peek().clear();
	}
	
	public void visit(CondFactExpr condFactExpr) {
		/* There is one boolean value on ExprStack which needs to be checked for equality with 
		 * `true` boolean value which is represented as an integer value of `1`.
		*/		
		Code.loadConst(1);
		Code.putFalseJump(Code.eq, 0);
		
		andConditionAddressesToPatch.peek().add(Code.pc - 2);
	}
	
	public void visit(CondFactRelopExpr condFactRelopExpr) {
		/* There are two integer values on ExprStack */ 
		
		Relop relop = condFactRelopExpr.getRelop();
		
		if(relop instanceof RelopEqual) {
			Code.putFalseJump(Code.eq, 0);
			
		} else if(relop instanceof RelopNotEqual) {
			Code.putFalseJump(Code.ne, 0);
			
		} else if(relop instanceof RelopLessOrEqual) {
			Code.putFalseJump(Code.le, 0);
			
		} else if(relop instanceof RelopGreaterOrEqual) {
			Code.putFalseJump(Code.ge, 0);
			
		} else if(relop instanceof RelopLess) {
			Code.putFalseJump(Code.lt, 0);
			
		} else if(relop instanceof RelopGreater) {
			Code.putFalseJump(Code.gt, 0);
			
		}
		
		/* `And` condition is the most nested one, therefore a conditional jump
		 * will always be generated after such condition.
		 */
		andConditionAddressesToPatch.peek().add(Code.pc - 2);
	}
	
	public void visit(FactorielFactor factorielFactor) {
		NumConst constant = (NumConst) factorielFactor.getConst();
		int value = constant.getN1();
		
		for(int i = 0; i < value - 1; i++) {
			Code.put(Code.dup);
			Code.loadConst(1);
			Code.put(Code.sub);
		}
		
		for(int i = 0; i < value - 1; i++) {
			Code.put(Code.mul);
		}
	}
	
	public void visit(BoolConst boolConst) {
		if(!(boolConst.getParent() instanceof OptArg) ) {
			Obj constObj = Tab.insert(Obj.Con, "$", boolConst.struct);
			constObj.setLevel(0);
			int adr = "true".equals(boolConst.getBool()) ? 1 : 0;
			constObj.setAdr(adr);
			
			Code.load(constObj);
		}
	}
	
	public void visit(CharConst charConst) {
		if(!(charConst.getParent() instanceof OptArg)) {
			Obj constObj = Tab.insert(Obj.Con, "$", charConst.struct);
			constObj.setLevel(0);
			constObj.setAdr(charConst.getC1().charAt(1));
			
			Code.load(constObj);
		}
	}
	
	public void visit(NumConst numConst) {
		if(!(numConst.getParent() instanceof OptArg) ) {
			Obj constObj = Tab.insert(Obj.Con, "$", numConst.struct);
			constObj.setLevel(0);
			constObj.setAdr(numConst.getN1());
			
			Code.load(constObj);
		}
	}
	
	/* Helpers */
	
	private Method getMethodByName(String name) {
		for(Method method: methods) {
			if(method.methodName.equals(name)) {
				return method;
			}
		}
		
		return null;
	}
	
}
