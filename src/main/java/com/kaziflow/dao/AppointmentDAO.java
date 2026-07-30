package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AppointmentDAO {

    private Connection conn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS appointment_types (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    duration_minutes INTEGER DEFAULT 30,
                    price REAL DEFAULT 0,
                    color TEXT DEFAULT '#2563eb',
                    active INTEGER DEFAULT 1
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS appointments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    appointment_no TEXT NOT NULL UNIQUE,
                    customer_name TEXT NOT NULL,
                    customer_phone TEXT,
                    customer_id INTEGER REFERENCES customers(id),
                    provider_id INTEGER REFERENCES employees(id),
                    provider_name TEXT,
                    type_id INTEGER REFERENCES appointment_types(id),
                    type_name TEXT NOT NULL,
                    appointment_date DATE NOT NULL,
                    start_time TEXT NOT NULL,
                    end_time TEXT,
                    duration_minutes INTEGER DEFAULT 30,
                    status TEXT DEFAULT 'scheduled'
                        CHECK(status IN ('scheduled','confirmed','in_progress','completed','cancelled','no_show')),
                    notes TEXT,
                    deposit REAL DEFAULT 0,
                    total_amount REAL DEFAULT 0,
                    created_by INTEGER REFERENCES users(id),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM appointment_types");
            if (rs.next() && rs.getInt(1) == 0) {
                s.execute("""
                    INSERT INTO appointment_types (name, duration_minutes, price, color) VALUES
                    ('General Consultation', 30, 500, '#2563eb'),
                    ('Follow-up Visit', 20, 300, '#16a34a'),
                    ('Haircut', 45, 300, '#7c3aed'),
                    ('Hair Treatment', 90, 800, '#db2777'),
                    ('Shave & Beard', 30, 200, '#ea580c'),
                    ('Manicure', 60, 500, '#0891b2'),
                    ('Full Grooming', 120, 1200, '#65a30d'),
                    ('Vaccination', 15, 1000, '#dc2626'),
                    ('Dental Checkup', 45, 2000, '#0284c7'),
                    ('Physiotherapy', 60, 1500, '#9333ea')
                """);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public List<String[]> getTypes() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT id, name, duration_minutes, price, color FROM appointment_types WHERE active=1 ORDER BY name");
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("name"),
                    String.valueOf(rs.getInt("duration_minutes")),
                    String.format("%.2f", rs.getDouble("price")),
                    rs.getString("color")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public boolean saveType(String name, int duration, double price, String color) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO appointment_types (name, duration_minutes, price, color) VALUES (?,?,?,?)")) {
            ps.setString(1, name); ps.setInt(2, duration);
            ps.setDouble(3, price); ps.setString(4, color);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public int create(String customerName, String customerPhone, Integer customerId,
                      Integer providerId, String providerName,
                      Integer typeId, String typeName,
                      String date, String startTime, int durationMinutes,
                      double totalAmount, double deposit,
                      String notes, int createdBy) {
        try (Connection c = conn()) {
            int count = 0;
            try (Statement s = c.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM appointments");
                if (rs.next()) count = rs.getInt(1);
            }
            String apptNo = String.format("APT-%04d", count + 1);
            String endTime = calcEndTime(startTime, durationMinutes);
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO appointments
                (appointment_no,customer_name,customer_phone,customer_id,
                 provider_id,provider_name,type_id,type_name,
                 appointment_date,start_time,end_time,duration_minutes,
                 total_amount,deposit,notes,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, apptNo); ps.setString(2, customerName);
                ps.setString(3, customerPhone);
                if (customerId != null) ps.setInt(4, customerId); else ps.setNull(4, Types.INTEGER);
                if (providerId != null) ps.setInt(5, providerId); else ps.setNull(5, Types.INTEGER);
                ps.setString(6, providerName);
                if (typeId != null) ps.setInt(7, typeId); else ps.setNull(7, Types.INTEGER);
                ps.setString(8, typeName); ps.setString(9, date);
                ps.setString(10, startTime); ps.setString(11, endTime);
                ps.setInt(12, durationMinutes);
                ps.setDouble(13, totalAmount); ps.setDouble(14, deposit);
                ps.setString(15, notes); ps.setInt(16, createdBy);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    public List<String[]> findByDate(String date) {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("""
            SELECT id,appointment_no,customer_name,customer_phone,
                   provider_name,type_name,start_time,end_time,
                   duration_minutes,status,deposit,total_amount,notes
            FROM appointments WHERE appointment_date=? ORDER BY start_time ASC
        """)) {
            ps.setString(1, date);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("appointment_no"),
                    rs.getString("customer_name"),
                    rs.getString("customer_phone") != null ? rs.getString("customer_phone") : "—",
                    rs.getString("provider_name") != null ? rs.getString("provider_name") : "Any",
                    rs.getString("type_name"),
                    rs.getString("start_time"),
                    rs.getString("end_time") != null ? rs.getString("end_time") : "—",
                    String.valueOf(rs.getInt("duration_minutes")),
                    rs.getString("status"),
                    String.format("%.2f", rs.getDouble("deposit")),
                    String.format("%.2f", rs.getDouble("total_amount")),
                    rs.getString("notes") != null ? rs.getString("notes") : "—"
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public List<String[]> findUpcoming(int days) {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT id,appointment_no,customer_name,customer_phone," +
            "provider_name,type_name,appointment_date,start_time,status,total_amount " +
            "FROM appointments WHERE appointment_date>=date('now') " +
            "AND appointment_date<=date('now','+" + days + " days') " +
            "AND status NOT IN ('cancelled','no_show') " +
            "ORDER BY appointment_date ASC, start_time ASC LIMIT 100";
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(sql);
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("appointment_no"),
                    rs.getString("customer_name"),
                    rs.getString("customer_phone") != null ? rs.getString("customer_phone") : "—",
                    rs.getString("provider_name") != null ? rs.getString("provider_name") : "Any",
                    rs.getString("type_name"),
                    rs.getString("appointment_date"),
                    rs.getString("start_time"),
                    rs.getString("status"),
                    String.format("%.2f", rs.getDouble("total_amount"))
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public boolean updateStatus(int id, String status) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE appointments SET status=? WHERE id=?")) {
            ps.setString(1, status); ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean delete(int id) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM appointments WHERE id=?")) {
            ps.setInt(1, id); return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public int getTodayCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT COUNT(*) FROM appointments WHERE appointment_date=date('now') AND status NOT IN ('cancelled','no_show')");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    public int getUpcomingCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT COUNT(*) FROM appointments WHERE appointment_date>=date('now') AND status IN ('scheduled','confirmed')");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    private String calcEndTime(String startTime, int durationMinutes) {
        try {
            String[] p = startTime.split(":");
            int total = Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]) + durationMinutes;
            return String.format("%02d:%02d", total / 60, total % 60);
        } catch (Exception e) { return startTime; }
    }
}
