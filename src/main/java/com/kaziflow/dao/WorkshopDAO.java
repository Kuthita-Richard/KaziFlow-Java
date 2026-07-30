package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WorkshopDAO {

    private Connection conn() throws SQLException { return DatabaseManager.getInstance().getConnection(); }

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS job_cards (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_no TEXT NOT NULL UNIQUE,
                    customer_name TEXT NOT NULL,
                    customer_phone TEXT,
                    item_description TEXT NOT NULL,
                    problem_description TEXT,
                    status TEXT DEFAULT 'received'
                        CHECK(status IN ('received','diagnosing','in_progress','waiting_parts','ready','collected','cancelled')),
                    assigned_to TEXT,
                    estimated_cost REAL DEFAULT 0,
                    deposit_paid REAL DEFAULT 0,
                    total_amount REAL DEFAULT 0,
                    notes TEXT,
                    created_by INTEGER REFERENCES users(id),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS job_parts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_id INTEGER NOT NULL REFERENCES job_cards(id) ON DELETE CASCADE,
                    product_id INTEGER REFERENCES products(id),
                    part_name TEXT NOT NULL,
                    quantity REAL NOT NULL DEFAULT 1,
                    unit_cost REAL NOT NULL DEFAULT 0,
                    line_total REAL NOT NULL DEFAULT 0
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS job_labour (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_id INTEGER NOT NULL REFERENCES job_cards(id) ON DELETE CASCADE,
                    description TEXT NOT NULL,
                    hours REAL NOT NULL DEFAULT 1,
                    rate REAL NOT NULL DEFAULT 500,
                    line_total REAL NOT NULL DEFAULT 0
                )""");
        } catch (Exception e) { e.printStackTrace(); }
    }

    public int createJob(String customerName, String customerPhone, String itemDesc,
                          String problemDesc, String assignedTo, double estimatedCost,
                          double deposit, String notes, int createdBy) {
        try (Connection c = conn()) {
            int count = 0;
            try (Statement s = c.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT COUNT(*)+1 FROM job_cards");
                if (rs.next()) count = rs.getInt(1);
            }
            String jobNo = String.format("JOB-%04d", count);
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO job_cards
                (job_no,customer_name,customer_phone,item_description,problem_description,
                 assigned_to,estimated_cost,deposit_paid,notes,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1,jobNo); ps.setString(2,customerName); ps.setString(3,customerPhone);
                ps.setString(4,itemDesc); ps.setString(5,problemDesc); ps.setString(6,assignedTo);
                ps.setDouble(7,estimatedCost); ps.setDouble(8,deposit);
                ps.setString(9,notes); ps.setInt(10,createdBy);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    public boolean updateStatus(int id, String status) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE job_cards SET status=?, updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
            ps.setString(1,status); ps.setInt(2,id); return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean addPart(int jobId, Integer productId, String partName, double qty, double unitCost) {
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO job_parts (job_id,product_id,part_name,quantity,unit_cost,line_total) VALUES (?,?,?,?,?,?)")) {
                ps.setInt(1,jobId);
                if (productId!=null) ps.setInt(2,productId); else ps.setNull(2,Types.INTEGER);
                ps.setString(3,partName); ps.setDouble(4,qty); ps.setDouble(5,unitCost); ps.setDouble(6,qty*unitCost);
                ps.executeUpdate();
            }
            if (productId!=null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE products SET stock_quantity=stock_quantity-? WHERE id=?")) {
                    ps.setDouble(1,qty); ps.setInt(2,productId); ps.executeUpdate();
                }
            }
            recalcJob(c,jobId); return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean addLabour(int jobId, String description, double hours, double rate) {
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO job_labour (job_id,description,hours,rate,line_total) VALUES (?,?,?,?,?)")) {
                ps.setInt(1,jobId); ps.setString(2,description);
                ps.setDouble(3,hours); ps.setDouble(4,rate); ps.setDouble(5,hours*rate); ps.executeUpdate();
            }
            recalcJob(c,jobId); return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public List<String[]> findAll(String statusFilter) {
        List<String[]> list = new ArrayList<>();
        String where = (statusFilter!=null) ? "WHERE status='"+statusFilter+"'" : "";
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT id,job_no,customer_name,customer_phone," +
                "item_description,status,assigned_to,total_amount,deposit_paid,created_at " +
                "FROM job_cards "+where+" ORDER BY created_at DESC LIMIT 100");
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")), rs.getString("job_no"),
                    rs.getString("customer_name"),
                    rs.getString("customer_phone")!=null?rs.getString("customer_phone"):"—",
                    rs.getString("item_description"), rs.getString("status"),
                    rs.getString("assigned_to")!=null?rs.getString("assigned_to"):"—",
                    String.format("%.2f",rs.getDouble("total_amount")),
                    String.format("%.2f",rs.getDouble("deposit_paid")), rs.getString("created_at")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public List<String[]> getParts(int jobId) {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT part_name,quantity,unit_cost,line_total FROM job_parts WHERE job_id=?")) {
            ps.setInt(1,jobId); ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new String[]{rs.getString("part_name"),
                String.format("%.0f",rs.getDouble("quantity")),
                String.format("%.2f",rs.getDouble("unit_cost")),
                String.format("%.2f",rs.getDouble("line_total"))});
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public List<String[]> getLabour(int jobId) {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT description,hours,rate,line_total FROM job_labour WHERE job_id=?")) {
            ps.setInt(1,jobId); ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new String[]{rs.getString("description"),
                String.format("%.1f",rs.getDouble("hours")),
                String.format("%.2f",rs.getDouble("rate")),
                String.format("%.2f",rs.getDouble("line_total"))});
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public int getOpenCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM job_cards WHERE status NOT IN ('collected','cancelled')");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    public int getReadyCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM job_cards WHERE status='ready'");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    private void recalcJob(Connection c, int jobId) throws SQLException {
        double parts=0, labour=0;
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(line_total),0) FROM job_parts WHERE job_id=?")) {
            ps.setInt(1,jobId); ResultSet rs=ps.executeQuery(); if(rs.next()) parts=rs.getDouble(1);
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(line_total),0) FROM job_labour WHERE job_id=?")) {
            ps.setInt(1,jobId); ResultSet rs=ps.executeQuery(); if(rs.next()) labour=rs.getDouble(1);
        }
        try (PreparedStatement ps = c.prepareStatement("UPDATE job_cards SET total_amount=?,updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
            ps.setDouble(1,parts+labour); ps.setInt(2,jobId); ps.executeUpdate();
        }
    }
}
