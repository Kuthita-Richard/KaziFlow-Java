package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class HotelDAO {

    private Connection conn() throws SQLException { return DatabaseManager.getInstance().getConnection(); }

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS hotel_rooms (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    room_no TEXT NOT NULL UNIQUE,
                    room_type TEXT NOT NULL DEFAULT 'Standard',
                    floor INTEGER DEFAULT 1,
                    capacity INTEGER DEFAULT 2,
                    rate_per_night REAL NOT NULL DEFAULT 0,
                    status TEXT DEFAULT 'available'
                        CHECK(status IN ('available','occupied','reserved','maintenance','cleaning')),
                    amenities TEXT,
                    current_reservation_id INTEGER
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS hotel_reservations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    reservation_no TEXT NOT NULL UNIQUE,
                    guest_name TEXT NOT NULL,
                    guest_phone TEXT,
                    guest_email TEXT,
                    room_id INTEGER NOT NULL REFERENCES hotel_rooms(id),
                    room_no TEXT,
                    check_in_date DATE NOT NULL,
                    check_out_date DATE NOT NULL,
                    nights INTEGER DEFAULT 1,
                    rate_per_night REAL DEFAULT 0,
                    room_total REAL DEFAULT 0,
                    extras_total REAL DEFAULT 0,
                    grand_total REAL DEFAULT 0,
                    deposit REAL DEFAULT 0,
                    status TEXT DEFAULT 'reserved'
                        CHECK(status IN ('reserved','checked_in','checked_out','cancelled','no_show')),
                    adults INTEGER DEFAULT 1,
                    children INTEGER DEFAULT 0,
                    notes TEXT,
                    created_by INTEGER REFERENCES users(id),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS guest_folio (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    reservation_id INTEGER NOT NULL REFERENCES hotel_reservations(id) ON DELETE CASCADE,
                    description TEXT NOT NULL,
                    amount REAL NOT NULL,
                    folio_date DATE DEFAULT (date('now')),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");
            // Seed rooms if empty
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM hotel_rooms");
            if (rs.next() && rs.getInt(1) == 0) {
                s.execute("""
                    INSERT INTO hotel_rooms (room_no,room_type,floor,capacity,rate_per_night) VALUES
                    ('101','Standard',1,2,3500),('102','Standard',1,2,3500),
                    ('103','Deluxe',1,2,5000),('201','Standard',2,2,3500),
                    ('202','Deluxe',2,2,5000),('203','Suite',2,4,8000),
                    ('301','Suite',3,4,8000),('302','Executive',3,2,6500)
                """);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Rooms ─────────────────────────────────────────────────────────────

    public List<String[]> getRooms() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("""
                SELECT r.id,r.room_no,r.room_type,r.floor,r.capacity,r.rate_per_night,r.status,
                       COALESCE(res.guest_name,'') as guest_name,
                       COALESCE(res.check_out_date,'') as checkout
                FROM hotel_rooms r
                LEFT JOIN hotel_reservations res ON res.id=r.current_reservation_id AND res.status='checked_in'
                ORDER BY r.floor,r.room_no""");
            while (rs.next()) list.add(new String[]{
                String.valueOf(rs.getInt("id")), rs.getString("room_no"),
                rs.getString("room_type"), String.valueOf(rs.getInt("floor")),
                String.valueOf(rs.getInt("capacity")),
                String.format("%.0f",rs.getDouble("rate_per_night")),
                rs.getString("status"), rs.getString("guest_name"), rs.getString("checkout")
            });
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public boolean updateRoomStatus(int roomId, String status) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE hotel_rooms SET status=? WHERE id=?")) {
            ps.setString(1,status); ps.setInt(2,roomId); return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Reservations ──────────────────────────────────────────────────────

    public int createReservation(String guestName, String phone, String email,
                                   int roomId, String roomNo, String checkIn, String checkOut,
                                   int nights, double ratePerNight, double deposit,
                                   int adults, int children, String notes, int createdBy) {
        try (Connection c = conn()) {
            int count = 0;
            try (Statement s = c.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT COUNT(*)+1 FROM hotel_reservations");
                if (rs.next()) count = rs.getInt(1);
            }
            String resNo = String.format("RES-%04d", count);
            double roomTotal = nights * ratePerNight;
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO hotel_reservations (reservation_no,guest_name,guest_phone,guest_email,
                room_id,room_no,check_in_date,check_out_date,nights,rate_per_night,
                room_total,grand_total,deposit,adults,children,notes,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1,resNo); ps.setString(2,guestName); ps.setString(3,phone);
                ps.setString(4,email); ps.setInt(5,roomId); ps.setString(6,roomNo);
                ps.setString(7,checkIn); ps.setString(8,checkOut); ps.setInt(9,nights);
                ps.setDouble(10,ratePerNight); ps.setDouble(11,roomTotal);
                ps.setDouble(12,roomTotal); ps.setDouble(13,deposit);
                ps.setInt(14,adults); ps.setInt(15,children);
                ps.setString(16,notes); ps.setInt(17,createdBy);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                int resId = keys.next() ? keys.getInt(1) : -1;
                if (resId > 0) {
                    try (PreparedStatement ps2 = c.prepareStatement(
                            "UPDATE hotel_rooms SET status='reserved', current_reservation_id=? WHERE id=?")) {
                        ps2.setInt(1,resId); ps2.setInt(2,roomId); ps2.executeUpdate();
                    }
                }
                return resId;
            }
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    public boolean checkIn(int reservationId, int roomId) {
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE hotel_reservations SET status='checked_in' WHERE id=?")) {
                ps.setInt(1,reservationId); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE hotel_rooms SET status='occupied' WHERE id=?")) {
                ps.setInt(1,roomId); ps.executeUpdate();
            }
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean checkOut(int reservationId, int roomId) {
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE hotel_reservations SET status='checked_out' WHERE id=?")) {
                ps.setInt(1,reservationId); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE hotel_rooms SET status='cleaning', current_reservation_id=NULL WHERE id=?")) {
                ps.setInt(1,roomId); ps.executeUpdate();
            }
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean addFolioCharge(int reservationId, String description, double amount) {
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO guest_folio (reservation_id,description,amount) VALUES (?,?,?)")) {
                ps.setInt(1,reservationId); ps.setString(2,description); ps.setDouble(3,amount);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE hotel_reservations SET extras_total=extras_total+?,grand_total=room_total+extras_total+? WHERE id=?")) {
                ps.setDouble(1,amount); ps.setDouble(2,amount); ps.setInt(3,reservationId);
                ps.executeUpdate();
            }
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public List<String[]> getReservations(String statusFilter) {
        List<String[]> list = new ArrayList<>();
        String where = (statusFilter!=null&&!statusFilter.equals("all")) ? "WHERE status='"+statusFilter+"'" : "";
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT id,reservation_no,guest_name,guest_phone," +
                "room_id,room_no,check_in_date,check_out_date,nights,grand_total,status FROM hotel_reservations "
                +where+" ORDER BY check_in_date DESC LIMIT 100");
            while (rs.next()) list.add(new String[]{
                String.valueOf(rs.getInt("id")), rs.getString("reservation_no"),
                rs.getString("guest_name"),
                rs.getString("guest_phone")!=null?rs.getString("guest_phone"):"—",
                String.valueOf(rs.getInt("room_id")), rs.getString("room_no"),
                rs.getString("check_in_date"), rs.getString("check_out_date"),
                String.valueOf(rs.getInt("nights")),
                String.format("%.2f",rs.getDouble("grand_total")), rs.getString("status")
            });
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public List<String[]> getFolio(int reservationId) {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT description,amount,folio_date FROM guest_folio WHERE reservation_id=? ORDER BY created_at")) {
            ps.setInt(1,reservationId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new String[]{
                rs.getString("description"),
                String.format("%.2f",rs.getDouble("amount")), rs.getString("folio_date")
            });
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    public int getOccupiedCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM hotel_rooms WHERE status='occupied'");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    public int getAvailableCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM hotel_rooms WHERE status='available'");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    public double getTodayRevenue() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(grand_total),0) FROM hotel_reservations WHERE status='checked_out' AND DATE(created_at)=DATE('now')");
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (Exception e) { return 0; }
    }
}
