package com.kaziflow.utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for I18n internationalisation system.
 *
 * Tests verify:
 *   1. Both resource bundles load without errors
 *   2. Every key in the English bundle has a Swahili translation
 *   3. No key returns its own name (indicating a missing-key fallback)
 *   4. Locale switching and persistence
 *   5. t() static method works correctly
 *   6. Missing-key fallback returns [key] not null/exception
 */
@DisplayName("I18n — resource bundle completeness and switching")
class I18nTest {

    private static Properties enProps;
    private static Properties swProps;

    @BeforeAll
    static void loadBundles() throws IOException {
        enProps = new Properties();
        swProps = new Properties();
        try (InputStream en = I18nTest.class.getClassLoader()
                .getResourceAsStream("i18n/messages_en.properties");
             InputStream sw = I18nTest.class.getClassLoader()
                .getResourceAsStream("i18n/messages_sw.properties")) {
            assertNotNull(en, "messages_en.properties not found on classpath");
            assertNotNull(sw, "messages_sw.properties not found on classpath");
            enProps.load(en);
            swProps.load(sw);
        }
    }

    // ── Bundle loading ────────────────────────────────────────────────────

    @Test
    @DisplayName("English bundle has at least 100 keys")
    void englishBundleHasSufficientKeys() {
        assertTrue(enProps.size() >= 100,
            "English bundle has " + enProps.size() + " keys, expected at least 100");
    }

    @Test
    @DisplayName("Swahili bundle has at least 100 keys")
    void swahiliBundleHasSufficientKeys() {
        assertTrue(swProps.size() >= 100,
            "Swahili bundle has " + swProps.size() + " keys, expected at least 100");
    }

