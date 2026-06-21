package com.omnigraph.model;

/**
 * Discrete harmonic spectrum: the parallel harmonic numbers and their signed
 * Fourier weights that define a periodic waveform.
 *
 * @param harmonicNumber the harmonic index {@code k} of each component
 * @param weight         the signed amplitude of each component
 */
public record Spectrum(int[] harmonicNumber, double[] weight) {

    public Spectrum {
        if (harmonicNumber.length != weight.length) {
            throw new IllegalArgumentException("harmonicNumber and weight must be equal length");
        }
    }

    public int size() {
        return harmonicNumber.length;
    }
}
