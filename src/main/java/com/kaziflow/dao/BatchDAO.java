package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * BatchDAO — manages batch/lot tracking for products with expiry dates.
 *
 * Applies to: Pharmacy, Chemist, Agrovet, Grocery, Liquor, Butchery.
 *
 * FEFO (First-Expiry-First-Out):
 *   When dispensing stock, BatchDAO.getFefoOrder() returns batches sorted
 *   by expiry_date ASC so the earliest-expiring stock is consumed first.
 *
 * Expiry workflow:
 *   1. Stock arrives → addBatch() creates a batch record
 *   2. Sale processed → dispenseBatch() reduces batch.remaining
 *   3. Expiry alerts → getExpiringSoon(days) returns batches expiring within N days
 *   4. Expired batches → auto-marked 'expired' by updateExpiredBatches()
 */
public class BatchDAO {

    private Connection conn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    // ── Add / Update ──────────────────────────────────────────────────────

    /**
     * Add a new batch for a product.
     * Also increases product.stock_quantity by the batch quantity.
     */
    public int addBatch(int productId, String batchNumber, String lotNumber,
                         double quantity, double costPrice,
                         String manufactureDate, String expiryDate,
                         Integer supplierId, String notes, int createdBy) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                // Validate expiry date
                LocalDate expiry = LocalDate.parse(expiryDate);
                if (expiry.isBefore(LocalDate.now())) {
                    c.rollback();
                    return -2; // -2 = already expired
                }

