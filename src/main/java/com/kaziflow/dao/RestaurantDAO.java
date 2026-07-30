package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * RestaurantDAO — table management, menu, orders, KOT for restaurants/cafes/hotels.
 */
public class RestaurantDAO {

    private Connection conn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS restaurant_tables (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    table_no    TEXT NOT NULL UNIQUE,
                    capacity    INTEGER DEFAULT 4,
                    section     TEXT DEFAULT 'Main',
                    status      TEXT DEFAULT 'free'
                                    CHECK(status IN ('free','occupied','reserved','cleaning')),
                    current_order_id INTEGER
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS menu_categories (
                    id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    name    TEXT NOT NULL UNIQUE,
                    sort_order INTEGER DEFAULT 0
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS menu_items (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    category_id INTEGER REFERENCES menu_categories(id),
                    name        TEXT NOT NULL,
                    description TEXT,
                    price       REAL NOT NULL,
                    kitchen_station TEXT DEFAULT 'Main Kitchen',
                    available   INTEGER DEFAULT 1,
                    prep_minutes INTEGER DEFAULT 15
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS restaurant_orders (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_no    TEXT NOT NULL UNIQUE,
                    table_id    INTEGER REFERENCES restaurant_tables(id),
                    table_no    TEXT,
                    order_type  TEXT DEFAULT 'dine_in'
                                    CHECK(order_type IN ('dine_in','takeaway','delivery')),
                    waiter_name TEXT,
                    status      TEXT DEFAULT 'open'
                                    CHECK(status IN ('open','kot_sent','ready','billed','paid','cancelled')),
                    customer_name TEXT,
                    customer_phone TEXT,
                    subtotal    REAL DEFAULT 0,
                    tax         REAL DEFAULT 0,
                    total       REAL DEFAULT 0,
                    notes       TEXT,
                    created_by  INTEGER REFERENCES users(id),
                    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS order_items (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id    INTEGER NOT NULL REFERENCES restaurant_orders(id) ON DELETE CASCADE,
                    menu_item_id INTEGER REFERENCES menu_items(id),
                    item_name   TEXT NOT NULL,
                    quantity    INTEGER NOT NULL DEFAULT 1,
                    unit_price  REAL NOT NULL,
                    modifiers   TEXT,
                    kitchen_station TEXT DEFAULT 'Main Kitchen',
                    status      TEXT DEFAULT 'pending'
                                    CHECK(status IN ('pending','preparing','ready','served')),
                    kot_sent    INTEGER DEFAULT 0,
                    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            // Seed demo tables if empty
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM restaurant_tables");
            if (rs.next() && rs.getInt(1) == 0) {
                s.execute("""
                    INSERT INTO restaurant_tables (table_no, capacity, section) VALUES
                    ('T1',4,'Main'),('T2',4,'Main'),('T3',2,'Main'),('T4',6,'Main'),
                    ('T5',4,'Terrace'),('T6',4,'Terrace'),('T7',8,'VIP'),('T8',2,'Bar')
                """);
            }
            // Seed menu categories if empty
            rs = s.executeQuery("SELECT COUNT(*) FROM menu_categories");
            if (rs.next() && rs.getInt(1) == 0) {
                s.execute("""
                    INSERT INTO menu_categories (name, sort_order) VALUES
                    ('Starters',1),('Main Course',2),('Grills',3),
                    ('Pasta & Rice',4),('Beverages',5),('Desserts',6)
                """);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Tables ────────────────────────────────────────────────────────────

    public List<String[]> getTables() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("""
                SELECT t.id, t.table_no, t.capacity, t.section, t.status,
                       COALESCE(o.order_no,'') as order_no,
                       COALESCE(o.total,0) as order_total
                FROM restaurant_tables t
                LEFT JOIN restaurant_orders o ON o.id = t.current_order_id AND o.status != 'paid'
                ORDER BY t.section, t.table_no
            """);
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("table_no"),
                    String.valueOf(rs.getInt("capacity")),
                    rs.getString("section"),
                    rs.getString("status"),
                    rs.getString("order_no"),
                    String.format("%.2f", rs.getDouble("order_total"))
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public boolean updateTableStatus(int tableId, String status) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE restaurant_tables SET status=? WHERE id=?")) {
            ps.setString(1, status); ps.setInt(2, tableId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Menu ──────────────────────────────────────────────────────────────

    public List<String[]> getMenuCategories() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT id, name FROM menu_categories ORDER BY sort_order");
            while (rs.next()) {
                list.add(new String[]{String.valueOf(rs.getInt("id")), rs.getString("name")});
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public List<String[]> getMenuItems(Integer categoryId) {
        List<String[]> list = new ArrayList<>();
        String where = categoryId != null ? "WHERE m.category_id=" + categoryId + " AND m.available=1"
                                           : "WHERE m.available=1";
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("""
                SELECT m.id, m.name, m.description, m.price, m.kitchen_station,
                       m.prep_minutes, mc.name as category
                FROM menu_items m
                LEFT JOIN menu_categories mc ON mc.id = m.category_id
                """ + where + " ORDER BY mc.sort_order, m.name");
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("name"),
                    rs.getString("description") != null ? rs.getString("description") : "",
                    String.format("%.2f", rs.getDouble("price")),
                    rs.getString("kitchen_station"),
                    String.valueOf(rs.getInt("prep_minutes")),
                    rs.getString("category") != null ? rs.getString("category") : "Uncategorised"
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public boolean saveMenuItem(Integer categoryId, String name, String description,
                                 double price, String station, int prepMinutes) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO menu_items (category_id,name,description,price,kitchen_station,prep_minutes) VALUES (?,?,?,?,?,?)")) {
            if (categoryId != null) ps.setInt(1, categoryId); else ps.setNull(1, Types.INTEGER);
            ps.setString(2, name); ps.setString(3, description);
            ps.setDouble(4, price); ps.setString(5, station); ps.setInt(6, prepMinutes);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Orders ────────────────────────────────────────────────────────────

    public int createOrder(int tableId, String tableNo, String orderType,
                            String waiterName, String customerName, String notes, int createdBy) {
        try (Connection c = conn()) {
            int count = 0;
            try (Statement s = c.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT COUNT(*)+1 FROM restaurant_orders");
                if (rs.next()) count = rs.getInt(1);
            }
            String orderNo = String.format("ORD-%04d", count);
            int orderId;
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO restaurant_orders
                (order_no,table_id,table_no,order_type,waiter_name,customer_name,notes,created_by)
                VALUES (?,?,?,?,?,?,?,?)
            """, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, orderNo); ps.setInt(2, tableId); ps.setString(3, tableNo);
                ps.setString(4, orderType); ps.setString(5, waiterName);
                ps.setString(6, customerName); ps.setString(7, notes); ps.setInt(8, createdBy);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                orderId = keys.next() ? keys.getInt(1) : -1;
            }
            if (orderId > 0) {
                // Link order to table
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE restaurant_tables SET status='occupied', current_order_id=? WHERE id=?")) {
                    ps.setInt(1, orderId); ps.setInt(2, tableId); ps.executeUpdate();
                }
            }
            return orderId;
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    public boolean addOrderItem(int orderId, int menuItemId, String itemName,
                                 int quantity, double unitPrice, String modifiers, String station) {
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO order_items (order_id,menu_item_id,item_name,quantity,unit_price,modifiers,kitchen_station)
                VALUES (?,?,?,?,?,?,?)
            """)) {
                ps.setInt(1, orderId); ps.setInt(2, menuItemId); ps.setString(3, itemName);
                ps.setInt(4, quantity); ps.setDouble(5, unitPrice);
                ps.setString(6, modifiers); ps.setString(7, station);
                ps.executeUpdate();
            }
            recalcOrder(c, orderId);
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean sendKOT(int orderId) {
        try (Connection c = conn()) {
            // Mark all pending items as preparing + kot_sent
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE order_items SET status='preparing', kot_sent=1 WHERE order_id=? AND kot_sent=0")) {
                ps.setInt(1, orderId); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE restaurant_orders SET status='kot_sent', updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
                ps.setInt(1, orderId); ps.executeUpdate();
            }
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean updateOrderStatus(int orderId, String status) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE restaurant_orders SET status=?, updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
            ps.setString(1, status); ps.setInt(2, orderId);
            // If paid/cancelled, free the table
            if ("paid".equals(status) || "cancelled".equals(status)) {
                try (PreparedStatement ps2 = c.prepareStatement(
                        "UPDATE restaurant_tables SET status='free', current_order_id=NULL WHERE current_order_id=?")) {
                    ps2.setInt(1, orderId); ps2.executeUpdate();
                }
            }
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public List<String[]> getOrderItems(int orderId) {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT id,item_name,quantity,unit_price,modifiers,kitchen_station,status,kot_sent FROM order_items WHERE order_id=?")) {
            ps.setInt(1, orderId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("item_name"),
                    String.valueOf(rs.getInt("quantity")),
                    String.format("%.2f", rs.getDouble("unit_price")),
                    rs.getString("modifiers") != null ? rs.getString("modifiers") : "",
                    rs.getString("kitchen_station"),
                    rs.getString("status"),
                    rs.getInt("kot_sent") == 1 ? "Yes" : "No"
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public List<String[]> getOpenOrders() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("""
                SELECT id,order_no,table_no,order_type,waiter_name,
                       status,total,created_at
                FROM restaurant_orders
                WHERE status NOT IN ('paid','cancelled')
                ORDER BY created_at DESC
            """);
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("order_no"),
                    rs.getString("table_no") != null ? rs.getString("table_no") : "Takeaway",
                    rs.getString("order_type"),
                    rs.getString("waiter_name") != null ? rs.getString("waiter_name") : "—",
                    rs.getString("status"),
                    String.format("%.2f", rs.getDouble("total")),
                    rs.getString("created_at")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public String[] getOrderById(int orderId) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM restaurant_orders WHERE id=?")) {
            ps.setInt(1, orderId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new String[]{
                    String.valueOf(rs.getInt("id")), rs.getString("order_no"),
                    rs.getString("table_no"), rs.getString("order_type"),
                    rs.getString("waiter_name"), rs.getString("status"),
                    String.format("%.2f", rs.getDouble("subtotal")),
                    String.format("%.2f", rs.getDouble("tax")),
                    String.format("%.2f", rs.getDouble("total")),
                    rs.getString("notes")
                };
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    public int getOpenTableCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM restaurant_tables WHERE status='occupied'");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    public int getOpenOrderCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM restaurant_orders WHERE status NOT IN ('paid','cancelled')");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    public double getTodayRevenue() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(total),0) FROM restaurant_orders WHERE DATE(created_at)=DATE('now') AND status='paid'");
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (Exception e) { return 0; }
    }

    // ── Private ───────────────────────────────────────────────────────────

    private void recalcOrder(Connection c, int orderId) throws SQLException {
        double subtotal = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(quantity*unit_price),0) FROM order_items WHERE order_id=?")) {
            ps.setInt(1, orderId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) subtotal = rs.getDouble(1);
        }
        double tax = subtotal * 0.16;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE restaurant_orders SET subtotal=?,tax=?,total=?,updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
            ps.setDouble(1, subtotal); ps.setDouble(2, tax);
            ps.setDouble(3, subtotal + tax); ps.setInt(4, orderId);
            ps.executeUpdate();
        }
    }
}
