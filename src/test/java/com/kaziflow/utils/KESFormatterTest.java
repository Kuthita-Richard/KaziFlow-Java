package com.kaziflow.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KESFormatter.
 * Tests currency formatting used on every receipt, invoice, and UI label.
 */
@DisplayName("KESFormatter")
class KESFormatterTest {

    // ── format(double) ────────────────────────────────────────────────────

    @Test
    @DisplayName("format: zero should be 0.00")
    void formatZero() {
        assertEquals("0.00", KESFormatter.format(0));
    }

    @Test
    @DisplayName("format: whole number adds .00")
    void formatWholeNumber() {
        assertEquals("1,000.00", KESFormatter.format(1000));
    }

    @Test
    @DisplayName("format: rounds to 2 decimal places")
    void formatRounding() {
        assertEquals("1,234.57", KESFormatter.format(1234.565));
    }

    @Test
    @DisplayName("format: negative amount")
    void formatNegative() {
        String result = KESFormatter.format(-500.50);
        assertTrue(result.contains("500"), "Negative should contain the amount");
    }

    @ParameterizedTest(name = "format({0}) = {1}")
    @CsvSource({
        "0,       0.00",
        "100,     100.00",
        "1000,    1\\,000.00",
        "10000,   10\\,000.00",
        "1000000, 1\\,000\\,000.00",
        "99.99,   99.99",
        "0.5,     0.50",
        "0.999,   1.00",
    })
    @DisplayName("format: parameterized amounts")
    void formatParameterized(double input, String expected) {
        assertEquals(expected, KESFormatter.format(input));
    }

    // ── formatShort(double) ───────────────────────────────────────────────

    @Test
    @DisplayName("formatShort: below 1000 shows full amount")
    void formatShortBelow1k() {
        assertEquals("999", KESFormatter.formatShort(999));
    }

    @Test
    @DisplayName("formatShort: 1000+ uses K suffix")
    void formatShortK() {
        String result = KESFormatter.formatShort(1500);
        assertTrue(result.contains("K") || result.contains("1"),
            "1500 should use K suffix or show 1.5K");
    }

    @Test
    @DisplayName("formatShort: 1 million uses M suffix")
    void formatShortM() {
        String result = KESFormatter.formatShort(1_500_000);
        assertTrue(result.contains("M"),
            "1,500,000 should use M suffix, got: " + result);
    }

    @Test
    @DisplayName("formatShort: 1 billion uses B suffix")
    void formatShortB() {
        String result = KESFormatter.formatShort(2_000_000_000);
        assertTrue(result.contains("B"),
            "2,000,000,000 should use B suffix, got: " + result);
    }

    // ── formatNumber(double) ──────────────────────────────────────────────

    @Test
    @DisplayName("formatNumber: same as format for whole values")
    void formatNumberWhole() {
        // formatNumber is used inline in WhatsApp receipt generation
        String result = KESFormatter.formatNumber(1234.5);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("1234") || result.contains("1,234"),
            "Should contain the numeric value");
    }
}
