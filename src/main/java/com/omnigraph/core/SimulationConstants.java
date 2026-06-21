package com.omnigraph.core;

/**
 * Immutable calibration profile for the harmonic engine.
 *
 * <p>The primary wave is modelled as a rotating phasor:
 * {@code theta(t) = B * (2*PI / T) * t}, with vertical projection
 * {@code y(t) = A * sin(theta(t))} and horizontal projection
 * {@code x(t) = A * cos(theta(t))}.
 *
 * <p>The default profile uses the mandated calibration constants
 * {@code A = 12}, {@code B = -2}, {@code T = 5e-4}. Because {@code T} describes
 * a 2 kHz physical signal that is far too fast to watch directly, the engine
 * samples each fundamental period into {@link #samplesPerPeriod} discrete ticks
 * and advances simulation time in steps of {@code T / samplesPerPeriod}. The
 * physical constants are therefore preserved exactly while the animation is
 * dilated to a human-observable rate.
 */
public final class SimulationConstants {

    /** Amplitude of the primary harmonic. */
    public final double amplitude;

    /** Frequency / direction multiplier (negative spins the phasor clockwise). */
    public final double frequencyFactor;

    /** Fundamental period in seconds. */
    public final double period;

    /** Number of discrete samples taken across one fundamental period. */
    public final int samplesPerPeriod;

    /** Engine tick frequency in Hz (wall-clock pacing of the math thread). */
    public final int ticksPerSecond;

    /** Number of odd harmonics summed for the Fourier square-wave synthesis. */
    public final int fourierHarmonics;

    public SimulationConstants(double amplitude,
                               double frequencyFactor,
                               double period,
                               int samplesPerPeriod,
                               int ticksPerSecond,
                               int fourierHarmonics) {
        if (period <= 0.0) {
            throw new IllegalArgumentException("period must be positive: " + period);
        }
        if (samplesPerPeriod <= 0) {
            throw new IllegalArgumentException("samplesPerPeriod must be positive: " + samplesPerPeriod);
        }
        if (ticksPerSecond <= 0) {
            throw new IllegalArgumentException("ticksPerSecond must be positive: " + ticksPerSecond);
        }
        if (fourierHarmonics <= 0) {
            throw new IllegalArgumentException("fourierHarmonics must be positive: " + fourierHarmonics);
        }
        this.amplitude = amplitude;
        this.frequencyFactor = frequencyFactor;
        this.period = period;
        this.samplesPerPeriod = samplesPerPeriod;
        this.ticksPerSecond = ticksPerSecond;
        this.fourierHarmonics = fourierHarmonics;
    }

    /** Mandated default calibration: A = 12, B = -2, T = 5 * 10^-4. */
    public static SimulationConstants defaults() {
        return new SimulationConstants(12.0, -2.0, 5e-4, 240, 120, 8);
    }

    /** Simulation-time advanced per engine tick (seconds). */
    public double timeStep() {
        return period / samplesPerPeriod;
    }

    /** Base angular frequency (rad/s) derived from the fundamental period. */
    public double baseAngularFrequency() {
        return (2.0 * Math.PI) / period;
    }

    @Override
    public String toString() {
        return "SimulationConstants[A=" + amplitude
                + ", B=" + frequencyFactor
                + ", T=" + period
                + ", samplesPerPeriod=" + samplesPerPeriod
                + ", ticksPerSecond=" + ticksPerSecond
                + ", fourierHarmonics=" + fourierHarmonics + "]";
    }
}
