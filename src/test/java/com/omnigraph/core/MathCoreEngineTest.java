package com.omnigraph.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omnigraph.model.HarmonicSnapshot;
import com.omnigraph.model.WaveformType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class MathCoreEngineTest {

    @Test
    @Timeout(5)
    void producesMonotonicFiniteFrames() throws InterruptedException {
        SimulationConstants constants = SimulationConstants.defaults();
        EngineParameters params = EngineParameters.fromDefaults(constants);
        params.setWaveform(WaveformType.SQUARE);
        SimulationState state = new SimulationState();
        MathCoreEngine engine = new MathCoreEngine(constants, params, state);

        engine.start();
        try {
            HarmonicSnapshot first = state.take();
            HarmonicSnapshot second = state.take();

            assertNotNull(first);
            assertNotNull(second);
            assertTrue(second.sequence() > first.sequence(), "sequence must advance");

            assertTrue(Double.isFinite(second.primaryX()));
            assertTrue(Double.isFinite(second.primaryY()));
            assertTrue(Double.isFinite(second.fourierValue()));
            assertTrue(second.spectrumSize() > 0);

            for (int i = 0; i < second.spectrumSize(); i++) {
                assertFalse(second.spectrumAmplitude(i) < 0, "spectrum amplitude must be non-negative");
            }
        } finally {
            engine.shutdown();
            state.close();
        }
    }
}
