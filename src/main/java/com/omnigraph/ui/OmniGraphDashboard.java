package com.omnigraph.ui;

import com.omnigraph.audio.AudioEngine;
import com.omnigraph.core.EngineParameters;
import com.omnigraph.core.MathCoreEngine;
import com.omnigraph.core.RenderBridge;
import com.omnigraph.core.SimulationConstants;
import com.omnigraph.core.SimulationObserver;
import com.omnigraph.core.SimulationState;
import com.omnigraph.model.HarmonicSnapshot;
import com.omnigraph.model.WaveformType;
import com.omnigraph.persistence.DatabaseLogger;
import com.omnigraph.persistence.SimulationRecord;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.DoubleConsumer;

import javax.imageio.ImageIO;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/**
 * Dark-themed laboratory dashboard. Observes the simulation pipeline and renders
 * four synchronized views (rotating phasor, time-domain trace, Fourier epicycle
 * chain, harmonic spectrum) while offering live controls that retune the running
 * math and audio engines, plus SQL and PNG export. Performs no math itself.
 */
public final class OmniGraphDashboard extends Application implements SimulationObserver {

    private static final Color BG = Color.web("#10121b");
    private static final Color PANEL = Color.web("#1b1e2b");
    private static final Color CANVAS_BG = Color.web("#0c0e16");
    private static final Color GRID = Color.web("#2a2f42");
    private static final Color AXIS = Color.web("#46506e");
    private static final Color ACCENT = Color.web("#34e0d0");
    private static final Color MAGENTA = Color.web("#ff5db1");
    private static final Color AMBER = Color.web("#ffcf5c");
    private static final Color TEXT = Color.web("#e6e9f5");
    private static final Color MUTED = Color.web("#8b93ad");

    private static final double CIRCLE_SCALE = 5.0;
    private static final double WAVE_SCALE = 5.0;
    private static final double FOURIER_SCALE = 3.5;
    private static final Path SQL_SCRIPT = Path.of("omnigraph_waveforms.sql");

    private final SimulationConstants constants = SimulationConstants.defaults();
    private final EngineParameters params = EngineParameters.fromDefaults(constants);
    private final DatabaseLogger databaseLogger = new DatabaseLogger();

    private SimulationState state;
    private RenderBridge bridge;
    private MathCoreEngine engine;
    private AudioEngine audio;

    private Canvas circleCanvas;
    private Canvas waveCanvas;
    private Canvas fourierCanvas;
    private Canvas spectrumCanvas;

    private final Deque<Double> sineHistory = new ArrayDeque<>();
    private final Deque<Double> fourierHistory = new ArrayDeque<>();
    private volatile HarmonicSnapshot latest;

    private BorderPane root;
    private TextField labelField;
    private Label statusLabel;
    private TextArea sqlConsole;
    private Button playPauseButton;
    private Button audioButton;

    @Override
    public void start(Stage stage) {
        this.state = new SimulationState();
        this.bridge = new RenderBridge(state);
        this.engine = new MathCoreEngine(constants, params, state);
        this.audio = new AudioEngine(params);

        root = new BorderPane();
        root.setBackground(Background.fill(BG));
        root.setTop(buildHeader());
        root.setCenter(buildCanvasGrid());
        root.setRight(buildControlPanel());
        root.setBottom(buildFooter());

        Scene scene = new Scene(root, 1340, 1000, BG);
        stage.setTitle("OmniGraph — Interconnected Harmonic Laboratory");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdownPipeline());
        stage.show();

        bridge.addObserver(this);
        bridge.start();
        engine.start();
        audio.start();

