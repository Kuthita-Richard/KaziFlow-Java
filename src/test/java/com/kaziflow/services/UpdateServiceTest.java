package com.kaziflow.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UpdateService.isNewerVersion().
 * The version comparison logic is pure Java (no network, no JavaFX) — ideal
 * for fast unit testing.
 */
@DisplayName("UpdateService — version comparison")
class UpdateServiceTest {

    @ParameterizedTest(name = "isNewerVersion({0}, {1}) = {2}")
    @CsvSource({
        // candidate newer than current
        "1.0.1,   1.0.0,  true",
        "1.1.0,   1.0.9,  true",
        "2.0.0,   1.9.9,  true",
        "v2.1.0,  2.0.0,  true",
        "v1.0.1,  v1.0.0, true",
        "10.0.0,  9.9.9,  true",
        "1.0.10,  1.0.9,  true",

        // same version (not newer)
        "1.0.0,   1.0.0,  false",
        "v1.0.0,  1.0.0,  false",
        "1.0.0,   v1.0.0, false",
        "2.0.0,   2.0.0,  false",

        // candidate older than current
        "1.0.0,   1.0.1,  false",
        "1.9.9,   2.0.0,  false",
        "0.9.9,   1.0.0,  false",

        // missing patch component
        "1.1,     1.0.0,  true",
        "1.0,     1.0.0,  false",
        "2.0,     1.9.9,  true",
    })
    @DisplayName("isNewerVersion: parameterized")
    void isNewerVersion(String candidate, String current, boolean expected) {
        assertEquals(expected,
            UpdateService.isNewerVersion(candidate, current),
            () -> String.format("isNewerVersion(\"%s\", \"%s\") should be %s",
                candidate, current, expected));
    }

    @ParameterizedTest(name = "null safety: isNewerVersion({0}, {1}) = false")
    @CsvSource({
        ", 1.0.0",
        "1.0.0, ",
        ", ",
    })
    @DisplayName("isNewerVersion: null inputs return false")
    void nullSafety(String candidate, String current) {
        assertFalse(UpdateService.isNewerVersion(candidate, current));
    }
}