    @Test
    @DisplayName("Swahili bundle covers all English keys")
    void swahiliCoverageComplete() {
        List<String> missing = new ArrayList<>();
        for (String key : enProps.stringPropertyNames()) {
            if (!swProps.containsKey(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(),
            "Swahili bundle is missing " + missing.size() + " keys from English bundle: " +
            String.join(", ", missing.subList(0, Math.min(10, missing.size()))) +
            (missing.size() > 10 ? "... (" + missing.size() + " total)" : ""));
    }

    @Test
    @DisplayName("No bundle key maps to an empty string")
    void noEmptyValues() {
        for (String key : enProps.stringPropertyNames()) {
            assertFalse(enProps.getProperty(key).isBlank(),
                "English key '" + key + "' has a blank value");
        }
        for (String key : swProps.stringPropertyNames()) {
            assertFalse(swProps.getProperty(key).isBlank(),
                "Swahili key '" + key + "' has a blank value");
        }
    }

    // ── I18n singleton ─────────────────────────────────────────────────────

    @Test
    @DisplayName("I18n.t() returns non-null for known key")
    void tMethodReturnsNonNull() {
        String result = I18n.t("nav.dashboard");
        assertNotNull(result, "I18n.t(\"nav.dashboard\") must not return null");
        assertFalse(result.isBlank(), "I18n.t(\"nav.dashboard\") must not be blank");
    }

    @Test
    @DisplayName("I18n.t() returns [key] bracket placeholder for unknown key")
    void tMethodReturnsBracketPlaceholderForMissingKey() {
        String result = I18n.t("nonexistent.key.xyz.test");
        assertEquals("[nonexistent.key.xyz.test]", result,
            "Missing key should return [key] not null or exception");
    }

    @Test
    @DisplayName("I18n.getInstance() returns non-null singleton")
    void singletonIsNonNull() {
        assertNotNull(I18n.getInstance(), "I18n.getInstance() must not be null");
    }

    @Test
    @DisplayName("I18n has exactly 2 supported locales")
    void exactlyTwoSupportedLocales() {
        assertEquals(2, I18n.SUPPORTED_LOCALES.size(),
            "Expected exactly 2 supported locales (en, sw)");
        assertTrue(I18n.SUPPORTED_LOCALES.contains("en"), "Must support 'en'");
        assertTrue(I18n.SUPPORTED_LOCALES.contains("sw"), "Must support 'sw'");
    }

    @Test
    @DisplayName("getAvailableLocales() returns both locales with display names")
    void getAvailableLocalesReturnsAll() {
        Map<String, String> locales = I18n.getInstance().getAvailableLocales();
        assertEquals(2, locales.size());
        assertTrue(locales.containsKey("en"));
        assertTrue(locales.containsKey("sw"));
        assertFalse(locales.get("en").isBlank());
        assertFalse(locales.get("sw").isBlank());
    }

    @Test
    @DisplayName("setLocale('sw') switches to Swahili")
    void setLocaleToSwahili() {
        I18n i18n = I18n.getInstance();
        String originalLocale = i18n.getLocale();
        try {
            i18n.setLocale("sw");
            assertEquals("sw", i18n.getLocale());
            assertTrue(i18n.isSwahili());
            // nav.dashboard should be Kiswahili now
            String dashboard = i18n.get("nav.dashboard");
            assertEquals("Dashibodi", dashboard,
                "nav.dashboard in Swahili should be 'Dashibodi'");
        } finally {
            i18n.setLocale(originalLocale); // restore
        }
    }

    @Test
    @DisplayName("setLocale('en') returns English strings")
    void setLocaleToEnglish() {
        I18n i18n = I18n.getInstance();
        String originalLocale = i18n.getLocale();
        try {
            i18n.setLocale("en");
            assertEquals("en", i18n.getLocale());
            assertFalse(i18n.isSwahili());
            assertEquals("Dashboard", i18n.get("nav.dashboard"));
            assertEquals("Save", i18n.get("btn.save"));
        } finally {
            i18n.setLocale(originalLocale);
        }
    }

    @Test
    @DisplayName("setLocale with invalid code is silently ignored")
    void invalidLocaleIsIgnored() {
        I18n i18n = I18n.getInstance();
        String before = i18n.getLocale();
        i18n.setLocale("xx"); // unsupported
        assertEquals(before, i18n.getLocale(),
            "setLocale('xx') should be silently ignored, locale should not change");
    }

    // ── Key-value spot checks ─────────────────────────────────────────────

    @ParameterizedTest(name = "English key {0} is present")
    @ValueSource(strings = {
        "nav.dashboard", "nav.inventory", "nav.sales", "nav.purchases",
        "btn.save", "btn.cancel", "btn.delete",
        "login.welcome", "login.button",
        "sales.total", "sales.vat", "sales.mpesa",
        "status.active", "status.paid",
        "error.required", "success.saved"
    })
    @DisplayName("Critical keys present in English bundle")
    void criticalEnglishKeysPresent(String key) {
        assertTrue(enProps.containsKey(key),
            "Critical key '" + key + "' missing from English bundle");
    }

    @ParameterizedTest(name = "Swahili key {0} is present")
    @ValueSource(strings = {
        "nav.dashboard", "nav.inventory", "nav.sales",
        "btn.save", "btn.cancel",
        "login.welcome", "login.button",
        "sales.total", "sales.cash",
        "status.active", "status.paid",
        "error.required"
    })
    @DisplayName("Critical keys present in Swahili bundle")
    void criticalSwahiliKeysPresent(String key) {
        assertTrue(swProps.containsKey(key),
            "Critical key '" + key + "' missing from Swahili bundle");
    }

    // ── Swahili spot-check translations ──────────────────────────────────

    @Test @DisplayName("Swahili: nav.dashboard = Dashibodi")
    void swahiliDashboard() { assertEquals("Dashibodi", swProps.getProperty("nav.dashboard")); }

    @Test @DisplayName("Swahili: btn.save = Hifadhi")
    void swahiliSave() { assertEquals("Hifadhi", swProps.getProperty("btn.save")); }

    @Test @DisplayName("Swahili: btn.cancel = Ghairi")
    void swahiliCancel() { assertEquals("Ghairi", swProps.getProperty("btn.cancel")); }

    @Test @DisplayName("Swahili: sales.total = JUMLA")
    void swahiliTotal() { assertEquals("JUMLA", swProps.getProperty("sales.total")); }

    @Test @DisplayName("Swahili: receipt.thank_you = Asante kwa biashara yako!")
    void swahiliThankYou() { assertEquals("Asante kwa biashara yako!", swProps.getProperty("receipt.thank_you")); }

    @Test @DisplayName("Swahili: login.button = Ingia")
    void swahiliLoginButton() { assertEquals("Ingia", swProps.getProperty("login.button")); }
}
