package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.Purchase;
import com.kaziflow.models.PurchaseItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PurchaseDAO {

    private Connection getConn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<Purchase> findAll() {
        List<Purchase> list = new ArrayList<>();
        String sql = """
            SELECT p.*, s.name as supplier_name FROM purchases p
            JOIN suppliers s ON p.supplier_id = s.id
            ORDER BY p.created_at DESC
        """;
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public Purchase save(Purchase purchase) {
        String sql = """
            INSERT INTO purchases (purchase_number, supplier_id, subtotal, vat_amount,
            total_amount, amount_paid, balance, status, payment_method, payment_status,
            due_date, notes, created_by, received_by)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (Connection conn = getConn()) {
            conn.setAutoCommit(false);
            String purchNum = com.kaziflow.services.NumberSequence.next(conn, com.kaziflow.services.NumberSequence.DocType.PURCHASE);   // first statement: takes the write lock
            purchase.setPurchaseNumber(purchNum);
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, purchNum);
                ps.setInt(2, purchase.getSupplierId());
                ps.setDouble(3, purchase.getSubtotal());
                ps.setDouble(4, purchase.getVatAmount());
                ps.setDouble(5, purchase.getTotalAmount());
                ps.setDouble(6, purchase.getAmountPaid());
                ps.setDouble(7, purchase.getTotalAmount() - purchase.getAmountPaid()); // balance
                ps.setString(8, purchase.getStatus() != null ? purchase.getStatus() : "pending");
                ps.setString(9, purchase.getPaymentMethod() != null ? purchase.getPaymentMethod() : "credit");
                ps.setString(10, purchase.getPaymentStatus() != null ? purchase.getPaymentStatus() : "unpaid");
                ps.setObject(11, purchase.getDueDate() != null ? purchase.getDueDate().toString() : null);
                ps.setString(12, purchase.getNotes());
                ps.setInt(13, purchase.getCreatedBy());
                ps.setInt(14, purchase.getReceivedBy());
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) {
                    purchase.setId(keys.getInt(1));
                    for (PurchaseItem item : purchase.getItems()) {
                        savePurchaseItem(purchase.getId(), item, conn);
                        // Stock only increases once goods are actually confirmed received — not
                        // the moment a purchase order is merely created. The status column and
                        // received_by column were already designed for exactly this distinction
                        // (CHECK(status IN ('pending','received','partial'))), but stock was being
                        // added unconditionally regardless of status, inflating on-hand quantity
                        // for orders that hadn't physically arrived yet. If the purchase is created
                        // already marked 'received' (a same-day cash purchase, for example), stock
                        // is added immediately; otherwise markReceived() below handles it later.
                        if ("received".equals(purchase.getStatus())) {
                            addStock(item.getProductId(), item.getQuantity(), conn);
                        }
                    }
                    // Update supplier outstanding balance
                    if ("credit".equals(purchase.getPaymentMethod()) || "unpaid".equals(purchase.getPaymentStatus())) {
                        updateSupplierBalance(purchase.getSupplierId(), purchase.getTotalAmount(), conn);
                    }
                }
                conn.commit();
                return purchase;
            } catch (Exception ex) {
                conn.rollback();
                ex.printStackTrace();
                return null;
            }
        } catch (SQLException e) { e.printStackTrace(); return null; }
    }

    private void savePurchaseItem(int purchaseId, PurchaseItem item, Connection conn) throws SQLException {
        String sql = "INSERT INTO purchase_items (purchase_id, product_id, product_name, quantity, unit_cost, line_total) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, purchaseId);
            ps.setInt(2, item.getProductId());
            ps.setString(3, item.getProductName());
            ps.setDouble(4, item.getQuantity());
            ps.setDouble(5, item.getUnitCost());
            ps.setDouble(6, item.getLineTotal());
            ps.executeUpdate();
        }
    }

    /**
     * Marks a pending (or partial) purchase as received: adds stock for every
     * line item and records who received it. Guarded so it can never be
     * applied twice — the WHERE clause only matches purchases not already
     * 'received', so a double-click or repeat call is a safe no-op rather
     * than double-crediting stock.
     */
    public boolean markReceived(int purchaseId, int receivedByUserId) {
        try (Connection conn = getConn()) {
            conn.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE purchases SET status='received', received_by=? WHERE id=? AND status != 'received'")) {
                    ps.setInt(1, receivedByUserId);
                    ps.setInt(2, purchaseId);
                    updated = ps.executeUpdate();
                }
                if (updated == 0) { conn.rollback(); return false; } // already received, or doesn't exist

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT product_id, quantity FROM purchase_items WHERE purchase_id=?")) {
                    ps.setInt(1, purchaseId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        addStock(rs.getInt("product_id"), rs.getDouble("quantity"), conn);
                    }
                }
                conn.commit();
                return true;
            } catch (Exception ex) {
                conn.rollback();
                ex.printStackTrace();
                return false;
            }
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    private void addStock(int productId, double qty, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE products SET stock_quantity = stock_quantity + ?, updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
            ps.setDouble(1, qty);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    private void updateSupplierBalance(int supplierId, double addBalance, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE suppliers SET outstanding_balance = outstanding_balance + ? WHERE id=?")) {
            ps.setDouble(1, addBalance);
            ps.setInt(2, supplierId);
            ps.executeUpdate();
        }
    }

    public double getTotalOutstanding() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(balance),0) FROM purchases WHERE payment_status != 'paid'");
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    /** For display in the form before saving: the number the next order will get (not reserved; it is assigned on save). */
    public String generatePurchaseNumber() {
        try (Connection conn = getConn()) {
            return com.kaziflow.services.NumberSequence.peek(conn, com.kaziflow.services.NumberSequence.DocType.PURCHASE);
        } catch (SQLException e) {
            e.printStackTrace();
            return "PO-0000";
        }
    }

    private Purchase mapRow(ResultSet rs) throws SQLException {
        Purchase p = new Purchase();
        p.setId(rs.getInt("id"));
        p.setPurchaseNumber(rs.getString("purchase_number"));
        p.setSupplierId(rs.getInt("supplier_id"));
        try { p.setSupplierName(rs.getString("supplier_name")); } catch (Exception ignored) {}
        p.setSubtotal(rs.getDouble("subtotal"));
        p.setVatAmount(rs.getDouble("vat_amount"));
        p.setTotalAmount(rs.getDouble("total_amount"));
        p.setAmountPaid(rs.getDouble("amount_paid"));
        p.setBalance(rs.getDouble("balance"));
        p.setStatus(rs.getString("status"));
        p.setPaymentMethod(rs.getString("payment_method"));
        p.setPaymentStatus(rs.getString("payment_status"));
        p.setNotes(rs.getString("notes"));
        try {
            String ts = rs.getString("created_at");
            if (ts != null) p.setCreatedAt(java.time.LocalDateTime.parse(ts.replace(" ", "T")));
        } catch (Exception ignored) {}
        return p;
    }

    public int getOrderCount() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM purchases");
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    /** Orders created in the current calendar month. */
    public int getMonthOrderCount() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM purchases WHERE strftime('%Y-%m', created_at) = strftime('%Y-%m', 'now', 'localtime')");
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    /** Average order value; null when there are no orders. */
    public Double getAverageOrderValue() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT AVG(total_amount) FROM purchases");
            if (rs.next()) { double v = rs.getDouble(1); return rs.wasNull() ? null : v; }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }
}
