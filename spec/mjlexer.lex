package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol; 

%%

%{

	private Symbol new_symbol(int type) {
		return new Symbol(type, yyline + 1, yycolumn);
	}

	private Symbol new_symbol(int type, Object value) {
		return new Symbol(type, yyline + 1, yycolumn, value);
	}

%}

%cup
%line
%column

%xstate COMMENT

%eofval{
	return new_symbol(sym.EOF);
%eofval}

%%

" "			{ }
"\b"		{ }
"\t"		{ }
"\r\n"		{ }
"\f"		{ }

"program" 	{ return new_symbol(sym.PROGRAM, yytext()); }
"read"		{ return new_symbol(sym.READ, yytext()); }
"print"		{ return new_symbol(sym.PRINT, yytext()); }
"const" 	{ return new_symbol(sym.CONST, yytext()); }

"if"		{ return new_symbol(sym.IF, yytext()); }
"else" 		{ return new_symbol(sym.ELSE, yytext()); }
"do"		{ return new_symbol(sym.DO, yytext()); }
"while" 	{ return new_symbol(sym.WHILE, yytext()); }
"break" 	{ return new_symbol(sym.BREAK, yytext()); }
"continue" 	{ return new_symbol(sym.CONTINUE, yytext()); }
"return"	{ return new_symbol(sym.RETURN, yytext()); }
"new" 		{ return new_symbol(sym.NEW, yytext()); }

"void" 		{ return new_symbol(sym.VOID, yytext()); }

"++"		{ return new_symbol(sym.INC, yytext()); }
"--"		{ return new_symbol(sym.DEC, yytext()); }
"+"			{ return new_symbol(sym.PLUS, yytext()); }
"-"			{ return new_symbol(sym.MINUS, yytext()); }
"*"			{ return new_symbol(sym.MUL, yytext()); } 
"/"			{ return new_symbol(sym.DIV, yytext()); } 
"%"			{ return new_symbol(sym.MOD, yytext()); }

"=="		{ return new_symbol(sym.EQUAL, yytext()); }
"!="		{ return new_symbol(sym.NOTEQUAL, yytext()); }
"<="		{ return new_symbol(sym.LESS_OR_EQUAL, yytext()); }
">="		{ return new_symbol(sym.GREATER_OR_EQUAL, yytext()); }
"<" 		{ return new_symbol(sym.LESS, yytext()); }
">"			{ return new_symbol(sym.GREATER, yytext()); }
"&&"		{ return new_symbol(sym.AND, yytext()); }
"||"		{ return new_symbol(sym.OR, yytext()); }

"="			{ return new_symbol(sym.ASSIGN, yytext()); }

":"			{ return new_symbol(sym.COLON, yytext()); }
";"			{ return new_symbol(sym.SEMI, yytext()); }
","			{ return new_symbol(sym.COMMA, yytext()); }
"("			{ return new_symbol(sym.LPAREN, yytext()); }
")"			{ return new_symbol(sym.RPAREN, yytext()); }
"{"			{ return new_symbol(sym.LBRACE, yytext()); }
"}"			{ return new_symbol(sym.RBRACE, yytext()); }
"["			{ return new_symbol(sym.LBRACKET, yytext()); }
"]"			{ return new_symbol(sym.RBRACKET, yytext()); }


"//"				{ yybegin(COMMENT); }
<COMMENT> . 		{ yybegin(COMMENT); }
<COMMENT> "\r\n" 	{ yybegin(YYINITIAL); }

"true" | "false"				{ return new_symbol(sym.BOOL_CONST, yytext()); }
\'.\'							{ return new_symbol(sym.CHAR_CONST, yytext()); }
[0-9]+							{ return new_symbol(sym.NUM_CONST, new Integer(yytext())); }
([a-z]|[A-Z])[a-z|A-Z|0-9|_]*	{ return new_symbol(sym.IDENT, yytext()); }

.			{ System.err.println("Leksicka greska (" + yytext() + ") u liniji " + (yyline + 1) + " i koloni " + (yycolumn + 1)); }

















