package com.omnigraph.core;

import com.omnigraph.dsp.JavaSpectralAnalyzer;
import com.omnigraph.dsp.SpectralAnalyzer;
import com.omnigraph.model.HarmonicSnapshot;
import com.omnigraph.model.Spectrum;
import com.omnigraph.model.WaveformType;

/**
 * Dedicated background thread that owns the simulation clock and performs every
 * trigonometric / geometric computation in the system.
 *
 * <p>The rendering layer never calls into this class for math; it only consumes
 * the frames this thread publishes into {@link SimulationState}. All tuning is
 * read live from a shared {@link EngineParameters}, so the UI can retune the
 * running simulation without restarting it. Phase is accumulated incrementally
 * rather than recomputed from {@code t}, so changing the frequency factor mid
 * run never causes a visual discontinuity.
 */
public final class MathCoreEngine extends Thread {

    /** FFT window length used for live spectral analysis (power of two). */
    private static final int ANALYSIS_N = 256;
    /** Number of measured harmonic bins exposed to the UI. */
    private static final int ANALYSIS_BINS = 40;

    private final EngineParameters params;
    private final SimulationState state;
    private final SpectralAnalyzer analyzer;

    private final int samplesPerPeriod;
    private final long tickNanos;
    private final double[] analysisBuffer = new double[ANALYSIS_N];

    private volatile boolean running = true;

    private final Object pauseLock = new Object();
    private volatile boolean paused = false;

    private long sequence = 0L;
    private double theta = 0.0;
    private double simulationTime = 0.0;

    public MathCoreEngine(SimulationConstants constants, EngineParameters params, SimulationState state) {
        this(constants, params, state, new JavaSpectralAnalyzer());
    }

    public MathCoreEngine(SimulationConstants constants, EngineParameters params,
                          SimulationState state, SpectralAnalyzer analyzer) {
        super("OmniGraph-MathSimulationThread");
        setDaemon(true);
        this.params = params;
        this.state = state;
        this.analyzer = analyzer;
        this.samplesPerPeriod = constants.samplesPerPeriod;
        this.tickNanos = 1_000_000_000L / constants.ticksPerSecond;
    }

    public String analyzerBackend() {
        return analyzer.backend();
    }

    @Override
    public void run() {
        long nextTickAt = System.nanoTime();
        while (running) {
            awaitResume();
            if (!running) {
                break;
            }

            HarmonicSnapshot frame = computeFrame();
            state.publish(frame);

            double speed = params.getSpeed();
            double dTheta = params.getFrequencyFactor() * (2.0 * Math.PI / samplesPerPeriod) * speed;
            theta += dTheta;
            simulationTime += (params.getPeriod() / samplesPerPeriod) * speed;
            sequence++;

            nextTickAt += tickNanos;
            long sleepNanos = nextTickAt - System.nanoTime();
            if (sleepNanos > 0) {
                try {
                    Thread.sleep(sleepNanos / 1_000_000L, (int) (sleepNanos % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                nextTickAt = System.nanoTime();
            }
        }
    }

    private HarmonicSnapshot computeFrame() {
        double amplitude = params.getAmplitude();
        WaveformType waveform = params.getWaveform();

        double primaryX = amplitude * Math.cos(theta);
        double primaryY = amplitude * Math.sin(theta);

        Spectrum spectrum = waveform.build(params.getHarmonics(), amplitude);
        int n = spectrum.size();

        double[] epX = new double[n];
        double[] epY = new double[n];
        int[] harmonicNumber = new int[n];
        double[] spectrumAmplitude = new double[n];

        double cumulativeX = 0.0;
        double cumulativeY = 0.0;
        for (int i = 0; i < n; i++) {
            int k = spectrum.harmonicNumber()[i];
            double weight = spectrum.weight()[i];
            double harmonicAngle = theta * k;
            cumulativeX += weight * Math.cos(harmonicAngle);
            cumulativeY += weight * Math.sin(harmonicAngle);
            epX[i] = cumulativeX;
            epY[i] = cumulativeY;
            harmonicNumber[i] = k;
            spectrumAmplitude[i] = Math.abs(weight);
        }

        double fourierValue = (n > 0) ? epY[n - 1] : 0.0;
        double[] measuredSpectrum = analyzeSpectrum(spectrum);

        return new HarmonicSnapshot(sequence, simulationTime, amplitude, theta, primaryX, primaryY,
                epX, epY, fourierValue, harmonicNumber, spectrumAmplitude, measuredSpectrum,
                waveform.displayName());
    }

    /**
     * Renders the current waveform over one fundamental period, runs it through
     * the FFT, and returns the measured per-harmonic amplitudes. Bin {@code k}
     * corresponds exactly to harmonic {@code k} because the window spans one
     * period. This is genuine spectral measurement, not a replay of the input
     * coefficients, so it both demonstrates the time/frequency duality and
     * exercises the native kernel in the live path.
     */
    private double[] analyzeSpectrum(Spectrum spectrum) {
        int components = spectrum.size();
        int[] k = spectrum.harmonicNumber();
        double[] weight = spectrum.weight();

        for (int j = 0; j < ANALYSIS_N; j++) {
            double phi = (2.0 * Math.PI * j) / ANALYSIS_N;
            double sample = 0.0;
            for (int i = 0; i < components; i++) {
                sample += weight[i] * Math.sin(k[i] * phi);
            }
            analysisBuffer[j] = sample;
        }

        double[] magnitude = analyzer.magnitude(analysisBuffer);
        int bins = Math.min(ANALYSIS_BINS, magnitude.length);
        double[] measured = new double[bins];
        double scale = 2.0 / ANALYSIS_N;
        for (int b = 0; b < bins; b++) {
            measured[b] = magnitude[b] * scale;
        }
        return measured;
    }

    private void awaitResume() {
        synchronized (pauseLock) {
            while (paused && running) {
                try {
                    pauseLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    public void pauseSimulation() {
        paused = true;
    }

    public void resumeSimulation() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void shutdown() {
        running = false;
        resumeSimulation();
        interrupt();
    }
}
