package com.craftinginterpreters.lox;

import java.util.List;
public interface LoxCallable {
    int arity(); // Related to the number of arguments the function expects.
    Object call(Interpreter interpreter, List<Object> arguments);
}
