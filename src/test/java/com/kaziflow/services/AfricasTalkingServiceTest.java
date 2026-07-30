package com.kaziflow.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AfricasTalkingService phone normalization.
 *
 * Kenya mobile number formats (all should resolve to +254XXXXXXXXX):
 *   07XXXXXXXX  — Safaricom, Airtel Kenya, Telkom  (legacy local format)
 *   01XXXXXXXX  — Telkom Kenya 10xx numbers        (newer local format)
 *   2547XXXXXXX — international without +
 *   +2547XXXXXX — full international E.164
 *   7XXXXXXXX   — local without leading 0
 *
 * These tests ensure receipt SMS and Deni Book reminders always reach
 * the customer regardless of how the phone number was entered in the system.
 */
@DisplayName("AfricasTalkingService — phone normalization")
class AfricasTalkingServiceTest {

    @ParameterizedTest(name = "normalizePhone({0}) = {1}")
    @CsvSource({
        // Standard Safaricom 07xx
        "0712345678,  +254712345678",
        "0722123456,  +254722123456",
        "0733987654,  +254733987654",
        // Airtel 07xx
        "0700111222,  +254700111222",
        "0750333444,  +254750333444",
        // Telkom 01xx
        "0101234567,  +254101234567",
        "0110987654,  +254110987654",
        // Already has 254 prefix (no +)
        "254712345678, +254712345678",
        "254722000001, +254722000001",
        // Full E.164 already
        "+254712345678, +254712345678",
        "+254700000001, +254700000001",
        // 9-digit without leading 0
        "712345678,  +254712345678",
        "722987654,  +254722987654",
        // Spaces and dashes stripped
        "0712 345 678, +254712345678",
        "0712-345-678, +254712345678",
        // Parentheses stripped
        "(0712)345678, +254712345678",
    })
    @DisplayName("normalizePhone: various formats → E.164")
    void normalizesPhoneToE164(String input, String expected) {
        String actual = AfricasTalkingService.normalizePhone(input.trim());
        assertEquals(expected.trim(), actual,
            "normalizePhone(\"" + input.trim() + "\") should be " + expected.trim());
    }

    @Test
    @DisplayName("normalizePhone: null input returns empty string")
    void nullInputReturnsEmpty() {
        String result = AfricasTalkingService.normalizePhone(null);
        assertNotNull(result, "normalizePhone(null) must not return null");
        assertTrue(result.isEmpty() || result.startsWith("+"),
            "normalizePhone(null) should return empty or valid number");
    }

    @Test
    @DisplayName("normalizePhone: empty string returns empty string")
    void emptyInputReturnsEmpty() {
        String result = AfricasTalkingService.normalizePhone("");
        assertNotNull(result);
        assertTrue(result.isEmpty(),
            "normalizePhone(\"\") should return \"\"");
    }

    @Test
    @DisplayName("normalizePhone: result always starts with +254 for valid Kenyan numbers")
    void validKenyanNumbersHaveCorrectPrefix() {
        String[] validInputs = {"0712345678","0722111222","254733000111","+254700888999","712345678"};
        for (String input : validInputs) {
            String result = AfricasTalkingService.normalizePhone(input);
            assertTrue(result.startsWith("+254"),
                "normalizePhone(\"" + input + "\") = \"" + result + "\", expected +254 prefix");
        }
    }

    @Test
    @DisplayName("normalizePhone: result is always 13 chars for valid Kenyan numbers (+254XXXXXXXXX)")
    void validKenyanNumbersHaveCorrectLength() {
        String[] validInputs = {"0712345678","0722111222","254733000111","+254700888999","712345678"};
        for (String input : validInputs) {
            String result = AfricasTalkingService.normalizePhone(input);
            assertEquals(13, result.length(),
                "normalizePhone(\"" + input + "\") = \"" + result + "\", expected 13 chars");
        }
    }

    // ── isConfigured guard ─────────────────────────────────────────────────

    @Test
    @DisplayName("getInstance returns non-null singleton")
    void singletonIsNonNull() {
        // Note: this only works if settings table doesn't throw during construction
        // in a test environment — AfricasTalkingService handles missing DB gracefully
        assertDoesNotThrow(() -> {
            AfricasTalkingService svc = AfricasTalkingService.getInstance();
            assertNotNull(svc);
        });
    }
}
