package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.utils.SessionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * StockTakeDAO — physical inventory count workflow (Phase 10E).
 *
 * Flow: startNewCount() snapshots every product's current system quantity into
 * a new stock_takes/stock_take_items pair (status='draft'). The counter fills in
 * counted_qty per item (saveCount()). completeStockTake() then applies the
 * variance to each product's live stock via ProductDAO.updateStock() and logs
 * an 'adjustment' stock_movements row per changed product, then marks the
 * stock take 'completed' so it can't be double-applied.
 *
 * Uses ensureTables() rather than being wired into DatabaseManager directly —
 * same lazy-creation pattern already used by DeniDAO, QuotationDAO, etc.
 */
public class StockTakeDAO {

    private Connection conn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS stock_takes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    reference TEXT NOT NULL UNIQUE,
                    status TEXT NOT NULL DEFAULT 'draft' CHECK(status IN ('draft','completed')),
                    notes TEXT,
                    created_by INTEGER REFERENCES users(id),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    completed_at DATETIME
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS stock_take_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    stock_take_id INTEGER NOT NULL REFERENCES stock_takes(id) ON DELETE CASCADE,
                    product_id INTEGER NOT NULL REFERENCES products(id),
                    product_name TEXT NOT NULL,
                    system_qty REAL NOT NULL,
                    counted_qty REAL,
                    unit_cost REAL NOT NULL DEFAULT 0
                )
            """);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    /** Starts a new draft count, snapshotting every product's current stock level. */
    public int startNewCount(String notes) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            String reference = "ST-" + System.currentTimeMillis();
            int stockTakeId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO stock_takes (reference, status, notes, created_by) VALUES (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, reference);
                ps.setString(2, "draft");
                ps.setString(3, notes);
                int userId = 1;
                try { userId = SessionManager.getInstance().getCurrentUser().getId(); } catch (Exception ignored) {}
                ps.setInt(4, userId);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                stockTakeId = keys.getInt(1);
            }

            try (PreparedStatement itemPs = c.prepareStatement("""
                    INSERT INTO stock_take_items (stock_take_id, product_id, product_name, system_qty, unit_cost)
                    SELECT ?, id, name, stock_quantity, cost_price FROM products WHERE status != 'inactive'
                """)) {
                itemPs.setInt(1, stockTakeId);
                itemPs.executeUpdate();
            }
            c.commit();
            return stockTakeId;
        } catch (SQLException e) { e.printStackTrace(); return -1; }
    }

    public List<Object[]> getItems(int stockTakeId) {
        List<Object[]> list = new ArrayList<>();
        String sql = "SELECT id, product_id, product_name, system_qty, counted_qty, unit_cost FROM stock_take_items WHERE stock_take_id=? ORDER BY product_name";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, stockTakeId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Object[]{
                    rs.getInt("id"), rs.getInt("product_id"), rs.getString("product_name"),
                    rs.getDouble("system_qty"),
                    rs.getObject("counted_qty") != null ? rs.getDouble("counted_qty") : null,
                    rs.getDouble("unit_cost")
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /** Saves the counted quantity for a single item — called as the user fills in each row, so nothing is lost mid-count. */
    public boolean saveCountedQty(int itemId, double countedQty) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE stock_take_items SET counted_qty=? WHERE id=?")) {
            ps.setDouble(1, countedQty);
            ps.setInt(2, itemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /**
     * Applies every counted variance to live product stock, logs one 'adjustment'
     * stock_movements row per changed product, then marks the stock take completed.
     * Items left uncounted (counted_qty IS NULL) are skipped — no adjustment made.
     * Returns the number of products actually adjusted.
     */
    public int completeStockTake(int stockTakeId) {
        int adjustedCount = 0;
        ProductDAO productDAO = new ProductDAO();
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            String sql = "SELECT product_id, product_name, system_qty, counted_qty FROM stock_take_items WHERE stock_take_id=? AND counted_qty IS NOT NULL";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, stockTakeId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    double systemQty = rs.getDouble("system_qty");
                    double countedQty = rs.getDouble("counted_qty");
                    double variance = countedQty - systemQty;
                    if (variance == 0) continue; // no discrepancy — nothing to adjust or log

                    int productId = rs.getInt("product_id");
                    productDAO.updateStock(productId, countedQty);

                    try (PreparedStatement movePs = c.prepareStatement(
                            "INSERT INTO stock_movements (product_id, movement_type, quantity, reference, notes) VALUES (?,?,?,?,?)")) {
                        movePs.setInt(1, productId);
                        movePs.setString(2, "adjustment");
                        movePs.setDouble(3, variance);
                        movePs.setString(4, "Stock take #" + stockTakeId);
                        movePs.setString(5, rs.getString("product_name") + ": system " + systemQty + " → counted " + countedQty);
                        movePs.executeUpdate();
                    }
                    adjustedCount++;
                }
            }
            try (PreparedStatement completePs = c.prepareStatement(
                    "UPDATE stock_takes SET status='completed', completed_at=CURRENT_TIMESTAMP WHERE id=?")) {
                completePs.setInt(1, stockTakeId);
                completePs.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) { e.printStackTrace(); }
        return adjustedCount;
    }

    public List<String[]> findAll() {
        List<String[]> list = new ArrayList<>();
        String sql = """
            SELECT st.id, st.reference, st.status, st.created_at, st.completed_at,
                   (SELECT COUNT(*) FROM stock_take_items WHERE stock_take_id = st.id) as item_count
            FROM stock_takes st ORDER BY st.created_at DESC
        """;
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(sql);
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")), rs.getString("reference"), rs.getString("status"),
                    rs.getString("created_at"), rs.getString("completed_at"), String.valueOf(rs.getInt("item_count"))
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}
