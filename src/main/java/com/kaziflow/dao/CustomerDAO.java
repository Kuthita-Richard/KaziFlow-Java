package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CustomerDAO {

    private Connection getConn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<String[]> findAll() {
        List<String[]> list = new ArrayList<>();
        String sql = """
            SELECT c.*, COUNT(s.id) as sale_count, COALESCE(SUM(s.total_amount),0) as total_spent
            FROM customers c
            LEFT JOIN sales s ON s.customer_id = c.id
            GROUP BY c.id ORDER BY total_spent DESC
        """;
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                int points = 0;
                try { points = rs.getInt("loyalty_points"); } catch (SQLException ignored) {} // column may not exist yet on a fresh DB before ensureLoyaltyColumn() runs
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("name"),
                    rs.getString("phone") != null ? rs.getString("phone") : "—",
                    rs.getString("email") != null ? rs.getString("email") : "—",
                    String.valueOf(rs.getInt("sale_count")),
                    String.format("%.2f", rs.getDouble("total_spent")),
                    rs.getString("created_at"),
                    String.valueOf(points)
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<String[]> search(String query) {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT * FROM customers WHERE name LIKE ? OR phone LIKE ? OR email LIKE ? ORDER BY name";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            String q = "%" + query + "%";
            ps.setString(1, q); ps.setString(2, q); ps.setString(3, q);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("name"),
                    rs.getString("phone") != null ? rs.getString("phone") : "—",
                    rs.getString("email") != null ? rs.getString("email") : "—",
                    "0", "0", rs.getString("created_at")
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean save(String name, String phone, String email, String address) {
        String sql = "INSERT INTO customers (name, phone, email, address) VALUES (?,?,?,?)";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setString(3, email);
            ps.setString(4, address);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean update(int id, String name, String phone, String email, String address) {
        String sql = "UPDATE customers SET name=?, phone=?, email=?, address=? WHERE id=?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name); ps.setString(2, phone);
            ps.setString(3, email); ps.setString(4, address);
            ps.setInt(5, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean delete(int id) {
        // Only delete if customer has no sales
        try (Connection conn = getConn();
             PreparedStatement check = conn.prepareStatement("SELECT COUNT(*) FROM sales WHERE customer_id=?")) {
            check.setInt(1, id);
            ResultSet rs = check.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) return false; // has sales, can't delete
        } catch (SQLException e) { e.printStackTrace(); return false; }

        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM customers WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public int getTotalCount() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM customers");
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    /** Returns top customers by spend, for dashboard/reports */
    public List<String[]> getTopCustomers(int limit) {
        List<String[]> list = new ArrayList<>();
        String sql = """
            SELECT c.name, c.phone, COUNT(s.id) as sales, COALESCE(SUM(s.total_amount),0) as total
            FROM customers c JOIN sales s ON s.customer_id = c.id
            GROUP BY c.id ORDER BY total DESC LIMIT ?
        """;
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    rs.getString("name"), rs.getString("phone"),
                    String.valueOf(rs.getInt("sales")),
                    String.format("%.2f", rs.getDouble("total"))
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    /** Seed demo customers if table is empty */
    public void seedIfEmpty() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM customers");
            if (rs.next() && rs.getInt(1) == 0) {
                s.execute("""
                    INSERT INTO customers (name, phone, email, address) VALUES
                    ('Kamau Njoroge', '+254712345001', 'kamau@gmail.com', 'Westlands, Nairobi'),
                    ('Wanjiru Mwangi', '+254712345002', 'wanjiru@outlook.com', 'Kasarani, Nairobi'),
                    ('Otieno Builders Ltd', '+254722345003', 'otieno@builders.co.ke', 'Embakasi, Nairobi'),
                    ('Fatuma Hassan', '+254733345004', '', 'Mombasa Road'),
                    ('John Kariuki', '+254744345005', 'jkariuki@yahoo.com', 'Thika Road')
                """);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ────────────────────────────────────────────────────────────────────
    // Loyalty points (Phase 10E)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Adds the loyalty_points column if it doesn't exist yet. SQLite has no
     * "ADD COLUMN IF NOT EXISTS", so this relies on catching the "duplicate
     * column" error on repeat runs — cheap and safe since ALTER TABLE ADD COLUMN
     * is idempotent in effect even though not in syntax.
     */
    public void ensureLoyaltyColumn() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            s.execute("ALTER TABLE customers ADD COLUMN loyalty_points INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            // Column already exists — expected on every run after the first. Anything else, surface it.
            if (e.getMessage() == null || !e.getMessage().toLowerCase().contains("duplicate column")) {
                e.printStackTrace();
            }
        }
    }

    public int getLoyaltyPoints(int customerId) {
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(
                "SELECT loyalty_points FROM customers WHERE id=?")) {
            ps.setInt(1, customerId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("loyalty_points");
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    /** Earns points on a completed sale — call with the actual amount paid (post-redemption), not the pre-discount total. */
    public boolean addLoyaltyPoints(int customerId, int points) {
        if (points <= 0) return true; // nothing to add is not an error
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE customers SET loyalty_points = loyalty_points + ? WHERE id=?")) {
            ps.setInt(1, points);
            ps.setInt(2, customerId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /**
     * Redeems points, but only ever down to zero — guarded with a WHERE clause
     * rather than trusting the caller's balance check, so a stale UI can't push
     * a customer's points negative via a race or a missed refresh.
     */
    public boolean redeemLoyaltyPoints(int customerId, int points) {
        if (points <= 0) return true;
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE customers SET loyalty_points = loyalty_points - ? WHERE id=? AND loyalty_points >= ?")) {
            ps.setInt(1, points);
            ps.setInt(2, customerId);
            ps.setInt(3, points);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }
}
