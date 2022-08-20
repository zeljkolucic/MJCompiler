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