                // Insert batch
                int batchId;
                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO batches
                    (product_id,batch_number,lot_number,quantity,remaining,cost_price,
                     manufacture_date,expiry_date,supplier_id,notes,created_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, productId);
                    ps.setString(2, batchNumber.trim().toUpperCase());
                    ps.setString(3, lotNumber != null ? lotNumber.trim() : null);
                    ps.setDouble(4, quantity);
                    ps.setDouble(5, quantity); // remaining starts equal to quantity
                    ps.setDouble(6, costPrice);
                    ps.setString(7, manufactureDate.isBlank() ? null : manufactureDate);
                    ps.setString(8, expiryDate);
                    if (supplierId != null) ps.setInt(9, supplierId);
                    else ps.setNull(9, Types.INTEGER);
                    ps.setString(10, notes);
                    ps.setInt(11, createdBy);
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    batchId = keys.next() ? keys.getInt(1) : -1;
                }

                // Increase product stock
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE products SET stock_quantity=stock_quantity+? WHERE id=?")) {
                    ps.setDouble(1, quantity);
                    ps.setInt(2, productId);
                    ps.executeUpdate();
                }

                c.commit();
                return batchId;
            } catch (Exception ex) {
                c.rollback();
                ex.printStackTrace();
                return -1;
            }
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    /**
     * Dispense quantity from a product's batches using FEFO order.
     * Returns number of batches touched, or -1 if insufficient stock.
     */
    public int dispenseFefo(int productId, double quantityNeeded) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                List<int[]> batches = new ArrayList<>(); // [id, remaining_as_int_check]
                double available = 0;

                // Get batches in FEFO order (earliest expiry first)
                try (PreparedStatement ps = c.prepareStatement("""
                    SELECT id, remaining FROM batches
                    WHERE product_id=? AND status='active' AND remaining > 0
                    ORDER BY expiry_date ASC
                """)) {
                    ps.setInt(1, productId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        batches.add(new int[]{rs.getInt("id"),
                            (int)(rs.getDouble("remaining") * 100)}); // store as cents
                        available += rs.getDouble("remaining");
                    }
                }

                if (available < quantityNeeded) {
                    c.rollback();
                    return -1; // insufficient stock
                }

                double remaining = quantityNeeded;
                int batchesTouched = 0;
                for (int[] batch : batches) {
                    if (remaining <= 0) break;
                    double batchRemaining = batch[1] / 100.0;
                    double take = Math.min(remaining, batchRemaining);
                    double newRemaining = batchRemaining - take;

                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE batches SET remaining=?, status=CASE WHEN ?<=0 THEN 'depleted' ELSE 'active' END WHERE id=?")) {
                        ps.setDouble(1, newRemaining);
                        ps.setDouble(2, newRemaining);
                        ps.setInt(3, batch[0]);
                        ps.executeUpdate();
                    }
                    remaining -= take;
                    batchesTouched++;
                }

                c.commit();
                return batchesTouched;
            } catch (Exception ex) {
                c.rollback(); ex.printStackTrace(); return -1;
            }
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    // ── Queries ───────────────────────────────────────────────────────────

    /** All batches for a product, FEFO order */
    public List<String[]> getBatchesForProduct(int productId) {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("""
            SELECT b.id, b.batch_number, b.lot_number, b.quantity, b.remaining,
                   b.cost_price, b.manufacture_date, b.expiry_date, b.status,
                   b.notes, b.created_at,
                   CAST(julianday(b.expiry_date) - julianday('now') AS INTEGER) as days_left
            FROM batches b
            WHERE b.product_id=?
            ORDER BY b.expiry_date ASC
        """)) {
            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("batch_number"),
                    rs.getString("lot_number") != null ? rs.getString("lot_number") : "—",
                    String.format("%.0f", rs.getDouble("quantity")),
                    String.format("%.0f", rs.getDouble("remaining")),
                    String.format("%.2f", rs.getDouble("cost_price")),
                    rs.getString("manufacture_date") != null ? rs.getString("manufacture_date") : "—",
                    rs.getString("expiry_date"),
                    rs.getString("status"),
                    rs.getString("notes") != null ? rs.getString("notes") : "—",
                    String.valueOf(rs.getInt("days_left")) + " days"
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    /** Products with batches expiring within N days — for alerts */
    public List<String[]> getExpiringSoon(int days) {
        List<String[]> list = new ArrayList<>();
        String sql = """
            SELECT p.id as product_id, p.name as product_name, p.sku,
                   b.id as batch_id, b.batch_number, b.remaining,
                   b.expiry_date,
                   CAST(julianday(b.expiry_date) - julianday('now') AS INTEGER) as days_left
            FROM batches b
            JOIN products p ON p.id = b.product_id
            WHERE b.status = 'active'
              AND b.remaining > 0
              AND b.expiry_date <= date('now', '+' || ? || ' days')
              AND b.expiry_date >= date('now')
            ORDER BY b.expiry_date ASC
        """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, days);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("product_id")),
                    rs.getString("product_name"),
                    rs.getString("sku"),
                    String.valueOf(rs.getInt("batch_id")),
                    rs.getString("batch_number"),
                    String.format("%.0f", rs.getDouble("remaining")),
                    rs.getString("expiry_date"),
                    String.valueOf(rs.getInt("days_left"))
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    /** All batches across all products — for the Batches management view */
    public List<String[]> findAll(String statusFilter) {
        List<String[]> list = new ArrayList<>();
        String where = statusFilter != null && !statusFilter.equals("all")
            ? "WHERE b.status='" + statusFilter + "'" : "";
        String sql = """
            SELECT b.id, p.id as product_id, p.name as product_name, p.sku, b.batch_number,
                   b.lot_number, b.quantity, b.remaining, b.cost_price,
                   b.expiry_date, b.status,
                   CAST(julianday(b.expiry_date)-julianday('now') AS INTEGER) as days_left
            FROM batches b
            JOIN products p ON p.id = b.product_id
            """ + where + " ORDER BY b.expiry_date ASC LIMIT 200";
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(sql);
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),            // [0] batch id
                    String.valueOf(rs.getInt("product_id")),    // [1] product id
                    rs.getString("product_name"),               // [2]
                    rs.getString("sku"),                        // [3]
                    rs.getString("batch_number"),               // [4]
                    rs.getString("lot_number") != null ? rs.getString("lot_number") : "—", // [5]
                    String.format("%.0f", rs.getDouble("quantity")),     // [6]
                    String.format("%.0f", rs.getDouble("remaining")),    // [7]
                    String.format("%.2f", rs.getDouble("cost_price")),   // [8]
                    rs.getString("expiry_date"),                // [9]
                    rs.getString("status"),                     // [10]
                    rs.getString("days_left")                   // [11]
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    /** Mark expired batches automatically */
    public int updateExpiredBatches() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            return s.executeUpdate("""
                UPDATE batches SET status='expired'
                WHERE status='active'
                  AND expiry_date < date('now')
            """);
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    public int getExpiringSoonCount(int days) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("""
            SELECT COUNT(DISTINCT product_id) FROM batches
            WHERE status='active' AND remaining>0
              AND expiry_date <= date('now','+'||?||' days')
              AND expiry_date >= date('now')
        """)) {
            ps.setInt(1, days);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    public int getExpiredCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT COUNT(*) FROM batches WHERE status='expired' AND remaining>0");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    public int getAlertDays() {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT value FROM settings WHERE key='expiry_alert_days'")) {
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Integer.parseInt(rs.getString("value")) : 30;
        } catch (Exception e) { return 30; }
    }

    public void setAlertDays(int days) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT OR REPLACE INTO settings(key,value) VALUES('expiry_alert_days',?)")) {
            ps.setString(1, String.valueOf(days));
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public boolean deleteBatch(int id) {
        try (Connection c = conn()) {
            // Get batch details for stock reversal
            double remaining = 0;
            int productId = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT product_id, remaining FROM batches WHERE id=?")) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    productId = rs.getInt("product_id");
                    remaining = rs.getDouble("remaining");
                }
            }
            c.setAutoCommit(false);
            // Reverse stock
            if (productId > 0 && remaining > 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE products SET stock_quantity=stock_quantity-? WHERE id=?")) {
                    ps.setDouble(1, remaining); ps.setInt(2, productId); ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM batches WHERE id=?")) {
                ps.setInt(1, id); ps.executeUpdate();
            }
            c.commit();
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }
}
