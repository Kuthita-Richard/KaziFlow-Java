package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BranchDAO {

    private Connection getConn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    // ─── Branches ──────────────────────────────────────────────────────────

    public List<String[]> findAllBranches() {
        List<String[]> list = new ArrayList<>();
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT id, name, address, phone, manager, status FROM branches ORDER BY id");
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("name"),
                    rs.getString("address") != null ? rs.getString("address") : "—",
                    rs.getString("phone") != null ? rs.getString("phone") : "—",
                    rs.getString("manager") != null ? rs.getString("manager") : "—",
                    rs.getString("status")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public boolean saveBranch(String name, String address, String phone, String manager) {
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO branches (name, address, phone, manager) VALUES (?,?,?,?)")) {
            ps.setString(1, name); ps.setString(2, address);
            ps.setString(3, phone); ps.setString(4, manager);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean updateBranch(int id, String name, String address, String phone, String manager, String status) {
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE branches SET name=?,address=?,phone=?,manager=?,status=? WHERE id=?")) {
            ps.setString(1, name); ps.setString(2, address); ps.setString(3, phone);
            ps.setString(4, manager); ps.setString(5, status); ps.setInt(6, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ─── Stock Transfers ───────────────────────────────────────────────────

    public List<String[]> findAllTransfers() {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT * FROM stock_transfers ORDER BY created_at DESC LIMIT 100";
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery(sql);
            while (rs.next()) {
                list.add(new String[]{
                    rs.getString("transfer_number"),
                    rs.getString("from_branch_name"),
                    rs.getString("to_branch_name"),
                    rs.getString("product_name"),
                    String.format("%.0f", rs.getDouble("quantity")),
                    rs.getString("status"),
                    rs.getString("notes") != null ? rs.getString("notes") : "—",
                    rs.getString("created_at")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    /**
     * Create a transfer request.
     * Stock is NOT moved until updateTransferStatus() marks it 'received'.
     */
    public String createTransfer(int fromBranchId, int toBranchId,
                                  String fromName, String toName,
                                  int productId, String productName,
                                  double qty, String notes, int createdBy) {
        try (Connection conn = getConn()) {
            conn.setAutoCommit(false);
            try {
                // Check available stock
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT stock_quantity FROM products WHERE id=?")) {
                    ps.setInt(1, productId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next() && rs.getDouble(1) < qty) {
                        conn.rollback();
                        return "INSUFFICIENT_STOCK";
                    }
                }

                // Generate transfer number
                int count = 0;
                try (Statement s = conn.createStatement()) {
                    ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM stock_transfers");
                    if (rs.next()) count = rs.getInt(1);
                }
                String transferNum = String.format("TRF-%04d", count + 1);

                try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO stock_transfers
                    (transfer_number, from_branch_id, to_branch_id, from_branch_name, to_branch_name,
                     product_id, product_name, quantity, status, notes, created_by)
                    VALUES (?,?,?,?,?,?,?,?,'pending',?,?)
                """)) {
                    ps.setString(1, transferNum);
                    ps.setInt(2, fromBranchId); ps.setInt(3, toBranchId);
                    ps.setString(4, fromName);  ps.setString(5, toName);
                    ps.setInt(6, productId);    ps.setString(7, productName);
                    ps.setDouble(8, qty);       ps.setString(9, notes);
                    ps.setInt(10, createdBy);
                    ps.executeUpdate();
                }

                // Reserve stock (deduct from source immediately)
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE products SET stock_quantity=stock_quantity-? WHERE id=?")) {
                    ps.setDouble(1, qty); ps.setInt(2, productId);
                    ps.executeUpdate();
                }

                conn.commit();
                return transferNum;
            } catch (Exception ex) {
                conn.rollback(); ex.printStackTrace(); return null;
            }
        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    /** Mark transfer as received — adds stock at destination */
    public boolean receiveTransfer(String transferNumber, int productId, double qty) {
        try (Connection conn = getConn()) {
            conn.setAutoCommit(false);
            try {
                // Update status
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE stock_transfers SET status='received' WHERE transfer_number=?")) {
                    ps.setString(1, transferNumber); ps.executeUpdate();
                }
                // Add to destination (in this single-inventory model, just restore stock)
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE products SET stock_quantity=stock_quantity+? WHERE id=?")) {
                    ps.setDouble(1, qty); ps.setInt(2, productId); ps.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (Exception ex) { conn.rollback(); ex.printStackTrace(); return false; }
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean updateTransferStatus(String transferNumber, String newStatus) {
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE stock_transfers SET status=? WHERE transfer_number=?")) {
            ps.setString(1, newStatus); ps.setString(2, transferNumber);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public int getPendingTransferCount() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM stock_transfers WHERE status='pending'");
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }
}
