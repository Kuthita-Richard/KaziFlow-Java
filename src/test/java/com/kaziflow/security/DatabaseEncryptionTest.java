package com.kaziflow.security;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseEncryption (AES-256-GCM at-rest encryption).
 *
 * Uses JUnit 5 @TempDir for isolated file I/O per test — no shared state
 * between tests, no cleanup required.
 */
@DisplayName("DatabaseEncryption — AES-256-GCM")
class DatabaseEncryptionTest {

    @TempDir
    Path tempDir;

    private DatabaseEncryption enc;
    private String dbPath;
    private static final char[] PASSPHRASE = "KaziFlowTest2024!".toCharArray();
    private static final char[] WRONG_PASSPHRASE = "WrongPassphrase!".toCharArray();

    @BeforeEach
    void setUp() throws Exception {
        // Each test gets a fresh singleton instance via a unique data dir
        String dataDir = tempDir.toString();
        dbPath = dataDir + "/kaziflow.db";
        // Use reflection to reset singleton for isolated tests
        java.lang.reflect.Field f = DatabaseEncryption.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
        enc = DatabaseEncryption.getInstance(dataDir);
    }

    // ── isEncryptionEnabled ───────────────────────────────────────────────

    @Test
    @DisplayName("isEncryptionEnabled: false when no .enc file exists")
    void notEnabledInitially() {
        assertFalse(enc.isEncryptionEnabled(dbPath));
    }

    @Test
    @DisplayName("isEncryptionEnabled: true after encryption")
    void enabledAfterEncrypt() throws Exception {
        writeTestDb(dbPath, "test db content");
        enc.unlockWithPassphrase(PASSPHRASE.clone());
        enc.encryptDatabase(dbPath);
        assertTrue(enc.isEncryptionEnabled(dbPath));
    }

    // ── Encrypt / Decrypt roundtrip ───────────────────────────────────────

    @Test
    @DisplayName("Encrypt then decrypt recovers original content")
    void encryptDecryptRoundtrip() throws Exception {
        String original = "SQLite database simulation content 1234567890";
        writeTestDb(dbPath, original);

        enc.unlockWithPassphrase(PASSPHRASE.clone());
        enc.encryptDatabase(dbPath);

        // Plaintext file should now be gone
        assertFalse(Files.exists(Path.of(dbPath)),
            "Plaintext file should be erased after encryption");

        // Encrypted file should exist
        assertTrue(Files.exists(Path.of(dbPath + ".enc")),
            "Encrypted file should exist");

        // Decrypt and verify content
        enc.decryptDatabase(dbPath);
        String recovered = Files.readString(Path.of(dbPath));
        assertEquals(original, recovered, "Recovered content must match original");
    }

    @Test
    @DisplayName("Decrypt with wrong passphrase throws exception")
    void wrongPassphraseThrows() throws Exception {
        writeTestDb(dbPath, "secret database content");

        enc.unlockWithPassphrase(PASSPHRASE.clone());
        enc.encryptDatabase(dbPath);
        enc.lock();

        // Reset with wrong passphrase
        enc.unlockWithPassphrase(WRONG_PASSPHRASE.clone());
        assertThrows(javax.crypto.AEADBadTagException.class,
            () -> enc.decryptDatabase(dbPath),
            "Wrong passphrase must throw AEADBadTagException (GCM tag mismatch)");
    }

    @Test
    @DisplayName("decryptDatabase: returns false if no .enc file")
    void decryptNoFileReturnsFalse() throws Exception {
        enc.unlockWithPassphrase(PASSPHRASE.clone());
        boolean result = enc.decryptDatabase(dbPath);
        assertFalse(result, "Should return false when no .enc file exists");
    }

    // ── Salt persistence ──────────────────────────────────────────────────

    @Test
    @DisplayName("Same passphrase + same salt = same derived key (deterministic)")
    void deterministicKeyDerivation() throws Exception {
        writeTestDb(dbPath, "test content abc");
        enc.unlockWithPassphrase(PASSPHRASE.clone());
        enc.encryptDatabase(dbPath);

        // Second unlock with same passphrase should be able to decrypt
        enc.lock();
        enc.unlockWithPassphrase(PASSPHRASE.clone());
        assertDoesNotThrow(() -> enc.decryptDatabase(dbPath),
            "Same passphrase should re-derive the same key and decrypt successfully");
    }

    // ── isUnlocked / lock ─────────────────────────────────────────────────

    @Test
    @DisplayName("isUnlocked: false before unlock, true after")
    void lockState() throws Exception {
        assertFalse(enc.isUnlocked());
        enc.unlockWithPassphrase(PASSPHRASE.clone());
        assertTrue(enc.isUnlocked());
        enc.lock();
        assertFalse(enc.isUnlocked());
    }

    @Test
    @DisplayName("encryptDatabase throws when not unlocked")
    void encryptWithoutUnlock() {
        assertThrows(IllegalStateException.class,
            () -> enc.encryptDatabase(dbPath),
            "Should throw IllegalStateException if no passphrase set");
    }

    // ── changePassphrase ──────────────────────────────────────────────────

    @Test
    @DisplayName("changePassphrase: new passphrase decrypts; old passphrase fails")
    void changePassphrase() throws Exception {
        char[] newPass = "NewPassphrase2025!".toCharArray();
        writeTestDb(dbPath, "important payroll data");

        // Enable with old passphrase
        enc.unlockWithPassphrase(PASSPHRASE.clone());
        enc.encryptDatabase(dbPath);
        enc.lock();

        // Change passphrase
        enc.changePassphrase(PASSPHRASE.clone(), newPass.clone(), dbPath);
        enc.lock();

        // New passphrase should work
        enc.unlockWithPassphrase(newPass.clone());
        assertDoesNotThrow(() -> enc.decryptDatabase(dbPath));

        // Clean up for next assertion
        enc.encryptDatabase(dbPath);
        enc.lock();

        // Old passphrase should now fail
        enc.unlockWithPassphrase(PASSPHRASE.clone());
        assertThrows(javax.crypto.AEADBadTagException.class,
            () -> enc.decryptDatabase(dbPath),
            "Old passphrase must fail after change");
    }

    // ── Tamper detection ──────────────────────────────────────────────────

    @Test
    @DisplayName("GCM tag detects file tampering (bit-flip attack)")
    void tamperDetection() throws Exception {
        writeTestDb(dbPath, "sensitive business data");
        enc.unlockWithPassphrase(PASSPHRASE.clone());
        enc.encryptDatabase(dbPath);

        // Flip a byte in the middle of the encrypted file
        Path encPath = Path.of(dbPath + ".enc");
        byte[] bytes = Files.readAllBytes(encPath);
        bytes[bytes.length / 2] ^= 0xFF;  // bit-flip
        Files.write(encPath, bytes);

        assertThrows(javax.crypto.AEADBadTagException.class,
            () -> enc.decryptDatabase(dbPath),
            "Tampered ciphertext must be detected by GCM authentication tag");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void writeTestDb(String path, String content) throws Exception {
        Files.writeString(Path.of(path), content, StandardCharsets.UTF_8);
    }
}