        startRenderLoop();
    }

    // ---- Layout -------------------------------------------------------------

    private VBox buildHeader() {
        Label title = new Label("OmniGraph");
        title.setFont(Font.font("System", FontWeight.BOLD, 26));
        title.setTextFill(TEXT);

        Label subtitle = new Label("Interconnected Harmonic Generation & Fourier Synthesis");
        subtitle.setTextFill(MUTED);
        subtitle.setFont(Font.font("System", 13));

        VBox box = new VBox(2, title, subtitle);
        box.setPadding(new Insets(16, 20, 10, 20));
        return box;
    }

    private GridPane buildCanvasGrid() {
        circleCanvas = new Canvas(300, 300);
        waveCanvas = new Canvas(620, 300);
        fourierCanvas = new Canvas(936, 250);
        spectrumCanvas = new Canvas(936, 170);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        grid.setPadding(new Insets(4, 12, 8, 20));

        grid.add(panel("View A — Dynamic Geometry (Unit Circle)", circleCanvas), 0, 0);
        grid.add(panel("View B — The Harmonic Plane (Sine Trace)", waveCanvas), 1, 0);
        grid.add(panel("View C — Spectral Synthesis (Fourier Epicycles)", fourierCanvas), 0, 1, 2, 1);
        grid.add(panel("View D — Harmonic Spectrum", spectrumCanvas), 0, 2, 2, 1);

        return grid;
    }

    private VBox panel(String heading, Canvas canvas) {
        Label label = new Label(heading);
        label.setTextFill(ACCENT);
        label.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));

        VBox box = new VBox(8, label, canvas);
        box.setPadding(new Insets(12));
        box.setBackground(Background.fill(PANEL));
        return box;
    }

    private VBox buildControlPanel() {
        Label heading = new Label("LIVE CONTROLS");
        heading.setTextFill(ACCENT);
        heading.setFont(Font.font("System", FontWeight.BOLD, 13));

        ComboBox<WaveformType> waveformBox = new ComboBox<>();
        waveformBox.getItems().setAll(WaveformType.values());
        waveformBox.setValue(params.getWaveform());
        waveformBox.setMaxWidth(Double.MAX_VALUE);
        waveformBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(WaveformType type) {
                return type == null ? "" : type.displayName();
            }

            @Override
            public WaveformType fromString(String s) {
                return WaveformType.valueOf(s);
            }
        });
        waveformBox.valueProperty().addListener((obs, old, val) -> {
            if (val != null) {
                params.setWaveform(val);
            }
        });

        Label waveformLabel = new Label("Target waveform");
        waveformLabel.setTextFill(MUTED);

        VBox controls = new VBox(12,
                heading,
                waveformLabel, waveformBox,
                labeledSlider("Amplitude (A)", 1, 24, params.getAmplitude(), 0,
                        params::setAmplitude),
                labeledSlider("Frequency factor (B)", -6, 6, params.getFrequencyFactor(), 1,
                        params::setFrequencyFactor),
                labeledSlider("Harmonics", 1, 32, params.getHarmonics(), 0,
                        v -> params.setHarmonics((int) Math.round(v))),
                labeledSlider("Speed", 0.1, 4.0, params.getSpeed(), 2,
                        params::setSpeed),
                new Separator(),
                buildAudioControls());

        controls.setPadding(new Insets(14, 18, 14, 14));
        controls.setBackground(Background.fill(PANEL));
        controls.setPrefWidth(280);
        return controls;
    }

    private VBox buildAudioControls() {
        Label heading = new Label("AUDIO SYNTHESIS");
        heading.setTextFill(AMBER);
        heading.setFont(Font.font("System", FontWeight.BOLD, 12));

        audioButton = new Button("Enable Audio");
        audioButton.setMaxWidth(Double.MAX_VALUE);
        audioButton.setOnAction(e -> toggleAudio());

        VBox box = new VBox(10,
                heading,
                audioButton,
                labeledSlider("Pitch (Hz)", 55, 880, audio.getFrequencyHz(), 0,
                        v -> audio.setFrequencyHz(v)),
                labeledSlider("Volume", 0.0, 1.0, 0.3, 2,
                        v -> audio.setVolume(v)));
        return box;
    }

    private VBox labeledSlider(String name, double min, double max, double value,
                              int decimals, DoubleConsumer onChange) {
        Slider slider = new Slider(min, max, value);
        slider.setMaxWidth(Double.MAX_VALUE);

        Label valueLabel = new Label(format(value, decimals));
        valueLabel.setTextFill(TEXT);
        valueLabel.setMinWidth(54);
        valueLabel.setAlignment(Pos.CENTER_RIGHT);

        Label nameLabel = new Label(name);
        nameLabel.setTextFill(MUTED);

        slider.valueProperty().addListener((obs, old, val) -> {
            double v = val.doubleValue();
            valueLabel.setText(format(v, decimals));
            onChange.accept(v);
        });

        HBox header = new HBox(nameLabel, new javafx.scene.layout.Region(), valueLabel);
        HBox.setHgrow(header.getChildren().get(1), javafx.scene.layout.Priority.ALWAYS);
        return new VBox(3, header, slider);
    }

    private static String format(double value, int decimals) {
        double rounded = Math.round(value * Math.pow(10, decimals)) / Math.pow(10, decimals);
        if (decimals == 0) {
            return Integer.toString((int) rounded);
        }
        return Double.toString(rounded);
    }

    private VBox buildFooter() {
        playPauseButton = new Button("Pause");
        playPauseButton.setOnAction(e -> togglePause());

        labelField = new TextField("waveform-01");
        labelField.setPromptText("waveform label");

        Button saveButton = new Button("Save Waveform → SQL");
        saveButton.setOnAction(e -> saveCurrentWaveform());

        Button screenshotButton = new Button("Export PNG");
        screenshotButton.setOnAction(e -> exportScreenshot());

        statusLabel = new Label("Engine running on " + engine.getName());
        statusLabel.setTextFill(MUTED);

        HBox controls = new HBox(12, playPauseButton, labelField, saveButton,
                screenshotButton, statusLabel);
        controls.setAlignment(Pos.CENTER_LEFT);

        sqlConsole = new TextArea(databaseLogger.generateSchema());
        sqlConsole.setEditable(false);
        sqlConsole.setPrefRowCount(4);
        sqlConsole.setWrapText(true);

        VBox box = new VBox(10, controls, sqlConsole);
        box.setPadding(new Insets(6, 20, 16, 20));
        return box;
    }

    // ---- Actions ------------------------------------------------------------

    private void togglePause() {
        if (engine.isPaused()) {
            engine.resumeSimulation();
            playPauseButton.setText("Pause");
            statusLabel.setText("Engine running");
        } else {
            engine.pauseSimulation();
            playPauseButton.setText("Resume");
            statusLabel.setText("Engine paused");
        }
    }

    private void toggleAudio() {
        if (!audio.isAvailable()) {
            statusLabel.setText("No audio device available on this host");
            audioButton.setDisable(true);
            return;
        }
        boolean next = !audio.isEnabled();
        audio.setEnabled(next);
        audioButton.setText(next ? "Mute Audio" : "Enable Audio");
        statusLabel.setText(next ? "Audio playing" : "Audio muted");
    }

    private void saveCurrentWaveform() {
        HarmonicSnapshot frame = latest;
        if (frame == null) {
            statusLabel.setText("Nothing to save yet");
            return;
        }
        String label = (labelField.getText() == null || labelField.getText().isBlank())
                ? "unnamed-waveform" : labelField.getText().trim();

        SimulationRecord record = new SimulationRecord(
                label,
                params.getAmplitude(),
                params.getFrequencyFactor(),
                params.getPeriod(),
                frame.simulationTime(),
                frame.primaryY(),
                frame.fourierValue(),
                params.getHarmonics(),
                LocalDate.now());

        String sql = databaseLogger.appendToScript(SQL_SCRIPT, record);
        sqlConsole.setText(sql);
        statusLabel.setText("Saved '" + label + "' → " + SQL_SCRIPT.toAbsolutePath());
    }

    private void exportScreenshot() {
        WritableImage image = root.snapshot(null, null);
        BufferedImage buffered = SwingFXUtils.fromFXImage(image, null);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File out = new File("omnigraph-" + stamp + ".png");
        try {
            ImageIO.write(buffered, "png", out);
            statusLabel.setText("Saved screenshot → " + out.getAbsolutePath());
        } catch (IOException ex) {
            statusLabel.setText("Screenshot failed: " + ex.getMessage());
        }
    }

    // ---- Observer callback (FX thread, via RenderBridge) --------------------

    @Override
    public void onSnapshot(HarmonicSnapshot snapshot) {
        latest = snapshot;
        pushBounded(sineHistory, snapshot.primaryY(), (int) waveCanvas.getWidth());
        pushBounded(fourierHistory, snapshot.fourierValue(), (int) fourierCanvas.getWidth() - 220);
    }

    private static void pushBounded(Deque<Double> history, double value, int max) {
        history.addLast(value);
        while (history.size() > max) {
            history.removeFirst();
        }
    }

    // ---- Rendering (FX thread, display rate) --------------------------------

    private void startRenderLoop() {
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                HarmonicSnapshot frame = latest;
                drawCircle(frame);
                drawWave(frame);
                drawFourier(frame);
                drawSpectrum(frame);
            }
        };
        timer.start();
    }

    private void drawCircle(HarmonicSnapshot frame) {
        GraphicsContext g = circleCanvas.getGraphicsContext2D();
        double w = circleCanvas.getWidth();
        double h = circleCanvas.getHeight();
        double cx = w / 2.0;
        double cy = h / 2.0;
        clear(g, w, h);

        if (frame == null) {
            return;
        }
        double r = frame.amplitude() * CIRCLE_SCALE;

        g.setStroke(AXIS);
        g.setLineWidth(1);
        g.strokeLine(cx - r - 16, cy, cx + r + 16, cy);
        g.strokeLine(cx, cy - r - 16, cx, cy + r + 16);

        g.setStroke(GRID);
        g.strokeOval(cx - r, cy - r, 2 * r, 2 * r);

        double tipX = cx + frame.primaryX() * CIRCLE_SCALE;
        double tipY = cy - frame.primaryY() * CIRCLE_SCALE;

        g.setStroke(MUTED);
        g.setLineDashes(4, 4);
        g.strokeLine(tipX, tipY, cx + r + 16, tipY);
        g.setLineDashes();

        g.setStroke(ACCENT);
        g.setLineWidth(2.5);
        g.strokeLine(cx, cy, tipX, tipY);
        g.setFill(ACCENT);
        g.fillOval(tipX - 5, tipY - 5, 10, 10);
        g.setFill(MAGENTA);
        g.fillOval(cx + r + 12, tipY - 4, 8, 8);
    }

    private void drawWave(HarmonicSnapshot frame) {
        GraphicsContext g = waveCanvas.getGraphicsContext2D();
        double w = waveCanvas.getWidth();
        double h = waveCanvas.getHeight();
        double midY = h / 2.0;
        clear(g, w, h);

        g.setStroke(AXIS);
        g.setLineWidth(1);
        g.strokeLine(0, midY, w, midY);

        drawScrollingTrace(g, sineHistory, w, midY, WAVE_SCALE, MAGENTA, 0);

        if (frame != null) {
            double y = midY - frame.primaryY() * WAVE_SCALE;
            g.setFill(MAGENTA);
            g.fillOval(w - 6, y - 4, 8, 8);
        }
    }

    private void drawFourier(HarmonicSnapshot frame) {
        GraphicsContext g = fourierCanvas.getGraphicsContext2D();
        double w = fourierCanvas.getWidth();
        double h = fourierCanvas.getHeight();
        double originX = 150;
        double midY = h / 2.0;
        clear(g, w, h);

        g.setStroke(AXIS);
        g.setLineWidth(1);
        g.strokeLine(originX, midY, w, midY);

        double traceStartX = 210;
        drawScrollingTrace(g, fourierHistory, w, midY, FOURIER_SCALE, AMBER, traceStartX);

        if (frame == null) {
            return;
        }

        double px = originX;
        double py = midY;
        g.setLineWidth(1.5);
        for (int i = 0; i < frame.epicycleCount(); i++) {
            double tipX = originX + frame.epicycleX(i) * FOURIER_SCALE;
            double tipY = midY - frame.epicycleY(i) * FOURIER_SCALE;
            double radius = Math.hypot(tipX - px, tipY - py);

            g.setStroke(GRID);
            g.strokeOval(px - radius, py - radius, 2 * radius, 2 * radius);
            g.setStroke(ACCENT);
            g.strokeLine(px, py, tipX, tipY);
            px = tipX;
            py = tipY;
        }

        g.setFill(AMBER);
        g.fillOval(px - 5, py - 5, 10, 10);
        g.setStroke(MUTED);
        g.setLineDashes(4, 4);
        g.strokeLine(px, py, traceStartX, py);
        g.setLineDashes();
    }

    private void drawSpectrum(HarmonicSnapshot frame) {
        GraphicsContext g = spectrumCanvas.getGraphicsContext2D();
        double w = spectrumCanvas.getWidth();
        double h = spectrumCanvas.getHeight();
        double baseY = h - 26;
        clear(g, w, h);

        g.setStroke(AXIS);
        g.setLineWidth(1);
        g.strokeLine(0, baseY, w, baseY);

        if (frame == null || frame.spectrumSize() == 0) {
            return;
        }

        double maxAmp = 0.0;
        for (int i = 0; i < frame.spectrumSize(); i++) {
            maxAmp = Math.max(maxAmp, frame.spectrumAmplitude(i));
        }
        if (maxAmp <= 0.0) {
            return;
        }

        int n = frame.spectrumSize();
        double slot = w / n;
        double barWidth = Math.min(slot * 0.6, 28);
        double maxBarHeight = baseY - 16;

        g.setFont(Font.font("System", 10));
        for (int i = 0; i < n; i++) {
            double amp = frame.spectrumAmplitude(i);
            double barHeight = (amp / maxAmp) * maxBarHeight;
            double x = (i * slot) + (slot - barWidth) / 2.0;
            double y = baseY - barHeight;

            g.setFill(i == 0 ? AMBER : ACCENT);
            g.fillRect(x, y, barWidth, barHeight);

            g.setFill(MUTED);
            g.fillText("k" + frame.harmonicNumber(i), x, baseY + 16);
        }

        g.setFill(TEXT);
        g.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        g.fillText(frame.waveformName() + "  •  " + n + " harmonics", 8, 16);
    }

    private void drawScrollingTrace(GraphicsContext g, Deque<Double> history,
                                    double w, double midY, double scale, Color color,
                                    double startX) {
        if (history.size() < 2) {
            return;
        }
        g.setStroke(color);
        g.setLineWidth(2);
        g.beginPath();
        int n = history.size();
        double span = w - startX;
        int i = 0;
        boolean first = true;
        for (double value : history) {
            double x = startX + (span * i) / (n - 1);
            double y = midY - value * scale;
            if (first) {
                g.moveTo(x, y);
                first = false;
            } else {
                g.lineTo(x, y);
            }
            i++;
        }
        g.stroke();
    }

    private void clear(GraphicsContext g, double w, double h) {
        g.setFill(CANVAS_BG);
        g.fillRect(0, 0, w, h);
    }

    @Override
    public void stop() {
        shutdownPipeline();
    }

    private void shutdownPipeline() {
        if (engine != null) {
            engine.shutdown();
        }
        if (bridge != null) {
            bridge.shutdown();
        }
        if (audio != null) {
            audio.shutdown();
        }
    }
}
