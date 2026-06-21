# OmniGraph

An interactive mathematical laboratory that demonstrates the interconnectedness
of geometry, signals, and complex calculus in real time. The MVP delivers
**Interconnected Harmonic Generation & Fourier Synthesis** across three
synchronized, live-updating canvases.

- **View A — Dynamic Geometry:** a rotating phasor on the unit circle.
- **View B — The Harmonic Plane:** the phasor's vertical (`y`) projection traced
  into the time domain, building a sine wave as the vector spins.
- **View C — Spectral Synthesis:** a Fourier epicycle chain that sums odd
  harmonics into a synthesized square-wave signal.

## Architecture

The core pipeline is deliberately decoupled into three threads so that the
rendering layer never performs a single math operation.

```
 MathCoreEngine ──publish()──▶ SimulationState ──take()──▶ RenderBridge ──Platform.runLater──▶ Dashboard
 (math thread)                 (wait/notifyAll                (observer subject)                 (FX thread,
  owns clock t,                 hand-off buffer,               fans out to observers)            View A/B/C)
  all trig/geometry)            latest-value-wins)
```

- **`MathCoreEngine`** (`extends Thread`) owns the simulation clock and computes
  every frame. It paces itself against wall-clock time.
- **`SimulationState`** is the one intentional use of low-level monitor
  primitives (`synchronized`, `wait()`, `notifyAll()`): a single-slot rendezvous
  where the newest frame wins and the consumer blocks until one is available. No
  `Executor`s are used in this core pipeline, by design.
- **`RenderBridge`** (`extends Thread`) is the `Observable` subject of the
  Observer pattern. It blocks on the buffer, then marshals each frame onto the
  JavaFX Application Thread. The math engine has no knowledge of who renders.
- **`OmniGraphDashboard`** is a pure `SimulationObserver`. It buffers values for
  the scrolling traces and paints them with an `AnimationTimer` at display rate.

## Calibration constants

The engine initializes with `A = 12`, `B = -2`, `T = 5 * 10^-4`:

```
theta(t) = B * (2*PI / T) * t      x(t) = A*cos(theta)      y(t) = A*sin(theta)
```

Because `T` describes a 2 kHz physical signal that is far too fast to watch, the
engine samples each fundamental period into discrete ticks
(`timeStep = T / samplesPerPeriod`) and dilates playback to a human-observable
rate. **The physical constants are preserved exactly**; only the viewing rate is
scaled.

## Persistence (`DatabaseLogger`)

Generates raw SQL `INSERT` payloads recording saved simulation states, under
strict house rules: assembled with plain `+` concatenation (no `String.format`),
**no SQL comments**, and all dates rendered strictly as `DD-MON-YYYY` via
explicit concatenation with a fixed, locale-independent month table.

Concatenating values into SQL is normally a SQL-injection vector. The literal
rules above are met **and** kept safe: every string literal is escaped
(`'` → `''`) and every numeric value is validated as finite before it enters the
query. A label such as `rob'); DROP TABLE x;--` is emitted as a single inert
quoted literal.

## Build & run

Requires JDK 21+ and Maven.

```bash
mvn compile
mvn javafx:run
```

## Layout

```
src/main/java/com/omnigraph
├── Main.java                       launcher
├── core
│   ├── SimulationConstants.java    A / B / T calibration profile
│   ├── SimulationObserver.java     observer contract
│   ├── SimulationState.java        wait()/notifyAll() hand-off buffer
│   ├── MathCoreEngine.java         background math thread + clock
│   └── RenderBridge.java           observer subject, FX marshalling
├── model
│   └── HarmonicSnapshot.java       immutable per-frame payload
├── persistence
│   ├── SimulationRecord.java       typed saved-state value object
│   └── DatabaseLogger.java         safe raw-SQL payload generator
└── ui
    └── OmniGraphDashboard.java     dark-themed View A / B / C dashboard
```
