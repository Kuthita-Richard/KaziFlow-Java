package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.Expense;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ExpenseDAO {

    private Connection getConn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<Expense> findAll() {
        List<Expense> list = new ArrayList<>();
        String sql = "SELECT * FROM expenses ORDER BY expense_date DESC LIMIT 200";
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<Expense> findByMonth(int year, int month) {
        List<Expense> list = new ArrayList<>();
        String sql = "SELECT * FROM expenses WHERE strftime('%Y', expense_date)=? AND strftime('%m', expense_date)=? ORDER BY expense_date DESC";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(year));
            ps.setString(2, String.format("%02d", month));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean save(Expense e) {
        String sql = "INSERT INTO expenses (description, category, amount, payment_method, receipt_number, notes, created_by, expense_date) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e.getDescription());
            ps.setString(2, e.getCategory());
            ps.setDouble(3, e.getAmount());
            ps.setString(4, e.getPaymentMethod() != null ? e.getPaymentMethod() : "cash");
            ps.setString(5, e.getReceiptNumber());
            ps.setString(6, e.getNotes());
            ps.setInt(7, e.getCreatedBy());
            ps.setString(8, e.getExpenseDate() != null ? e.getExpenseDate().toString() : LocalDate.now().toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) { ex.printStackTrace(); return false; }
    }

    public boolean delete(int id) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM expenses WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public double getMonthTotal() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE strftime('%Y-%m', expense_date) = strftime('%Y-%m', 'now')");
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public double getTotal() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(amount),0) FROM expenses");
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private Expense mapRow(ResultSet rs) throws SQLException {
        Expense e = new Expense();
        e.setId(rs.getInt("id"));
        e.setDescription(rs.getString("description"));
        e.setCategory(rs.getString("category"));
        e.setAmount(rs.getDouble("amount"));
        e.setPaymentMethod(rs.getString("payment_method"));
        e.setReceiptNumber(rs.getString("receipt_number"));
        e.setNotes(rs.getString("notes"));
        e.setCreatedBy(rs.getInt("created_by"));
        try { e.setExpenseDate(LocalDate.parse(rs.getString("expense_date"))); } catch (Exception ignored) {}
        return e;
    }
}
