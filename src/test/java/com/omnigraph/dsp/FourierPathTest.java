package com.omnigraph.dsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.omnigraph.expr.FunctionParser;
import com.omnigraph.model.Epicycle;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class FourierPathTest {

    private final SpectralAnalyzer java = new JavaSpectralAnalyzer();

    @Test
    void unitCircleDecomposesToSingleEpicycle() {
        int n = 64;
        double[] re = new double[n];
        double[] im = new double[n];
        for (int j = 0; j < n; j++) {
            double t = (2.0 * Math.PI * j) / n;
            re[j] = Math.cos(t);
            im[j] = Math.sin(t);
        }
        List<Epicycle> epicycles = FourierPath.decompose(re, im, java);
        Epicycle dominant = epicycles.get(0);
        assertEquals(1, dominant.frequency());
        assertEquals(1.0, dominant.amplitude(), 1e-9);
        assertTrue(epicycles.get(1).amplitude() < 1e-9, "all other epicycles should vanish");
    }

    @Test
    void reconstructionMatchesOriginalSamples() {
        int n = 128;
        double[][] path = FourierPath.fromFunction(FunctionParser.parse("sin(x) + 0.3*cos(3*x)"),
                0, 2 * Math.PI, n);
        List<Epicycle> epicycles = FourierPath.decompose(path[0], path[1], java);

        double maxErr = 0.0;
        for (int j = 0; j < n; j++) {
            double[] p = FourierPath.pointAt(epicycles, epicycles.size(), (double) j / n);
            maxErr = Math.max(maxErr, Math.abs(p[0] - path[0][j]));
            maxErr = Math.max(maxErr, Math.abs(p[1] - path[1][j]));
        }
        assertTrue(maxErr < 1e-9, "full epicycle sum should reconstruct the path, err=" + maxErr);
    }

    @Test
    void nativeAndJavaDecompositionsAgree() {
        Assumptions.assumeTrue(NativeSpectralAnalyzer.isAvailable(),
                "native library not built; skipping cross-check");
        SpectralAnalyzer nat = new NativeSpectralAnalyzer();
        int n = 256;
        double[][] path = FourierPath.fromFunction(FunctionParser.parse("x*x"), -Math.PI, Math.PI, n);

        List<Epicycle> je = FourierPath.decompose(path[0], path[1], java);
        List<Epicycle> ne = FourierPath.decompose(path[0], path[1], nat);
        assertEquals(je.size(), ne.size());
        double maxDiff = 0.0;
        for (int i = 0; i < je.size(); i++) {
            maxDiff = Math.max(maxDiff, Math.abs(je.get(i).amplitude() - ne.get(i).amplitude()));
        }
        assertTrue(maxDiff < 1e-9, "native and java epicycles diverged by " + maxDiff);
    }
}
