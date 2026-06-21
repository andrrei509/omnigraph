/*
 * OmniGraph native DSP kernel.
 *
 * High-performance radix-2 Cooley-Tukey FFT exposed to the JVM through JNI.
 * The JVM side (com.omnigraph.dsp.NativeSpectralAnalyzer) loads this library
 * when present and falls back to a pure-Java implementation otherwise, so the
 * application runs identically with or without the compiled binary.
 */

#include <jni.h>
#include <math.h>
#include <stdlib.h>

/* In-place iterative radix-2 FFT. n must be a power of two. */
static void fft(double *re, double *im, int n) {
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
        double ang = -2.0 * M_PI / (double) len;
        double wr = cos(ang);
        double wi = sin(ang);
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

JNIEXPORT void JNICALL
Java_com_omnigraph_dsp_NativeSpectralAnalyzer_magnitudeSpectrum(JNIEnv *env,
                                                                jclass cls,
                                                                jdoubleArray input,
                                                                jdoubleArray output) {
    (void) cls;
    jsize n = (*env)->GetArrayLength(env, input);
    jsize outLen = (*env)->GetArrayLength(env, output);
    if (n <= 0) {
        return;
    }

    jdouble *in = (*env)->GetDoubleArrayElements(env, input, NULL);
    double *re = (double *) malloc(sizeof(double) * (size_t) n);
    double *im = (double *) calloc((size_t) n, sizeof(double));
    if (re == NULL || im == NULL) {
        free(re);
        free(im);
        (*env)->ReleaseDoubleArrayElements(env, input, in, JNI_ABORT);
        return;
    }

    for (int i = 0; i < n; i++) {
        re[i] = in[i];
    }

    fft(re, im, n);

    int half = n / 2;
    int m = outLen < half ? outLen : half;
    double *mag = (double *) malloc(sizeof(double) * (size_t) m);
    for (int i = 0; i < m; i++) {
        mag[i] = sqrt(re[i] * re[i] + im[i] * im[i]);
    }

    (*env)->SetDoubleArrayRegion(env, output, 0, m, mag);
    (*env)->ReleaseDoubleArrayElements(env, input, in, JNI_ABORT);
    free(re);
    free(im);
    free(mag);
}

JNIEXPORT void JNICALL
Java_com_omnigraph_dsp_NativeSpectralAnalyzer_complexForward(JNIEnv *env,
                                                             jclass cls,
                                                             jdoubleArray re,
                                                             jdoubleArray im) {
    (void) cls;
    jsize n = (*env)->GetArrayLength(env, re);
    if (n <= 0) {
        return;
    }
    jdouble *r = (*env)->GetDoubleArrayElements(env, re, NULL);
    jdouble *i = (*env)->GetDoubleArrayElements(env, im, NULL);

    fft(r, i, n);

    /* mode 0 commits the in-place changes back to the Java arrays */
    (*env)->ReleaseDoubleArrayElements(env, re, r, 0);
    (*env)->ReleaseDoubleArrayElements(env, im, i, 0);
}
