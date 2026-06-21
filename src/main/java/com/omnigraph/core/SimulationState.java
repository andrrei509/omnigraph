package com.omnigraph.core;

import com.omnigraph.model.HarmonicSnapshot;

/**
 * Thread-safe single-slot hand-off buffer between the producing math thread and
 * the consuming render bridge.
 *
 * <p>This is the one place in the core pipeline that uses low-level monitor
 * primitives ({@code synchronized}, {@link Object#wait()},
 * {@link Object#notifyAll()}) deliberately, rather than a high-level concurrent
 * collection. It implements a "latest value wins" rendezvous: the producer
 * overwrites the slot every tick and wakes the consumer; the consumer blocks
 * until a fresh frame is available. Dropping intermediate frames is intentional
 * — the renderer only ever cares about the most recent geometry.
 */
public final class SimulationState {

    private final Object lock = new Object();

    private HarmonicSnapshot latest;
    private boolean hasFresh;
    private boolean closed;

    /**
     * Publishes a freshly computed frame. Called only by the math thread.
     * Overwrites any frame the consumer has not yet taken and wakes a waiter.
     */
    public void publish(HarmonicSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        synchronized (lock) {
            this.latest = snapshot;
            this.hasFresh = true;
            lock.notifyAll();
        }
    }

    /**
     * Blocks until a fresh frame is published, then returns it. Called only by
     * the render bridge thread.
     *
     * @return the newest frame, or {@code null} if the buffer was closed while
     *         waiting (a signal to the consumer to shut down)
     * @throws InterruptedException if the waiting thread is interrupted
     */
    public HarmonicSnapshot take() throws InterruptedException {
        synchronized (lock) {
            while (!hasFresh && !closed) {
                lock.wait();
            }
            if (closed && !hasFresh) {
                return null;
            }
            hasFresh = false;
            return latest;
        }
    }

    /**
     * Returns the most recently published frame without blocking or consuming
     * it, or {@code null} if nothing has been published yet.
     */
    public HarmonicSnapshot peek() {
        synchronized (lock) {
            return latest;
        }
    }

    /** Permanently unblocks any waiting consumer so it can terminate cleanly. */
    public void close() {
        synchronized (lock) {
            closed = true;
            lock.notifyAll();
        }
    }
}
