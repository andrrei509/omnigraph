package com.omnigraph.core;

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

    private final EngineParameters params;
    private final SimulationState state;

    private final int samplesPerPeriod;
    private final long tickNanos;

    private volatile boolean running = true;

    private final Object pauseLock = new Object();
    private volatile boolean paused = false;

    private long sequence = 0L;
    private double theta = 0.0;
    private double simulationTime = 0.0;

    public MathCoreEngine(SimulationConstants constants, EngineParameters params, SimulationState state) {
        super("OmniGraph-MathSimulationThread");
        setDaemon(true);
        this.params = params;
        this.state = state;
        this.samplesPerPeriod = constants.samplesPerPeriod;
        this.tickNanos = 1_000_000_000L / constants.ticksPerSecond;
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
        return new HarmonicSnapshot(sequence, simulationTime, amplitude, theta, primaryX, primaryY,
                epX, epY, fourierValue, harmonicNumber, spectrumAmplitude, waveform.displayName());
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
