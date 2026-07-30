package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LaundryDAO {

    private Connection conn() throws SQLException { return DatabaseManager.getInstance().getConnection(); }

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS laundry_orders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_no TEXT NOT NULL UNIQUE,
                    customer_name TEXT NOT NULL,
                    customer_phone TEXT,
                    item_count INTEGER DEFAULT 1,
                    service_type TEXT DEFAULT 'wash_fold',
                    status TEXT DEFAULT 'received'
                        CHECK(status IN ('received','washing','ironing','ready','collected','cancelled')),
                    due_date DATE,
                    total_amount REAL DEFAULT 0,
                    deposit_paid REAL DEFAULT 0,
                    notes TEXT,
                    created_by INTEGER REFERENCES users(id),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS laundry_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id INTEGER NOT NULL REFERENCES laundry_orders(id) ON DELETE CASCADE,
                    garment_type TEXT NOT NULL,
                    quantity INTEGER DEFAULT 1,
                    unit_price REAL DEFAULT 0,
                    starch TEXT DEFAULT 'none',
                    special_instructions TEXT
                )""");
        } catch (Exception e) { e.printStackTrace(); }
    }

    public int createOrder(String customerName, String phone, String serviceType,
                            String dueDate, double amount, double deposit, String notes, int createdBy) {
        try (Connection c = conn()) {
            int count = 0;
            try (Statement s = c.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT COUNT(*)+1 FROM laundry_orders");
                if (rs.next()) count = rs.getInt(1);
            }
            String orderNo = String.format("LDY-%04d", count);
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO laundry_orders (order_no,customer_name,customer_phone,service_type,
                due_date,total_amount,deposit_paid,notes,created_by)
                VALUES (?,?,?,?,?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1,orderNo); ps.setString(2,customerName); ps.setString(3,phone);
                ps.setString(4,serviceType); ps.setString(5,dueDate.isBlank()?null:dueDate);
                ps.setDouble(6,amount); ps.setDouble(7,deposit); ps.setString(8,notes); ps.setInt(9,createdBy);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    public boolean addItem(int orderId, String garmentType, int qty, double unitPrice, String starch, String instructions) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO laundry_items (order_id,garment_type,quantity,unit_price,starch,special_instructions) VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1,orderId); ps.setString(2,garmentType); ps.setInt(3,qty);
            ps.setDouble(4,unitPrice); ps.setString(5,starch); ps.setString(6,instructions);
            ps.executeUpdate();
            // Recalc total
            try (PreparedStatement ps2 = c.prepareStatement(
                    "UPDATE laundry_orders SET total_amount=(SELECT COALESCE(SUM(quantity*unit_price),0) FROM laundry_items WHERE order_id=?) WHERE id=?")) {
                ps2.setInt(1,orderId); ps2.setInt(2,orderId); ps2.executeUpdate();
            }
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean updateStatus(int id, String status) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE laundry_orders SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
            ps.setString(1,status); ps.setInt(2,id); return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public List<String[]> findAll(String statusFilter) {
        List<String[]> list = new ArrayList<>();
        String where = (statusFilter!=null&&!statusFilter.equals("all")) ? "WHERE status='"+statusFilter+"'" : "";
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT id,order_no,customer_name,customer_phone," +
                "service_type,status,due_date,total_amount,deposit_paid,created_at " +
                "FROM laundry_orders "+where+" ORDER BY created_at DESC LIMIT 100");
            while (rs.next()) list.add(new String[]{
                String.valueOf(rs.getInt("id")), rs.getString("order_no"),
                rs.getString("customer_name"),
                rs.getString("customer_phone")!=null?rs.getString("customer_phone"):"—",
                rs.getString("service_type").replace("_"," "),
                rs.getString("status"),
                rs.getString("due_date")!=null?rs.getString("due_date"):"—",
                String.format("%.2f",rs.getDouble("total_amount")),
                String.format("%.2f",rs.getDouble("deposit_paid")),
                rs.getString("created_at")
            });
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public int getOpenCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM laundry_orders WHERE status NOT IN ('collected','cancelled')");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    public int getReadyCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM laundry_orders WHERE status='ready'");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }
}
