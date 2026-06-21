package com.omnigraph.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;

/**
 * Persistence module that emits raw SQL payloads recording simulation states.
 *
 * <p>House rules honoured throughout this class:
 * <ul>
 *   <li>SQL strings are assembled with plain {@code +} concatenation — no
 *       {@code String.format} and no formatting frameworks.</li>
 *   <li>The generated SQL never contains SQL comments.</li>
 *   <li>Every date is rendered strictly as {@code DD-MON-YYYY} via explicit
 *       concatenation (see {@link #toOracleDateLiteral}).</li>
 * </ul>
 *
 * <p>Important: concatenating values into SQL is, by default, a SQL-injection
 * vector. The literal house rules above are met <em>and</em> kept safe by
 * routing every value through {@link #quote} (string literals are escaped) and
 * {@link #numeric} (values are validated as finite numbers). Callers that need
 * a parameterised statement instead can pass the output's shape to a
 * {@code PreparedStatement}; this class is the audit-log / payload generator.
 */
public final class DatabaseLogger {

    private static final String TABLE = "omnigraph_waveform_log";

    private static final String[] MONTHS = {
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    };

    private final boolean echoToConsole;

    public DatabaseLogger() {
        this(true);
    }

    public DatabaseLogger(boolean echoToConsole) {
        this.echoToConsole = echoToConsole;
    }

    /**
     * Returns the schema DDL. The capture date column stores the canonical
     * {@code DD-MON-YYYY} text representation.
     */
    public String generateSchema() {
        return "CREATE TABLE " + TABLE + " ("
                + "id INTEGER PRIMARY KEY, "
                + "label VARCHAR(128) NOT NULL, "
                + "amplitude DOUBLE NOT NULL, "
                + "frequency DOUBLE NOT NULL, "
                + "period DOUBLE NOT NULL, "
                + "sim_time DOUBLE NOT NULL, "
                + "sine_value DOUBLE NOT NULL, "
                + "fourier_value DOUBLE NOT NULL, "
                + "harmonics INTEGER NOT NULL, "
                + "captured_on VARCHAR(11) NOT NULL"
                + ");";
    }

    /** Builds a single INSERT payload for the given saved state. */
    public String buildInsert(SimulationRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        String sql = "INSERT INTO " + TABLE + " ("
                + "label, amplitude, frequency, period, sim_time, "
                + "sine_value, fourier_value, harmonics, captured_on"
                + ") VALUES ("
                + quote(record.label()) + ", "
                + numeric(record.amplitude()) + ", "
                + numeric(record.frequency()) + ", "
                + numeric(record.period()) + ", "
                + numeric(record.simTime()) + ", "
                + numeric(record.sineValue()) + ", "
                + numeric(record.fourierValue()) + ", "
                + record.harmonics() + ", "
                + toOracleDateLiteral(record.capturedOn())
                + ");";

        if (echoToConsole) {
            System.out.println("[DatabaseLogger] persisting waveform '" + record.label()
                    + "' captured " + toOracleDateLiteral(record.capturedOn())
                    + " :: " + sql);
        }
        return sql;
    }

    /**
     * Appends an INSERT for the given record to a {@code .sql} script file,
     * creating the file with the schema DDL as a header the first time. Returns
     * the line that was written. All values flow through the same escaping path
     * as {@link #buildInsert}, so the on-disk script is injection-safe too.
     */
    public String appendToScript(Path scriptPath, SimulationRecord record) {
        String insert = buildInsert(record);
        try {
            boolean fresh = !Files.exists(scriptPath);
            if (scriptPath.getParent() != null) {
                Files.createDirectories(scriptPath.getParent());
            }
            StringBuilder payload = new StringBuilder();
            if (fresh) {
                payload.append(generateSchema()).append(System.lineSeparator());
            }
            payload.append(insert).append(System.lineSeparator());
            Files.writeString(scriptPath, payload.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append to SQL script: " + scriptPath, e);
        }
        return insert;
    }

    /**
     * Renders a date as a quoted {@code 'DD-MON-YYYY'} literal using explicit
     * concatenation and a fixed uppercase month table (locale-independent).
     */
    public String toOracleDateLiteral(LocalDate date) {
        if (date == null) {
            return "NULL";
        }
        String day = (date.getDayOfMonth() < 10 ? "0" : "") + date.getDayOfMonth();
        String month = MONTHS[date.getMonthValue() - 1];
        String year = Integer.toString(date.getYear());
        return "'" + day + "-" + month + "-" + year + "'";
    }

    /** Escapes and quotes a string literal, or yields {@code NULL}. */
    private static String quote(String value) {
        if (value == null) {
            return "NULL";
        }
        return "'" + value.replace("'", "''") + "'";
    }

    /** Validates a value as a finite number before it enters the SQL string. */
    private static String numeric(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("non-finite numeric value rejected: " + value);
        }
        return Double.toString(value);
    }
}
