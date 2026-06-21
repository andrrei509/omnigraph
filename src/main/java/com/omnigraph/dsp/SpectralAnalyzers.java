package com.omnigraph.dsp;

/** Selects the fastest available {@link SpectralAnalyzer} backend. */
public final class SpectralAnalyzers {

    private SpectralAnalyzers() {
    }

    public static SpectralAnalyzer best() {
        if (NativeSpectralAnalyzer.isAvailable()) {
            return new NativeSpectralAnalyzer();
        }
        return new JavaSpectralAnalyzer();
    }
}
