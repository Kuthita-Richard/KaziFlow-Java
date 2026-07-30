package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SaleReturnDAO {

    private Connection getConn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    /**
     * Process a return: creates a sale_return record, restores stock,
     * and marks the original sale as 'refunded' if fully returned.
     * Returns the generated return_number or null on failure.
     */
    public String processReturn(int saleId, String saleNumber, int productId,
                                 String productName, double qty, double unitPrice,
                                 String reason, int processedBy) {
        try (Connection conn = getConn()) {
            conn.setAutoCommit(false);
            try {
                // Generate return number
                ResultSet countRs = conn.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM sale_returns");
                int count = countRs.next() ? countRs.getInt(1) : 0;
                String returnNum = String.format("RET-%04d", count + 1);
                double refundAmount = qty * unitPrice;

                // Insert return record
                String insertSql = """
                    INSERT INTO sale_returns
                    (return_number, sale_id, sale_number, product_id, product_name,
                     quantity, unit_price, refund_amount, reason, processed_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                """;
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, returnNum);
                    ps.setInt(2, saleId);
                    ps.setString(3, saleNumber);
                    ps.setInt(4, productId);
                    ps.setString(5, productName);
                    ps.setDouble(6, qty);
                    ps.setDouble(7, unitPrice);
                    ps.setDouble(8, refundAmount);
                    ps.setString(9, reason);
                    ps.setInt(10, processedBy);
                    ps.executeUpdate();
                }

                // Restore stock
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE products SET stock_quantity = stock_quantity + ? WHERE id = ?")) {
                    ps.setDouble(1, qty);
                    ps.setInt(2, productId);
                    ps.executeUpdate();
                }

                // Create negative transaction entry
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO transactions (reference, description, transaction_type, category, amount, payment_method, transaction_date, created_by) VALUES (?,?,?,?,?,?,date('now'),?)")) {
                    ps.setString(1, returnNum);
                    ps.setString(2, "Return: " + productName + " x" + (int) qty + " (" + saleNumber + ")");
                    ps.setString(3, "expense");
                    ps.setString(4, "Returns");
                    ps.setDouble(5, refundAmount);
                    ps.setString(6, "cash");
                    ps.setInt(7, processedBy);
                    ps.executeUpdate();
                }

                conn.commit();
                return returnNum;
            } catch (Exception ex) {
                conn.rollback();
                ex.printStackTrace();
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Returns all returns for a given sale */
    public List<String[]> getReturnsBySale(int saleId) {
        List<String[]> list = new ArrayList<>();
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM sale_returns WHERE sale_id = ? ORDER BY created_at DESC")) {
            ps.setInt(1, saleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    rs.getString("return_number"),
                    rs.getString("product_name"),
                    String.format("%.0f", rs.getDouble("quantity")),
                    String.format("%.2f", rs.getDouble("refund_amount")),
                    rs.getString("reason"),
                    rs.getString("created_at")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    /** Returns all returns, most recent first */
    public List<String[]> findAll() {
        List<String[]> list = new ArrayList<>();
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT * FROM sale_returns ORDER BY created_at DESC LIMIT 100");
            while (rs.next()) {
                list.add(new String[]{
                    rs.getString("return_number"),
                    rs.getString("sale_number"),
                    rs.getString("product_name"),
                    String.format("%.0f", rs.getDouble("quantity")),
                    String.format("%.2f", rs.getDouble("refund_amount")),
                    rs.getString("reason"),
                    rs.getString("created_at")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public double getTotalRefundsThisMonth() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT COALESCE(SUM(refund_amount),0) FROM sale_returns " +
                "WHERE strftime('%Y-%m',created_at) = strftime('%Y-%m','now')");
            if (rs.next()) return rs.getDouble(1);
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }
}
