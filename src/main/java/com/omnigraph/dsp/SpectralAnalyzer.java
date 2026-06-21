package com.omnigraph.dsp;

/**
 * Computes the magnitude spectrum of a real, time-domain signal.
 *
 * <p>Two implementations exist behind this contract: a native C/JNI kernel for
 * performance and a pure-Java fallback for portability. They are numerically
 * equivalent, which the test suite enforces.
 */
public interface SpectralAnalyzer {

    /**
     * Returns the magnitude of bins {@code 0 .. n/2 - 1} for a real input of
     * length {@code n}, where {@code n} must be a power of two.
     */
    double[] magnitude(double[] samples);

    /**
     * In-place forward complex FFT of length {@code re.length} (a power of two).
     * On return the arrays hold {@code X[k] = sum x[n] exp(-2*PI*i*k*n/N)}. This
     * preserves phase, which the epicycle decomposition needs.
     */
    void forwardTransform(double[] re, double[] im);

    /** Short identifier of the backend in use (for display/diagnostics). */
    String backend();
}
