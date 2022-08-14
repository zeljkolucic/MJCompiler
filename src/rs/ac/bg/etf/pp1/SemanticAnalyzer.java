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
	
	private Obj currentMethod = null;
	private boolean returnFound = false;
	
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
			report_error("Greska [" + type.getLine() + "]: Nije pronadjen tip " + typeName + " u tabeli simbola!", null);
		} else {
			if (typeNode.getKind() == Obj.Type) {
				type.struct = typeNode.getType();
			} else {
				errorDetected = true;
				type.struct = Tab.noType;
				report_error("Greska [" + type.getLine() + "]:  Ime " + type.getTypeName() + " ne predstavlja tip!", null);
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
					report_error("Greska [" + constValue.getLine() + "]: Konstanta " + name + " nije odgovarajuceg tipa!", null);
				}
				
				NumConst numConst = (NumConst) constValue;
				int value = numConst.getN1();
				constNode.setAdr(value);
				
			} else if(constValue instanceof CharConst) {
				if(!type.assignableTo(Tab.charType)) {
					errorDetected = true;
					report_error("Greska [" + constValue.getLine() + "]: Konstanta " + name + " nije odgovarajuceg tipa!", null);
				}
				
				CharConst charConst = (CharConst) constValue;
				
				// Uzima se vrijednost karaktera sa pozicije 1, jer se na poziciji 0 nalazi '
				char character = charConst.getC1().charAt(1);
				constNode.setAdr(character);
				
			} else if(constValue instanceof BoolConst) {
				if(!type.assignableTo(SymbolTable.boolType)) {
					errorDetected = true;
					report_error("Greska [" + constValue.getLine() + "]: Konstanta " + name + " nije odgovarajuceg tipa!", null);
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
	
	/**
	 * Obradjuje deklaraciju methode i dodaje je 
	 * u tabelu simbola. Otvara novi opseg za datu
	 * metodu.
	 */
	public void visit(MethodTypeName methodTypeName) {
		ReturnType returnType = methodTypeName.getReturnType();
		Struct type = Tab.noType;
		
		if(returnType instanceof RetType) 
			type = ((RetType) returnType).getType().struct;
		
		String name = methodTypeName.getMethodName();
		currentMethod = Tab.insert(Obj.Meth, name, type);
		
		methodTypeName.obj = currentMethod;
		Tab.openScope();
	}
	
	/**
	 * Proverava da li metoda koja se trenutno obradjuje 
	 * ima return iskaz u zavisnosti od njenog povratnog tipa.
	 * Uvezuje promenljive iz tekuceg opsega i zatvara taj opseg.
	 */
	public void visit(MethodDecl methodDecl) {
		if(!returnFound && currentMethod.getType() != Tab.noType) {
			errorDetected = true;
			report_error("Greska [" + methodDecl.getLine() + "]: Funkcija " + currentMethod.getName() + " nema return iskaz!", null);
		}
		
		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();
		
		returnFound = false;
		currentMethod = null;
	}
	
	public void visit(ReturnExprStatement returnExprStatement) {
		returnFound = true;
		
		Struct currentMethodType = currentMethod.getType();
		Struct expr = returnExprStatement.getExpr().struct;
		
		if(expr.getKind() == Struct.Array) {
			expr = expr.getElemType();
		}
		
		if(!currentMethodType.compatibleWith(expr)) {
			errorDetected = true;
			report_error("Greska [" + returnExprStatement.getLine() + "]: Tip izraza u return naredbi se ne slaze sa tipom povratne vrednosti funkcije!", null);
		}
	}
	
	public void visit(ReturnStatement returnStatement) {
		returnFound = true;
		
		Struct currentMethodType = currentMethod.getType();
		if(currentMethodType != Tab.noType) {
			errorDetected = true;
			report_error("Greska [" + returnStatement.getLine() + "]: Tip izraza u return naredbi se ne slaze sa tipom povratne vrednosti funkcije!", null);
		}
	}
	
	public void visit(DesignatorAssignStatement designatorAssignStatement) {
		Designator designator = designatorAssignStatement.getDesignator();
		Expr expr = designatorAssignStatement.getExpr();
		
		if(designator.obj.getKind() == Obj.Meth ) {
			errorDetected = true;
			report_error("Greska [" + designatorAssignStatement.getLine() + "]: Odrediste u izrazu dodele ne moze biti metoda!", null);
			
		} else if(!expr.struct.assignableTo(designator.obj.getType())) {
			errorDetected = true;
			report_error("Greska [" + designatorAssignStatement.getLine() + "]: Nekompatibilni tipovi u izrazu dodele!", null);
		}
	}
	
	public void visit(DesignatorIncStatement designatorIncStatement) {
		
	}
	
	public void visit(DesignatorDecStatement designatorDecStatement) {
		
	}
	
	public void visit(DesignatorFunctionCall designatorFunctionCall) {
		
	}
	
	public void visit(DesignatorIdent designatorIdent) {
		Obj obj = Tab.find(designatorIdent.getI1());
		if(obj == Tab.noObj) {
			errorDetected = true;
			report_error("Greska [" + designatorIdent.getLine() + "]: Ime " + designatorIdent.getI1() + " nije deklarisano!", null);
			designatorIdent.obj = Tab.noObj;
			
		} else {
			designatorIdent.obj = obj;
		}
	}
	
	public void visit(DesignatorIdentArray designatorIdentArray) {
		Designator designator = designatorIdentArray.getDesignator();
		if(designator.obj.getType().getKind() == Struct.Array) {
			Struct expr = designatorIdentArray.getExpr().struct;
			if(!expr.compatibleWith(Tab.intType)) {
				errorDetected = true;
				report_error("Greska [" + designatorIdentArray.getLine() + "]: Izraz unutar [] mora biti tipa int!", null);
				designatorIdentArray.obj = Tab.noObj;
				
			} else {
				int kind = designator.obj.getType().getElemType().getKind();
				String name = designator.obj.getName();
				Struct type = designator.obj.getType().getElemType();
				
				designatorIdentArray.obj = new Obj(kind, name, type);
			}
		} else {
			errorDetected = true;
			report_error("Greska [" + designatorIdentArray.getLine() + "]: " + designator.obj.getName() + " mora biti deklarisan kao niz!", null);
			designatorIdentArray.obj = Tab.noObj;
		}			
	}
	
	/**
	 * Oba sabirka moraju da budu istog tipa, i to konkretno 
	 * tipa int, u suprotnom se radi o gresci.
	 */
	public void visit(AddOpTermExpr addOpTermExpr) {
		Struct te = addOpTermExpr.getExpr().struct;
		Struct t = addOpTermExpr.getTerm().struct;
		
		if(te.compatibleWith(t) && te == Tab.intType) {
			addOpTermExpr.struct = te;
			
		} else {
			errorDetected = true;
			report_error("Greska [" + addOpTermExpr.getLine() + "]: Nekomaptibilni tipovi u izrazu za sabiranje!", null);
			addOpTermExpr.struct = Tab.noType;
		}
	}
	
	/**
	 * S obzirom da se radi o negaciji izraza, tip Term-a
	 * mora da bude int, u suprotnom se radi o gresci.
	 */
	public void visit(NegTermExpr negTermExpr) {
		Struct type = negTermExpr.getTerm().struct;
		if(!type.assignableTo(Tab.intType)) {
			errorDetected = true;
			report_error("Greska [" + negTermExpr.getLine() + "]: Izraz mora biti tipa int!", null);
			negTermExpr.struct = Tab.noType;
			
		} else {
			negTermExpr.struct = type;
		}
	}
	
	public void visit(PosTermExpr posTermExpr) {
		posTermExpr.struct = posTermExpr.getTerm().struct;
	}
	
	/**
	 * Ukoliko je tip Factor-a int, tada i MulFacList mora da bude
	 * istog tipa, jer zajedno ucestvuju u operaciji mnozenja. Tip Factor-a
	 * ne mora da bude int, i tada celi Term dobija isti tip kao i dati Factor.
	 */
	public void visit(Term term) {
		Struct factorType = term.getFactor().struct;
		MulFacList mulFacList = term.getMulFacList();
		
		if(factorType.assignableTo(Tab.intType)) {
			if(mulFacList instanceof MulopFactor && !mulFacList.struct.assignableTo(Tab.intType) ) {
				errorDetected = true;
				report_error("Greska [" + term.getLine() + "]: Cinilac mora da bude tipa int!", null);
				term.struct = Tab.noType;
			} else {
				term.struct = factorType;
			}
		} else {
			term.struct = factorType;
		}
	}
	
	/**
	 * S obzirom da se radi o operaciji mnozenja, 
	 * jedino ima smisla da su cinioci tipa int. U suprotnom,
	 * radi se o gresci.
	 */
	public void visit(MulopFactor mulopFactor) {
		Struct factorType = mulopFactor.getFactor().struct;
		if(!factorType.assignableTo(Tab.intType)) {
			errorDetected = true;
			report_error("Greska [" + mulopFactor.getLine() + "]: Cinilac mora da bude tipa int!", null);
			mulopFactor.struct = Tab.noType;
			
		} else {
			mulopFactor.struct = factorType;
		}
	}
	
	public void visit(NoMulopFactor noMulopFactor) {
		noMulopFactor.struct = Tab.noType;
	}
	
	public void visit(ConstFactor constFactor) {
		constFactor.struct = constFactor.getConst().struct;
	}
	
	public void visit(ExprFactor exprFactor) {
		exprFactor.struct = exprFactor.getExpr().struct;
	}
	
	public void visit(NewArray newArray) {
		Struct exprStruct = newArray.getExpr().struct;
		if(!exprStruct.assignableTo(Tab.intType)) {
			errorDetected = true;
			report_error("Greska [" + newArray.getLine() + "]: Izraz unutar [] mora biti tipa int!", null);
			newArray.struct = Tab.noType;
			
		} else {
			Struct elementType = newArray.getType().struct;
			newArray.struct = new Struct(Struct.Array);
			newArray.struct.setElementType(elementType);
		}
	}
	
	public void visit(Var var) {
		var.struct = var.getDesignator().obj.getType();
	}
	
	public void visit(NewObj newObj) {
		newObj.struct = newObj.getType().struct;
	}
	
	public void visit(BoolConst boolConst) {
		boolConst.struct = SymbolTable.boolType;
	}
	
	public void visit(CharConst charConst) {
		charConst.struct = Tab.charType;
	}
	
	public void visit(NumConst numConst) {
		numConst.struct = Tab.intType;
	}
	
	public boolean passed() {
		return !errorDetected;
	}
	
}
