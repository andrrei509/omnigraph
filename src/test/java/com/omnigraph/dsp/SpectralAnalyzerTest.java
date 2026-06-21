package com.omnigraph.dsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class SpectralAnalyzerTest {

    private static final int N = 256;

    private static double[] signal(double... componentsByHarmonic) {
        double[] x = new double[N];
        for (int j = 0; j < N; j++) {
            double phi = (2.0 * Math.PI * j) / N;
            double s = 0.0;
            for (int k = 1; k < componentsByHarmonic.length; k++) {
                s += componentsByHarmonic[k] * Math.sin(k * phi);
            }
            x[j] = s;
        }
        return x;
    }

    @Test
    void javaFftRecoversKnownAmplitudes() {
        double[] x = signal(0, 0, 0, 3.0, 0, 1.5); // 3*sin(3φ) + 1.5*sin(5φ)
        double[] mag = new JavaSpectralAnalyzer().magnitude(x);
        double scale = 2.0 / N;
        assertEquals(3.0, mag[3] * scale, 1e-9);
        assertEquals(1.5, mag[5] * scale, 1e-9);
        assertEquals(0.0, mag[4] * scale, 1e-9);
    }

    @Test
    void rejectsNonPowerOfTwo() {
        assertThrows(IllegalArgumentException.class,
                () -> new JavaSpectralAnalyzer().magnitude(new double[100]));
    }

    @Test
    void nativeMatchesJavaWhenAvailable() {
        Assumptions.assumeTrue(NativeSpectralAnalyzer.isAvailable(),
                "native library not built; skipping cross-check");
        double[] x = signal(0, 2.0, 0, 0, 0.7, 0, 0.3);
        double[] javaMag = new JavaSpectralAnalyzer().magnitude(x);
        double[] nativeMag = new NativeSpectralAnalyzer().magnitude(x);
        assertEquals(javaMag.length, nativeMag.length);
        double maxDiff = 0.0;
        for (int i = 0; i < javaMag.length; i++) {
            maxDiff = Math.max(maxDiff, Math.abs(javaMag[i] - nativeMag[i]));
        }
        assertTrue(maxDiff < 1e-9, "native and java spectra diverged by " + maxDiff);
    }
}
