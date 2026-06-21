package com.omnigraph.expr;

/** Thrown when a user-supplied function string cannot be parsed. */
public final class ExpressionException extends RuntimeException {

    public ExpressionException(String message) {
        super(message);
    }
}
