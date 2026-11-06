package com.kaziflow.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Document numbers (INV-0001, PO-0001, …): one atomic, gapless, configurable sequence per document type.
 *
 * <p>Why: every generator used to be {@code SELECT COUNT(*) … + 1} read in a transaction that wrote later. That (a) gives the
 * same number to two cashiers on a LAN, (b) fails with SQLITE_BUSY_SNAPSHOT when another writer commits between the read and
 * the write, and (c) collides after any delete (count drops, the number already exists, the UNIQUE insert fails at checkout).
 *
 * <p>How: {@link #next} must be called on the connection of the transaction that will use the number. Its FIRST statement is
 * a write, so the transaction takes SQLite's write lock before it reads anything (no snapshot to go stale, other writers wait
 * out {@code busy_timeout} instead of failing); everything after runs exclusively until commit. If the transaction rolls back,
 * the counter rolls back with it — the number is handed out again, so tax-invoice numbers have no gaps. The counter lives in
 * {@code number_sequences} (prefix and padding per document type, changeable with {@link #configure}). The first use on a
 * database that already has documents continues after the highest number present.
 */
public final class NumberSequence {
    private NumberSequence() {}

    /** The documents that are numbered: where they live and their default format. Add an entry to number a new document. */
    public enum DocType {
        SALE("sales", "sale_number", "INV-", 4, "Sales invoice"),
        PURCHASE("purchases", "purchase_number", "PO-", 4, "Purchase order"),
        QUOTATION("quotations", "quote_number", "QT-", 4, "Quotation"),
        SALE_RETURN("sale_returns", "return_number", "RET-", 4, "Sales return"),
        STOCK_TRANSFER("stock_transfers", "transfer_number", "TRF-", 4, "Stock transfer"),
        EMPLOYEE("employees", "employee_number", "EMP-", 3, "Employee"),
        /** The counter inside the local eTIMS serial (LOCAL-2026-000001); use {@link #nextNumber}, the format is not configurable. */
        ETIMS_LOCAL("etims_serials", "sequence", "", 1, "Local eTIMS serial", false);

        public final String table, column, defaultPrefix, label;
        public final int defaultPadding;
        public final boolean configurable;
        DocType(String table, String column, String prefix, int padding, String label) { this(table, column, prefix, padding, label, true); }
        DocType(String table, String column, String prefix, int padding, String label, boolean configurable) {
            this.table = table; this.column = column; this.defaultPrefix = prefix; this.defaultPadding = padding; this.label = label; this.configurable = configurable;
        }
    }

    private static final int ATTEMPTS = 4;
    private static final String DDL = "CREATE TABLE IF NOT EXISTS number_sequences ("
            + "doc_type TEXT PRIMARY KEY, prefix TEXT NOT NULL, padding INTEGER NOT NULL, next_value INTEGER NOT NULL DEFAULT 0)";

    /** Allocates the next number. Call it first in the transaction that will store the document. */
    public static String next(Connection c, DocType t) throws SQLException {
        for (int attempt = 1; ; attempt++) {
            try { return allocate(c, t); }
            catch (SQLException e) {
                // Plain BUSY (the write lock stayed taken for the whole busy_timeout) can be retried here: nothing has been read
                // in this transaction yet. BUSY_SNAPSHOT cannot — the caller must restart its transaction (see isBusy).
                if (attempt >= ATTEMPTS || !isBusy(e) || isSnapshot(e)) throw e;
                try { Thread.sleep(60L * attempt * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw e; }
            }
        }
    }

    /** Allocates the next counter value as a plain number (for documents whose number is composed elsewhere, e.g. the eTIMS serial). */
    public static long nextNumber(Connection c, DocType t) throws SQLException {
        String v = next(c, t);
        try { return Long.parseLong(v); } catch (NumberFormatException e) { throw new SQLException("Sequence " + t + " is not numeric: " + v, e); }
    }

    /** The number {@link #next} would hand out now, without using it (for showing "next number" in a form). */
    public static String peek(Connection c, DocType t) throws SQLException {
        String prefix = t.defaultPrefix; int padding = t.defaultPadding; long next = 0;
        try (PreparedStatement ps = c.prepareStatement("SELECT prefix, padding, next_value FROM number_sequences WHERE doc_type = ?")) {
            ps.setString(1, t.name());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) { prefix = rs.getString(1); padding = rs.getInt(2); next = rs.getLong(3); } }
        } catch (SQLException e) { if (!missingTable(e)) throw e; }
        if (next <= 0) next = highestIssued(c, t, prefix) + 1;
        return format(prefix, padding, next);
    }

    /** Creates the sequence table if needed (idempotent). Called at start-up by {@code SchemaMigrations}. */
    public static void ensureTable() {
        try (Connection c = com.kaziflow.database.DatabaseManager.getInstance().getConnection()) { ensureTable(c); }
        catch (SQLException e) { e.printStackTrace(); }
    }

    private static void ensureTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) { st.execute(DDL); }
    }

    /** Changes the format of future numbers (the counter continues). Prefix: 1–10 of letters, digits, - _ / . ; padding 1–9. */
    public static void configure(Connection c, DocType t, String prefix, int padding) throws SQLException {
        if (!t.configurable) throw new IllegalArgumentException(t.label + " numbers cannot be reformatted");
        if (prefix == null || !prefix.matches("[A-Za-z0-9_/.-]{1,10}")) throw new IllegalArgumentException("Prefix must be 1–10 letters, digits or - _ / .");
        if (padding < 1 || padding > 9) throw new IllegalArgumentException("Padding must be between 1 and 9");
        ensureTable(c);
        insertRow(c, t);
        try (PreparedStatement ps = c.prepareStatement("UPDATE number_sequences SET prefix = ?, padding = ? WHERE doc_type = ?")) {
            ps.setString(1, prefix); ps.setInt(2, padding); ps.setString(3, t.name()); ps.executeUpdate();
        }
    }

    public static String format(String prefix, int padding, long n) { return prefix + String.format("%0" + padding + "d", n); }

    public static boolean isBusy(Throwable e) {
        for (Throwable x = e; x != null; x = x.getCause()) {
            String m = x.getMessage();
            if (m != null && (m.contains("SQLITE_BUSY") || m.contains("database is locked"))) return true;
        }
        return false;
    }

    private static boolean isSnapshot(Throwable e) {
        for (Throwable x = e; x != null; x = x.getCause()) { String m = x.getMessage(); if (m != null && m.contains("SNAPSHOT")) return true; }
        return false;
    }

    private static String allocate(Connection c, DocType t) throws SQLException {
        // The FIRST statement must be the write. (Even "CREATE TABLE IF NOT EXISTS" on an existing table reads the schema and starts
        // a read snapshot, and the write after it then fails with SQLITE_BUSY_SNAPSHOT when another connection committed meanwhile —
        // which is why the table is created at start-up, SchemaMigrations, and only created here if it is somehow missing.)
        try { insertRow(c, t); }
        catch (SQLException e) {
            if (!String.valueOf(e.getMessage()).contains("no such table")) throw e;
            ensureTable(c);
            insertRow(c, t);
        }
        String prefix = t.defaultPrefix; int padding = t.defaultPadding; long next = 0;
        try (PreparedStatement ps = c.prepareStatement("SELECT prefix, padding, next_value FROM number_sequences WHERE doc_type = ?")) {
            ps.setString(1, t.name());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) { prefix = rs.getString(1); padding = rs.getInt(2); next = rs.getLong(3); } }
        }
        if (next <= 0) next = highestIssued(c, t, prefix) + 1;        // first use here: continue after what already exists
        try (PreparedStatement ps = c.prepareStatement("UPDATE number_sequences SET next_value = ? WHERE doc_type = ?")) {
            ps.setLong(1, next + 1); ps.setString(2, t.name()); ps.executeUpdate();
        }
        return format(prefix, padding, next);
    }

    private static void insertRow(Connection c, DocType t) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO number_sequences (doc_type, prefix, padding, next_value) VALUES (?,?,?,0)")) {
            ps.setString(1, t.name()); ps.setString(2, t.defaultPrefix); ps.setInt(3, t.defaultPadding); ps.executeUpdate();
        }
    }

    /** Highest numeric suffix among existing documents with this prefix (0 if none, or the table does not exist yet). */
    private static long highestIssued(Connection c, DocType t, String prefix) throws SQLException {
        String like = prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTR(" + t.column + ", ?) AS INTEGER)), 0) FROM " + t.table + " WHERE " + t.column + " LIKE ? ESCAPE '\\'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, prefix.length() + 1); ps.setString(2, like);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0; }
        } catch (SQLException e) { if (missingTable(e)) return 0; throw e; }
    }

    private static boolean missingTable(SQLException e) { return String.valueOf(e.getMessage()).contains("no such table"); }
}
