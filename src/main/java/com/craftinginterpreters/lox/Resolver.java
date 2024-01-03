package com.craftinginterpreters.lox;

/*
 *
 *  var a = "global";
 *  {
 *      fun showA() {
 *          print a;
 *      }
 *      showA();
 *      var a = "block";
 *      showA();
 *   }
 *
 *   The above code should produce
 *   global
 *   global
 *
 *   A variable usage refers to the preceding declaration with the same name
 *      in the innermost scope that encloses the expression where the variable is used.
 *
 *   A closure retains a reference to the Environment instance in play *when the function
 *      was declared* => Scheme Interpreters
 *
 *   Our solution is baking the static resolution into the access operation itself, instead of
 *      making the data more statically structured
 *      making the data more statically structured
 *
 *   If we could ensure that variable lookup always walked the same number of links in the
 *      environment chain, that would ensure that it found the same variable in the
 *      same scope every time.
 *
 *   So, to resolve a variable we only need to calculate how many hops away the declared variable
 *      will be in the environment chain.
 *
 *   The resolver is detached from both the Parser and Interpreter
 *      its job is to do static semantic analysis on the code
 *
 *   It walks the entire tree once, resolving each symbol scope.
 *
 */

import java.util.List;
import java.util.Stack;
import java.util.Map;
import java.util.HashMap;

public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

    private final Interpreter interpreter;
    private final Stack<HashMap<String, Boolean>> scopes = new Stack<>();

    private enum FunctionType {
        NONE,
        FUNCITON
    }

    private FunctionType currentFunction = FunctionType.NONE;

    public Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    public void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    private void resolve(Stmt statement) {
        statement.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    /*
     *  Look for symbol from innermost scope to the outermost scope.
     *      Looking in each map for a matching name. If we find the variable we resolve it,
     *      passing the number of scopes between the current innermost scope and the scope
     *      where the variable was found.
     *
     *  If we walk through the block scopes and never find the variable, we leave it
     *      unresolved and assume it's global.
     */
    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }

    private void resolveFunction(Stmt.Function function, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        beginScope();
        for (Token param : function.params) {
            declare(param);
            define(param);
        }
        resolve(function.body);
        endScope();

        currentFunction = enclosingFunction;
    }

    private void beginScope() {
        scopes.push(new HashMap<String, Boolean>());
    }

    private void endScope() {
        scopes.pop();
    }

    private void declare(Token name) {
        if (scopes.isEmpty()) return;

        Map<String, Boolean> scope = scopes.peek();
        // Local variables cannot shadow variables in the same scope like rust does.
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Already a variable with this name in this scope");
        }

        // Mark the variable as "not ready" yet. By putting false;
        // This means we have not finished resolving its initializer;
        scope.put(name.lexeme, false);
    }

    private void define(Token name) {
        if (scopes.isEmpty()) return;
        // Mark the variable as fully initialized
        scopes.peek().put(name.lexeme, true);
    }

    // Visitor for statement and expression

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for (Expr argument : expr.arguments) {
            resolve(argument);
        }

        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        // Nothing to resolve, really.
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        // Check if identifier name is used as its own initializer
        if (!scopes.isEmpty() &&
            scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
            Lox.error(expr.name, "Can't read local variable in its own initializer.");
        }

        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    /*
     *  Unlike variables, we define the name eagerly, before resolving function body.
     *  This lets a function recursively refer to itself inside its own body.
     */
    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCITON);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null)
            resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }

        if (stmt.value != null)
            resolve(stmt.value);
        return null;
    }

    /*
     * We split binding in two steps, declaring and defining
     *  in order to handle edge cases like this:
     *
     *  var a = "outer";
     *  {
     *  var a = a;
     *  }
     *  This should generate an error.
     *
     *  Resolving var statements happens in three steps.
     *  1. Declaring the variable
     *  2. Resolving the initializer
     *  3. Defining the variable
     */
    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null)
            resolve(stmt.initializer);
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }
}
