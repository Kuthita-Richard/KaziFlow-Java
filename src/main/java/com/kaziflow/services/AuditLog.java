package com.kaziflow.services;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.utils.SessionManager;

import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Audit logging service — now a tamper-evident hash chain (Phase 10 security
 * hardening), similar in spirit to how a blockchain links blocks, without any
 * of the consensus/mining machinery a real blockchain needs (this is a single
 * local database, not a distributed ledger — that machinery would be
 * pointless overhead here).
 *
 * Every row stores record_hash = SHA-256(prev_hash + this row's own fields).
 * Since each hash depends on the one before it, editing or deleting any
 * historical row breaks every hash after it — verifyChainIntegrity() walks
 * the whole table and reports exactly where a break happened, if any.
 *
 * IMPORTANT CHANGE: writes used to fire off on an unordered new Thread per
 * call, which could let two log() calls race to read "the last hash" and
 * fork the chain. That's not just a style nit — a forked chain is invalid by
 * construction. Writes are now serialized through a single-thread executor,
 * so hashes are always computed and inserted strictly one at a time.
 */
public class AuditLog {

    private static final String INSERT_SQL =
        "INSERT INTO audit_log (user_id, user_name, action, description, module, record_id, ip_address, prev_hash, record_hash, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)";

    // Single-writer queue — the whole point of a hash chain is a strict, non-racing sequence.
    private static final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "audit-log-writer");
        t.setDaemon(true);
        return t;
    });

    /** Adds hash-chain columns if they don't exist yet — same idempotent-migration pattern as CustomerDAO's loyalty column. */
    public static void ensureHashColumns() {
        try (Connection conn = DatabaseManager.getInstance().getConnection(); Statement s = conn.createStatement()) {
            try { s.execute("ALTER TABLE audit_log ADD COLUMN prev_hash TEXT"); } catch (SQLException ignored) {}
            try { s.execute("ALTER TABLE audit_log ADD COLUMN record_hash TEXT"); } catch (SQLException ignored) {}
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ─── Log Methods ─────────────────────────────────────────────────────────

    public static void log(String action, String description) {
        log(action, description, null, null);
    }

    public static void log(String action, String description, String module, Integer recordId) {
        int userId = 0;
        String userName = "system";
        try {
            var user = SessionManager.getInstance().getCurrentUser();
            if (user != null) { userId = user.getId(); userName = user.getName(); }
        } catch (Exception ignored) {}

        final int fUserId = userId;
        final String fUserName = userName;
        final String timestamp = LocalDateTime.now().toString();

        writer.submit(() -> {
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                String prevHash = fetchLastHash(conn);
                String recordHash = computeHash(prevHash, fUserId, fUserName, action, description, module, recordId, timestamp);

                try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                    ps.setInt(1, fUserId);
                    ps.setString(2, fUserName);
                    ps.setString(3, action);
                    ps.setString(4, description);
                    ps.setString(5, module);
                    if (recordId != null) ps.setInt(6, recordId); else ps.setNull(6, Types.INTEGER);
                    ps.setString(7, "127.0.0.1");
                    ps.setString(8, prevHash);
                    ps.setString(9, recordHash);
                    ps.setString(10, timestamp);
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                System.err.println("[AuditLog] Failed to write: " + e.getMessage());
            }
        });
    }

    private static String fetchLastHash(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT record_hash FROM audit_log ORDER BY id DESC LIMIT 1")) {
            if (rs.next()) {
                String h = rs.getString("record_hash");
                if (h != null) return h;
            }
        }
        return "GENESIS"; // first-ever entry has no predecessor — a fixed, well-known anchor value
    }

    private static String computeHash(String prevHash, int userId, String userName, String action,
                                       String description, String module, Integer recordId, String timestamp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = prevHash + "|" + userId + "|" + userName + "|" + action + "|"
                + description + "|" + module + "|" + recordId + "|" + timestamp;
            byte[] hashBytes = digest.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            return "HASH_ERROR"; // deliberately invalid so a chain-verify pass flags it rather than silently accepting a bad link
        }
    }

    /**
     * Walks every audit_log row in insertion order, recomputing each row's hash from
     * its own fields + the previous row's stored hash, and comparing it to what's
     * actually stored. Returns an empty list if the whole chain is intact, or a
     * description of every break found (which row, and how it disagrees).
     */
    public static List<String> verifyChainIntegrity() {
        List<String> breaks = new ArrayList<>();
        String expectedPrev = "GENESIS";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM audit_log ORDER BY id ASC")) {
            int rowNum = 0;
            while (rs.next()) {
                rowNum++;
                String storedPrev = rs.getString("prev_hash");
                String storedHash = rs.getString("record_hash");
                if (storedHash == null) {
                    breaks.add("Row #" + rowNum + " (id=" + rs.getInt("id") + ") has no hash — predates the integrity feature or was tampered with.");
                    // fetchLastHash() falls back to "GENESIS" whenever the most recent row's hash is
                    // null (it doesn't walk backward hunting for the last real hash) — so whatever got
                    // inserted right after this row genuinely does link to "GENESIS", not to some earlier
                    // row's hash. Match that here, or every row after a legacy null-hash row would be
                    // flagged as a false chain break even though nothing was actually tampered with.
                    expectedPrev = "GENESIS";
                    continue;
                }
                if (storedPrev == null || !storedPrev.equals(expectedPrev)) {
                    breaks.add("Row #" + rowNum + " (id=" + rs.getInt("id") + "): expected previous hash "
                        + shortHash(expectedPrev) + " but row links to " + shortHash(storedPrev) + " — chain broken here.");
                }
                Integer recordId = (Integer) rs.getObject("record_id");
                String recomputed = computeHash(storedPrev, rs.getInt("user_id"), rs.getString("user_name"),
                    rs.getString("action"), rs.getString("description"), rs.getString("module"),
                    recordId, rs.getString("created_at"));
                if (!recomputed.equals(storedHash)) {
                    breaks.add("Row #" + rowNum + " (id=" + rs.getInt("id") + "): stored content doesn't match its hash — this row was likely edited after being logged.");
                }
                expectedPrev = storedHash;
            }
        } catch (Exception e) {
            breaks.add("Could not complete verification: " + e.getMessage());
        }
        return breaks;
    }

    private static String shortHash(String h) {
        if (h == null) return "null";
        return h.length() > 12 ? h.substring(0, 12) + "…" : h;
    }

    // Convenience methods for common actions
    public static void logSale(int saleId, double total) {
        log("SALE_COMPLETED", "Sale completed. Total: KES " + String.format("%.2f", total), "sales", saleId);
    }

    public static void logLogin(String email) {
        log("USER_LOGIN", "User logged in: " + email, "auth", null);
    }

    public static void logLogout(String email) {
        log("USER_LOGOUT", "User logged out: " + email, "auth", null);
    }

    public static void logProductEdit(int productId, String productName) {
        log("PRODUCT_UPDATED", "Product updated: " + productName, "inventory", productId);
    }

    public static void logExpenseAdded(double amount, String category) {
        log("EXPENSE_ADDED", "Expense added: KES " + String.format("%.2f", amount) + " (" + category + ")", "finance", null);
    }

    public static void logPurchaseOrder(int poId, String poNumber, double total) {
        log("PURCHASE_ORDER_CREATED", "PO created: " + poNumber + " — KES " + String.format("%.2f", total), "purchases", poId);
    }

    public static void logUserCreated(String userName) {
        log("USER_CREATED", "New user created: " + userName, "settings", null);
    }

    public static void logBackup(String filePath) {
        log("BACKUP_CREATED", "Database backed up to: " + filePath, "settings", null);
    }

    // ─── Read Methods ─────────────────────────────────────────────────────────

    public static List<String[]> getRecentLogs(int limit) {
        List<String[]> logs = new ArrayList<>();
        String sql = "SELECT * FROM audit_log ORDER BY created_at DESC LIMIT ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                logs.add(new String[]{
                    rs.getString("user_name"),
                    rs.getString("action"),
                    rs.getString("description"),
                    rs.getString("module") != null ? rs.getString("module") : "system",
                    rs.getString("created_at")
                });
            }
        } catch (Exception e) {
            System.err.println("[AuditLog] Read failed: " + e.getMessage());
        }
        return logs;
    }

    public static int getTodayCount() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM audit_log WHERE DATE(created_at) = DATE('now')");
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }
}
