# OmniGraph

An interactive mathematical laboratory that demonstrates the interconnectedness
of geometry, signals, and complex calculus in real time. It delivers
**Interconnected Harmonic Generation & Fourier Synthesis** across four
synchronized, live-updating views you can retune — and hear — as it runs.

- **View A — Dynamic Geometry:** a rotating phasor on the unit circle.
- **View B — The Harmonic Plane:** the phasor's vertical (`y`) projection traced
  into the time domain, building a sine wave as the vector spins.
- **View C — Spectral Synthesis:** a Fourier epicycle chain that sums harmonics
  into the selected synthesized signal.
- **View D — Harmonic Spectrum:** a live bar chart of each harmonic's amplitude.

## Live laboratory

Everything below retunes the running simulation in real time — no restart:

- **Target waveform:** Sine, Square, Sawtooth, or Triangle. The same spectrum
  drives Views C and D *and* the audio engine.
- **Sliders:** amplitude `A`, frequency factor `B`, harmonic count, and playback
  speed. Phase is accumulated incrementally, so changing `B` mid-run never causes
  a visual jump.
- **Audio synthesis:** a real-time additive synth (`javax.sound`) on its own
  thread sonifies the exact waveform on screen — change the waveform or harmonic
  count and you hear the timbre change. Degrades gracefully if no audio device is
  present (e.g. a headless host).
- **Export:** save the current state as injection-safe SQL appended to
  `omnigraph_waveforms.sql`, or capture the whole dashboard to a timestamped PNG.

## Architecture

The core pipeline is deliberately decoupled across threads so the rendering and
audio layers never perform a single math operation.

```
 MathCoreEngine ──publish()──▶ SimulationState ──take()──▶ RenderBridge ──Platform.runLater──▶ Dashboard
 (math thread)                 (wait/notifyAll                (observer subject)                 (FX thread,
  owns clock + all             hand-off buffer,               fans out to observers)            Views A–D)
  trig/geometry)               latest-value-wins)

 EngineParameters ── shared live tuning, read by both ──▶ MathCoreEngine + AudioEngine
```

- **`MathCoreEngine`** (`extends Thread`) owns the clock and computes every
  frame, reading live knobs from a shared `EngineParameters`. Paces itself
  against wall-clock time.
- **`SimulationState`** is the one intentional use of low-level monitor
  primitives (`synchronized`, `wait()`, `notifyAll()`): a single-slot rendezvous
  where the newest frame wins and the consumer blocks until one is available. No
  `Executor`s are used in this core pipeline, by design.
- **`RenderBridge`** (`extends Thread`) is the `Observable` subject of the
  Observer pattern. It blocks on the buffer, then marshals each frame onto the
  JavaFX Application Thread. The math engine has no knowledge of who renders.
- **`AudioEngine`** (`extends Thread`) streams 16-bit PCM additive synthesis from
  the same shared spectrum.
- **`OmniGraphDashboard`** is a pure `SimulationObserver`. It buffers values for
  the scrolling traces and paints all four views with an `AnimationTimer`.

## Calibration constants

The engine initializes with `A = 12`, `B = -2`, `T = 5 * 10^-4`:

```
x(t) = A*cos(theta)      y(t) = A*sin(theta)      with theta accumulated from B
```

Because `T` describes a 2 kHz physical signal that is far too fast to watch, the
engine samples each fundamental period into discrete ticks and dilates playback
to a human-observable rate. **The physical constants are preserved exactly**;
only the viewing rate is scaled (and is adjustable via the Speed control).

## Persistence (`DatabaseLogger`)

Generates raw SQL `INSERT` payloads recording saved simulation states, under
strict house rules: assembled with plain `+` concatenation (no `String.format`),
**no SQL comments**, and all dates rendered strictly as `DD-MON-YYYY` via
explicit concatenation with a fixed, locale-independent month table.

Concatenating values into SQL is normally a SQL-injection vector. The literal
rules above are met **and** kept safe: every string literal is escaped
(`'` → `''`) and every numeric value is validated as finite before it enters the
query. A label such as `rob'); DROP TABLE x;--` is emitted as a single inert
quoted literal. The same escaping path is used when appending to the on-disk
`.sql` script.

## Build, test, run

Requires JDK 21+ and Maven.

```bash
mvn test          # 9 unit tests: waveform math + SQL rules (date format, injection safety)
mvn javafx:run    # launch the dashboard
```

## Layout

```
src/main/java/com/omnigraph
├── Main.java                       launcher
├── core
│   ├── SimulationConstants.java    A / B / T default calibration profile
│   ├── EngineParameters.java       thread-safe live tuning shared by both engines
│   ├── SimulationObserver.java     observer contract
│   ├── SimulationState.java        wait()/notifyAll() hand-off buffer
│   ├── MathCoreEngine.java         background math thread + clock
│   └── RenderBridge.java           observer subject, FX marshalling
├── model
│   ├── WaveformType.java           Sine/Square/Sawtooth/Triangle Fourier series
│   ├── Spectrum.java               harmonic numbers + signed weights
│   └── HarmonicSnapshot.java       immutable per-frame payload
├── audio
│   └── AudioEngine.java            real-time additive PCM synthesis thread
├── persistence
│   ├── SimulationRecord.java       typed saved-state value object
│   └── DatabaseLogger.java         safe raw-SQL payload generator + script export
└── ui
    └── OmniGraphDashboard.java     dark-themed Views A–D + live controls + export

src/test/java/com/omnigraph        WaveformType / DatabaseLogger / MathCoreEngine tests
```
