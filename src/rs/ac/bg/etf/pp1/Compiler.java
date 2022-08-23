package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import java_cup.runtime.Symbol;
import rs.ac.bg.etf.pp1.SemanticAnalyzer.Method;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Scope;

public class Compiler {

	static {
		DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}
	
	public static void tsdump() {
		MySymbolTableVisitor symbolTableVisitor = new MySymbolTableVisitor();
		Scope sc = Tab.currentScope;
		sc.accept(symbolTableVisitor);
		System.out.println(symbolTableVisitor.getOutput());
	}
	
	
	public static void main(String[] args) throws Exception {
		String fileName = args[0];
		if(fileName.contains("/")) {
			fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
		}
		
		Logger log = Logger.getLogger(Compiler.class);
		
		Reader br = null;
		try {
			File sourceCode = new File("test/" + fileName + ".mj");
			log.info("Compiling source file: " + sourceCode.getAbsolutePath());
			
			br = new BufferedReader(new FileReader(sourceCode));
			Yylex lexer = new Yylex(br);
			
			MJParser p = new MJParser(lexer);
	        Symbol s = p.parse(); 
	        
	        Program prog = (Program)(s.value); 
	        SymbolTable.init();
			
			log.info(prog.toString(""));				

			SemanticAnalyzer v = new SemanticAnalyzer();
			prog.traverseBottomUp(v); 
			
			System.out.println("=====================SYMBOL TABLE DUMP=========================");
			tsdump();
			
			if(!p.errorDetected && v.passed()) {
				log.info("Parsiranje uspesno zavrseno!");
				
				String outputFilePath = args[1];
				File objFile = new File(outputFilePath + fileName + ".obj");
				if(objFile.exists()) 
					objFile.delete();
				FileOutputStream fileOutputStream = new FileOutputStream(objFile);				
				
				LinkedList<Method> methods = v.getMethods();
				CodeGenerator codeGenerator = new CodeGenerator(methods);
				prog.traverseBottomUp(codeGenerator);
				
				Code.dataSize = v.nVars;
				Code.mainPc = codeGenerator.getMainPc();
				
				Code.write(fileOutputStream);
				
			} else {
				log.error("Parsiranje nije uspesno zavrseno!");
			}
		} 
		finally {
			if (br != null) 
				try { 
					br.close(); 
				} catch (IOException e1) { 
					log.error(e1.getMessage(), e1); 
				}
		}
	}

}
