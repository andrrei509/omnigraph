package com.omnigraph.ui;

import com.omnigraph.core.MathCoreEngine;
import com.omnigraph.core.RenderBridge;
import com.omnigraph.core.SimulationConstants;
import com.omnigraph.core.SimulationObserver;
import com.omnigraph.core.SimulationState;
import com.omnigraph.model.HarmonicSnapshot;
import com.omnigraph.persistence.DatabaseLogger;
import com.omnigraph.persistence.SimulationRecord;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Dark-themed dashboard that observes the simulation pipeline and renders the
 * three synchronized views:
 * <ul>
 *   <li><b>View A</b> – the rotating unit-circle phasor.</li>
 *   <li><b>View B</b> – the time-domain sine traced from the phasor's y.</li>
 *   <li><b>View C</b> – the Fourier epicycle chain and synthesized signal.</li>
 * </ul>
 *
 * <p>This class is a pure {@link SimulationObserver}: it performs no math. It
 * receives immutable frames on the FX thread, buffers the values needed for the
 * scrolling traces, and an {@link AnimationTimer} paints them at display rate.
 */
public final class OmniGraphDashboard extends Application implements SimulationObserver {

    private static final Color BG = Color.web("#12141c");
    private static final Color PANEL = Color.web("#1b1e2b");
    private static final Color GRID = Color.web("#2a2f42");
    private static final Color AXIS = Color.web("#46506e");
    private static final Color ACCENT = Color.web("#34e0d0");
    private static final Color MAGENTA = Color.web("#ff5db1");
    private static final Color AMBER = Color.web("#ffcf5c");
    private static final Color TEXT = Color.web("#e6e9f5");
    private static final Color MUTED = Color.web("#8b93ad");

    private final SimulationConstants constants = SimulationConstants.defaults();
    private final DatabaseLogger databaseLogger = new DatabaseLogger();

    private SimulationState state;
    private RenderBridge bridge;
    private MathCoreEngine engine;

    private Canvas circleCanvas;
    private Canvas waveCanvas;
    private Canvas fourierCanvas;

    private final Deque<Double> sineHistory = new ArrayDeque<>();
    private final Deque<Double> fourierHistory = new ArrayDeque<>();
    private volatile HarmonicSnapshot latest;

    private double unitScale;

    private TextField labelField;
    private Label statusLabel;
    private TextArea sqlConsole;
    private Button playPauseButton;

    @Override
    public void start(Stage stage) {
        this.state = new SimulationState();
        this.bridge = new RenderBridge(state);
        this.engine = new MathCoreEngine(constants, state);
        this.unitScale = 80.0 / constants.amplitude;

        BorderPane root = new BorderPane();
        root.setBackground(javafx.scene.layout.Background.fill(BG));
        root.setTop(buildHeader());
        root.setCenter(buildCanvasGrid());
        root.setBottom(buildControlBar());

        Scene scene = new Scene(root, 1180, 820, BG);
        stage.setTitle("OmniGraph — Interconnected Harmonic Laboratory");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdownPipeline());
        stage.show();

        bridge.addObserver(this);
        bridge.start();
        engine.start();

