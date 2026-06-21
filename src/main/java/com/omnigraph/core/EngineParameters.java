package com.omnigraph.core;

import java.util.concurrent.atomic.AtomicReference;

import com.omnigraph.model.WaveformType;

/**
 * Thread-safe, mutable set of live tuning knobs shared by the math engine and
 * the audio engine. The UI writes; both engines read. All fields are
 * independently volatile (or atomic for the enum), so reads are lock-free and
 * always observe the latest committed value.
 */
public final class EngineParameters {

    private volatile double amplitude;
    private volatile double frequencyFactor;
    private volatile double period;
    private volatile int harmonics;
    private volatile double speed = 1.0;
    private final AtomicReference<WaveformType> waveform = new AtomicReference<>(WaveformType.SQUARE);

    public static EngineParameters fromDefaults(SimulationConstants constants) {
        EngineParameters p = new EngineParameters();
        p.amplitude = constants.amplitude;
        p.frequencyFactor = constants.frequencyFactor;
        p.period = constants.period;
        p.harmonics = constants.fourierHarmonics;
        return p;
    }

    public double getAmplitude() {
        return amplitude;
    }

    public void setAmplitude(double amplitude) {
        this.amplitude = amplitude;
    }

    public double getFrequencyFactor() {
        return frequencyFactor;
    }

    public void setFrequencyFactor(double frequencyFactor) {
        this.frequencyFactor = frequencyFactor;
    }

    public double getPeriod() {
        return period;
    }

    public void setPeriod(double period) {
        if (period <= 0.0) {
            throw new IllegalArgumentException("period must be positive: " + period);
        }
        this.period = period;
    }

    public int getHarmonics() {
        return harmonics;
    }

    public void setHarmonics(int harmonics) {
        this.harmonics = Math.max(1, harmonics);
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public WaveformType getWaveform() {
        return waveform.get();
    }

    public void setWaveform(WaveformType type) {
        waveform.set(type);
    }
}
