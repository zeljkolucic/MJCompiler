package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.*;
import rs.etf.pp1.symboltable.concepts.*;

public class SemanticPass extends VisitorAdaptor {

	Logger log = Logger.getLogger(getClass());
	
	/**
	 * Dodaje objektni cvor u tabelu simbola
	 * i cuva referencu na njega. Otvara novi opseg
	 * koji pripada programu.
	 */
	public void visit(ProgName progName) {
		int kind = Obj.Prog;
		String name = progName.getProgramName();
		Struct type = Tab.noType;
		
		progName.obj = Tab.insert(kind, name, type);
		Tab.openScope();
	}
	
	
	/**
	 * Uvezuje lokalne simbole koji pripadaju
	 * tekucem opsegu i postavlja polje `locals`
	 * klase ProgramName koja predstavlja objektni
	 * cvor. Zatvara tekuci opseg.
	 */
	public void visit(Program program) {
		ProgName progName = program.getProgName();
		Tab.chainLocalSymbols(progName.obj);
		Tab.closeScope();
	}
	
}
