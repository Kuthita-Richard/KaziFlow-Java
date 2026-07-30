package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DeniDAO — manages the Deni Book (credit/debt tracker).
 *
 * "Deni" = debt in Swahili. Every Kenyan shop runs on informal credit.
 * This tracks who owes what, when they last paid, and sends reminders.
 *
 * Tables used: deni_entries, deni_payments
 */
public class DeniDAO {

    private Connection conn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    // ── Schema ────────────────────────────────────────────────────────────

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS deni_entries (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    customer_id INTEGER REFERENCES customers(id),
                    name        TEXT NOT NULL,
                    phone       TEXT,
                    description TEXT,
                    amount      REAL NOT NULL,
                    paid        REAL NOT NULL DEFAULT 0,
                    status      TEXT DEFAULT 'unpaid'
                                    CHECK(status IN ('unpaid','partial','paid')),
                    due_date    DATE,
                    created_by  INTEGER REFERENCES users(id),
                    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS deni_payments (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    deni_id     INTEGER NOT NULL REFERENCES deni_entries(id) ON DELETE CASCADE,
                    amount      REAL NOT NULL,
                    note        TEXT,
                    paid_at     DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    /** Add a new credit entry. Returns new id or -1 on failure. */
    public int addEntry(String name, String phone, String description,
                        double amount, String dueDate, int createdBy) {
        String sql = """
            INSERT INTO deni_entries
            (name, phone, description, amount, paid, status, due_date, created_by)
            VALUES (?,?,?,?,0,'unpaid',?,?)
        """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setString(3, description);
            ps.setDouble(4, amount);
            ps.setString(5, dueDate.isBlank() ? null : dueDate);
            ps.setInt(6, createdBy);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    /** Record a payment against a deni entry. */
    public boolean recordPayment(int deniId, double amount, String note) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                // Insert payment record
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO deni_payments (deni_id, amount, note) VALUES (?,?,?)")) {
                    ps.setInt(1, deniId); ps.setDouble(2, amount); ps.setString(3, note);
                    ps.executeUpdate();
                }
                // Update paid total and status
                try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE deni_entries
                    SET paid = paid + ?,
                        status = CASE
                            WHEN paid + ? >= amount THEN 'paid'
                            WHEN paid + ? > 0 THEN 'partial'
                            ELSE 'unpaid'
                        END,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                """)) {
                    ps.setDouble(1, amount); ps.setDouble(2, amount);
                    ps.setDouble(3, amount); ps.setInt(4, deniId);
                    ps.executeUpdate();
                }
                c.commit();
                return true;
            } catch (Exception ex) { c.rollback(); ex.printStackTrace(); return false; }
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean delete(int id) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("DELETE FROM deni_entries WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Queries ───────────────────────────────────────────────────────────

    /** All entries: id, name, phone, description, amount, paid, balance, status, due_date, days_ago */
    public List<String[]> findAll(String statusFilter) {
        List<String[]> list = new ArrayList<>();
        String where = "unpaid_and_partial".equals(statusFilter)
            ? "WHERE d.status IN ('unpaid','partial')"
            : statusFilter != null ? "WHERE d.status = '" + statusFilter + "'"
            : "";
        String sql = """
            SELECT d.id, d.name, d.phone, d.description,
                   d.amount, d.paid, (d.amount - d.paid) AS balance,
                   d.status, d.due_date,
                   CAST(julianday('now') - julianday(d.created_at) AS INTEGER) AS days_ago,
                   d.updated_at
            FROM deni_entries d
            """ + where + " ORDER BY balance DESC, d.updated_at DESC";
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(sql);
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("name"),
                    rs.getString("phone") != null ? rs.getString("phone") : "—",
                    rs.getString("description") != null ? rs.getString("description") : "—",
                    String.format("%.2f", rs.getDouble("amount")),
                    String.format("%.2f", rs.getDouble("paid")),
                    String.format("%.2f", rs.getDouble("balance")),
                    rs.getString("status"),
                    rs.getString("due_date") != null ? rs.getString("due_date") : "—",
                    rs.getString("days_ago") + " days ago",
                    rs.getString("updated_at")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public List<String[]> search(String query) {
        List<String[]> list = new ArrayList<>();
        String sql = """
            SELECT id, name, phone, description, amount, paid,
                   (amount-paid) AS balance, status, due_date,
                   CAST(julianday('now')-julianday(created_at) AS INTEGER) AS days_ago,
                   updated_at
            FROM deni_entries
            WHERE name LIKE ? OR phone LIKE ? OR description LIKE ?
            ORDER BY balance DESC
        """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            String q = "%" + query + "%";
            ps.setString(1, q); ps.setString(2, q); ps.setString(3, q);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("name"),
                    rs.getString("phone") != null ? rs.getString("phone") : "—",
                    rs.getString("description") != null ? rs.getString("description") : "—",
                    String.format("%.2f", rs.getDouble("amount")),
                    String.format("%.2f", rs.getDouble("paid")),
                    String.format("%.2f", rs.getDouble("balance")),
                    rs.getString("status"),
                    rs.getString("due_date") != null ? rs.getString("due_date") : "—",
                    rs.getString("days_ago") + " days ago",
                    rs.getString("updated_at")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public List<String[]> getPaymentHistory(int deniId) {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM deni_payments WHERE deni_id=? ORDER BY paid_at DESC")) {
            ps.setInt(1, deniId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.format("%.2f", rs.getDouble("amount")),
                    rs.getString("note") != null ? rs.getString("note") : "—",
                    rs.getString("paid_at")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    public double getTotalOutstanding() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT COALESCE(SUM(amount-paid),0) FROM deni_entries WHERE status != 'paid'");
            if (rs.next()) return rs.getDouble(1);
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    public int getOverdueCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT COUNT(*) FROM deni_entries WHERE status != 'paid' " +
                "AND due_date IS NOT NULL AND due_date < date('now')");
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    public int getDebtorCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT COUNT(*) FROM deni_entries WHERE status != 'paid'");
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }
}
