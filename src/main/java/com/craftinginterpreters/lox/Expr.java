package com.craftinginterpreters.lox;

import java.util.List;

class Expr {
	static class Binary extends Expr {
		public Binary(Expr left, Token operator, Expr right) {
			this.left = left;
			this.operator = operator;
			this.right = right;
		}
		public Expr left;
		public Token operator;
		public Expr right;
	}
	static class Grouping extends Expr {
		public Grouping(Expr expression) {
			this.expression = expression;
		}
		public Expr expression;
	}
	static class Literal extends Expr {
		public Literal(Object value) {
			this.value = value;
		}
		public Object value;
	}
	static class Unary extends Expr {
		public Unary(Token operator, Expr right) {
			this.operator = operator;
			this.right = right;
		}
		public Token operator;
		public Expr right;
	}
}