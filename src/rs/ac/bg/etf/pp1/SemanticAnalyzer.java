package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.*;
import rs.etf.pp1.symboltable.concepts.*;

public class SemanticAnalyzer extends VisitorAdaptor {

	Logger log = Logger.getLogger(getClass());
	
	public void report_error(String message, SyntaxNode info) {
		errorDetected = true;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0)
			msg.append(" na liniji ").append(line);
		log.error(msg.toString());
		System.err.println(msg.toString());
	}

	public void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0 : info.getLine();
		if (line != 0)
			msg.append(" na liniji ").append(line);
		log.info(msg.toString());
		System.out.println(msg.toString());
	}
	
	boolean errorDetected = false;
	
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
	
	/**
	 * Ispituje da li se dati tip vec nalazi u tabeli
	 * simbola, i ukoliko se ne nalazi to znaci da 
	 * nije validan tip. Ukoliko se nalazi, a tip 
	 * tog cvora nije Type, to znaci da je u nekom 
	 * ugnjezdenijem opsegu korisnik koristio kljucnu 
	 * rijec za naziv promjenljive.
	 */
	public void visit(Type type) {
		String typeName = type.getTypeName();
		Obj typeNode = Tab.find(typeName);
		
		if(typeNode == Tab.noObj) {
			errorDetected = true;
			type.struct = Tab.noType;
			report_error("Nije pronadjen tip " + typeName + " u tabeli simbola!", null);
		} else {
			if (typeNode.getKind() == Obj.Type) {
				type.struct = typeNode.getType();
			} else {
				errorDetected = true;
				type.struct = Tab.noType;
				report_error("Greska: Ime " + type.getTypeName() + " ne predstavlja tip!", type);
			}
		}
	}
	
}
