package com.omnigraph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WaveformTypeTest {

    @Test
    void sineHasOnlyFundamental() {
        Spectrum s = WaveformType.SINE.build(8, 12.0);
        assertEquals(1, s.size());
        assertEquals(1, s.harmonicNumber()[0]);
        assertEquals(12.0, s.weight()[0], 1e-9);
    }

    @Test
    void squareUsesOnlyOddHarmonics() {
        Spectrum s = WaveformType.SQUARE.build(5, 1.0);
        assertEquals(5, s.size());
        for (int k : s.harmonicNumber()) {
            assertTrue(k % 2 == 1, "square must use odd harmonics only, saw " + k);
        }
    }

    @Test
    void sawtoothUsesEveryHarmonicWithAlternatingSign() {
        Spectrum s = WaveformType.SAWTOOTH.build(4, 1.0);
        assertEquals(4, s.size());
        assertEquals(1, s.harmonicNumber()[0]);
        assertEquals(2, s.harmonicNumber()[1]);
        assertTrue(s.weight()[0] > 0, "fundamental should be positive");
        assertTrue(s.weight()[1] < 0, "second harmonic should be negative");
    }

    @Test
    void triangleDecaysFasterThanSquare() {
        Spectrum tri = WaveformType.TRIANGLE.build(3, 1.0);
        // third harmonic of a 1/k^2 series is much smaller than the fundamental
        double ratio = Math.abs(tri.weight()[2]) / Math.abs(tri.weight()[0]);
        assertTrue(ratio < 0.2, "triangle should decay quickly, ratio was " + ratio);
    }
}
