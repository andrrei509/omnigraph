package com.omnigraph.expr;

import java.util.function.DoubleUnaryOperator;

/**
 * Recursive-descent parser/compiler for single-variable real expressions.
 *
 * <p>Compiles a string such as {@code "sin(x) + 0.5*cos(3*x)"} into a
 * {@link DoubleUnaryOperator} that maps {@code x -> f(x)}. Supports {@code + - *
 * / ^}, parentheses, unary minus, the variable {@code x}, the constants
 * {@code pi} and {@code e}, and the functions {@code sin cos tan exp log ln sqrt
 * abs}.
 *
 * <p>Grammar (precedence low to high):
 * <pre>
 *   expr   := term (('+'|'-') term)*
 *   term   := power (('*'|'/') power)*
 *   power  := unary ('^' power)?        // right-associative
 *   unary  := ('+'|'-') unary | atom
 *   atom   := number | 'x' | const | func '(' expr ')' | '(' expr ')'
 * </pre>
 */
public final class FunctionParser {

    private final String src;
    private int pos;

    private FunctionParser(String src) {
        this.src = src;
    }

    public static DoubleUnaryOperator parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new ExpressionException("empty expression");
        }
        FunctionParser parser = new FunctionParser(expression);
        DoubleUnaryOperator fn = parser.parseExpr();
        parser.skipSpaces();
        if (parser.pos < parser.src.length()) {
            throw new ExpressionException("unexpected character '" + parser.peek()
                    + "' at position " + parser.pos);
        }
        return fn;
    }

    private DoubleUnaryOperator parseExpr() {
        DoubleUnaryOperator left = parseTerm();
        while (true) {
            skipSpaces();
            char c = peek();
            if (c == '+') {
                pos++;
                DoubleUnaryOperator right = parseTerm();
                DoubleUnaryOperator l = left;
                left = x -> l.applyAsDouble(x) + right.applyAsDouble(x);
            } else if (c == '-') {
                pos++;
                DoubleUnaryOperator right = parseTerm();
                DoubleUnaryOperator l = left;
                left = x -> l.applyAsDouble(x) - right.applyAsDouble(x);
            } else {
                return left;
            }
        }
    }

    private DoubleUnaryOperator parseTerm() {
        DoubleUnaryOperator left = parsePower();
        while (true) {
            skipSpaces();
            char c = peek();
            if (c == '*') {
                pos++;
                DoubleUnaryOperator right = parsePower();
                DoubleUnaryOperator l = left;
                left = x -> l.applyAsDouble(x) * right.applyAsDouble(x);
            } else if (c == '/') {
                pos++;
                DoubleUnaryOperator right = parsePower();
                DoubleUnaryOperator l = left;
                left = x -> l.applyAsDouble(x) / right.applyAsDouble(x);
            } else {
                return left;
            }
        }
    }

    private DoubleUnaryOperator parsePower() {
        DoubleUnaryOperator base = parseUnary();
        skipSpaces();
        if (peek() == '^') {
            pos++;
            DoubleUnaryOperator exp = parsePower();
            DoubleUnaryOperator b = base;
            return x -> Math.pow(b.applyAsDouble(x), exp.applyAsDouble(x));
        }
        return base;
    }

    private DoubleUnaryOperator parseUnary() {
        skipSpaces();
        char c = peek();
        if (c == '+') {
            pos++;
            return parseUnary();
        }
        if (c == '-') {
            pos++;
            DoubleUnaryOperator operand = parseUnary();
            return x -> -operand.applyAsDouble(x);
        }
        return parseAtom();
    }

    private DoubleUnaryOperator parseAtom() {
        skipSpaces();
        char c = peek();

        if (c == '(') {
            pos++;
            DoubleUnaryOperator inner = parseExpr();
            expect(')');
            return inner;
        }

        if (Character.isDigit(c) || c == '.') {
            return constant(parseNumber());
        }

        if (Character.isLetter(c)) {
            String name = parseIdentifier();
            switch (name) {
                case "x":
                    return x -> x;
                case "pi":
                    return constant(Math.PI);
                case "e":
                    return constant(Math.E);
                default:
                    return parseFunction(name);
            }
        }

        throw new ExpressionException("unexpected character '" + c + "' at position " + pos);
    }

    private DoubleUnaryOperator parseFunction(String name) {
        skipSpaces();
        expect('(');
        DoubleUnaryOperator arg = parseExpr();
        expect(')');
        switch (name) {
            case "sin":
                return x -> Math.sin(arg.applyAsDouble(x));
            case "cos":
                return x -> Math.cos(arg.applyAsDouble(x));
            case "tan":
                return x -> Math.tan(arg.applyAsDouble(x));
            case "exp":
                return x -> Math.exp(arg.applyAsDouble(x));
            case "log":
            case "ln":
                return x -> Math.log(arg.applyAsDouble(x));
            case "sqrt":
                return x -> Math.sqrt(arg.applyAsDouble(x));
            case "abs":
                return x -> Math.abs(arg.applyAsDouble(x));
            default:
                throw new ExpressionException("unknown function '" + name + "'");
        }
    }

    private double parseNumber() {
        int start = pos;
        while (pos < src.length()
                && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
            pos++;
        }
        // optional exponent
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            int save = pos;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            if (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            } else {
                pos = save; // not an exponent after all
            }
        }
        try {
            return Double.parseDouble(src.substring(start, pos));
        } catch (NumberFormatException e) {
            throw new ExpressionException("invalid number at position " + start);
        }
    }

    private String parseIdentifier() {
        int start = pos;
        while (pos < src.length() && Character.isLetter(src.charAt(pos))) {
            pos++;
        }
        return src.substring(start, pos);
    }

    private static DoubleUnaryOperator constant(double value) {
        return x -> value;
    }

    private void expect(char c) {
        skipSpaces();
        if (peek() != c) {
            throw new ExpressionException("expected '" + c + "' at position " + pos);
        }
        pos++;
    }

    private void skipSpaces() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }
}
