package com.omnigraph.core;

import com.omnigraph.model.HarmonicSnapshot;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javafx.application.Platform;

/**
 * Decoupling stage between the math pipeline and the UI. Acts as the
 * {@code Observable} subject of the Observer pattern: it owns the observer
 * registry, while the math engine remains entirely unaware of who is rendering.
 *
 * <p>It runs its own consumer thread that blocks on
 * {@link SimulationState#take()} (the {@code wait()}/{@code notifyAll()}
 * rendezvous), then marshals each frame onto the JavaFX Application Thread via
 * {@link Platform#runLater}. This guarantees the strict separation required:
 * math happens on the simulation thread, fan-out happens here, and observers
 * touch the scene graph only on the FX thread.
 */
public final class RenderBridge extends Thread {

    private final SimulationState state;
    private final List<SimulationObserver> observers = new CopyOnWriteArrayList<>();

    private volatile boolean running = true;

    public RenderBridge(SimulationState state) {
        super("OmniGraph-RenderBridge");
        setDaemon(true);
        this.state = state;
    }

    public void addObserver(SimulationObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(SimulationObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void run() {
        while (running) {
            HarmonicSnapshot frame;
            try {
                frame = state.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (frame == null) {
                break;
            }
            dispatch(frame);
        }
    }

    private void dispatch(HarmonicSnapshot frame) {
        if (observers.isEmpty()) {
            return;
        }
        Platform.runLater(() -> {
            for (SimulationObserver observer : observers) {
                observer.onSnapshot(frame);
            }
        });
    }

    public void shutdown() {
        running = false;
        state.close();
        interrupt();
    }
}
