package com.kaziflow.utils;

import java.util.*;
import java.util.prefs.Preferences;

/**
 * I18n — Internationalisation singleton for KaziFlow ERP.
 *
 * USAGE:
 *   // In any view:
 *   import static com.kaziflow.utils.I18n.t;
 *
 *   Label title = new Label(t("nav.dashboard"));
 *   button.setText(t("btn.save"));
 *
 * SUPPORTED LOCALES (Phase 6D):
 *   "en"  — English (default)
 *   "sw"  — Kiswahili
 *
 * ADDING A NEW LOCALE:
 *   1. Create src/main/resources/i18n/messages_XX.properties
 *   2. Add "XX" to SUPPORTED_LOCALES
 *   3. Add the locale's display name to LOCALE_NAMES
 *
 * SWITCHING LANGUAGE:
 *   I18n.getInstance().setLocale("sw");
 *   // Persisted in OS Preferences — survives restart.
 *   // Note: existing cached views rebuild on next navigation (same
 *   //  limitation as dark mode — see Phase 7 full-reactivity candidate).
 *
 * MISSING KEY BEHAVIOUR:
 *   If a key is not found in the current locale bundle, the English bundle
 *   is tried as fallback. If still not found, the key itself is returned
 *   wrapped in square brackets (e.g. "[nav.missing_key]") so missing
 *   translations are visible in the UI during development.
 */
public class I18n {

    public static final List<String> SUPPORTED_LOCALES = List.of("en", "sw");
    public static final Map<String, String> LOCALE_NAMES = Map.of(
        "en", "English",
        "sw", "Kiswahili"
    );

    private static final String BUNDLE_BASE   = "i18n/messages";
    private static final String PREF_KEY      = "kaziflow.locale";
    private static final String DEFAULT_LOCALE = "en";

    private static I18n instance;
    private ResourceBundle bundle;
    private ResourceBundle fallback;    // English fallback
    private String currentLocale;
    private final Preferences prefs;

    private I18n() {
        prefs = Preferences.userNodeForPackage(I18n.class);
        String saved = prefs.get(PREF_KEY, DEFAULT_LOCALE);
        // Validate saved locale
        if (!SUPPORTED_LOCALES.contains(saved)) saved = DEFAULT_LOCALE;
        load(saved);
    }

    public static synchronized I18n getInstance() {
        if (instance == null) instance = new I18n();
        return instance;
    }

    // ── Static convenience method ─────────────────────────────────────────

    /**
     * Translate a key to the current locale's string.
     * Import statically for the cleanest call sites:
     *   import static com.kaziflow.utils.I18n.t;
     *   Label lbl = new Label(t("nav.dashboard"));
     */
    public static String t(String key) {
        return getInstance().get(key);
    }

    /**
     * Translate with format arguments (String.format style):
     *   t("msg.items_count", 5)  →  "5 items"
     */
    public static String t(String key, Object... args) {
        return String.format(getInstance().get(key), args);
    }

    // ── Instance methods ──────────────────────────────────────────────────

    public String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e1) {
            // Try English fallback
            try {
                return fallback.getString(key);
            } catch (MissingResourceException e2) {
                // Return a visible placeholder so missing translations are caught in dev
                return "[" + key + "]";
            }
        }
    }

    public String getLocale() { return currentLocale; }

    public void setLocale(String locale) {
        if (!SUPPORTED_LOCALES.contains(locale)) return;
        load(locale);
        prefs.put(PREF_KEY, locale);
    }

    public boolean isSwahili() { return "sw".equals(currentLocale); }

    /** Returns all available locales with their display names, for a settings picker. */
    public Map<String, String> getAvailableLocales() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String code : SUPPORTED_LOCALES) {
            map.put(code, LOCALE_NAMES.getOrDefault(code, code));
        }
        return map;
    }

    // ── Private ───────────────────────────────────────────────────────────

    private void load(String locale) {
        currentLocale = locale;
        try {
            bundle = ResourceBundle.getBundle(BUNDLE_BASE,
                new Locale.Builder().setLanguage(locale).build(),
                I18n.class.getClassLoader());
        } catch (MissingResourceException e) {
            System.err.println("[I18n] Bundle not found for locale: " + locale + ", falling back to en");
            bundle = loadEnglish();
        }
        fallback = loadEnglish();
    }

    private ResourceBundle loadEnglish() {
        try {
            return ResourceBundle.getBundle(BUNDLE_BASE,
                new Locale.Builder().setLanguage("en").build(),
                I18n.class.getClassLoader());
        } catch (MissingResourceException e) {
            System.err.println("[I18n] CRITICAL: English bundle not found!");
            return new ListResourceBundle() {
                @Override protected Object[][] getContents() { return new Object[0][]; }
            };
        }
    }
}
