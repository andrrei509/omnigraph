package com.omnigraph.expr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.DoubleUnaryOperator;

import org.junit.jupiter.api.Test;

class FunctionParserTest {

    @Test
    void respectsOperatorPrecedence() {
        assertEquals(14.0, FunctionParser.parse("2 + 3 * 4").applyAsDouble(0), 1e-12);
        assertEquals(20.0, FunctionParser.parse("(2 + 3) * 4").applyAsDouble(0), 1e-12);
    }

    @Test
    void powerIsRightAssociative() {
        assertEquals(512.0, FunctionParser.parse("2 ^ 3 ^ 2").applyAsDouble(0), 1e-9);
    }

    @Test
    void evaluatesFunctionsAndVariable() {
        DoubleUnaryOperator f = FunctionParser.parse("sin(x) + 0.5*cos(2*x)");
        assertEquals(Math.sin(1.3) + 0.5 * Math.cos(2 * 1.3), f.applyAsDouble(1.3), 1e-12);
    }

    @Test
    void handlesUnaryMinusAndConstants() {
        assertEquals(-5.0, FunctionParser.parse("-x").applyAsDouble(5), 1e-12);
        assertEquals(Math.PI, FunctionParser.parse("pi").applyAsDouble(0), 1e-12);
        assertEquals(3.0, FunctionParser.parse("abs(-3)").applyAsDouble(0), 1e-12);
    }

    @Test
    void rejectsMalformedExpressions() {
        assertThrows(ExpressionException.class, () -> FunctionParser.parse("2 +"));
        assertThrows(ExpressionException.class, () -> FunctionParser.parse("sin(x"));
        assertThrows(ExpressionException.class, () -> FunctionParser.parse(""));
    }
}
