package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.CounterVisitor.*;
import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {

	private int mainPc;
	
	public int getMainPc() {
		return mainPc;
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
		
		VarCounter varCounter = new VarCounter();
		methodNode.traverseTopDown(varCounter);
		
		// Generate the entry
		Code.put(Code.enter);
		Code.put(formalParamCounter.getCount());
		Code.put(formalParamCounter.getCount() + varCounter.getCount());
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
		
	}
	
	public void visit(ContinueStatement continueStatement) {
		
	}
	
	public void visit(ReturnStatement returnStatement) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	public void visit(ReturnExprStatement returnExprStatement) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	public void visit(DoWhileStatement doWhileStatement) {
		
	}
	
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
		int offset = functionObj.getAdr() - Code.pc;
		
		Code.put(Code.call);
		Code.put2(offset); // Offset contains 2 bytes
		
		if(designatorFunctionCall.getDesignator().obj.getType() != Tab.noType) {
			Code.put(Code.pop);
		}
	}
	
	public void visit(DesignatorIdent designatorIdent) {
		SyntaxNode parent = designatorIdent.getParent();
		
		if(!(parent instanceof DesignatorAssignStatement) && !(parent instanceof FunctionCall) && !(parent instanceof DesignatorFunctionCall))  {
			Code.load(designatorIdent.obj);
		}
	}
	
	public void visit(DesignatorIdentArray designatorIdentArray) {
		SyntaxNode parent = designatorIdentArray.getParent();
		
		if(!(parent instanceof DesignatorAssignStatement) && !(parent instanceof DesignatorIncStatement) && !(parent instanceof DesignatorDecStatement)) {
			Designator designator = designatorIdentArray.getDesignator();

			if(designator.obj.getType().getElemType() == Tab.intType || designator.obj.getType().getElemType() == SymbolTable.boolType) {
				Code.put(Code.aload);
				
			} else if(designator.obj.getType().getElemType() == Tab.charType) {
				Code.put(Code.baload);
			}
		}
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
		int offset = functionObj.getAdr() - Code.pc;
		
		Code.put(Code.call);
		Code.put2(offset); // Offset contains 2 bytes
	}
	
	public void visit(BoolConst boolConst) {
		Obj constObj = Tab.insert(Obj.Con, "$", boolConst.struct);
		constObj.setLevel(0);
		int adr = "true".equals(boolConst.getBool()) ? 1 : 0;
		constObj.setAdr(adr);
		
		Code.load(constObj);
	}
	
	public void visit(CharConst charConst) {
		Obj constObj = Tab.insert(Obj.Con, "$", charConst.struct);
		constObj.setLevel(0);
		constObj.setAdr(charConst.getC1().charAt(1));
		
		Code.load(constObj);
	}
	
	public void visit(NumConst numConst) {
		Obj constObj = Tab.insert(Obj.Con, "$", numConst.struct);
		constObj.setLevel(0);
		constObj.setAdr(numConst.getN1());
		
		Code.load(constObj);
	}
	
}
