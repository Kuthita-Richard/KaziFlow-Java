package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class GymDAO {

    private Connection conn() throws SQLException { return DatabaseManager.getInstance().getConnection(); }

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS membership_plans (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    duration_days INTEGER NOT NULL DEFAULT 30,
                    price REAL NOT NULL,
                    description TEXT,
                    active INTEGER DEFAULT 1
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS memberships (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    member_no TEXT NOT NULL UNIQUE,
                    first_name TEXT NOT NULL,
                    last_name TEXT NOT NULL,
                    phone TEXT,
                    email TEXT,
                    plan_id INTEGER REFERENCES membership_plans(id),
                    plan_name TEXT,
                    start_date DATE NOT NULL,
                    end_date DATE NOT NULL,
                    status TEXT DEFAULT 'active'
                        CHECK(status IN ('active','expired','frozen','cancelled')),
                    photo_path TEXT,
                    notes TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS gym_checkins (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    membership_id INTEGER NOT NULL REFERENCES memberships(id),
                    member_name TEXT,
                    checkin_time DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS gym_classes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    instructor TEXT,
                    schedule TEXT,
                    capacity INTEGER DEFAULT 20,
                    price REAL DEFAULT 0,
                    active INTEGER DEFAULT 1
                )""");
            // Seed plans if empty
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM membership_plans");
            if (rs.next() && rs.getInt(1) == 0) {
                s.execute("""
                    INSERT INTO membership_plans (name, duration_days, price, description) VALUES
                    ('Day Pass',      1,    200,  'Single day access'),
                    ('Weekly',        7,    800,  '7 day access'),
                    ('Monthly',       30,   2500, 'Full month access'),
                    ('Quarterly',     90,   6500, '3 months — save 13%'),
                    ('Semi-Annual',  180,  11000, '6 months — save 26%'),
                    ('Annual',       365,  20000, 'Full year — best value')
                """);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Membership Plans ──────────────────────────────────────────────────

    public List<String[]> getPlans() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT id,name,duration_days,price,description FROM membership_plans WHERE active=1 ORDER BY duration_days");
            while (rs.next()) list.add(new String[]{
                String.valueOf(rs.getInt("id")), rs.getString("name"),
                String.valueOf(rs.getInt("duration_days")),
                String.format("%.2f", rs.getDouble("price")),
                rs.getString("description") != null ? rs.getString("description") : ""
            });
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public boolean savePlan(String name, int days, double price, String desc) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO membership_plans (name,duration_days,price,description) VALUES (?,?,?,?)")) {
            ps.setString(1,name); ps.setInt(2,days); ps.setDouble(3,price); ps.setString(4,desc);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Memberships ───────────────────────────────────────────────────────

    public int enroll(String firstName, String lastName, String phone, String email,
                       int planId, String planName, int durationDays, String notes) {
        try (Connection c = conn()) {
            int count = 0;
            try (Statement s = c.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT COUNT(*)+1 FROM memberships");
                if (rs.next()) count = rs.getInt(1);
            }
            String memberNo = String.format("MBR-%04d", count);
            String startDate = LocalDate.now().toString();
            String endDate   = LocalDate.now().plusDays(durationDays).toString();
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO memberships (member_no,first_name,last_name,phone,email,
                plan_id,plan_name,start_date,end_date,notes)
                VALUES (?,?,?,?,?,?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1,memberNo); ps.setString(2,firstName); ps.setString(3,lastName);
                ps.setString(4,phone); ps.setString(5,email); ps.setInt(6,planId);
                ps.setString(7,planName); ps.setString(8,startDate);
                ps.setString(9,endDate); ps.setString(10,notes);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    public boolean renew(int memberId, int planId, String planName, int durationDays) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("""
            UPDATE memberships SET plan_id=?,plan_name=?,
            start_date=date('now'),
            end_date=date('now','+'||?||' days'),
            status='active' WHERE id=?""")) {
            ps.setInt(1,planId); ps.setString(2,planName);
            ps.setInt(3,durationDays); ps.setInt(4,memberId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean updateStatus(int id, String status) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE memberships SET status=? WHERE id=?")) {
            ps.setString(1,status); ps.setInt(2,id); return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public List<String[]> findAll(String statusFilter) {
        List<String[]> list = new ArrayList<>();
        String where = (statusFilter != null && !statusFilter.equals("all"))
            ? "WHERE status='" + statusFilter + "'" : "";
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("""
                SELECT id, member_no, first_name||' '||last_name AS name,
                       phone, plan_name, start_date, end_date, status,
                       CAST(julianday(end_date)-julianday('now') AS INTEGER) AS days_left
                FROM memberships """ + where + " ORDER BY end_date ASC LIMIT 200");
            while (rs.next()) list.add(new String[]{
                String.valueOf(rs.getInt("id")), rs.getString("member_no"),
                rs.getString("name"),
                rs.getString("phone") != null ? rs.getString("phone") : "—",
                rs.getString("plan_name") != null ? rs.getString("plan_name") : "—",
                rs.getString("start_date"), rs.getString("end_date"),
                rs.getString("status"), String.valueOf(rs.getInt("days_left"))
            });
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public List<String[]> search(String query) {
        List<String[]> list = new ArrayList<>();
        String q = "%" + query + "%";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("""
            SELECT id,member_no,first_name||' '||last_name AS name,phone,plan_name,
                   start_date,end_date,status,
                   CAST(julianday(end_date)-julianday('now') AS INTEGER) AS days_left
            FROM memberships WHERE first_name LIKE ? OR last_name LIKE ? OR member_no LIKE ? OR phone LIKE ?
            ORDER BY end_date ASC LIMIT 50""")) {
            ps.setString(1,q); ps.setString(2,q); ps.setString(3,q); ps.setString(4,q);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new String[]{
                String.valueOf(rs.getInt("id")), rs.getString("member_no"),
                rs.getString("name"),
                rs.getString("phone") != null ? rs.getString("phone") : "—",
                rs.getString("plan_name") != null ? rs.getString("plan_name") : "—",
                rs.getString("start_date"), rs.getString("end_date"),
                rs.getString("status"), String.valueOf(rs.getInt("days_left"))
            });
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public boolean checkIn(int memberId, String memberName) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO gym_checkins (membership_id,member_name) VALUES (?,?)")) {
            ps.setInt(1,memberId); ps.setString(2,memberName); return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public List<String[]> getTodayCheckins() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("""
                SELECT g.id, m.member_no, g.member_name, m.plan_name, g.checkin_time
                FROM gym_checkins g JOIN memberships m ON m.id=g.membership_id
                WHERE DATE(g.checkin_time)=DATE('now') ORDER BY g.checkin_time DESC""");
            while (rs.next()) list.add(new String[]{
                String.valueOf(rs.getInt("id")), rs.getString("member_no"),
                rs.getString("member_name"),
                rs.getString("plan_name") != null ? rs.getString("plan_name") : "—",
                rs.getString("checkin_time")
            });
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    public int getActiveCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM memberships WHERE status='active' AND end_date>=date('now')");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    public int getExpiringCount(int days) {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM memberships WHERE status='active' " +
                "AND end_date>=date('now') AND end_date<=date('now','+" + days + " days')");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    public int getTodayCheckinCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM gym_checkins WHERE DATE(checkin_time)=DATE('now')");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    public void autoExpire() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("UPDATE memberships SET status='expired' WHERE status='active' AND end_date < date('now')");
        } catch (Exception e) { e.printStackTrace(); }
    }
}
