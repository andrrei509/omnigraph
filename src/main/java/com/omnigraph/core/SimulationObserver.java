package com.omnigraph.core;

import com.omnigraph.model.HarmonicSnapshot;

/**
 * Observer contract for consumers of computed simulation frames.
 *
 * <p>Implementations are notified once per published frame. Notifications are
 * delivered on the JavaFX Application Thread by {@link RenderBridge}, so
 * implementations may touch the scene graph directly but must never perform
 * blocking or heavy computation inside the callback.
 */
@FunctionalInterface
public interface SimulationObserver {

    void onSnapshot(HarmonicSnapshot snapshot);
}
