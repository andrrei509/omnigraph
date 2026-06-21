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

    private final double amplitude;
    private final double primaryAngle;
    private final double primaryX;
    private final double primaryY;

    private final double[] epicycleX;
    private final double[] epicycleY;
    private final double fourierValue;

    private final int[] harmonicNumber;
    private final double[] spectrumAmplitude;
    private final double[] measuredSpectrum;
    private final String waveformName;

    public HarmonicSnapshot(long sequence,
                            double simulationTime,
                            double amplitude,
                            double primaryAngle,
                            double primaryX,
                            double primaryY,
                            double[] epicycleX,
                            double[] epicycleY,
                            double fourierValue,
                            int[] harmonicNumber,
                            double[] spectrumAmplitude,
                            double[] measuredSpectrum,
                            String waveformName) {
        if (epicycleX.length != epicycleY.length) {
            throw new IllegalArgumentException("epicycle coordinate arrays must be equal length");
        }
        if (harmonicNumber.length != spectrumAmplitude.length) {
            throw new IllegalArgumentException("spectrum arrays must be equal length");
        }
        this.sequence = sequence;
        this.simulationTime = simulationTime;
        this.amplitude = amplitude;
        this.primaryAngle = primaryAngle;
        this.primaryX = primaryX;
        this.primaryY = primaryY;
        this.epicycleX = epicycleX;
        this.epicycleY = epicycleY;
        this.fourierValue = fourierValue;
        this.harmonicNumber = harmonicNumber;
        this.spectrumAmplitude = spectrumAmplitude;
        this.measuredSpectrum = measuredSpectrum;
        this.waveformName = waveformName;
    }

    public long sequence() {
        return sequence;
    }

    public double simulationTime() {
        return simulationTime;
    }

    /** Amplitude in effect for this frame (View A radius scale). */
    public double amplitude() {
        return amplitude;
    }

    public double primaryAngle() {
        return primaryAngle;
    }

    public double primaryX() {
        return primaryX;
    }

    public double primaryY() {
        return primaryY;
    }

    public int epicycleCount() {
        return epicycleX.length;
    }

    public double epicycleX(int i) {
        return epicycleX[i];
    }

    public double epicycleY(int i) {
        return epicycleY[i];
    }

    /** Synthesized signal value: vertical position of the final chain tip (View C). */
    public double fourierValue() {
        return fourierValue;
    }

    public int spectrumSize() {
        return harmonicNumber.length;
    }

    /** Harmonic index k of spectrum bin i (View D). */
    public int harmonicNumber(int i) {
        return harmonicNumber[i];
    }

    /** Theoretical absolute amplitude of harmonic bin i (View D). */
    public double spectrumAmplitude(int i) {
        return spectrumAmplitude[i];
    }

    /** Number of FFT-measured amplitude bins, indexed by harmonic number. */
    public int measuredBins() {
        return measuredSpectrum.length;
    }

    /** FFT-measured amplitude at bin {@code i} (i == harmonic number). */
    public double measuredAmplitude(int i) {
        return measuredSpectrum[i];
    }

    public String waveformName() {
        return waveformName;
    }
}
