package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.FormalParamDeclaration;
import rs.ac.bg.etf.pp1.ast.OptArg;
import rs.ac.bg.etf.pp1.ast.VarDecl;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;

public class CounterVisitor extends VisitorAdaptor {

	protected int count = 0;
	
	public int getCount() {
		return count;
	}
	
	public static class FormalParamCounter extends CounterVisitor {
		
		public void visit(FormalParamDeclaration formalParamDeclaration) {
			count++;
		}
		
	}
	
	public static class OptArgsCounter extends CounterVisitor {
		
		public void visit(OptArg optArg) {
			count++;
		}
		
	}
	
	public static class VarCounter extends CounterVisitor {
		
		public void visit(VarDecl varDecl) {
			count++;
		}
		
	}
	
}
