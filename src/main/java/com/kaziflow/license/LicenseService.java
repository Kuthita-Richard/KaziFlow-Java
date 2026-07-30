package com.kaziflow.license;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Properties;
import java.io.*;
import java.nio.file.*;

/**
 * LicenseService — offline-first license activation for KaziFlow ERP.
 *
 * DESIGN GOALS:
 *  - Works fully offline (no license server dependency) — appropriate for an
 *    offline-first ERP targeting areas with unreliable internet.
 *  - License keys are HMAC-SHA256 signed so they cannot be forged without
 *    the private signing secret (held only by Anthropic... by KaziFlow/Richard).
 *  - Hardware-bound: license is tied to a machine fingerprint derived from
 *    stable system properties, preventing simple key-sharing across machines
 *    while still tolerating minor hardware changes (RAM upgrades etc. don't
 *    change the fingerprint — only OS+arch+username are used).
 *  - Grace period: a trial license format allows N days of full access
 *    without a paid key, then prompts for activation.
 *
 * LICENSE KEY FORMAT (Base64 of a pipe-delimited signed payload):
 *   KAZI-{edition}-{expiryDate}-{machineHash8}-{signature16}
 *   e.g. KAZI-PRO-20271231-A1B2C3D4-9F8E7D6C5B4A3210
 *
 * EDITIONS:
 *   TRIAL    — 30 days from first run, all modules, watermarked receipts
 *   STANDARD — perpetual, core + up to 5 vertical modules
 *   PRO      — perpetual, all 10 vertical modules + AI assistant + SMS/M-Pesa
 *   ENTERPRISE — PRO + multi-branch + priority support
 *
 * This is intentionally a "soft" license check: it never blocks data access
 * (the user's data always remains theirs) — only nags via a banner and
 * eventually a one-time-dismissible blocking dialog after the grace period.
 * This protects against accidental hard data lock-out while still creating
 * commercial pressure to activate.
 */
public class LicenseService {

    public enum Edition { TRIAL, STANDARD, PRO, ENTERPRISE }
    public enum Status { ACTIVE, TRIAL_ACTIVE, TRIAL_EXPIRED, INVALID, NOT_ACTIVATED }

    // ⚠ In a real production build, this secret must NOT be embedded in the
    // client binary in plaintext — it should be obfuscated or, better,
    // signature verification should use a public key (RSA/EdDSA) while only
    // the license-generation tool (run by KaziFlow/Richard, never shipped)
    // holds the private signing key. HMAC symmetric-key is used here for
    // simplicity; documented as a Phase 7+ hardening item below.
    private static final String SIGNING_SECRET = "KaziFlow-ERP-2026-Signing-Key-CHANGE-IN-PRODUCTION";
    private static final int    TRIAL_DAYS = 30;
    private static final String LICENSE_FILE = "license.properties";

    private static LicenseService instance;
    private final String dataDir;
    private Properties licenseProps;

    private LicenseService(String dataDir) {
        this.dataDir = dataDir;
        loadLicenseFile();
    }

    public static synchronized LicenseService getInstance(String dataDir) {
        if (instance == null) instance = new LicenseService(dataDir);
        return instance;
    }

    // ── Activation ────────────────────────────────────────────────────────

    /**
     * Validates and activates a license key. Returns true if valid and
     * the key's machine-binding matches this machine.
     */
    public boolean activate(String licenseKey) {
        ParsedLicense parsed = parseAndVerify(licenseKey);
        if (parsed == null) return false;

        if (!parsed.machineHash.equals(machineFingerprint())) {
            System.err.println("[License] Key is bound to a different machine.");
            return false;
        }

        licenseProps.setProperty("license.key", licenseKey);
        licenseProps.setProperty("license.edition", parsed.edition.name());
        licenseProps.setProperty("license.expiry", parsed.expiry.toString());
        licenseProps.setProperty("license.activated_at", LocalDate.now().toString());
        saveLicenseFile();
        return true;
    }

    public void deactivate() {
        licenseProps.remove("license.key");
        licenseProps.remove("license.edition");
        licenseProps.remove("license.expiry");
        saveLicenseFile();
    }

    // ── Status checks ────────────────────────────────────────────────────

