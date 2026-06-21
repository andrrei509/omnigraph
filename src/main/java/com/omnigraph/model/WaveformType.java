package com.omnigraph.model;

/**
 * The periodic target waveforms the synthesis engine can build from a sum of
 * rotating harmonics. Each constant knows its own Fourier series, so the math
 * engine and the audio engine stay agnostic of the underlying coefficients.
 */
public enum WaveformType {

    /** A single fundamental — the pure building block. */
    SINE("Sine") {
        @Override
        public Spectrum build(int harmonics, double amplitude) {
            return new Spectrum(new int[] {1}, new double[] {amplitude});
        }
    },

    /** Odd harmonics with {@code 1/k} decay. */
    SQUARE("Square") {
        @Override
        public Spectrum build(int harmonics, double amplitude) {
            int n = Math.max(1, harmonics);
            int[] k = new int[n];
            double[] w = new double[n];
            double scale = (4.0 * amplitude) / Math.PI;
            for (int i = 0; i < n; i++) {
                k[i] = (2 * i) + 1;
                w[i] = scale / k[i];
            }
            return new Spectrum(k, w);
        }
    },

    /** All harmonics with alternating sign and {@code 1/k} decay. */
    SAWTOOTH("Sawtooth") {
        @Override
        public Spectrum build(int harmonics, double amplitude) {
            int n = Math.max(1, harmonics);
            int[] k = new int[n];
            double[] w = new double[n];
            double scale = (2.0 * amplitude) / Math.PI;
            for (int i = 0; i < n; i++) {
                k[i] = i + 1;
                double sign = (k[i] % 2 == 1) ? 1.0 : -1.0;
                w[i] = (scale * sign) / k[i];
            }
            return new Spectrum(k, w);
        }
    },

    /** Odd harmonics with alternating sign and {@code 1/k^2} decay. */
    TRIANGLE("Triangle") {
        @Override
        public Spectrum build(int harmonics, double amplitude) {
            int n = Math.max(1, harmonics);
            int[] k = new int[n];
            double[] w = new double[n];
            double scale = (8.0 * amplitude) / (Math.PI * Math.PI);
            for (int i = 0; i < n; i++) {
                k[i] = (2 * i) + 1;
                double sign = (i % 2 == 0) ? 1.0 : -1.0;
                w[i] = (scale * sign) / (k[i] * (double) k[i]);
            }
            return new Spectrum(k, w);
        }
    };

    private final String displayName;

    WaveformType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Builds the harmonic spectrum approximating this waveform.
     *
     * @param harmonics number of harmonic components to include
     * @param amplitude target peak amplitude of the fundamental scaling
     */
    public abstract Spectrum build(int harmonics, double amplitude);
}
