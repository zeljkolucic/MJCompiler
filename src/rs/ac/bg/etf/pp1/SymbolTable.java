package rs.ac.bg.etf.pp1;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.*;

public class SymbolTable extends Tab {

	public static final Struct boolType = new Struct(Struct.Bool);
	
	public static void init() {
		Tab.init();
		
		Scope universe = Tab.currentScope();
		universe.addToLocals(new Obj(Obj.Type, "bool", boolType));
	}
	
}
