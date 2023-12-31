package com.craftinginterpreters.lox;

import javax.swing.plaf.basic.BasicTreeUI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

public class Parser {

    private static class ParseError extends RuntimeException {}
    private final List<Token> tokens;

    // Points to the next token to be parsed
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();

        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    /*
     *  Each method produces a syntax tree for that rule
     *      and returns it to the caller.
     *
     *  When the body of the rule contains a non-terminal - a reference to another rule
     *      - we call that other rule's method
     *
     */

    /*
     *  Current grammar for the Lox programming language
     *
     *  ** lower precedence (top of our grammar)
     *
     *  STATEMENT
     *
     *  program -> declaration* EOF;
     *
     *  declaration -> varDecl | statement;
     *  varDecl -> "var" IDENTIFIER ("=" expression)? ";";
     *
     *  statement -> exprStmt | forStmt | ifStmt | printStmt | whileStatement | block;
     *  exprStmt -> expression ";";
     *  forStmt -> "for" "(" (varDecl | exprStmt | ";") expression? ";" expression? ")" statement;
     *  ifStmt -> "if" "(" expression ")" statement ("else" statement)?;
     *  printStmt -> "print" expression ";";
     *  whileStatement -> "while" "(" expression ")" statement;
     *  block -> "{" declaration* "}";
     *
     *  EXPRESSION
     *
     *  expression -> assignment;
     *  assignment -> IDENTIFIER "=" assignment | logic_or;
     *  logic_or -> logic_and ("or" logic_and)*;
     *  logic_and -> equality ("and" equality)*;
     *  equality -> comparison ( ("!=" | "==" ) comparison)*;
     *  comparison -> term ( ( ">" | ">=" | "<" | "<=" ) term )*;
     *  term -> factor ( ( "-" | "+" ) factor )*;
     *  factor -> unary ( ( "/" | "*" ) unary )*;
     *  unary -> ( "!" | "-" ) unary | primary;
     *  primary -> IDENTIFIER | NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")";
     *
     *  ** higher precedence (bottom of our grammar)
     *
     *  Recursive descent parsing technique
     *
     *  Each of these methods represent a grammar rule
     */

    // STATEMENT

    // This is the entry point for our grammar
    //  all the parsing begins here...
    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expected variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expected ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt statement() {
        if (match(PRINT)) return printStatement();
        if (match(IF)) return ifStatement();
        if (match(WHILE)) return whileStatement();
        if (match(FOR)) return forStmt();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expected ';' after expression");
        return new Stmt.Expression(expr);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expected ';' after value");
        return new Stmt.Print(value);
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expected '(' after if.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expected ')' after condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expected '(' after while.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expected ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt forStmt() {
        consume(LEFT_PAREN, "Expected '(' after for.");
        Stmt initializer;
        if (match(SEMICOLON))
            initializer = null;
        else if (match(VAR))
            initializer = varDeclaration();
        else
            initializer = expressionStatement();

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses");

        // Construct a equivalent while loop
        Stmt body = statement(); // Parse the body

        if (increment != null) {
            body = new Stmt.Block(
                    Arrays.asList(
                            body,
                            new Stmt.Expression(increment) // Insert increment at the end
                    )
            );
        }

        if (condition == null) // Check for infinite loop
            condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body); // Create a while loop

        if (initializer != null) {
            // Insert the initializer before the while loop
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expected '}' at the end of block.");
        return statements;
    }

    // EXPRESSION

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = or(); // possible lvalue
        // If assignment, this will eventually parse our identifier in primary()

        // What if the (possible) lvalue is not an identifier, but a literal ou anything invalid?
        // This should be treated inside the Interpreter

        if (match(EQUAL)) {
            // This is an assignment
            Token equals = previous();
            Expr value = assignment(); // Recursion

            // if valid rvalue
            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }
            // not a valid rvalue
            throw error(equals, "Invalid assignment target.");
        }
        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while(match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while(match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        // If execution reaches this point. We got the highest level of precedence
        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        throw error(peek(), "Expected expression!");
    }

    /*
     *  Helper methods
     */

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    /**
     * @return whether the current token is of the given type
     * and advances if so
     */
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private void synchronize() {
        advance();

        // Discards tokens util it finds a statement boundary
        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

}
