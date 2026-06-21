package com.omnigraph.dsp;

/**
 * Pure-Java radix-2 Cooley-Tukey FFT. Mirrors the native kernel exactly so the
 * application produces identical spectra whether or not the C library is built.
 */
public final class JavaSpectralAnalyzer implements SpectralAnalyzer {

    @Override
    public double[] magnitude(double[] samples) {
        int n = samples.length;
        if (n == 0 || (n & (n - 1)) != 0) {
            throw new IllegalArgumentException("sample count must be a power of two: " + n);
        }

        double[] re = samples.clone();
        double[] im = new double[n];
        fft(re, im);

        int half = n / 2;
        double[] mag = new double[half];
        for (int i = 0; i < half; i++) {
            mag[i] = Math.hypot(re[i], im[i]);
        }
        return mag;
    }

    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double tr = re[i]; re[i] = re[j]; re[j] = tr;
                double ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
        }

        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2.0 * Math.PI / len;
            double wr = Math.cos(ang);
            double wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1.0;
                double ci = 0.0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k;
                    int b = a + len / 2;
                    double tr = cr * re[b] - ci * im[b];
                    double ti = cr * im[b] + ci * re[b];
                    re[b] = re[a] - tr;
                    im[b] = im[a] - ti;
                    re[a] += tr;
                    im[a] += ti;
                    double ncr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = ncr;
                }
            }
        }
    }

    @Override
    public String backend() {
        return "java";
    }
}
