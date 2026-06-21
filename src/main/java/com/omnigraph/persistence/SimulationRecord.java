package com.omnigraph.persistence;

import java.time.LocalDate;

/**
 * Plain value object describing a saved simulation state. Carrying these as a
 * typed record (rather than loose strings) is what lets {@link DatabaseLogger}
 * validate and escape each field before it ever reaches a SQL string.
 *
 * @param label       user-supplied name for the saved waveform
 * @param amplitude   calibration amplitude A
 * @param frequency   calibration frequency factor B
 * @param period      calibration period T
 * @param simTime     simulation time at the moment of capture
 * @param sineValue   instantaneous primary sine value (View B)
 * @param fourierValue instantaneous synthesized value (View C)
 * @param harmonics   number of harmonics in the synthesis
 * @param capturedOn  capture date (rendered as DD-MON-YYYY in SQL)
 */
public record SimulationRecord(String label,
                               double amplitude,
                               double frequency,
                               double period,
                               double simTime,
                               double sineValue,
                               double fourierValue,
                               int harmonics,
                               LocalDate capturedOn) {
}
