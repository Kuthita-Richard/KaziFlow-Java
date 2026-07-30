package com.kaziflow.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.prefs.Preferences;

/**
 * UpdateService — checks for KaziFlow ERP updates from GitHub Releases API.
 *
 * DESIGN:
 *  - Checks https://api.github.com/repos/richard-kuthita/kaziflow-erp/releases/latest
 *    on startup (background thread, non-blocking).
 *  - Compares the published tag_name (e.g. "v1.2.0") against the current
 *    CURRENT_VERSION constant using semantic versioning comparison.
 *  - If a newer version is available, calls the provided onUpdateAvailable
 *    callback with (latestVersion, downloadUrl) on the JavaFX thread.
 *  - Respects a "skip this version" preference so users can dismiss a
 *    notification once and not see it again until a newer release.
 *  - Rate-limits checks to once per 24 hours using java.util.prefs.Preferences.
 *  - Offline-safe: any IOException is silently swallowed (offline-first ERP
 *    must not break on startup if there is no internet).
 */
public class UpdateService {

    public static final String CURRENT_VERSION = "2.0.0";  // Phase 5+6 release

    private static final String RELEASES_URL =
        "https://api.github.com/repos/richard-kuthita/kaziflow-erp/releases/latest";
    private static final long CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000; // 24 hours
    private static final String PREF_LAST_CHECK   = "kaziflow.update.last_check";
    private static final String PREF_SKIP_VERSION = "kaziflow.update.skip_version";

    private static UpdateService instance;
    private final OkHttpClient http;
    private final Gson gson = new Gson();
    private final Preferences prefs;

    private UpdateService() {
        http = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build();
        prefs = Preferences.userNodeForPackage(UpdateService.class);
    }

    public static synchronized UpdateService getInstance() {
        if (instance == null) instance = new UpdateService();
        return instance;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Checks for updates in the background. Safe to call on the JavaFX thread —
     * the network call is made on a daemon thread.
     *
     * @param onUpdateAvailable called on the JavaFX Application Thread if a new
     *                          version is found; receives (latestVersion, downloadUrl).
     *                          Will NOT be called if the user previously skipped
     *                          this version, or if checked within the last 24 hours.
     */
    public void checkForUpdates(BiConsumer<String, String> onUpdateAvailable) {
        // Skip if checked recently
        long lastCheck = prefs.getLong(PREF_LAST_CHECK, 0);
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return;

        Thread daemon = new Thread(() -> {
            try {
                UpdateInfo info = fetchLatestRelease();
                if (info == null) return;

                prefs.putLong(PREF_LAST_CHECK, System.currentTimeMillis());

                if (!isNewerVersion(info.tagName, CURRENT_VERSION)) return;

                // User skipped this version?
                String skipped = prefs.get(PREF_SKIP_VERSION, "");
                if (skipped.equals(info.tagName)) return;

                // Notify on JavaFX thread
                javafx.application.Platform.runLater(() ->
                    onUpdateAvailable.accept(info.tagName, info.downloadUrl));

            } catch (IOException e) {
                // Offline or network error — silently ignore (offline-first ERP)
                System.out.println("[Update] Check failed (offline?): " + e.getMessage());
            }
        });
        daemon.setDaemon(true);
        daemon.setName("kaziflow-update-check");
        daemon.start();
    }

    /**
     * Tells UpdateService to skip this version — won't notify again until
     * a version newer than versionToSkip is released.
     */
    public void skipVersion(String versionToSkip) {
        prefs.put(PREF_SKIP_VERSION, versionToSkip);
    }

    /** Clears the "skip" preference so the update prompt reappears. */
    public void resetSkip() {
        prefs.remove(PREF_SKIP_VERSION);
    }

    /** Resets the last-check timestamp, forcing a check on next call. */
    public void resetLastCheck() {
        prefs.remove(PREF_LAST_CHECK);
    }

    // ── Version comparison ────────────────────────────────────────────────

    /**
     * Returns true if `candidate` is strictly newer than `current`.
     * Strips leading 'v' prefix, splits on '.', compares major.minor.patch.
     * Non-numeric suffixes (e.g. "1.2.0-beta") are compared lexicographically
     * in the patch component.
     */
    public static boolean isNewerVersion(String candidate, String current) {
        if (candidate == null || current == null) return false;
        String c = candidate.startsWith("v") ? candidate.substring(1) : candidate;
        String r = current.startsWith("v") ? current.substring(1) : current;
        String[] cv = c.split("\\.", 3);
        String[] rv = r.split("\\.", 3);
        try {
            for (int i = 0; i < Math.max(cv.length, rv.length); i++) {
                int cp = i < cv.length ? Integer.parseInt(cv[i]) : 0;
                int rp = i < rv.length ? Integer.parseInt(rv[i]) : 0;
                if (cp != rp) return cp > rp;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to string comparison for pre-release suffixes
            return c.compareTo(r) > 0;
        }
        return false; // same version
    }

    // ── GitHub Releases API ───────────────────────────────────────────────

    private UpdateInfo fetchLatestRelease() throws IOException {
        Request req = new Request.Builder()
            .url(RELEASES_URL)
            .header("User-Agent", "KaziFlowERP/" + CURRENT_VERSION)
            .header("Accept", "application/vnd.github+json")
            .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            String body = resp.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);
            if (json == null || !json.has("tag_name")) return null;

            String tag = json.get("tag_name").getAsString();
            String url = json.has("html_url")
                ? json.get("html_url").getAsString()
                : "https://github.com/richard-kuthita/kaziflow-erp/releases";

            return new UpdateInfo(tag, url);
        }
    }

    // ── Internal model ────────────────────────────────────────────────────

    private static class UpdateInfo {
        final String tagName;
        final String downloadUrl;
        UpdateInfo(String tagName, String downloadUrl) {
            this.tagName = tagName;
            this.downloadUrl = downloadUrl;
        }
    }
}
