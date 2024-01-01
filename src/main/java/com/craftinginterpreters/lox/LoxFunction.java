package com.craftinginterpreters.lox;

import java.util.List;

public class LoxFunction implements LoxCallable {

    private final Stmt.Function declaration;
    private final Environment closure;

    public LoxFunction(Stmt.Function declaration, Environment closure) {
        this.declaration = declaration;
        this.closure = closure;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        // Define function environment (captured when function is defined)
        Environment environment = new Environment(closure);

        // Why traditional for loop?
        for (int i = 0; i < declaration.params.size(); i++) {
            // Fill parameters with arguments by copying them into function scope
            environment.define(declaration.params.get(i).lexeme,
                    arguments.get(i));
        }

        // A really clever way of handling return statements
        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            return returnValue.value;
        }

        return null;
    }

    @Override
    public String toString() {
        return "<fun" + declaration.name.lexeme + ">";
    }
}