    /** Call once at app startup. Auto-starts the trial period on first run. */
    public Status checkStatus() {
        String key = licenseProps.getProperty("license.key");

        if (key != null) {
            ParsedLicense parsed = parseAndVerify(key);
            if (parsed == null) return Status.INVALID;
            if (!parsed.machineHash.equals(machineFingerprint())) return Status.INVALID;
            if (parsed.expiry.isBefore(LocalDate.now())) return Status.TRIAL_EXPIRED;
            return Status.ACTIVE;
        }

        // No key on file — check / start trial
        String trialStart = licenseProps.getProperty("trial.start_date");
        if (trialStart == null) {
            trialStart = LocalDate.now().toString();
            licenseProps.setProperty("trial.start_date", trialStart);
            saveLicenseFile();
        }

        try {
            LocalDate started = LocalDate.parse(trialStart);
            LocalDate trialEnd = started.plusDays(TRIAL_DAYS);
            if (LocalDate.now().isBefore(trialEnd) || LocalDate.now().isEqual(trialEnd)) {
                return Status.TRIAL_ACTIVE;
            }
            return Status.TRIAL_EXPIRED;
        } catch (DateTimeParseException e) {
            return Status.NOT_ACTIVATED;
        }
    }

    public int trialDaysRemaining() {
        String trialStart = licenseProps.getProperty("trial.start_date");
        if (trialStart == null) return TRIAL_DAYS;
        try {
            LocalDate started = LocalDate.parse(trialStart);
            long elapsed = java.time.temporal.ChronoUnit.DAYS.between(started, LocalDate.now());
            return (int) Math.max(0, TRIAL_DAYS - elapsed);
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    public Edition getEdition() {
        String e = licenseProps.getProperty("license.edition");
        if (e == null) return Edition.TRIAL;
        try { return Edition.valueOf(e); } catch (IllegalArgumentException ex) { return Edition.TRIAL; }
    }

    public String getMaskedKey() {
        String key = licenseProps.getProperty("license.key");
        if (key == null || key.length() < 8) return "Not activated";
        return key.substring(0, 9) + "••••••••" + key.substring(key.length() - 4);
    }

    /** The unique fingerprint this machine's license key must be bound to. */
    public String machineFingerprint() {
        try {
            String raw = System.getProperty("os.name", "")
                       + "|" + System.getProperty("os.arch", "")
                       + "|" + System.getProperty("user.name", "");
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().withoutPadding().encodeToString(hash).substring(0, 8).toUpperCase();
        } catch (Exception e) {
            return "UNKNOWN0";
        }
    }

    // ── License key generation (for the standalone license-tool, not shipped UI) ──

    /**
     * Generates a signed license key. This method is intended to be called
     * from a separate internal tool (e.g. a small CLI script run by
     * Richard/KaziFlow when issuing a license to a customer) — NOT exposed
     * in the shipped application UI.
     */
    public static String generateLicenseKey(Edition edition, LocalDate expiry, String machineHash) {
        String payload = "KAZI-" + edition.name() + "-" + expiry.toString().replace("-", "") + "-" + machineHash;
        String signature = hmacSign(payload);
        return payload + "-" + signature.substring(0, 16).toUpperCase();
    }

    // ── Internal: parse + verify ────────────────────────────────────────

    private record ParsedLicense(Edition edition, LocalDate expiry, String machineHash) {}

    private ParsedLicense parseAndVerify(String key) {
        if (key == null) return null;
        String[] parts = key.split("-");
        if (parts.length != 5 || !"KAZI".equals(parts[0])) return null;

        try {
            Edition edition = Edition.valueOf(parts[1]);
            String dateStr = parts[2];
            LocalDate expiry = LocalDate.parse(
                dateStr.substring(0,4) + "-" + dateStr.substring(4,6) + "-" + dateStr.substring(6,8));
            String machineHash = parts[3];
            String signature = parts[4];

            String payload = parts[0] + "-" + parts[1] + "-" + parts[2] + "-" + parts[3];
            String expectedSig = hmacSign(payload).substring(0, 16).toUpperCase();

            if (!expectedSig.equalsIgnoreCase(signature)) {
                System.err.println("[License] Signature mismatch — key may be tampered or forged.");
                return null;
            }
            return new ParsedLicense(edition, expiry, machineHash);
        } catch (Exception e) {
            return null;
        }
    }

    private static String hmacSign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : result) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC signing failed", e);
        }
    }

    // ── Persistence ──────────────────────────────────────────────────────

    private void loadLicenseFile() {
        licenseProps = new Properties();
        Path path = Paths.get(dataDir, LICENSE_FILE);
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                licenseProps.load(in);
            } catch (IOException e) {
                System.err.println("[License] Could not read license file: " + e.getMessage());
            }
        }
    }

    private void saveLicenseFile() {
        try {
            Files.createDirectories(Paths.get(dataDir));
            Path path = Paths.get(dataDir, LICENSE_FILE);
            try (OutputStream out = Files.newOutputStream(path)) {
                licenseProps.store(out, "KaziFlow ERP License — do not edit manually");
            }
        } catch (IOException e) {
            System.err.println("[License] Could not save license file: " + e.getMessage());
        }
    }
}
