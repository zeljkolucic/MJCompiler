package rs.ac.bg.etf.pp1;

import java.util.*;
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
	
	private boolean errorDetected = false;
	
	private int nVars;
	
	private List<ConstDecl> constDeclarations = new LinkedList<>();
	private List<VarDecl> varDeclarations = new LinkedList<>();
	
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
		nVars = Tab.currentScope.getnVars();
		ProgName progName = program.getProgName();
		Tab.chainLocalSymbols(progName.obj);
		Tab.closeScope();
	}
	
	/**
	 * Ispituje da li se dati tip vec nalazi u tabeli
	 * simbola, i ukoliko se ne nalazi to znaci da 
	 * nije validan tip. Ukoliko se nalazi, a vrsta 
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
			report_error("Greska (" + type.getLine() + "): Nije pronadjen tip " + typeName + " u tabeli simbola!", null);
		} else {
			if (typeNode.getKind() == Obj.Type) {
				type.struct = typeNode.getType();
			} else {
				errorDetected = true;
				type.struct = Tab.noType;
				report_error("Greska (" + type.getLine() + "):  Ime " + type.getTypeName() + " ne predstavlja tip!", null);
			}
		}
	}
	
	/**
	 * Obradjuje deklaracije konstanti odvojene zapetama.
	 * Dodaje deklarisanu konstantu u tabelu simbola, ispituje 
	 * tip pojedinacne konstante i upisuje njenu vrednost u 
	 * `adr` polje objektnog cvora.  
	 */
	public void visit(ConstDeclarations constDeclarations) {
		Struct type = constDeclarations.getType().struct;
		for(ConstDecl constDeclaration: this.constDeclarations) {
			String name = constDeclaration.getConstName();
			
			Obj constNode = Tab.insert(Obj.Con, name, type);
			Const constValue = constDeclaration.getConst();
			
			if(constValue instanceof NumConst) {
				if(!type.assignableTo(Tab.intType)) {
					errorDetected = true;
					report_error("Greska (" + constValue.getLine() + "): Konstanta " + name + " nije odgovarajuceg tipa!", null);
				}
				
				NumConst numConst = (NumConst) constValue;
				int value = numConst.getN1();
				constNode.setAdr(value);
				
			} else if(constValue instanceof CharConst) {
				if(!type.assignableTo(Tab.charType)) {
					errorDetected = true;
					report_error("Greska (" + constValue.getLine() + "): Konstanta " + name + " nije odgovarajuceg tipa!", null);
				}
				
				CharConst charConst = (CharConst) constValue;
				
				// Uzima se vrijednost karaktera sa pozicije 1, jer se na poziciji 0 nalazi '
				char character = charConst.getC1().charAt(1);
				constNode.setAdr(character);
				
			} else if(constValue instanceof BoolConst) {
				if(!type.assignableTo(SymbolTable.boolType)) {
					errorDetected = true;
					report_error("Greska (" + constValue.getLine() + "): Konstanta " + name + " nije odgovarajuceg tipa!", null);
				}
				
				BoolConst boolConst = (BoolConst) constValue;
				String value = boolConst.getBool();
				
				if("true".equals(value)) {
					constNode.setAdr(1);
				} else if("false".equals(value)) {
					constNode.setAdr(0);
				}
			}
		}
		
		this.constDeclarations.clear();
	}
	
	public void visit(ConstDecl constDecl) {
		constDeclarations.add(constDecl);
	}
	
	public void visit(VarDeclarations varDeclarations) {
		Struct type = varDeclarations.getType().struct;
		visitVarDeclarations(type);
	}
	
	public void visit(VarDeclarationsList varDeclarationsList) {
		Struct type = varDeclarationsList.getType().struct;
		visitVarDeclarations(type);
	}
	
	/**
	 * Pomocna metoda za obradu deklaracija promenljivih odvojenih zapetama.
	 * Dodaje deklarisanu promenljivu u tabelu simbola i ispituje 
	 * tip pojedinacne promenljive.  
	 */
	private void visitVarDeclarations(Struct type) {
		for(VarDecl varDeclaration: this.varDeclarations) {
			String name = varDeclaration.getVarName();
			Array array = varDeclaration.getArray();
			
			if(array instanceof IsArray) {
				Struct arrayType = new Struct(Struct.Array);
				arrayType.setElementType(type);
				Tab.insert(Obj.Var, name, arrayType);
				
			} else if(array instanceof NotArray) {
				Tab.insert(Obj.Var, name, type);
			}
		}
		
		this.varDeclarations.clear();
	}
	
	public void visit(VarDecl varDecl) {
		varDeclarations.add(varDecl);
	}
	
	public boolean passed() {
		return !errorDetected;
	}
	
}
