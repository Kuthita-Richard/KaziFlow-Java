package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.Sale;
import com.kaziflow.models.SaleItem;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SaleDAO {

    private Connection getConn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<Sale> findAll() {
        List<Sale> list = new ArrayList<>();
        String sql = """
            SELECT s.*, u.name as served_by_name FROM sales s
            LEFT JOIN users u ON s.served_by = u.id
            ORDER BY s.created_at DESC LIMIT 100
        """;
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public Sale findById(int id) {
        String sql = "SELECT s.*, u.name as served_by_name FROM sales s LEFT JOIN users u ON s.served_by=u.id WHERE s.id=?";
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Sale sale = mapRow(rs);
                sale.setItems(findItems(id, conn));
                return sale;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public Sale save(Sale sale) {
        String sql = """
            INSERT INTO sales (sale_number, customer_id, customer_name, subtotal, discount_amount,
            vat_amount, total_amount, amount_paid, change_amount, payment_method, mpesa_ref, status, served_by, delivery_charge)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (Connection conn = getConn()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                String saleNum = generateSaleNumber(conn);
                sale.setSaleNumber(saleNum);
                ps.setString(1, saleNum);
                ps.setObject(2, sale.getCustomerId());
                ps.setString(3, sale.getCustomerName() != null ? sale.getCustomerName() : "Walk-in Customer");
                ps.setDouble(4, sale.getSubtotal());
                ps.setDouble(5, sale.getDiscountAmount());
                ps.setDouble(6, sale.getVatAmount());
                ps.setDouble(7, sale.getTotalAmount());
                ps.setDouble(8, sale.getAmountPaid());
                ps.setDouble(9, sale.getChangeAmount());
                ps.setString(10, sale.getPaymentMethod() != null ? sale.getPaymentMethod() : "cash");
                ps.setString(11, sale.getMpesaRef());
                ps.setString(12, "completed");
                ps.setInt(13, sale.getServedBy());
                ps.setDouble(14, sale.getDeliveryCharge());
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) {
                    sale.setId(keys.getInt(1));
                    // Insert items
                    for (SaleItem item : sale.getItems()) {
                        saveSaleItem(sale.getId(), item, conn);
                        // Reduce stock
                        updateStock(item.getProductId(), -item.getQuantity(), conn);
                    }
                }
                conn.commit();
                sale.setCreatedAt(java.time.LocalDateTime.now());
                // Generate ETIMS serial asynchronously (non-blocking)
                final int finalId = sale.getId();
                final String finalNum = sale.getSaleNumber();
                new Thread(() ->
                    com.kaziflow.services.ETIMSService.getInstance().generateSerial(finalId, finalNum)
                ).start();
                return sale;
            } catch (Exception ex) {
                conn.rollback();
                ex.printStackTrace();
                return null;
            }
        } catch (SQLException e) { e.printStackTrace(); return null; }
    }

    private void saveSaleItem(int saleId, SaleItem item, Connection conn) throws SQLException {
        String sql = "INSERT INTO sale_items (sale_id, product_id, product_name, quantity, unit_price, cost_price, discount, line_total) VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, saleId);
            ps.setInt(2, item.getProductId());
            ps.setString(3, item.getProductName());
            ps.setDouble(4, item.getQuantity());
            ps.setDouble(5, item.getUnitPrice());
            ps.setDouble(6, item.getCostPrice());
            ps.setDouble(7, item.getDiscount());
            ps.setDouble(8, item.getLineTotal());
            ps.executeUpdate();
        }
    }

    private void updateStock(int productId, double delta, Connection conn) throws SQLException {
        // Update main stock quantity
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE products SET stock_quantity = stock_quantity + ?, updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
            ps.setDouble(1, delta);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
        // If reducing stock (delta < 0) and batches exist, apply FEFO dispensing
        if (delta < 0) {
            try {
                // Check if product has any active batches
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM batches WHERE product_id=? AND status='active' AND remaining>0")) {
                    ps.setInt(1, productId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next() && rs.getInt(1) > 0) {
                        // Dispense from batches using FEFO (separate connection to avoid transaction conflict)
                        new com.kaziflow.dao.BatchDAO().dispenseFefo(productId, -delta);
                    }
                }
            } catch (Exception ignored) {
                // Batch dispensing is best-effort — sale still proceeds even if batch update fails
            }
        }
    }

    private List<SaleItem> findItems(int saleId, Connection conn) throws SQLException {
        List<SaleItem> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM sale_items WHERE sale_id=?")) {
            ps.setInt(1, saleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                SaleItem item = new SaleItem();
                item.setId(rs.getInt("id"));
                item.setSaleId(saleId);
                item.setProductId(rs.getInt("product_id"));
                item.setProductName(rs.getString("product_name"));
                item.setQuantity(rs.getDouble("quantity"));
                item.setUnitPrice(rs.getDouble("unit_price"));
                item.setCostPrice(rs.getDouble("cost_price"));
                item.setDiscount(rs.getDouble("discount"));
                item.setLineTotal(rs.getDouble("line_total"));
                items.add(item);
            }
        }
        return items;
    }

    public double getTodayRevenue() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(total_amount),0) FROM sales WHERE DATE(created_at)=DATE('now') AND status='completed'");
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public int getTodayCount() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM sales WHERE DATE(created_at)=DATE('now')");
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public double getWeekRevenue() {
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(total_amount),0) FROM sales WHERE created_at >= DATE('now','-7 days') AND status='completed'");
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    /** Returns payment method breakdown as map of method->count for this month. */
    public java.util.Map<String, Integer> getPaymentMethodBreakdown() {
        java.util.LinkedHashMap<String, Integer> map = new java.util.LinkedHashMap<>();
        String sql = "SELECT payment_method, COUNT(*) as cnt FROM sales WHERE status='completed' AND strftime('%Y-%m', created_at) = strftime('%Y-%m', 'now') GROUP BY payment_method";
        try (Connection conn = getConn(); Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery(sql);
            while (rs.next()) map.put(rs.getString("payment_method"), rs.getInt("cnt"));
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    private String generateSaleNumber(Connection conn) throws SQLException {
        ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM sales");
        int count = rs.next() ? rs.getInt(1) : 0;
        return String.format("INV-%04d", count + 1);
    }

    /** Returns revenue totals keyed by "YYYY-MM", last 12 months descending */
    public java.util.LinkedHashMap<String, Double> getMonthlyRevenue() {
        java.util.LinkedHashMap<String, Double> map = new java.util.LinkedHashMap<>();
        String sql = """
            SELECT strftime('%Y-%m', created_at) as month, SUM(total_amount) as total
            FROM sales WHERE status='completed'
            GROUP BY month ORDER BY month DESC LIMIT 12
        """;
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) map.put(rs.getString("month"), rs.getDouble("total"));
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    /** Returns sale count per weekday (0=Sun..6=Sat) for the last 7 days */
    public java.util.Map<String, Integer> getDailyCountsThisWeek() {
        java.util.LinkedHashMap<String, Integer> map = new java.util.LinkedHashMap<>();
        String[] days = {"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
        for (String d : days) map.put(d, 0);
        String sql = """
            SELECT strftime('%w', created_at) as dow, COUNT(*) as cnt
            FROM sales
            WHERE created_at >= date('now','-6 days') AND status='completed'
            GROUP BY dow
        """;
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                int dow = rs.getInt("dow");
                if (dow >= 0 && dow < 7) map.put(days[dow], rs.getInt("cnt"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    /** Revenue for a specific year/month (1-based) */
    public double getMonthRevenue(int year, int month) {
        String ym = String.format("%04d-%02d", year, month);
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(total_amount),0) FROM sales WHERE strftime('%Y-%m',created_at)=? AND status='completed'")) {
            ps.setString(1, ym);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private Sale mapRow(ResultSet rs) throws SQLException {
        Sale s = new Sale();
        s.setId(rs.getInt("id"));
        s.setSaleNumber(rs.getString("sale_number"));
        s.setCustomerName(rs.getString("customer_name"));
        s.setSubtotal(rs.getDouble("subtotal"));
        s.setDiscountAmount(rs.getDouble("discount_amount"));
        try { s.setDeliveryCharge(rs.getDouble("delivery_charge")); } catch (SQLException ignored) {} // column may not exist on a DB that hasn't run the migration yet
        s.setVatAmount(rs.getDouble("vat_amount"));
        s.setTotalAmount(rs.getDouble("total_amount"));
        s.setAmountPaid(rs.getDouble("amount_paid"));
        s.setChangeAmount(rs.getDouble("change_amount"));
        s.setPaymentMethod(rs.getString("payment_method"));
        s.setStatus(rs.getString("status"));
        s.setServedByName(rs.getString("served_by_name"));
        try { s.setCreatedAt(LocalDateTime.parse(rs.getString("created_at").replace(" ", "T"))); } catch (Exception ignored) {}
        return s;
    }

    // ────────────────────────────────────────────────────────────────────
    // Dashboard drill-through / date-filter support (Phase 10C)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Completed sales within [startDate, endDate] inclusive, most recent first.
     * Dates are "YYYY-MM-DD" strings — the caller (DashboardView) supplies whatever
     * range the user picked, so this stays dynamic rather than hardcoded to "today"/"this week".
     */
    public List<Sale> findByDateRange(String startDate, String endDate) {
        List<Sale> list = new ArrayList<>();
        String sql = """
            SELECT s.*, u.name as served_by_name FROM sales s
            LEFT JOIN users u ON s.served_by = u.id
            WHERE date(s.created_at) BETWEEN ? AND ? AND s.status='completed'
            ORDER BY s.created_at DESC
        """;
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, startDate);
            ps.setString(2, endDate);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /** Revenue + order count for a given date range — powers the dashboard's date-filter summary. */
    public double[] getRevenueAndCountForRange(String startDate, String endDate) {
        String sql = """
            SELECT COALESCE(SUM(total_amount),0) as rev, COUNT(*) as cnt
            FROM sales WHERE date(created_at) BETWEEN ? AND ? AND status='completed'
        """;
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, startDate);
            ps.setString(2, endDate);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new double[]{ rs.getDouble("rev"), rs.getInt("cnt") };
        } catch (SQLException e) { e.printStackTrace(); }
        return new double[]{0, 0};
    }

    /** Daily revenue points within a date range, in chronological order — feeds the drill-through chart. */
    public java.util.LinkedHashMap<String, Double> getDailyRevenueForRange(String startDate, String endDate) {
        java.util.LinkedHashMap<String, Double> map = new java.util.LinkedHashMap<>();
        String sql = """
            SELECT date(created_at) as d, SUM(total_amount) as total
            FROM sales WHERE date(created_at) BETWEEN ? AND ? AND status='completed'
            GROUP BY d ORDER BY d ASC
        """;
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, startDate);
            ps.setString(2, endDate);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put(rs.getString("d"), rs.getDouble("total"));
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    /** Top N products by revenue within a date range, joined from sale_items. */
    public List<Object[]> getTopProducts(String startDate, String endDate, int limit) {
        List<Object[]> results = new ArrayList<>();
        String sql = """
            SELECT si.product_name, SUM(si.quantity) as qty, SUM(si.line_total) as revenue
            FROM sale_items si
            JOIN sales s ON s.id = si.sale_id
            WHERE date(s.created_at) BETWEEN ? AND ? AND s.status='completed'
            GROUP BY si.product_name
            ORDER BY revenue DESC
            LIMIT ?
        """;
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, startDate);
            ps.setString(2, endDate);
            ps.setInt(3, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(new Object[]{ rs.getString("product_name"), rs.getDouble("qty"), rs.getDouble("revenue") });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return results;
    }

    /** Top N customers by total spend within a date range. Walk-in sales are excluded — there's nothing to rank there. */
    public List<Object[]> getTopCustomers(String startDate, String endDate, int limit) {
        List<Object[]> results = new ArrayList<>();
        String sql = """
            SELECT customer_name, COUNT(*) as orders, SUM(total_amount) as spend
            FROM sales
            WHERE date(created_at) BETWEEN ? AND ? AND status='completed'
              AND customer_name IS NOT NULL AND customer_name != 'Walk-in Customer'
            GROUP BY customer_name
            ORDER BY spend DESC
            LIMIT ?
        """;
        try (Connection conn = getConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, startDate);
            ps.setString(2, endDate);
            ps.setInt(3, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(new Object[]{ rs.getString("customer_name"), rs.getInt("orders"), rs.getDouble("spend") });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return results;
    }
}
