package com.omnigraph.core;

import com.omnigraph.model.HarmonicSnapshot;

/**
 * Dedicated background thread that owns the simulation clock and performs every
 * trigonometric / geometric computation in the system.
 *
 * <p>The rendering layer never calls into this class for math; it only consumes
 * the frames this thread publishes into {@link SimulationState}. The thread
 * paces itself against wall-clock time so the visual animation runs at a steady
 * rate regardless of how fast the CPU can compute frames.
 */
public final class MathCoreEngine extends Thread {

    private final SimulationConstants constants;
    private final SimulationState state;

    private final double[] harmonicWeight;
    private final double angularVelocity;
    private final double timeStep;
    private final long tickNanos;

    private volatile boolean running = true;

    private final Object pauseLock = new Object();
    private volatile boolean paused = false;

    private long sequence = 0L;
    private double simulationTime = 0.0;

    public MathCoreEngine(SimulationConstants constants, SimulationState state) {
        super("OmniGraph-MathSimulationThread");
        setDaemon(true);
        this.constants = constants;
        this.state = state;
        this.angularVelocity = constants.frequencyFactor * constants.baseAngularFrequency();
        this.timeStep = constants.timeStep();
        this.tickNanos = 1_000_000_000L / constants.ticksPerSecond;
        this.harmonicWeight = buildSquareWaveWeights(constants.fourierHarmonics, constants.amplitude);
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

            simulationTime += timeStep;
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
                // Fell behind schedule; resync rather than spiral.
                nextTickAt = System.nanoTime();
            }
        }
    }

    private HarmonicSnapshot computeFrame() {
        double theta = angularVelocity * simulationTime;
        double primaryX = constants.amplitude * Math.cos(theta);
        double primaryY = constants.amplitude * Math.sin(theta);

        int n = harmonicWeight.length;
        double[] epX = new double[n];
        double[] epY = new double[n];

        double cumulativeX = 0.0;
        double cumulativeY = 0.0;
        for (int i = 0; i < n; i++) {
            int k = (2 * i) + 1;
            double harmonicAngle = theta * k;
            cumulativeX += harmonicWeight[i] * Math.cos(harmonicAngle);
            cumulativeY += harmonicWeight[i] * Math.sin(harmonicAngle);
            epX[i] = cumulativeX;
            epY[i] = cumulativeY;
        }

        double fourierValue = (n > 0) ? epY[n - 1] : 0.0;
        return new HarmonicSnapshot(sequence, simulationTime, theta, primaryX, primaryY,
                epX, epY, fourierValue);
    }

    /**
     * Fourier coefficients for a square wave: only odd harmonics, each with
     * amplitude {@code (4A/PI) / k}. Precomputed once since they never change
     * for a fixed calibration.
     */
    private static double[] buildSquareWaveWeights(int harmonics, double amplitude) {
        double[] weights = new double[harmonics];
        double scale = (4.0 * amplitude) / Math.PI;
        for (int i = 0; i < harmonics; i++) {
            int k = (2 * i) + 1;
            weights[i] = scale / k;
        }
        return weights;
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

    /** Requests a clean shutdown of the simulation thread. */
    public void shutdown() {
        running = false;
        resumeSimulation();
        interrupt();
    }
}
