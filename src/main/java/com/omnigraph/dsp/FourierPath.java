package com.omnigraph.dsp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import com.omnigraph.model.Epicycle;

/**
 * Decomposes a closed 2D path (treated as a complex signal) into a chain of
 * Fourier epicycles, and provides utilities for sampling paths from functions
 * or drawn points.
 *
 * <p>The forward DFT is computed through a {@link SpectralAnalyzer}, so the
 * native FFT kernel powers the decomposition when available. Each frequency bin
 * {@code k} becomes a rotating vector; bins above {@code N/2} are remapped to
 * negative frequencies so the chain spins in both directions, exactly as the
 * complex Fourier series requires.
 */
public final class FourierPath {

    private FourierPath() {
    }

    /**
     * Decomposes the complex samples {@code (re[n], im[n])} into epicycles sorted
     * by descending amplitude (largest, most significant circles first).
     */
    public static List<Epicycle> decompose(double[] re, double[] im, SpectralAnalyzer analyzer) {
        int n = re.length;
        if (n == 0 || (n & (n - 1)) != 0 || im.length != n) {
            throw new IllegalArgumentException("re/im must be equal power-of-two length");
        }

        double[] r = re.clone();
        double[] i = im.clone();
        analyzer.forwardTransform(r, i);

        List<Epicycle> epicycles = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            double cr = r[k] / n;
            double ci = i[k] / n;
            double amplitude = Math.hypot(cr, ci);
            double phase = Math.atan2(ci, cr);
            int frequency = (k <= n / 2) ? k : k - n;
            epicycles.add(new Epicycle(frequency, amplitude, phase));
        }
        epicycles.sort((a, b) -> Double.compare(b.amplitude(), a.amplitude()));
        return epicycles;
    }

    /** Reconstructs the path point at parameter {@code t} from the first {@code count} epicycles. */
    public static double[] pointAt(List<Epicycle> epicycles, int count, double t) {
        double x = 0.0;
        double y = 0.0;
        int limit = Math.min(count, epicycles.size());
        for (int i = 0; i < limit; i++) {
            Epicycle e = epicycles.get(i);
            x += e.xAt(t);
            y += e.yAt(t);
        }
        return new double[] {x, y};
    }

    /**
     * Samples the graph of {@code y = f(x)} across {@code [a, b]} into a complex
     * path of {@code n} points (x as real, f(x) as imaginary), centered on its
     * own mean so the epicycle chain is balanced about the origin.
     */
    public static double[][] fromFunction(DoubleUnaryOperator f, double a, double b, int n) {
        requirePowerOfTwo(n);
        double[] re = new double[n];
        double[] im = new double[n];
        for (int j = 0; j < n; j++) {
            double x = a + ((b - a) * j) / n;
            re[j] = x;
            double y = f.applyAsDouble(x);
            im[j] = Double.isFinite(y) ? y : 0.0;
        }
        center(re, im);
        return new double[][] {re, im};
    }

    /**
     * Resamples an arbitrary sequence of {@code (x, y)} points to a complex path
     * of {@code n} points by linear interpolation along the point index, then
     * centers it on its mean.
     */
    public static double[][] fromPoints(List<double[]> points, int n) {
        requirePowerOfTwo(n);
        if (points.size() < 2) {
            throw new IllegalArgumentException("need at least two points");
        }
        double[] re = new double[n];
        double[] im = new double[n];
        int m = points.size();
        for (int j = 0; j < n; j++) {
            double u = ((double) j / n) * (m - 1);
            int lo = (int) Math.floor(u);
            int hi = Math.min(lo + 1, m - 1);
            double frac = u - lo;
            double[] p0 = points.get(lo);
            double[] p1 = points.get(hi);
            re[j] = p0[0] + (p1[0] - p0[0]) * frac;
            im[j] = p0[1] + (p1[1] - p0[1]) * frac;
        }
        center(re, im);
        return new double[][] {re, im};
    }

    private static void center(double[] re, double[] im) {
        int n = re.length;
        double mr = 0.0;
        double mi = 0.0;
        for (int j = 0; j < n; j++) {
            mr += re[j];
            mi += im[j];
        }
        mr /= n;
        mi /= n;
        for (int j = 0; j < n; j++) {
            re[j] -= mr;
            im[j] -= mi;
        }
    }

    private static void requirePowerOfTwo(int n) {
        if (n <= 0 || (n & (n - 1)) != 0) {
            throw new IllegalArgumentException("n must be a power of two: " + n);
        }
    }
}
