package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FuelStationDAO {

    private Connection conn() throws SQLException { return DatabaseManager.getInstance().getConnection(); }

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS fuel_pumps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pump_no TEXT NOT NULL UNIQUE,
                    fuel_type TEXT NOT NULL DEFAULT 'Petrol',
                    nozzle_count INTEGER DEFAULT 1,
                    active INTEGER DEFAULT 1
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS fuel_shifts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    shift_no TEXT NOT NULL UNIQUE,
                    attendant_name TEXT NOT NULL,
                    start_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    end_time DATETIME,
                    opening_cash REAL DEFAULT 0,
                    closing_cash REAL DEFAULT 0,
                    total_sales REAL DEFAULT 0,
                    status TEXT DEFAULT 'open'
                        CHECK(status IN ('open','closed')),
                    notes TEXT,
                    created_by INTEGER REFERENCES users(id)
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS fuel_sales (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    shift_id INTEGER REFERENCES fuel_shifts(id),
                    pump_id INTEGER REFERENCES fuel_pumps(id),
                    pump_no TEXT,
                    fuel_type TEXT NOT NULL,
                    litres REAL NOT NULL,
                    price_per_litre REAL NOT NULL,
                    amount REAL NOT NULL,
                    payment_method TEXT DEFAULT 'cash',
                    vehicle_no TEXT,
                    customer_name TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS dip_readings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fuel_type TEXT NOT NULL,
                    reading_litres REAL NOT NULL,
                    reading_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    attendant TEXT,
                    notes TEXT
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS fuel_prices (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fuel_type TEXT NOT NULL UNIQUE,
                    price_per_litre REAL NOT NULL,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");
            // Seed pumps and prices if empty
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM fuel_pumps");
            if (rs.next() && rs.getInt(1) == 0) {
                s.execute("""
                    INSERT INTO fuel_pumps (pump_no,fuel_type,nozzle_count) VALUES
                    ('P1','Petrol',2),('P2','Diesel',2),('P3','Petrol',1),('P4','Diesel',1)""");
                s.execute("""
                    INSERT INTO fuel_prices (fuel_type,price_per_litre) VALUES
                    ('Petrol',212.00),('Diesel',200.00),('Super Petrol',220.00)""");
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Prices ────────────────────────────────────────────────────────────

    public List<String[]> getPrices() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT id,fuel_type,price_per_litre,updated_at FROM fuel_prices ORDER BY fuel_type");
            while (rs.next()) list.add(new String[]{
                String.valueOf(rs.getInt("id")), rs.getString("fuel_type"),
                String.format("%.2f",rs.getDouble("price_per_litre")), rs.getString("updated_at")
            });
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public boolean updatePrice(String fuelType, double price) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT OR REPLACE INTO fuel_prices (fuel_type,price_per_litre,updated_at) VALUES (?,?,CURRENT_TIMESTAMP)")) {
            ps.setString(1,fuelType); ps.setDouble(2,price); return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Shifts ────────────────────────────────────────────────────────────

    public int openShift(String attendantName, double openingCash, String notes, int createdBy) {
        try (Connection c = conn()) {
            int count = 0;
            try (Statement s = c.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT COUNT(*)+1 FROM fuel_shifts");
                if (rs.next()) count = rs.getInt(1);
            }
            String shiftNo = String.format("SHF-%04d", count);
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO fuel_shifts (shift_no,attendant_name,opening_cash,notes,created_by)
                VALUES (?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1,shiftNo); ps.setString(2,attendantName);
                ps.setDouble(3,openingCash); ps.setString(4,notes); ps.setInt(5,createdBy);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    public boolean closeShift(int shiftId, double closingCash) {
        try (Connection c = conn()) {
            double totalSales = 0;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(SUM(amount),0) FROM fuel_sales WHERE shift_id=?")) {
                ps.setInt(1,shiftId); ResultSet rs = ps.executeQuery();
                if (rs.next()) totalSales = rs.getDouble(1);
            }
            try (PreparedStatement ps = c.prepareStatement("""
                UPDATE fuel_shifts SET status='closed',end_time=CURRENT_TIMESTAMP,
                closing_cash=?,total_sales=? WHERE id=?""")) {
                ps.setDouble(1,closingCash); ps.setDouble(2,totalSales); ps.setInt(3,shiftId);
                return ps.executeUpdate() > 0;
            }
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public String[] getOpenShift() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT id,shift_no,attendant_name,opening_cash,start_time FROM fuel_shifts WHERE status='open' LIMIT 1");
            if (rs.next()) return new String[]{
                String.valueOf(rs.getInt("id")), rs.getString("shift_no"),
                rs.getString("attendant_name"), String.format("%.2f",rs.getDouble("opening_cash")),
                rs.getString("start_time")
            };
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // ── Sales ─────────────────────────────────────────────────────────────

    public int recordSale(Integer shiftId, String pumpNo, String fuelType,
                           double litres, double pricePerLitre, String paymentMethod,
                           String vehicleNo, String customerName) {
        double amount = litres * pricePerLitre;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("""
            INSERT INTO fuel_sales (shift_id,pump_no,fuel_type,litres,price_per_litre,
            amount,payment_method,vehicle_no,customer_name)
            VALUES (?,?,?,?,?,?,?,?,?)""", Statement.RETURN_GENERATED_KEYS)) {
            if (shiftId!=null) ps.setInt(1,shiftId); else ps.setNull(1,Types.INTEGER);
            ps.setString(2,pumpNo); ps.setString(3,fuelType); ps.setDouble(4,litres);
            ps.setDouble(5,pricePerLitre); ps.setDouble(6,amount);
            ps.setString(7,paymentMethod); ps.setString(8,vehicleNo); ps.setString(9,customerName);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    public List<String[]> getTodaySales() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("""
                SELECT id,pump_no,fuel_type,litres,price_per_litre,amount,
                       payment_method,vehicle_no,customer_name,created_at
                FROM fuel_sales WHERE DATE(created_at)=DATE('now')
                ORDER BY created_at DESC""");
            while (rs.next()) list.add(new String[]{
                String.valueOf(rs.getInt("id")), rs.getString("pump_no"),
                rs.getString("fuel_type"), String.format("%.2f",rs.getDouble("litres")),
                String.format("%.2f",rs.getDouble("price_per_litre")),
                String.format("%.2f",rs.getDouble("amount")),
                rs.getString("payment_method"),
                rs.getString("vehicle_no")!=null?rs.getString("vehicle_no"):"—",
                rs.getString("customer_name")!=null?rs.getString("customer_name"):"Walk-in",
                rs.getString("created_at")
            });
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public boolean addDipReading(String fuelType, double litres, String attendant, String notes) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO dip_readings (fuel_type,reading_litres,attendant,notes) VALUES (?,?,?,?)")) {
            ps.setString(1,fuelType); ps.setDouble(2,litres);
            ps.setString(3,attendant); ps.setString(4,notes);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    public double getTodayRevenue() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(amount),0) FROM fuel_sales WHERE DATE(created_at)=DATE('now')");
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (Exception e) { return 0; }
    }

    public double getTodayLitres() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COALESCE(SUM(litres),0) FROM fuel_sales WHERE DATE(created_at)=DATE('now')");
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (Exception e) { return 0; }
    }

    public List<String[]> getShifts() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT id,shift_no,attendant_name,status,total_sales,start_time,end_time FROM fuel_shifts ORDER BY start_time DESC LIMIT 20");
            while (rs.next()) list.add(new String[]{
                String.valueOf(rs.getInt("id")), rs.getString("shift_no"),
                rs.getString("attendant_name"), rs.getString("status"),
                String.format("%.2f",rs.getDouble("total_sales")),
                rs.getString("start_time"), rs.getString("end_time")!=null?rs.getString("end_time"):"—"
            });
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }
}
