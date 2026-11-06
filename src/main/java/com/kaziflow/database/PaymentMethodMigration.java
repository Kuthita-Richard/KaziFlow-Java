package com.kaziflow.database;

import com.kaziflow.network.NetworkConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rebuilds {@code sales} without {@code CHECK(payment_method IN ('cash','mpesa','card','bank'))} (databases created before
 * v11.43), replacing it with a foreign key to the {@code payment_methods} master table — SQLite cannot drop a CHECK in place.
 *
 * <p>The procedure is SQLite's documented table-rebuild one, with the safeguards a populated database needs:
 * <ul>
 *   <li>a consistent backup first ({@code VACUUM INTO}); no backup, no migration;</li>
 *   <li>{@code PRAGMA foreign_keys = OFF} for the rebuild (outside the transaction): with it ON, dropping {@code sales} would
 *       cascade-delete {@code sale_items}; it is switched back ON in a {@code finally};</li>
 *   <li>the new table is derived from the table's CURRENT stored definition (so columns added later by ALTER TABLE are kept),
 *       and the column lists are compared before copying;</li>
 *   <li>one {@code BEGIN IMMEDIATE} transaction: row count and the sum of totals are verified after the copy, indexes, triggers
 *       and views are recreated, the AUTOINCREMENT counter is preserved, and the foreign-key check must not report MORE
 *       violations than before (a legacy database may already contain orphans; the rebuild must not add any);</li>
 *   <li>any failure rolls everything back and leaves the old table untouched.</li>
 * </ul>
 * Idempotent: a table without the CHECK is left alone. Not run in LAN client mode (the server's file is not ours to rebuild).
 */
public final class PaymentMethodMigration {
    private PaymentMethodMigration() {}

    private static final Pattern CHECK = Pattern.compile("(?i)\\s*CHECK\\s*\\(\\s*payment_method\\s+IN\\s*\\([^)]*\\)\\s*\\)");
    private static final Pattern CREATE_HEAD = Pattern.compile("(?i)^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[\"`\\[]?sales[\"`\\]]?");
    private static final String TMP = "sales_pm_new";

    /** Runs the migration on the application's database if it needs it. Returns true if the table was rebuilt. */
    public static boolean run() {
        if (NetworkConfig.getInstance().getMode() == NetworkConfig.Mode.CLIENT) return false;
        try (Connection c = DatabaseManager.getInstance().getConnection()) {
            boolean rebuilt = run(c);
            if (rebuilt) System.out.println("[DB] sales rebuilt: payment_method is now master data (payment_methods)");
            return rebuilt;
        } catch (Exception e) {
            System.err.println("[DB] Payment-method migration failed — sales is unchanged and keeps working with its four built-in methods: " + e.getMessage());
            return false;
        }
    }

    /** True when the CHECK is gone, so a method added in Settings can be used on a sale. */
    public static boolean isSalesTableMigrated(Connection c) throws SQLException {
        String sql = salesSql(c);
        return sql == null || !CHECK.matcher(sql).find();
    }

    public static boolean isSalesTableMigrated() {
        try (Connection c = DatabaseManager.getInstance().getConnection()) { return isSalesTableMigrated(c); }
        catch (SQLException e) { return false; }
    }

    /** Connection-level (testable). The connection must be a plain local connection. */
    public static boolean run(Connection c) throws SQLException {
        String oldSql = salesSql(c);
        if (oldSql == null || !CHECK.matcher(oldSql).find()) return false;                   // nothing to do / already migrated

        // every payment method already used by a sale must exist in the master table (the new foreign key)
        List<String> missing = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT payment_method FROM sales WHERE payment_method IS NOT NULL AND payment_method NOT IN (SELECT code FROM payment_methods)")) {
            while (rs.next()) missing.add(rs.getString(1));
        } catch (SQLException e) { throw new SQLException("payment_methods table is missing: " + e.getMessage(), e); }
        if (!missing.isEmpty()) throw new SQLException("sales uses payment methods that are not in payment_methods: " + missing);

        String newSql = buildNewSql(oldSql);
        List<String> cols = columns(c, "sales");
        List<String> extras = new ArrayList<>();                                                // indexes, triggers, views to recreate
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND ((type IN ('index','trigger') AND tbl_name = 'sales') OR (type = 'view' AND lower(sql) LIKE '%sales%'))")) {
            while (rs.next()) extras.add(rs.getString(1));
        }
        List<String> viewNames = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'view' AND lower(sql) LIKE '%sales%'")) {
            while (rs.next()) viewNames.add(rs.getString(1));
        }

        backup(c);                                                                              // throws → nothing has been touched
        long fkBefore = fkViolations(c);
        long rowsBefore = scalar(c, "SELECT COUNT(*) FROM sales");
        double sumBefore = scalarD(c, "SELECT COALESCE(SUM(total_amount),0) FROM sales");
        Long seqBefore = scalarOrNull(c, "SELECT seq FROM sqlite_sequence WHERE name = 'sales'");

        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(true);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");                                            // must precede BEGIN; ON would cascade-delete sale_items
            st.execute("PRAGMA legacy_alter_table = ON");                                       // views/triggers that mention sales must not block the rename
            try {
                st.execute("BEGIN IMMEDIATE");
                for (String v : viewNames) st.execute("DROP VIEW IF EXISTS \"" + v.replace("\"", "\"\"") + "\"");
                st.execute(newSql);
                List<String> newCols = columns(c, TMP);
                if (!newCols.equals(cols)) throw new SQLException("rebuilt table has different columns: " + newCols + " vs " + cols);
                String list = String.join(", ", cols);
                st.execute("INSERT INTO " + TMP + " (" + list + ") SELECT " + list + " FROM sales");
                if (scalar(c, "SELECT COUNT(*) FROM " + TMP) != rowsBefore) throw new SQLException("row count changed during the copy");
                if (Math.abs(scalarD(c, "SELECT COALESCE(SUM(total_amount),0) FROM " + TMP) - sumBefore) > 0.0001) throw new SQLException("sales totals changed during the copy");
                st.execute("DROP TABLE sales");
                st.execute("ALTER TABLE " + TMP + " RENAME TO sales");
                for (String ddl : extras) st.execute(ddl);
                long maxId = scalar(c, "SELECT COALESCE(MAX(id),0) FROM sales");
                long seq = Math.max(maxId, seqBefore == null ? 0 : seqBefore);                  // AUTOINCREMENT must never hand out an id that was used
                if (scalarOrNull(c, "SELECT seq FROM sqlite_sequence WHERE name = 'sales'") == null) st.execute("INSERT INTO sqlite_sequence (name, seq) VALUES ('sales', " + seq + ")");
                else st.execute("UPDATE sqlite_sequence SET seq = " + seq + " WHERE name = 'sales'");
                if (fkViolations(c) > fkBefore) throw new SQLException("the rebuild introduced foreign-key violations");
                st.execute("COMMIT");
            } catch (SQLException e) {
                try { st.execute("ROLLBACK"); } catch (SQLException ignored) { /* no transaction was open */ }
                throw e;
            }
        } finally {
            try (Statement st = c.createStatement()) { st.execute("PRAGMA legacy_alter_table = OFF"); st.execute("PRAGMA foreign_keys = ON"); }
            c.setAutoCommit(autoCommit);
        }
        return true;
    }

    static String buildNewSql(String oldSql) {
        Matcher head = CREATE_HEAD.matcher(oldSql);
        if (!head.find()) throw new IllegalStateException("unrecognised definition of sales: " + oldSql);
        String s = head.replaceFirst("CREATE TABLE " + TMP);
        s = CHECK.matcher(s).replaceFirst(" REFERENCES payment_methods(code)");
        if (!s.contains("REFERENCES payment_methods(code)") || CHECK.matcher(s).find()) throw new IllegalStateException("could not rewrite the payment_method column: " + s);
        return s;
    }

    private static void backup(Connection c) throws SQLException {
        String file = null;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT file FROM pragma_database_list WHERE name = 'main'")) { if (rs.next()) file = rs.getString(1); }
        if (file == null || file.isBlank()) throw new SQLException("cannot back up an in-memory database");
        String target = file + ".pre-paymentmethods-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".bak";
        try (Statement st = c.createStatement()) { st.execute("VACUUM INTO '" + target.replace("'", "''") + "'"); }
    }

    private static String salesSql(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'sales'")) { return rs.next() ? rs.getString(1) : null; }
    }

    private static List<String> columns(Connection c, String table) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) { while (rs.next()) out.add(rs.getString("name")); }
        return out;
    }

    private static long fkViolations(Connection c) throws SQLException {
        long n = 0;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA foreign_key_check")) { while (rs.next()) n++; }
        return n;
    }

    private static long scalar(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) { return rs.next() ? rs.getLong(1) : 0; }
    }

    private static double scalarD(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) { return rs.next() ? rs.getDouble(1) : 0; }
    }

    private static Long scalarOrNull(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) { if (!rs.next()) return null; long v = rs.getLong(1); return rs.wasNull() ? null : v; }
    }
}
