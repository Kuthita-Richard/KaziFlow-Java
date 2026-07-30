package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductDAO {

    private Connection getConn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<Product> findAll() {
        List<Product> list = new ArrayList<>();
        String sql = """
            SELECT p.*, c.name as category_name, s.name as supplier_name
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN suppliers s ON p.supplier_id = s.id
            ORDER BY p.name
        """;
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<Product> search(String query) {
        List<Product> list = new ArrayList<>();
        String sql = """
            SELECT p.*, c.name as category_name, s.name as supplier_name
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN suppliers s ON p.supplier_id = s.id
            WHERE p.name LIKE ? OR p.sku LIKE ? OR p.barcode LIKE ?
            ORDER BY p.name
        """;
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            String q = "%" + query + "%";
            ps.setString(1, q); ps.setString(2, q); ps.setString(3, q);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<Product> findLowStock() {
        List<Product> list = new ArrayList<>();
        String sql = """
            SELECT p.*, c.name as category_name, s.name as supplier_name
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN suppliers s ON p.supplier_id = s.id
            WHERE p.stock_quantity <= p.min_stock_level AND p.status = 'active'
            ORDER BY p.stock_quantity ASC
        """;
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean save(Product p) {
        String sql = """
            INSERT INTO products (sku, name, description, category_id, supplier_id,
            selling_price, cost_price, stock_quantity, min_stock_level, unit, barcode, status)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getSku());
            ps.setString(2, p.getName());
            ps.setString(3, p.getDescription());
            ps.setInt(4, p.getCategoryId());
            ps.setInt(5, p.getSupplierId());
            ps.setDouble(6, p.getSellingPrice());
            ps.setDouble(7, p.getCostPrice());
            ps.setDouble(8, p.getStockQuantity());
            ps.setDouble(9, p.getMinStockLevel());
            ps.setString(10, p.getUnit() != null ? p.getUnit() : "pcs");
            ps.setString(11, p.getBarcode());
            ps.setString(12, p.getStatus() != null ? p.getStatus() : "active");
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean update(Product p) {
        String sql = """
            UPDATE products SET name=?, description=?, category_id=?, supplier_id=?,
            selling_price=?, cost_price=?, stock_quantity=?, min_stock_level=?,
            unit=?, barcode=?, status=?, updated_at=CURRENT_TIMESTAMP WHERE id=?
        """;
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getName());
            ps.setString(2, p.getDescription());
            ps.setInt(3, p.getCategoryId());
            ps.setInt(4, p.getSupplierId());
            ps.setDouble(5, p.getSellingPrice());
            ps.setDouble(6, p.getCostPrice());
            ps.setDouble(7, p.getStockQuantity());
            ps.setDouble(8, p.getMinStockLevel());
            ps.setString(9, p.getUnit());
            ps.setString(10, p.getBarcode());
            ps.setString(11, p.getStatus());
            ps.setInt(12, p.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean updateStock(int productId, double newQty) {
        String sql = "UPDATE products SET stock_quantity=?, updated_at=CURRENT_TIMESTAMP WHERE id=?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newQty);
            ps.setInt(2, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean delete(int id) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement("UPDATE products SET status='inactive' WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public int getTotalCount() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM products WHERE status='active'");
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public double getTotalStockValue() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT SUM(selling_price * stock_quantity) FROM products WHERE status='active'");
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public int getLowStockCount() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM products WHERE stock_quantity <= min_stock_level AND status='active'");
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private Product mapRow(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getInt("id"));
        p.setSku(rs.getString("sku"));
        p.setName(rs.getString("name"));
        p.setDescription(rs.getString("description"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setCategoryName(rs.getString("category_name"));
        p.setSupplierId(rs.getInt("supplier_id"));
        p.setSupplierName(rs.getString("supplier_name"));
        p.setSellingPrice(rs.getDouble("selling_price"));
        p.setCostPrice(rs.getDouble("cost_price"));
        p.setStockQuantity(rs.getDouble("stock_quantity"));
        p.setMinStockLevel(rs.getDouble("min_stock_level"));
        p.setUnit(rs.getString("unit"));
        p.setBarcode(rs.getString("barcode"));
        p.setStatus(rs.getString("status"));
        return p;
    }
}
