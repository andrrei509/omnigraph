package com.omnigraph.ui;

import com.omnigraph.audio.AudioEngine;
import com.omnigraph.core.EngineParameters;
import com.omnigraph.core.MathCoreEngine;
import com.omnigraph.core.RenderBridge;
import com.omnigraph.core.SimulationConstants;
import com.omnigraph.core.SimulationObserver;
import com.omnigraph.core.SimulationState;
import com.omnigraph.dsp.FourierPath;
import com.omnigraph.dsp.SpectralAnalyzer;
import com.omnigraph.dsp.SpectralAnalyzers;
import com.omnigraph.expr.ExpressionException;
import com.omnigraph.expr.FunctionParser;
import com.omnigraph.model.Epicycle;
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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import java.util.function.DoubleUnaryOperator;
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

    private static final int PATH_SAMPLES = 512;
    private static final double PATH_DT = 0.002;
    private static final Color EPI_CIRCLE = Color.web("#2f3550");

    private SimulationState state;
    private RenderBridge bridge;
    private MathCoreEngine engine;
    private AudioEngine audio;
    private SpectralAnalyzer analyzer;
    private String analyzerBackend = "java";

    private Canvas circleCanvas;
    private Canvas waveCanvas;
    private Canvas fourierCanvas;
    private Canvas spectrumCanvas;

    private final Deque<Double> sineHistory = new ArrayDeque<>();
    private final Deque<Double> fourierHistory = new ArrayDeque<>();
    private volatile HarmonicSnapshot latest;

    private Region root;
    private TextField labelField;
    private Label statusLabel;
    private TextArea sqlConsole;
    private Button playPauseButton;
    private Button audioButton;

    // ---- Path Studio state --------------------------------------------------
    private Canvas studioDrawCanvas;
    private Canvas studioAnimCanvas;
    private TextField functionField;
    private Slider epicycleSlider;
    private Label epicycleCountLabel;
    private Label studioStatus;

    private final java.util.List<double[]> drawnPoints = new java.util.ArrayList<>();
    private volatile java.util.List<com.omnigraph.model.Epicycle> epicycles = java.util.List.of();
    private double[][] targetPath;
    private double pathScale = 1.0;
    private double animT = 0.0;
    private final Deque<double[]> trail = new ArrayDeque<>();

    @Override
    public void start(Stage stage) {
        this.analyzer = SpectralAnalyzers.best();
        this.analyzerBackend = analyzer.backend();
        this.state = new SimulationState();
        this.bridge = new RenderBridge(state);
        this.engine = new MathCoreEngine(constants, params, state, analyzer);
        this.audio = new AudioEngine(params);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab("Harmonic Lab", buildHarmonicLab()));
        tabs.getTabs().add(new Tab("Path Studio", buildPathStudio()));
        tabs.setBackground(Background.fill(BG));
        root = tabs;

        Scene scene = new Scene(tabs, 1360, 1010, BG);
        stage.setTitle("OmniGraph — Interconnected Mathematical Laboratory");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdownPipeline());
        stage.show();

        bridge.addObserver(this);
        bridge.start();
        engine.start();
        audio.start();

        startRenderLoop();
        startPathLoop();
    }

    private BorderPane buildHarmonicLab() {
        BorderPane lab = new BorderPane();
        lab.setBackground(Background.fill(BG));
        lab.setTop(buildHeader());
        lab.setCenter(buildCanvasGrid());
        lab.setRight(buildControlPanel());
        lab.setBottom(buildFooter());
        return lab;
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
        grid.add(panel("View D — Measured Spectrum (FFT) vs Theoretical", spectrumCanvas), 0, 2, 2, 1);

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

        if (frame == null || frame.measuredBins() == 0) {
            return;
        }

        int highestHarmonic = 1;
        for (int i = 0; i < frame.spectrumSize(); i++) {
            highestHarmonic = Math.max(highestHarmonic, frame.harmonicNumber(i));
        }
        int displayBins = Math.min(frame.measuredBins() - 1, highestHarmonic + 2);

        double[] theoretical = new double[frame.measuredBins()];
        for (int i = 0; i < frame.spectrumSize(); i++) {
            int k = frame.harmonicNumber(i);
            if (k < theoretical.length) {
                theoretical[k] = frame.spectrumAmplitude(i);
            }
        }

        double maxAmp = 0.0;
        for (int b = 1; b <= displayBins; b++) {
            maxAmp = Math.max(maxAmp, Math.max(frame.measuredAmplitude(b), theoretical[b]));
        }
        if (maxAmp <= 0.0) {
            return;
        }

        double slot = w / (displayBins + 1.0);
        double barWidth = Math.min(slot * 0.55, 26);
        double maxBarHeight = baseY - 18;

        g.setFont(Font.font("System", 10));
        for (int b = 1; b <= displayBins; b++) {
            double measured = frame.measuredAmplitude(b);
            double barHeight = (measured / maxAmp) * maxBarHeight;
            double x = (b * slot) + (slot - barWidth) / 2.0;
            double y = baseY - barHeight;

            g.setFill(b == 1 ? AMBER : ACCENT);
            g.fillRect(x, y, barWidth, barHeight);

            // theoretical reference marker for the same harmonic
            double tHeight = (theoretical[b] / maxAmp) * maxBarHeight;
            double tY = baseY - tHeight;
            g.setStroke(MAGENTA);
            g.setLineWidth(2);
            g.strokeLine(x - 2, tY, x + barWidth + 2, tY);

            g.setFill(MUTED);
            g.fillText("k" + b, x, baseY + 16);
        }

        g.setFill(TEXT);
        g.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        g.fillText(frame.waveformName() + "  •  bars = FFT (" + analyzerBackend
                + ")  •  lines = theoretical", 8, 16);
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

    // ---- Path Studio --------------------------------------------------------

    private BorderPane buildPathStudio() {
        Label title = new Label("Path Studio — draw a shape or type a function; epicycles redraw it");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(TEXT);
        Label sub = new Label("A complex DFT (FFT backend: " + analyzerBackend
                + ") turns any closed path into a chain of rotating vectors.");
        sub.setTextFill(MUTED);
        VBox header = new VBox(2, title, sub);
        header.setPadding(new Insets(14, 20, 8, 20));

        studioDrawCanvas = new Canvas(440, 440);
        clearStudioDrawCanvas();
        studioDrawCanvas.setOnMousePressed(e -> {
            drawnPoints.clear();
            drawnPoints.add(new double[] {e.getX(), e.getY()});
            clearStudioDrawCanvas();
        });
        studioDrawCanvas.setOnMouseDragged(e -> {
            drawnPoints.add(new double[] {e.getX(), e.getY()});
            renderDrawnStroke();
        });

        studioAnimCanvas = new Canvas(620, 440);

        functionField = new TextField("sin(3*x) + 0.5*cos(5*x)");
        functionField.setPromptText("f(x), e.g. sin(x)+0.3*cos(3*x)");

        Button plotButton = new Button("Plot f(x)");
        plotButton.setMaxWidth(Double.MAX_VALUE);
        plotButton.setOnAction(e -> plotFunction());

        Button useDrawingButton = new Button("Decompose Drawing");
        useDrawingButton.setMaxWidth(Double.MAX_VALUE);
        useDrawingButton.setOnAction(e -> decomposeDrawing());

        Button clearButton = new Button("Clear Drawing");
        clearButton.setMaxWidth(Double.MAX_VALUE);
        clearButton.setOnAction(e -> {
            drawnPoints.clear();
            clearStudioDrawCanvas();
        });

        epicycleSlider = new Slider(1, 60, 60);
        epicycleSlider.setMaxWidth(Double.MAX_VALUE);
        epicycleCountLabel = new Label("Epicycles: 60");
        epicycleCountLabel.setTextFill(TEXT);
        epicycleSlider.valueProperty().addListener((o, ov, nv) ->
                epicycleCountLabel.setText("Epicycles: " + (int) nv.doubleValue()
                        + " / " + (int) epicycleSlider.getMax()));

        studioStatus = new Label("Type a function and press Plot, or draw on the left canvas.");
        studioStatus.setTextFill(MUTED);
        studioStatus.setWrapText(true);

        Label fnLabel = new Label("Function f(x)   (domain -π .. π)");
        fnLabel.setTextFill(MUTED);
        Label drawHint = new Label("Or draw freehand on the left, then:");
        drawHint.setTextFill(MUTED);
        Label countHint = new Label("Truncate the series:");
        countHint.setTextFill(MUTED);

        VBox controls = new VBox(10,
                fnLabel, functionField, plotButton,
                new Separator(),
                drawHint, useDrawingButton, clearButton,
                new Separator(),
                countHint, epicycleCountLabel, epicycleSlider,
                new Separator(),
                studioStatus);
        controls.setPadding(new Insets(16, 16, 16, 16));
        controls.setBackground(Background.fill(PANEL));
        controls.setPrefWidth(280);

        HBox canvases = new HBox(16,
                panel("Input — draw a closed shape", studioDrawCanvas),
                panel("Output — epicycles retrace the path", studioAnimCanvas));
        canvases.setPadding(new Insets(8, 16, 8, 20));

        BorderPane studio = new BorderPane();
        studio.setBackground(Background.fill(BG));
        studio.setTop(header);
        studio.setCenter(canvases);
        studio.setRight(controls);

        plotFunction();
        return studio;
    }

    private void clearStudioDrawCanvas() {
        GraphicsContext g = studioDrawCanvas.getGraphicsContext2D();
        double w = studioDrawCanvas.getWidth();
        double h = studioDrawCanvas.getHeight();
        clear(g, w, h);
        g.setStroke(AXIS);
        g.setLineWidth(1);
        g.strokeLine(0, h / 2, w, h / 2);
        g.strokeLine(w / 2, 0, w / 2, h);
    }

    private void renderDrawnStroke() {
        if (drawnPoints.size() < 2) {
            return;
        }
        GraphicsContext g = studioDrawCanvas.getGraphicsContext2D();
        double[] p0 = drawnPoints.get(drawnPoints.size() - 2);
        double[] p1 = drawnPoints.get(drawnPoints.size() - 1);
        g.setStroke(MAGENTA);
        g.setLineWidth(2);
        g.strokeLine(p0[0], p0[1], p1[0], p1[1]);
    }

    private void plotFunction() {
        try {
            DoubleUnaryOperator f = FunctionParser.parse(functionField.getText());
            double[][] path = FourierPath.fromFunction(f, -Math.PI, Math.PI, PATH_SAMPLES);
            setupPath(path, "f(x) = " + functionField.getText().trim());
            previewPathOnDrawCanvas(path);
        } catch (ExpressionException ex) {
            studioStatus.setText("Parse error: " + ex.getMessage());
        }
    }

    private void decomposeDrawing() {
        if (drawnPoints.size() < 2) {
            studioStatus.setText("Draw a shape on the left canvas first.");
            return;
        }
        double w = studioDrawCanvas.getWidth();
        double h = studioDrawCanvas.getHeight();
        java.util.List<double[]> mathPts = new java.util.ArrayList<>(drawnPoints.size());
        for (double[] p : drawnPoints) {
            mathPts.add(new double[] {p[0] - w / 2, h / 2 - p[1]});
        }
        double[][] path = FourierPath.fromPoints(mathPts, PATH_SAMPLES);
        setupPath(path, "freehand drawing (" + drawnPoints.size() + " points)");
    }

    private void setupPath(double[][] path, String description) {
        this.targetPath = path;
        this.epicycles = FourierPath.decompose(path[0], path[1], analyzer);

        double maxAbs = 1e-9;
        for (int j = 0; j < path[0].length; j++) {
            maxAbs = Math.max(maxAbs, Math.max(Math.abs(path[0][j]), Math.abs(path[1][j])));
        }
        double half = Math.min(studioAnimCanvas.getWidth(), studioAnimCanvas.getHeight()) / 2.0 - 30;
        this.pathScale = half / maxAbs;

        this.animT = 0.0;
        this.trail.clear();

        int max = epicycles.size();
        int def = Math.min(max, 60);
        epicycleSlider.setMax(max);
        epicycleSlider.setValue(def);
        epicycleCountLabel.setText("Epicycles: " + def + " / " + max);
        studioStatus.setText("Decomposed " + description + " into " + max
                + " epicycles via FFT (" + analyzerBackend + ").");
    }

    private void previewPathOnDrawCanvas(double[][] path) {
        clearStudioDrawCanvas();
        GraphicsContext g = studioDrawCanvas.getGraphicsContext2D();
        double w = studioDrawCanvas.getWidth();
        double h = studioDrawCanvas.getHeight();
        double cx = w / 2;
        double cy = h / 2;

        double maxAbs = 1e-9;
        for (int j = 0; j < path[0].length; j++) {
            maxAbs = Math.max(maxAbs, Math.max(Math.abs(path[0][j]), Math.abs(path[1][j])));
        }
        double s = (Math.min(w, h) / 2 - 20) / maxAbs;

        g.setStroke(ACCENT);
        g.setLineWidth(1.5);
        g.beginPath();
        for (int j = 0; j < path[0].length; j++) {
            double x = cx + path[0][j] * s;
            double y = cy - path[1][j] * s;
            if (j == 0) {
                g.moveTo(x, y);
            } else {
                g.lineTo(x, y);
            }
        }
        g.stroke();
    }

    private void startPathLoop() {
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                drawPathStudio();
            }
        };
        timer.start();
    }

    private void drawPathStudio() {
        GraphicsContext g = studioAnimCanvas.getGraphicsContext2D();
        double w = studioAnimCanvas.getWidth();
        double h = studioAnimCanvas.getHeight();
        double cx = w / 2;
        double cy = h / 2;
        clear(g, w, h);

        java.util.List<Epicycle> eps = epicycles;
        if (eps.isEmpty()) {
            return;
        }

        if (targetPath != null) {
            g.setStroke(GRID);
            g.setLineWidth(1);
            g.beginPath();
            for (int j = 0; j < targetPath[0].length; j++) {
                double x = cx + targetPath[0][j] * pathScale;
                double y = cy - targetPath[1][j] * pathScale;
                if (j == 0) {
                    g.moveTo(x, y);
                } else {
                    g.lineTo(x, y);
                }
            }
            g.stroke();
        }

        int m = (int) epicycleSlider.getValue();
        double x = cx;
        double y = cy;
        for (int i = 0; i < m && i < eps.size(); i++) {
            Epicycle e = eps.get(i);
            double prevX = x;
            double prevY = y;
            double r = e.amplitude() * pathScale;

            if (r > 0.8) {
                g.setStroke(EPI_CIRCLE);
                g.setLineWidth(1);
                g.strokeOval(prevX - r, prevY - r, 2 * r, 2 * r);
            }
            x += e.xAt(animT) * pathScale;
            y -= e.yAt(animT) * pathScale;
            g.setStroke(ACCENT);
            g.setLineWidth(1.2);
            g.strokeLine(prevX, prevY, x, y);
        }

        trail.addLast(new double[] {x, y});
        while (trail.size() > (int) (1.0 / PATH_DT) + 2) {
            trail.removeFirst();
        }

        if (trail.size() > 1) {
            g.setStroke(AMBER);
            g.setLineWidth(2.5);
            g.beginPath();
            boolean first = true;
            for (double[] p : trail) {
                if (first) {
                    g.moveTo(p[0], p[1]);
                    first = false;
                } else {
                    g.lineTo(p[0], p[1]);
                }
            }
            g.stroke();
        }

        g.setFill(AMBER);
        g.fillOval(x - 4, y - 4, 8, 8);

        animT += PATH_DT;
        if (animT >= 1.0) {
            animT -= 1.0;
            trail.clear();
        }
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
