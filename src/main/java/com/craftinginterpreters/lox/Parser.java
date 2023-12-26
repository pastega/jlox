package com.craftinginterpreters.lox;

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

    public Expr parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
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
     *  expression -> equality
     *  equality -> comparison ( ("!=" | "==" ) comparison)*;
     *  comparison -> term ( ( ">" | ">=" | "<" | "<=" ) term )*;
     *  term -> factor ( ( "-" | "+" ) factor )*;
     *  factor -> unary ( ( "/" | "*" ) unary )*;
     *  unary -> ( "!" | "-" ) unary | primary;
     *  primary -> NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")";
     *
     *  ** higher precedence (bottom of our grammar)
     *
     *  Recursive descent parsing technique
     *
     *  Each of these methods represent a grammar rule
     */

    // expression -> equality
    private Expr expression() {
        return equality();
    }

    // equality -> comparison ( ("!=" | "==" ) comparison)*;
    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // comparison -> term ( ( ">" | ">=" | "<" | "<=" ) term )*;
    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // term -> factor ( ( "-" | "+" ) factor )*;
    private Expr term() {
        Expr expr = factor();

        while(match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // factor -> unary ( ( "/" | "*" ) unary )*;
    private Expr factor() {
        Expr expr = unary();

        while(match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    // unary -> ( "!" | "-" ) unary | primary;
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        // If execution reaches this point. We got the highest level of precedence
        return primary();
    }

    // primary -> NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")";
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
