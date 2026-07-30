package com.kaziflow.services;

import com.kaziflow.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ETIMS (Electronic Tax Invoice Management System) service.
 *
 * This is a compliance-ready stub. When KRA provides production API
 * credentials, replace generateSerial() with a real API call to:
 *   POST https://etims.kra.go.ke/api/v1/invoice
 *
 * Until then it generates a locally sequential serial that mirrors the
 * expected format, stored in the etims_serials table.
 *
 * Format: KRA-{year}-{6-digit-sequence}
 * Example: KRA-2024-000142
 */
public class ETIMSService {

    private static ETIMSService instance;

    private ETIMSService() {
        ensureTable();
    }

    public static ETIMSService getInstance() {
        if (instance == null) instance = new ETIMSService();
        return instance;
    }

    /**
     * Generate and persist an ETIMS serial for a completed sale.
     * @param saleId  the sale.id this serial belongs to
     * @param saleNumber  the human-readable sale number
     * @return the generated serial string, or "ETIMS-PENDING" on failure
     */
    public String generateSerial(int saleId, String saleNumber) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Get next sequence
                int seq = 1;
                try (Statement st = conn.createStatement()) {
                    ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(sequence),0)+1 FROM etims_serials");
                    if (rs.next()) seq = rs.getInt(1);
                }

                String year   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy"));
                String serial = String.format("KRA-%s-%06d", year, seq);

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO etims_serials (serial, sale_id, sale_number, sequence, status) VALUES (?,?,?,?,?)")) {
                    ps.setString(1, serial);
                    ps.setInt(2, saleId);
                    ps.setString(3, saleNumber);
                    ps.setInt(4, seq);
                    ps.setString(5, "local"); // "local" until KRA API confirms
                    ps.executeUpdate();
                }

                // Stamp the sale record
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE sales SET etims_serial=? WHERE id=?")) {
                    ps.setString(1, serial);
                    ps.setInt(2, saleId);
                    ps.executeUpdate();
                }

                conn.commit();
                return serial;
            } catch (Exception ex) {
                conn.rollback();
                ex.printStackTrace();
                return "ETIMS-PENDING";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "ETIMS-PENDING";
        }
    }

    /** Get the stored ETIMS serial for a sale, or null if none */
    public String getSerial(int saleId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT serial FROM etims_serials WHERE sale_id=?")) {
            ps.setInt(1, saleId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("serial");
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public int getTotalIssuedToday() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT COUNT(*) FROM etims_serials WHERE DATE(created_at)=DATE('now')");
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    private void ensureTable() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement s = conn.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS etims_serials (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    serial TEXT NOT NULL UNIQUE,
                    sale_id INTEGER REFERENCES sales(id),
                    sale_number TEXT,
                    sequence INTEGER NOT NULL,
                    status TEXT DEFAULT 'local',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            // Add etims_serial column to sales table if not present
            try { s.execute("ALTER TABLE sales ADD COLUMN etims_serial TEXT"); } catch (Exception ignored) {}
        } catch (Exception e) { e.printStackTrace(); }
    }
}
