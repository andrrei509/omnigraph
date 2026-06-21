package com.omnigraph.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class DatabaseLoggerTest {

    private final DatabaseLogger logger = new DatabaseLogger(false);

    @Test
    void formatsDateAsDdMonYyyyWithZeroPaddedDay() {
        assertEquals("'07-JUN-2026'", logger.toOracleDateLiteral(LocalDate.of(2026, 6, 7)));
        assertEquals("'25-DEC-1999'", logger.toOracleDateLiteral(LocalDate.of(1999, 12, 25)));
    }

    @Test
    void escapesSingleQuotesToNeutralizeInjection() {
        SimulationRecord record = new SimulationRecord(
                "rob'); DROP TABLE x;--",
                12.0, -2.0, 5e-4, 0.001, 7.5, 9.1, 8, LocalDate.of(2026, 6, 21));
        String sql = logger.buildInsert(record);
        assertTrue(sql.contains("'rob''); DROP TABLE x;--'"),
                "single quote must be doubled to stay inside the literal");
    }

    @Test
    void generatedSqlContainsNoComments() {
        SimulationRecord record = new SimulationRecord(
                "clean", 12.0, -2.0, 5e-4, 0.001, 1.0, 2.0, 4, LocalDate.of(2026, 1, 1));
        String schema = logger.generateSchema();
        String insert = logger.buildInsert(record);
        assertFalse(schema.contains("/*") || schema.contains("--"), "schema must contain no comments");
        assertFalse(insert.contains("/*"), "insert must contain no block comments");
    }

    @Test
    void rejectsNonFiniteNumbers() {
        SimulationRecord record = new SimulationRecord(
                "bad", Double.NaN, -2.0, 5e-4, 0.0, 0.0, 0.0, 4, LocalDate.of(2026, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> logger.buildInsert(record));
    }
}
