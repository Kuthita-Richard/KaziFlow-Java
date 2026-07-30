package com.kaziflow.license;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LicenseService — HMAC-signed offline license activation.
 *
 * Uses @TempDir for isolated, auto-cleaned license file storage per test,
 * and resets the singleton instance between tests via reflection so each
 * test gets a fresh LicenseService bound to its own temp directory.
 */
@DisplayName("LicenseService — offline activation")
class LicenseServiceTest {

    @TempDir
    Path tempDir;

    private LicenseService service;

    @BeforeEach
    void setUp() throws Exception {
        resetSingleton();
        service = LicenseService.getInstance(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSingleton();
    }

    /** Resets the private static singleton so each test starts fresh. */
    private void resetSingleton() throws Exception {
        Field instanceField = LicenseService.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    // ── Trial period ──────────────────────────────────────────────────────

    @Test
    @DisplayName("First run starts a 30-day trial automatically")
    void firstRunStartsTrial() {
        LicenseService.Status status = service.checkStatus();
        assertEquals(LicenseService.Status.TRIAL_ACTIVE, status,
            "First-run check should auto-start TRIAL_ACTIVE status");
    }

    @Test
    @DisplayName("Trial days remaining starts at 30")
    void trialStartsAtThirtyDays() {
        service.checkStatus(); // triggers trial start
        int days = service.trialDaysRemaining();
        assertEquals(30, days, "Trial should start with 30 days remaining");
    }

    @Test
    @DisplayName("Trial status persists across getInstance calls with same dataDir")
    void trialPersistsAcrossInstances() throws Exception {
        service.checkStatus(); // start trial
        resetSingleton();
        LicenseService service2 = LicenseService.getInstance(tempDir.toString());
        assertEquals(LicenseService.Status.TRIAL_ACTIVE, service2.checkStatus(),
            "Trial state must persist to disk and be re-read by a new instance");
    }

    // ── Machine fingerprint ───────────────────────────────────────────────

    @Test
    @DisplayName("Machine fingerprint is 8 characters")
    void fingerprintIsEightChars() {
        String fp = service.machineFingerprint();
        assertNotNull(fp);
        assertEquals(8, fp.length(), "Machine fingerprint should be 8 chars: " + fp);
    }

    @Test
    @DisplayName("Machine fingerprint is deterministic (same on repeated calls)")
    void fingerprintIsDeterministic() {
        String fp1 = service.machineFingerprint();
        String fp2 = service.machineFingerprint();
        assertEquals(fp1, fp2, "Fingerprint must be stable across calls on the same machine");
    }

    // ── License key generation + activation round-trip ────────────────────

    @Test
    @DisplayName("Generated key activates successfully when machine matches")
    void generatedKeyActivatesSuccessfully() {
        String machineHash = service.machineFingerprint();
        String key = LicenseService.generateLicenseKey(
            LicenseService.Edition.PRO, LocalDate.now().plusYears(1), machineHash);

        boolean activated = service.activate(key);
        assertTrue(activated, "A correctly-signed key for this machine must activate successfully");
        assertEquals(LicenseService.Status.ACTIVE, service.checkStatus());
        assertEquals(LicenseService.Edition.PRO, service.getEdition());
    }

    @Test
    @DisplayName("Key bound to a different machine fails to activate")
    void wrongMachineKeyFailsToActivate() {
        String wrongMachineHash = "FFFFFFFF"; // not this machine's fingerprint
        String key = LicenseService.generateLicenseKey(
            LicenseService.Edition.STANDARD, LocalDate.now().plusYears(1), wrongMachineHash);

        boolean activated = service.activate(key);
        assertFalse(activated, "A key bound to a different machine must not activate");
    }

    @Test
    @DisplayName("Tampered key (modified edition) fails signature verification")
    void tamperedKeyFailsActivation() {
        String machineHash = service.machineFingerprint();
        String validKey = LicenseService.generateLicenseKey(
            LicenseService.Edition.STANDARD, LocalDate.now().plusYears(1), machineHash);

        // Tamper: upgrade STANDARD to ENTERPRISE without re-signing
        String tamperedKey = validKey.replace("STANDARD", "ENTERPRISE");

        boolean activated = service.activate(tamperedKey);
        assertFalse(activated,
            "A tampered key (edition changed without valid signature) must fail activation");
    }

    @Test
    @DisplayName("Garbage / malformed key fails gracefully (no exception)")
    void malformedKeyFailsGracefully() {
        assertDoesNotThrow(() -> {
            boolean result = service.activate("NOT-A-VALID-KEY-AT-ALL");
            assertFalse(result, "Malformed key should return false, not throw");
        });
    }

    @Test
    @DisplayName("Null key fails gracefully")
    void nullKeyFailsGracefully() {
        assertDoesNotThrow(() -> {
            boolean result = service.activate(null);
            assertFalse(result);
        });
    }

    @Test
    @DisplayName("Empty string key fails gracefully")
    void emptyKeyFailsGracefully() {
        assertDoesNotThrow(() -> {
            boolean result = service.activate("");
            assertFalse(result);
        });
    }

    // ── Expired license ──────────────────────────────────────────────────

    @Test
    @DisplayName("Expired license key returns TRIAL_EXPIRED-equivalent status")
    void expiredLicenseKeyDetected() {
        String machineHash = service.machineFingerprint();
        String expiredKey = LicenseService.generateLicenseKey(
            LicenseService.Edition.PRO, LocalDate.now().minusDays(1), machineHash);

        service.activate(expiredKey);
        LicenseService.Status status = service.checkStatus();
        assertEquals(LicenseService.Status.TRIAL_EXPIRED, status,
            "An expired license key should report TRIAL_EXPIRED status");
    }

    // ── Deactivation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Deactivate clears the active license and falls back to trial state")
    void deactivateClearsLicense() {
        String machineHash = service.machineFingerprint();
        String key = LicenseService.generateLicenseKey(
            LicenseService.Edition.PRO, LocalDate.now().plusYears(1), machineHash);
        service.activate(key);
        assertEquals(LicenseService.Status.ACTIVE, service.checkStatus());

        service.deactivate();
        LicenseService.Status afterDeactivate = service.checkStatus();
        assertNotEquals(LicenseService.Status.ACTIVE, afterDeactivate,
            "After deactivate(), status must not report ACTIVE");
    }

    // ── Masked key display ───────────────────────────────────────────────

    @Test
    @DisplayName("getMaskedKey returns 'Not activated' when no license")
    void maskedKeyShowsNotActivated() {
        assertEquals("Not activated", service.getMaskedKey());
    }

    @Test
    @DisplayName("getMaskedKey hides the middle of an active key")
    void maskedKeyHidesMiddle() {
        String machineHash = service.machineFingerprint();
        String key = LicenseService.generateLicenseKey(
            LicenseService.Edition.PRO, LocalDate.now().plusYears(1), machineHash);
        service.activate(key);

        String masked = service.getMaskedKey();
        assertNotEquals(key, masked, "Masked key must differ from the full key");
        assertTrue(masked.contains("••••••••"), "Masked key must contain bullet mask characters");
    }

    // ── Edition default ───────────────────────────────────────────────────

    @Test
    @DisplayName("Default edition before activation is TRIAL")
    void defaultEditionIsTrial() {
        assertEquals(LicenseService.Edition.TRIAL, service.getEdition());
    }
}
