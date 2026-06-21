package com.omnigraph.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import com.omnigraph.core.EngineParameters;
import com.omnigraph.model.Spectrum;
import com.omnigraph.model.WaveformType;

/**
 * Real-time additive-synthesis audio engine. It reuses the exact
 * {@link WaveformType} and harmonic count from the shared
 * {@link EngineParameters}, so the timbre you hear is the same spectrum the math
 * engine is drawing — change the waveform or harmonic count and both the visuals
 * and the sound change together.
 *
 * <p>Runs on its own thread and streams 16-bit mono PCM to a
 * {@link SourceDataLine}. If no audio device is available (e.g. a headless
 * host), it degrades gracefully and reports itself unavailable rather than
 * throwing.
 */
public final class AudioEngine extends Thread {

    private static final float SAMPLE_RATE = 44_100f;
    private static final int FRAMES_PER_BUFFER = 1_024;

    private final EngineParameters params;

    private volatile boolean running = true;
    private volatile boolean enabled = false;
    private volatile boolean available = false;
    private volatile double frequencyHz = 220.0;
    private volatile double volume = 0.3;

    private double phase = 0.0;

    public AudioEngine(EngineParameters params) {
        super("OmniGraph-AudioEngine");
        setDaemon(true);
        this.params = params;
    }

    @Override
    public void run() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        SourceDataLine line;
        try {
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, FRAMES_PER_BUFFER * 2 * 4);
            line.start();
            available = true;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            available = false;
            return;
        }

        byte[] buffer = new byte[FRAMES_PER_BUFFER * 2];
        try {
            while (running) {
                if (enabled) {
                    fillWaveform(buffer);
                } else {
                    java.util.Arrays.fill(buffer, (byte) 0);
                    phase = 0.0;
                }
                line.write(buffer, 0, buffer.length);
            }
        } finally {
            line.drain();
            line.stop();
            line.close();
        }
    }

    private void fillWaveform(byte[] buffer) {
        Spectrum spectrum = params.getWaveform().build(params.getHarmonics(), 1.0);
        int n = spectrum.size();

        double norm = 0.0;
        for (int i = 0; i < n; i++) {
            norm += Math.abs(spectrum.weight()[i]);
        }
        if (norm <= 0.0) {
            norm = 1.0;
        }

        double freq = frequencyHz;
        double vol = volume;
        double phaseStep = (2.0 * Math.PI * freq) / SAMPLE_RATE;

        for (int frame = 0; frame < FRAMES_PER_BUFFER; frame++) {
            double sample = 0.0;
            for (int i = 0; i < n; i++) {
                sample += spectrum.weight()[i] * Math.sin(spectrum.harmonicNumber()[i] * phase);
            }
            sample = (sample / norm) * vol;
            if (sample > 1.0) {
                sample = 1.0;
            } else if (sample < -1.0) {
                sample = -1.0;
            }

            short pcm = (short) (sample * Short.MAX_VALUE);
            buffer[frame * 2] = (byte) (pcm & 0xff);
            buffer[(frame * 2) + 1] = (byte) ((pcm >> 8) & 0xff);

            phase += phaseStep;
            if (phase > 2.0 * Math.PI) {
                phase -= 2.0 * Math.PI;
            }
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setFrequencyHz(double frequencyHz) {
        this.frequencyHz = frequencyHz;
    }

    public double getFrequencyHz() {
        return frequencyHz;
    }

    public void setVolume(double volume) {
        this.volume = Math.max(0.0, Math.min(1.0, volume));
    }

    public void shutdown() {
        running = false;
        interrupt();
    }
}
