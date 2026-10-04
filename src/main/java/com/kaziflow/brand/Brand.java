package com.kaziflow.brand;

import com.kaziflow.services.UpdateService;
import com.kaziflow.utils.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.Base64;
import java.util.Properties;

/**
 * Brand — every name the product shows about itself, read from {@code /brand/brand.properties}.
 *
 * <p>Hierarchy: <b>Tihems</b> (the group) → <b>Tihems Software</b> (its software department, owner of this
 * product) → the <b>product</b>. All share one logo/icon; only the names differ by level. Code never hardcodes
 * any of them: it asks {@code Brand}. That makes a rename, a white-label install or a change of company name a
 * properties edit — and {@code BrandGuardTest} fails the build if a hardcoded product name sneaks back in.
 *
 * <p>An installation can override any key in {@code <data folder>/brand-override.properties}.
 *
 * <p>JavaFX-free on purpose, so services (receipts, email, exports, the AI assistant's prompt) can use it.
 * Image handling that needs JavaFX lives in {@link BrandAssets}.
 */
public final class Brand {

    private static final String RESOURCE = "/brand/brand.properties";
    private static final String OVERRIDE_FILE = "brand-override.properties";
    private static volatile Properties props;

    private Brand() {}

    // ── names ─────────────────────────────────────────────────────────────

    /** The group, e.g. "Tihems". */
    public static String groupName()       { return get("group.name"); }
    /** The software department that owns the product, e.g. "Tihems Software". */
    public static String departmentName()  { return get("department.name"); }
    /** Short product name, e.g. "KaziFlow". */
    public static String productName()     { return get("product.name"); }
    /** Full product name, e.g. "KaziFlow ERP". */
    public static String productFullName() { return get("product.fullName"); }
    public static String tagline()         { return get("product.tagline"); }
    public static String supportEmail()    { return get("support.email"); }
    public static String website()         { return get("website"); }

    // ── composed lines (so wording is consistent everywhere) ──────────────

    /** "© 2026 Tihems Software. All rights reserved." */
    public static String copyright() { return "© " + Year.now() + " " + get("legal.holder") + ". All rights reserved."; }
    /** "Tihems Software · a Tihems company" */
    public static String lineage()   { return departmentName() + " · a " + groupName() + " company"; }
    /** "Powered by KaziFlow ERP" */
    public static String poweredBy() { return "Powered by " + productFullName(); }
    /** "KaziFlow ERP by Tihems Software" */
    public static String productByDepartment() { return productFullName() + " by " + departmentName(); }

    /** The running application version (single source: the update checker's constant). */
    public static String version()   { return UpdateService.CURRENT_VERSION; }

    // ── images as data URIs, for self-contained HTML pages (reports, receipts) ──

    /** {@code data:image/png;base64,…} of a file in /brand (e.g. "icon-32.png", "logo-light.png"), or "" if missing. */
    public static String dataUri(String assetFile) {
        try (InputStream in = Brand.class.getResourceAsStream("/brand/" + assetFile)) {
            if (in == null) return "";
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (IOException e) {
            return "";
        }
    }

    // ── loading ───────────────────────────────────────────────────────────

    /** Re-reads brand.properties and any override file (used by tests and after an override is edited). */
    public static synchronized void reload() { props = load(); }

    private static String get(String key) {
        Properties p = props;
        if (p == null) { synchronized (Brand.class) { if (props == null) props = load(); p = props; } }
        String v = p.getProperty(key);
        return v == null ? "" : v.trim();
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = Brand.class.getResourceAsStream(RESOURCE)) {
            if (in != null) try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) { p.load(r); }
        } catch (IOException e) {
            System.err.println("[Brand] Could not read " + RESOURCE + ": " + e.getMessage());
        }
        Path override = Path.of(AppPaths.dataDir(), OVERRIDE_FILE);
        if (Files.isRegularFile(override)) {
            try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) { p.load(r); }
            catch (IOException e) { System.err.println("[Brand] Could not read " + override + ": " + e.getMessage()); }
        }
        return p;
    }
}
