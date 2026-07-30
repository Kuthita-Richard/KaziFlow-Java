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
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                String purchNum = generatePurchaseNumber(conn);
                purchase.setPurchaseNumber(purchNum);
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
                        addStock(item.getProductId(), item.getQuantity(), conn);
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

    private String generatePurchaseNumber(Connection conn) throws SQLException {
        ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM purchases");
        int count = rs.next() ? rs.getInt(1) : 0;
        return String.format("PO-%04d", count + 1);
    }

    /** Public, connection-less variant for preview/display in the UI before saving. */
    public String generatePurchaseNumber() {
        try (Connection conn = getConn()) {
            return generatePurchaseNumber(conn);
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
}
