package com.omnigraph.model;

/**
 * A single rotating vector in a Fourier epicycle chain.
 *
 * <p>At parameter {@code t} in {@code [0,1)} it contributes the complex value
 * {@code amplitude * exp(i*(2*PI*frequency*t + phase))}. Summing every epicycle
 * retraces the decomposed path.
 *
 * @param frequency integer cycles per loop (may be negative)
 * @param amplitude radius of this circle
 * @param phase     starting angle in radians
 */
public record Epicycle(int frequency, double amplitude, double phase) {

    public double xAt(double t) {
        return amplitude * Math.cos((2.0 * Math.PI * frequency * t) + phase);
    }

    public double yAt(double t) {
        return amplitude * Math.sin((2.0 * Math.PI * frequency * t) + phase);
    }
}
