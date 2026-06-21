package com.omnigraph.model;

/**
 * Immutable, self-contained frame of computed geometry handed from the math
 * thread to the rendering layer.
 *
 * <p>Instances are created fresh on every engine tick and never mutated after
 * publication, which is what makes them safe to share across threads without
 * additional locking once a reference has been handed off.
 */
public final class HarmonicSnapshot {

    private final long sequence;
    private final double simulationTime;

    private final double primaryAngle;
    private final double primaryX;
    private final double primaryY;

    private final double[] epicycleX;
    private final double[] epicycleY;
    private final double fourierValue;

    public HarmonicSnapshot(long sequence,
                            double simulationTime,
                            double primaryAngle,
                            double primaryX,
                            double primaryY,
                            double[] epicycleX,
                            double[] epicycleY,
                            double fourierValue) {
        if (epicycleX.length != epicycleY.length) {
            throw new IllegalArgumentException("epicycle coordinate arrays must be equal length");
        }
        this.sequence = sequence;
        this.simulationTime = simulationTime;
        this.primaryAngle = primaryAngle;
        this.primaryX = primaryX;
        this.primaryY = primaryY;
        this.epicycleX = epicycleX;
        this.epicycleY = epicycleY;
        this.fourierValue = fourierValue;
    }

    /** Monotonically increasing frame id. */
    public long sequence() {
        return sequence;
    }

    /** Simulation time of this frame in seconds. */
    public double simulationTime() {
        return simulationTime;
    }

    /** Primary phasor angle in radians (View A). */
    public double primaryAngle() {
        return primaryAngle;
    }

    /** Horizontal projection of the primary phasor, amplitude-scaled (View A). */
    public double primaryX() {
        return primaryX;
    }

    /** Vertical projection of the primary phasor, amplitude-scaled (Views A and B). */
    public double primaryY() {
        return primaryY;
    }

    /** Number of epicycle tips in the Fourier chain (View C). */
    public int epicycleCount() {
        return epicycleX.length;
    }

    /** Cumulative x of the chain tip after the i-th harmonic vector (View C). */
    public double epicycleX(int i) {
        return epicycleX[i];
    }

    /** Cumulative y of the chain tip after the i-th harmonic vector (View C). */
    public double epicycleY(int i) {
        return epicycleY[i];
    }

    /** Synthesized signal value: vertical position of the final chain tip (View C). */
    public double fourierValue() {
        return fourierValue;
    }
}
