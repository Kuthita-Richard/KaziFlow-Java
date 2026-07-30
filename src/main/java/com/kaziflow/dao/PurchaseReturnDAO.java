package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * PurchaseReturnDAO — return goods back to supplier.
 * Deducts stock and adjusts supplier payable balance.
 */
public class PurchaseReturnDAO {

    private Connection conn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS purchase_returns (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    return_number   TEXT NOT NULL UNIQUE,
                    purchase_id     INTEGER REFERENCES purchases(id),
                    supplier_id     INTEGER REFERENCES suppliers(id),
                    supplier_name   TEXT NOT NULL,
                    product_id      INTEGER REFERENCES products(id),
                    product_name    TEXT NOT NULL,
                    quantity        REAL NOT NULL,
                    unit_cost       REAL NOT NULL,
                    total_credit    REAL NOT NULL,
                    reason          TEXT,
                    status          TEXT DEFAULT 'pending'
                                        CHECK(status IN ('pending','approved','credited')),
                    created_by      INTEGER REFERENCES users(id),
                    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public String processReturn(int supplierId, String supplierName,
                                 int productId, String productName,
                                 double qty, double unitCost,
                                 String reason, int createdBy) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                // Check sufficient stock
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT stock_quantity FROM products WHERE id=?")) {
                    ps.setInt(1, productId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next() && rs.getDouble(1) < qty) {
                        c.rollback();
                        return "INSUFFICIENT_STOCK";
                    }
                }

                // Generate return number
                int count = 0;
                try (Statement s = c.createStatement()) {
                    ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM purchase_returns");
                    if (rs.next()) count = rs.getInt(1);
                }
                String retNum = String.format("PR-%04d", count + 1);
                double totalCredit = qty * unitCost;

                // Insert return record
                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO purchase_returns
                    (return_number,supplier_id,supplier_name,product_id,product_name,
                     quantity,unit_cost,total_credit,reason,created_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                """)) {
                    ps.setString(1, retNum);
                    ps.setInt(2, supplierId);    ps.setString(3, supplierName);
                    ps.setInt(4, productId);     ps.setString(5, productName);
                    ps.setDouble(6, qty);        ps.setDouble(7, unitCost);
                    ps.setDouble(8, totalCredit); ps.setString(9, reason);
                    ps.setInt(10, createdBy);
                    ps.executeUpdate();
                }

                // Deduct stock
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE products SET stock_quantity=stock_quantity-? WHERE id=?")) {
                    ps.setDouble(1, qty); ps.setInt(2, productId);
                    ps.executeUpdate();
                }

                // Credit to supplier balance (reduce what we owe them)
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE suppliers SET balance=balance-? WHERE id=?")) {
                    ps.setDouble(1, totalCredit); ps.setInt(2, supplierId);
                    ps.executeUpdate();
                }

                c.commit();
                return retNum;
            } catch (Exception ex) {
                c.rollback(); ex.printStackTrace(); return null;
            }
        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    public List<String[]> findAll() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("""
                SELECT id, return_number, supplier_name, product_name,
                       quantity, unit_cost, total_credit, reason, status, created_at
                FROM purchase_returns ORDER BY created_at DESC LIMIT 100
            """);
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("return_number"),
                    rs.getString("supplier_name"),
                    rs.getString("product_name"),
                    String.format("%.0f", rs.getDouble("quantity")),
                    String.format("%.2f", rs.getDouble("unit_cost")),
                    String.format("%.2f", rs.getDouble("total_credit")),
                    rs.getString("reason") != null ? rs.getString("reason") : "—",
                    rs.getString("status"),
                    rs.getString("created_at")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public double getTotalCreditThisMonth() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT COALESCE(SUM(total_credit),0) FROM purchase_returns " +
                "WHERE strftime('%Y-%m',created_at)=strftime('%Y-%m','now')");
            if (rs.next()) return rs.getDouble(1);
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }
}
