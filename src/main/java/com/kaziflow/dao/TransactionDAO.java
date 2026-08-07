package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.Transaction;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {

    private Connection getConn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<Transaction> findAll() {
        List<Transaction> list = new ArrayList<>();
        String sql = "SELECT t.*, a.name as account_name FROM transactions t LEFT JOIN accounts a ON t.account_id=a.id ORDER BY t.transaction_date DESC LIMIT 200";
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<Transaction> findByDateRange(LocalDate from, LocalDate to) {
        List<Transaction> list = new ArrayList<>();
        String sql = "SELECT t.*, a.name as account_name FROM transactions t LEFT JOIN accounts a ON t.account_id=a.id WHERE t.transaction_date BETWEEN ? AND ? ORDER BY t.transaction_date DESC";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean save(Transaction t) {
        String sql = "INSERT INTO transactions (reference, description, account_id, transaction_type, category, amount, vat_amount, payment_method, notes, created_by, transaction_date) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.getReference());
            ps.setString(2, t.getDescription());
            ps.setInt(3, t.getAccountId());
            ps.setString(4, t.getTransactionType());
            ps.setString(5, t.getCategory());
            ps.setDouble(6, t.getAmount());
            ps.setDouble(7, t.getVatAmount());
            ps.setString(8, t.getPaymentMethod());
            ps.setString(9, t.getNotes());
            ps.setInt(10, t.getCreatedBy());
            ps.setString(11, t.getTransactionDate() != null ? t.getTransactionDate().toString() : LocalDate.now().toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public double getTotalRevenue() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(total_amount),0) FROM sales WHERE status='completed'");
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public double getTotalExpenses() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(amount),0) FROM expenses");
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    /** Real output VAT — the actual sum of vat_amount already charged on completed sales, not an approximation. */
    public double getTotalOutputVAT() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(vat_amount),0) FROM sales WHERE status='completed'");
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    /** Real input VAT — the actual sum of vat_amount already paid on purchases, reclaimable against output VAT. */
    public double getTotalInputVAT() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(vat_amount),0) FROM purchases");
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    /**
     * Net VAT payable = output VAT (charged on sales) minus input VAT (paid on purchases,
     * reclaimable) — standard VAT accounting, and never negative (a net input-VAT credit
     * carries forward rather than becoming a negative liability here).
     */
    public double getNetVATPayable() {
        return Math.max(getTotalOutputVAT() - getTotalInputVAT(), 0);
    }

    public double getMonthRevenue() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(total_amount),0) FROM sales WHERE strftime('%Y-%m',created_at)=strftime('%Y-%m','now') AND status='completed'");
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public double getMonthExpenses() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE strftime('%Y-%m',expense_date)=strftime('%Y-%m','now')");
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    /** Monthly revenue from sales. Key = "YYYY-MM", value = total. Last 6 months. */
    public java.util.LinkedHashMap<String, Double> getMonthlyRevenueBreakdown() {
        java.util.LinkedHashMap<String, Double> map = new java.util.LinkedHashMap<>();
        String sql = "SELECT strftime('%Y-%m', created_at) as m, COALESCE(SUM(total_amount),0) " +
                     "FROM sales WHERE status='completed' GROUP BY m ORDER BY m ASC LIMIT 6";
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery(sql);
            while (rs.next()) map.put(rs.getString("m"), rs.getDouble(2));
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    /** Monthly expenses from expenses table. Key = "YYYY-MM", value = total. Last 6 months. */
    public java.util.LinkedHashMap<String, Double> getMonthlyExpenseBreakdown() {
        java.util.LinkedHashMap<String, Double> map = new java.util.LinkedHashMap<>();
        String sql = "SELECT strftime('%Y-%m', expense_date) as m, COALESCE(SUM(amount),0) " +
                     "FROM expenses GROUP BY m ORDER BY m ASC LIMIT 6";
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery(sql);
            while (rs.next()) map.put(rs.getString("m"), rs.getDouble(2));
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    /** Revenue for a specific year+month (1-12). */
    public double getRevenueForMonth(int year, int month) {
        String ym = String.format("%04d-%02d", year, month);
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(total_amount),0) FROM sales WHERE strftime('%Y-%m',created_at)=? AND status='completed'")) {
            ps.setString(1, ym);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    /** Expenses for a specific year+month (1-12). */
    public double getExpensesForMonth(int year, int month) {
        String ym = String.format("%04d-%02d", year, month);
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(amount),0) FROM expenses WHERE strftime('%Y-%m',expense_date)=?")) {
            ps.setString(1, ym);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private Transaction mapRow(ResultSet rs) throws SQLException {
        Transaction t = new Transaction();
        t.setId(rs.getInt("id"));
        t.setReference(rs.getString("reference"));
        t.setDescription(rs.getString("description"));
        t.setAccountId(rs.getInt("account_id"));
        t.setAccountName(rs.getString("account_name"));
        t.setTransactionType(rs.getString("transaction_type"));
        t.setCategory(rs.getString("category"));
        t.setAmount(rs.getDouble("amount"));
        t.setVatAmount(rs.getDouble("vat_amount"));
        t.setPaymentMethod(rs.getString("payment_method"));
        t.setNotes(rs.getString("notes"));
        try { t.setTransactionDate(LocalDate.parse(rs.getString("transaction_date"))); } catch (Exception ignored) {}
        return t;
    }

    /**
     * Per-day income vs. expense totals within [from, to] — powers the dashboard's
     * cash-flow chart (Phase 10C). Keyed by "YYYY-MM-DD", each value is {income, expense}.
     */
    public java.util.LinkedHashMap<String, double[]> getDailyCashFlowForRange(LocalDate from, LocalDate to) {
        java.util.LinkedHashMap<String, double[]> map = new java.util.LinkedHashMap<>();
        String sql = """
            SELECT transaction_date as d, transaction_type,
                   SUM(amount) as total
            FROM transactions
            WHERE transaction_date BETWEEN ? AND ? AND transaction_type IN ('income','expense')
            GROUP BY d, transaction_type
            ORDER BY d ASC
        """;
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String d = rs.getString("d");
                double[] pair = map.computeIfAbsent(d, k -> new double[]{0, 0});
                if ("income".equals(rs.getString("transaction_type"))) pair[0] = rs.getDouble("total");
                else pair[1] = rs.getDouble("total");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }
}
