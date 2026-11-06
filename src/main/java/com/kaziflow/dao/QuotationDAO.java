package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * QuotationDAO — manages quotations and proforma invoices.
 *
 * A quotation can be:
 *   - Sent to a customer as a price estimate
 *   - Converted to a real sale with one click
 *   - Printed as a proforma invoice
 */
public class QuotationDAO {

    private Connection conn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS quotations (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    quote_number    TEXT NOT NULL UNIQUE,
                    customer_name   TEXT NOT NULL,
                    customer_phone  TEXT,
                    customer_email  TEXT,
                    subtotal        REAL NOT NULL DEFAULT 0,
                    tax_amount      REAL NOT NULL DEFAULT 0,
                    discount        REAL NOT NULL DEFAULT 0,
                    total           REAL NOT NULL DEFAULT 0,
                    status          TEXT DEFAULT 'draft'
                                        CHECK(status IN ('draft','sent','accepted','declined','converted')),
                    notes           TEXT,
                    valid_days      INTEGER DEFAULT 7,
                    created_by      INTEGER REFERENCES users(id),
                    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS quotation_items (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    quotation_id    INTEGER NOT NULL REFERENCES quotations(id) ON DELETE CASCADE,
                    product_id      INTEGER REFERENCES products(id),
                    description     TEXT NOT NULL,
                    quantity        REAL NOT NULL DEFAULT 1,
                    unit_price      REAL NOT NULL,
                    discount        REAL DEFAULT 0,
                    line_total      REAL NOT NULL
                )
            """);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Create ────────────────────────────────────────────────────────────

    public int createQuotation(String customerName, String phone, String email,
                                String notes, int validDays, int createdBy) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
            // Quote number and the quotation row in ONE transaction; the number is allocated first (takes the write lock)
            String quoteNum = com.kaziflow.services.NumberSequence.next(c, com.kaziflow.services.NumberSequence.DocType.QUOTATION);

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO quotations (quote_number,customer_name,customer_phone," +
                    "customer_email,notes,valid_days,created_by) VALUES (?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, quoteNum);
                ps.setString(2, customerName);
                ps.setString(3, phone);
                ps.setString(4, email);
                ps.setString(5, notes);
                ps.setInt(6, validDays);
                ps.setInt(7, createdBy);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                int id = keys.next() ? keys.getInt(1) : -1;
                c.commit();
                return id;
            }
            } catch (Exception ex) { c.rollback(); throw ex; }
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    public boolean addItem(int quotationId, Integer productId, String description,
                            double qty, double unitPrice, double discount) {
        double lineTotal = qty * unitPrice * (1 - discount / 100);
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO quotation_items (quotation_id,product_id,description,quantity," +
                "unit_price,discount,line_total) VALUES (?,?,?,?,?,?,?)")) {
            ps.setInt(1, quotationId);
            if (productId != null) ps.setInt(2, productId); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, description);
            ps.setDouble(4, qty);
            ps.setDouble(5, unitPrice);
            ps.setDouble(6, discount);
            ps.setDouble(7, lineTotal);
            ps.executeUpdate();
            recalcTotals(c, quotationId);
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Read ──────────────────────────────────────────────────────────────

    public List<String[]> findAll() {
        List<String[]> list = new ArrayList<>();
        String sql = """
            SELECT id, quote_number, customer_name, customer_phone,
                   total, status, valid_days, created_at,
                   date(created_at, '+' || valid_days || ' days') AS expiry_date
            FROM quotations ORDER BY created_at DESC LIMIT 100
        """;
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(sql);
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("quote_number"),
                    rs.getString("customer_name"),
                    rs.getString("customer_phone") != null ? rs.getString("customer_phone") : "—",
                    String.format("%.2f", rs.getDouble("total")),
                    rs.getString("status"),
                    rs.getString("expiry_date"),
                    rs.getString("created_at")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public List<String[]> getItems(int quotationId) {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM quotation_items WHERE quotation_id=?")) {
            ps.setInt(1, quotationId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("description"),
                    String.format("%.2f", rs.getDouble("quantity")),
                    String.format("%.2f", rs.getDouble("unit_price")),
                    String.format("%.1f", rs.getDouble("discount")),
                    String.format("%.2f", rs.getDouble("line_total")),
                    String.valueOf(rs.getInt("product_id"))
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public String[] getById(int id) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM quotations WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("quote_number"),
                    rs.getString("customer_name"),
                    rs.getString("customer_phone") != null ? rs.getString("customer_phone") : "",
                    rs.getString("customer_email") != null ? rs.getString("customer_email") : "",
                    String.format("%.2f", rs.getDouble("subtotal")),
                    String.format("%.2f", rs.getDouble("tax_amount")),
                    String.format("%.2f", rs.getDouble("total")),
                    rs.getString("status"),
                    rs.getString("notes") != null ? rs.getString("notes") : "",
                    String.valueOf(rs.getInt("valid_days")),
                    rs.getString("created_at")
                };
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // ── Update ────────────────────────────────────────────────────────────

    public boolean updateStatus(int id, String status) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE quotations SET status=?, updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
            ps.setString(1, status);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean deleteItem(int itemId) {
        try (Connection c = conn()) {
            // Get quotation_id first for recalc
            int qid = -1;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT quotation_id FROM quotation_items WHERE id=?")) {
                ps.setInt(1, itemId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) qid = rs.getInt(1);
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM quotation_items WHERE id=?")) {
                ps.setInt(1, itemId);
                ps.executeUpdate();
            }
            if (qid > 0) recalcTotals(c, qid);
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean delete(int id) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM quotations WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Recalculation ─────────────────────────────────────────────────────

    private void recalcTotals(Connection c, int quotationId) throws SQLException {
        double subtotal = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(line_total),0) FROM quotation_items WHERE quotation_id=?")) {
            ps.setInt(1, quotationId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) subtotal = rs.getDouble(1);
        }
        double tax = subtotal * com.kaziflow.services.VATConfig.getInstance().getRate();
        double total = subtotal + tax;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE quotations SET subtotal=?,tax_amount=?,total=?,updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
            ps.setDouble(1, subtotal);
            ps.setDouble(2, tax);
            ps.setDouble(3, total);
            ps.setInt(4, quotationId);
            ps.executeUpdate();
        }
    }

    public int getTotalCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM quotations");
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }
}
