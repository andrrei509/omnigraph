package com.omnigraph.dsp;

/**
 * JNI binding to the native C FFT kernel ({@code libomnigraph_dsp}).
 *
 * <p>The library is loaded once at class init. If it is not on
 * {@code java.library.path} (i.e. it was never compiled), {@link #isAvailable()}
 * reports {@code false} and callers fall back to {@link JavaSpectralAnalyzer}.
 */
public final class NativeSpectralAnalyzer implements SpectralAnalyzer {

    private static final boolean AVAILABLE;

    static {
        boolean ok;
        try {
            System.loadLibrary("omnigraph_dsp");
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        AVAILABLE = ok;
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    private static native void magnitudeSpectrum(double[] input, double[] output);

    @Override
    public double[] magnitude(double[] samples) {
        int n = samples.length;
        if (n == 0 || (n & (n - 1)) != 0) {
            throw new IllegalArgumentException("sample count must be a power of two: " + n);
        }
        double[] out = new double[n / 2];
        magnitudeSpectrum(samples, out);
        return out;
    }

    @Override
    public String backend() {
        return "native-c";
    }
}
