package com.omnigraph;

import com.omnigraph.ui.OmniGraphDashboard;

import javafx.application.Application;

/**
 * Thin launcher. A non-JavaFX entry class keeps startup working cleanly when
 * the app is run from a plain (non-modular) classpath build.
 */
public final class Main {

    public static void main(String[] args) {
        Application.launch(OmniGraphDashboard.class, args);
    }
}
