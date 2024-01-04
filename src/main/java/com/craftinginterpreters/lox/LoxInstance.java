package com.craftinginterpreters.lox;

import java.util.Map;
import java.util.HashMap;

public class LoxInstance {

    private final LoxClass loxClass;
    private final Map<String, Object> fields = new HashMap<>();
    LoxInstance(LoxClass loxClass) {
        this.loxClass = loxClass;
    }

    public Object get(Token name) {
        if (fields.containsKey(name.lexeme)) {
            return fields.get(name.lexeme);
        }

        LoxFunction method = loxClass.findMethod(name.lexeme);
        if (method != null) return method.bind(this);

        throw new RuntimeError(name, "Undefined property '" + name.lexeme + "'.");
    }

    public void set(Token name, Object value) {
        fields.put(name.lexeme, value);
    }

    @Override
    public String toString() {
        return "<instance of " + loxClass.name + ">";
    }
}
