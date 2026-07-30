package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DeliveryDAO — delivery management (Phase 10B).
 *
 * Pricing model, per real-world Kenyan delivery pricing research: a flat
 * per-km rate for the whole business (the common baseline), PLUS optional
 * fixed-price overrides for specific named/frequent locations (mirroring how
 * real Kenyan couriers use zone pricing for well-known destinations rather
 * than always calculating from scratch), PLUS an optional per-kg surcharge
 * for heavier parcels (weight is a real factor alongside distance).
 *
 * All settings are editable in Settings → Delivery; a named location with
 * override_price set always wins over the per-km calculation for that
 * location, since it represents a known, negotiated rate.
 */
public class DeliveryDAO {

    private Connection conn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS delivery_locations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    distance_km REAL NOT NULL,
                    override_price REAL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS deliveries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    delivery_number TEXT NOT NULL UNIQUE,
                    sale_id INTEGER REFERENCES sales(id),
                    customer_name TEXT NOT NULL,
                    customer_phone TEXT,
                    location_name TEXT,
                    distance_km REAL NOT NULL DEFAULT 0,
                    weight_kg REAL NOT NULL DEFAULT 0,
                    charge REAL NOT NULL DEFAULT 0,
                    rider_name TEXT,
                    rider_phone TEXT,
                    status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','dispatched','delivered','cancelled')),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    dispatched_at DATETIME,
                    delivered_at DATETIME
                )
            """);
            // Additive column on the existing sales table so a delivery charge folds
            // straight into the sale total, the same way discount_amount already does.
            try { s.execute("ALTER TABLE sales ADD COLUMN delivery_charge REAL NOT NULL DEFAULT 0"); }
            catch (SQLException ignored) { /* already exists on repeat runs */ }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ── Pricing settings ────────────────────────────────────────────────

    public double getRatePerKm() {
        return getSetting("delivery_rate_per_km", 50.0); // a reasonable Nairobi-boda-boda-ish default, always editable
    }

    public double getRatePerKg() {
        return getSetting("delivery_rate_per_kg", 0.0); // opt-in — 0 means weight doesn't affect price unless the business sets one
    }

    public void saveRates(double perKm, double perKg) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES (?,?)")) {
            ps.setString(1, "delivery_rate_per_km"); ps.setString(2, String.valueOf(perKm)); ps.executeUpdate();
            ps.setString(1, "delivery_rate_per_kg"); ps.setString(2, String.valueOf(perKg)); ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private double getSetting(String key, double fallback) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT value FROM settings WHERE key=?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getString("value") != null) return Double.parseDouble(rs.getString("value"));
        } catch (Exception ignored) {}
        return fallback;
    }

    /**
     * Computes the delivery charge for a distance/weight — a named location's
     * override_price always wins if provided, since it represents an already
     * agreed/known rate rather than a calculated estimate.
     */
    public double calculateCharge(Double overridePrice, double distanceKm, double weightKg) {
        if (overridePrice != null) return overridePrice;
        return (distanceKm * getRatePerKm()) + (weightKg * getRatePerKg());
    }

    // ── Named locations ──────────────────────────────────────────────────

    public boolean addLocation(String name, double distanceKm, Double overridePrice) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO delivery_locations (name, distance_km, override_price) VALUES (?,?,?)")) {
            ps.setString(1, name);
            ps.setDouble(2, distanceKm);
            if (overridePrice != null) ps.setDouble(3, overridePrice); else ps.setNull(3, Types.REAL);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<String[]> findAllLocations() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM delivery_locations ORDER BY name")) {
            while (rs.next()) {
                Double override = rs.getObject("override_price") != null ? rs.getDouble("override_price") : null;
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")), rs.getString("name"),
                    String.format("%.1f", rs.getDouble("distance_km")),
                    override != null ? String.format("%.2f", override) : "—"
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean deleteLocation(int id) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("DELETE FROM delivery_locations WHERE id=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ── Deliveries ───────────────────────────────────────────────────────

    public int createDelivery(Integer saleId, String customerName, String phone, String locationName,
                               double distanceKm, double weightKg, double charge) {
        String deliveryNumber = "DEL-" + System.currentTimeMillis();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO deliveries (delivery_number, sale_id, customer_name, customer_phone, location_name, distance_km, weight_kg, charge) VALUES (?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, deliveryNumber);
            if (saleId != null) ps.setInt(2, saleId); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, customerName);
            ps.setString(4, phone);
            ps.setString(5, locationName);
            ps.setDouble(6, distanceKm);
            ps.setDouble(7, weightKg);
            ps.setDouble(8, charge);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (SQLException e) { e.printStackTrace(); return -1; }
    }

    public boolean assignRider(int deliveryId, String riderName, String riderPhone) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE deliveries SET rider_name=?, rider_phone=?, status='dispatched', dispatched_at=CURRENT_TIMESTAMP WHERE id=?")) {
            ps.setString(1, riderName); ps.setString(2, riderPhone); ps.setInt(3, deliveryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean markDelivered(int deliveryId) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE deliveries SET status='delivered', delivered_at=CURRENT_TIMESTAMP WHERE id=?")) {
            ps.setInt(1, deliveryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean cancelDelivery(int deliveryId) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE deliveries SET status='cancelled' WHERE id=?")) {
            ps.setInt(1, deliveryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<String[]> findAllDeliveries() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM deliveries ORDER BY created_at DESC")) {
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")), rs.getString("delivery_number"), rs.getString("customer_name"),
                    rs.getString("customer_phone"), rs.getString("location_name"),
                    String.format("%.1f", rs.getDouble("distance_km")), String.format("%.2f", rs.getDouble("charge")),
                    rs.getString("rider_name") != null ? rs.getString("rider_name") : "—", rs.getString("status")
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}