        startRenderLoop();
    }

    private VBox buildHeader() {
        Label title = new Label("OmniGraph");
        title.setFont(Font.font("System", FontWeight.BOLD, 26));
        title.setTextFill(TEXT);

        Label subtitle = new Label("Interconnected Harmonic Generation & Fourier Synthesis    "
                + "A=" + constants.amplitude
                + "   B=" + constants.frequencyFactor
                + "   T=" + constants.period);
        subtitle.setTextFill(MUTED);
        subtitle.setFont(Font.font("System", 13));

        VBox box = new VBox(2, title, subtitle);
        box.setPadding(new Insets(16, 20, 12, 20));
        return box;
    }

    private GridPane buildCanvasGrid() {
        circleCanvas = new Canvas(360, 360);
        waveCanvas = new Canvas(760, 360);
        fourierCanvas = new Canvas(1140, 320);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        grid.setPadding(new Insets(4, 20, 8, 20));

        grid.add(panel("View A — Dynamic Geometry (Unit Circle)", circleCanvas), 0, 0);
        grid.add(panel("View B — The Harmonic Plane (Sine Trace)", waveCanvas), 1, 0);
        grid.add(panel("View C — Spectral Synthesis (Fourier Epicycles)", fourierCanvas), 0, 1, 2, 1);

        return grid;
    }

    private VBox panel(String heading, Canvas canvas) {
        Label label = new Label(heading);
        label.setTextFill(ACCENT);
        label.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));

        VBox box = new VBox(8, label, canvas);
        box.setPadding(new Insets(12));
        box.setBackground(javafx.scene.layout.Background.fill(PANEL));
        return box;
    }

    private VBox buildControlBar() {
        playPauseButton = new Button("Pause");
        playPauseButton.setOnAction(e -> togglePause());

        labelField = new TextField("waveform-01");
        labelField.setPromptText("waveform label");

        Button saveButton = new Button("Save Waveform → SQL");
        saveButton.setOnAction(e -> saveCurrentWaveform());

        statusLabel = new Label("Engine running on " + engine.getName());
        statusLabel.setTextFill(MUTED);

        HBox controls = new HBox(12, playPauseButton, labelField, saveButton, statusLabel);
        controls.setAlignment(Pos.CENTER_LEFT);

        sqlConsole = new TextArea();
        sqlConsole.setEditable(false);
        sqlConsole.setPrefRowCount(4);
        sqlConsole.setWrapText(true);
        sqlConsole.setText(databaseLogger.generateSchema());

        VBox box = new VBox(10, controls, sqlConsole);
        box.setPadding(new Insets(8, 20, 18, 20));
        return box;
    }

    private void togglePause() {
        if (engine.isPaused()) {
            engine.resumeSimulation();
            playPauseButton.setText("Pause");
            statusLabel.setText("Engine running on " + engine.getName());
        } else {
            engine.pauseSimulation();
            playPauseButton.setText("Resume");
            statusLabel.setText("Engine paused");
        }
    }

    private void saveCurrentWaveform() {
        HarmonicSnapshot frame = latest;
        if (frame == null) {
            statusLabel.setText("Nothing to save yet");
            return;
        }
        String label = labelField.getText() == null || labelField.getText().isBlank()
                ? "unnamed-waveform" : labelField.getText().trim();

        SimulationRecord record = new SimulationRecord(
                label,
                constants.amplitude,
                constants.frequencyFactor,
                constants.period,
                frame.simulationTime(),
                frame.primaryY(),
                frame.fourierValue(),
                constants.fourierHarmonics,
                LocalDate.now());

        String sql = databaseLogger.buildInsert(record);
        sqlConsole.setText(sql);
        statusLabel.setText("Saved '" + label + "' at frame " + frame.sequence());
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
        double r = constants.amplitude * unitScale;

        clear(g, w, h);
        g.setStroke(AXIS);
        g.setLineWidth(1);
        g.strokeLine(cx - r - 20, cy, cx + r + 20, cy);
        g.strokeLine(cx, cy - r - 20, cx, cy + r + 20);

        g.setStroke(GRID);
        g.strokeOval(cx - r, cy - r, 2 * r, 2 * r);

        if (frame == null) {
            return;
        }

        double tipX = cx + frame.primaryX() * unitScale;
        double tipY = cy - frame.primaryY() * unitScale;

        g.setStroke(MUTED);
        g.setLineWidth(1);
        g.setLineDashes(4, 4);
        g.strokeLine(tipX, tipY, cx + r + 20, tipY);
        g.setLineDashes();

        g.setStroke(ACCENT);
        g.setLineWidth(2.5);
        g.strokeLine(cx, cy, tipX, tipY);

        g.setFill(ACCENT);
        g.fillOval(tipX - 5, tipY - 5, 10, 10);
        g.setFill(MAGENTA);
        g.fillOval(cx + r + 16, tipY - 4, 8, 8);
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

        drawScrollingTrace(g, sineHistory, w, midY, unitScale, MAGENTA);

        if (frame != null) {
            double y = midY - frame.primaryY() * unitScale;
            g.setFill(MAGENTA);
            g.fillOval(w - 6, y - 4, 8, 8);
        }
    }

    private void drawFourier(HarmonicSnapshot frame) {
        GraphicsContext g = fourierCanvas.getGraphicsContext2D();
        double w = fourierCanvas.getWidth();
        double h = fourierCanvas.getHeight();
        double originX = 160;
        double midY = h / 2.0;

        clear(g, w, h);
        g.setStroke(AXIS);
        g.setLineWidth(1);
        g.strokeLine(originX, midY, w, midY);

        double traceStartX = 220;
        drawScrollingTrace(g, fourierHistory, w, midY, unitScale, AMBER, traceStartX);

        if (frame == null) {
            return;
        }

        double px = originX;
        double py = midY;
        g.setLineWidth(1.5);
        for (int i = 0; i < frame.epicycleCount(); i++) {
            double tipX = originX + frame.epicycleX(i) * unitScale;
            double tipY = midY - frame.epicycleY(i) * unitScale;
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
        g.setLineWidth(1);
        g.setLineDashes(4, 4);
        g.strokeLine(px, py, traceStartX, py);
        g.setLineDashes();
    }

    private void drawScrollingTrace(GraphicsContext g, Deque<Double> history,
                                    double w, double midY, double scale, Color color) {
        drawScrollingTrace(g, history, w, midY, scale, color, 0);
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
        g.setFill(Color.web("#0e1018"));
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
    }
}
