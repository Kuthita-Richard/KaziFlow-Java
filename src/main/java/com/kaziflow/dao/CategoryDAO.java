package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {

    private Connection getConn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    /** Returns list of [id, name] pairs */
    public List<String[]> findAll() {
        List<String[]> list = new ArrayList<>();
        try (Connection conn = getConn();
             ResultSet rs = conn.createStatement().executeQuery("SELECT id, name FROM categories ORDER BY name")) {
            while (rs.next()) list.add(new String[]{rs.getString("id"), rs.getString("name")});
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<String> findNames() {
        List<String> names = new ArrayList<>();
        try (Connection conn = getConn();
             ResultSet rs = conn.createStatement().executeQuery("SELECT name FROM categories ORDER BY name")) {
            while (rs.next()) names.add(rs.getString("name"));
        } catch (SQLException e) { e.printStackTrace(); }
        return names;
    }

    public boolean save(String name) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO categories (name) VALUES (?)")) {
            ps.setString(1, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean delete(int id) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM categories WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public int getIdByName(String name) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM categories WHERE name=?")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) { e.printStackTrace(); }
        return 1; // default
    }
}
