package com.kaziflow.services;

import com.kaziflow.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Local receipt numbering, and the slot where REAL eTIMS submission will go.
 *
 * <p><b>This does NOT talk to KRA.</b> Nothing here is transmitted, and nothing here is issued by KRA. The numbers
 * it generates are an internal counter, labelled {@code LOCAL-<year>-<sequence>} so that nobody — a cashier, a
 * customer, an auditor — can mistake them for a KRA control-unit / invoice number. (An earlier version formatted
 * them as {@code KRA-<year>-<sequence>} and printed them on receipts as "ETIMS Serial", which looked like a tax
 * document number issued by the revenue authority. That was wrong and has been removed.)
 *
 * <p>Rows have a {@code status}: {@code local} (internal counter only) or, once real integration exists,
 * {@code transmitted} / {@code verified}. Only the latter may ever be printed as an eTIMS number — see
 * {@link #getTransmittedSerial(int)}. Real submission needs KRA onboarding (OSCU/VSCU, credentials, certification);
 * see docs/design/product-depth-plan.md, release R4.
 */
public class ETIMSService {

    /** Prefix of the internal counter. Deliberately NOT "KRA": these numbers are not issued by KRA. */
    public static final String LOCAL_PREFIX = "LOCAL";
    public static final String STATUS_LOCAL = "local";
    /** Statuses meaning KRA genuinely received/accepted the invoice (none can occur until real integration exists). */
    public static final java.util.Set<String> TRANSMITTED_STATUSES = java.util.Set.of("transmitted", "verified");

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
                String serial = String.format("%s-%s-%06d", LOCAL_PREFIX, year, seq);

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

    /**
     * Whether KaziFlow submits invoices to KRA. Currently false. UI that talks about eTIMS must use this rather than
     * assume — flip it only when real transmission (with KRA's response stored) is implemented and certified.
     */
    public boolean isConnected() { return false; }

    /**
     * The eTIMS invoice number for a sale ONLY if KRA actually received it; otherwise null. Receipts print an eTIMS
     * number only when this is non-null, so a purely local counter can never appear as a tax-authority number.
     */
    public String getTransmittedSerial(int saleId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT serial, status FROM etims_serials WHERE sale_id=?")) {
            ps.setInt(1, saleId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && TRANSMITTED_STATUSES.contains(String.valueOf(rs.getString("status")).toLowerCase())) return rs.getString("serial");
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
            // One-time, idempotent relabel of numbers issued by the old version: anything still 'local' that was
            // formatted like a KRA serial becomes LOCAL-…, so old receipts that are reprinted or looked up don't show one.
            s.execute("UPDATE sales SET etims_serial = '" + LOCAL_PREFIX + "-' || substr(etims_serial, 5) "
                    + "WHERE etims_serial LIKE 'KRA-%' AND id IN (SELECT sale_id FROM etims_serials WHERE status = '" + STATUS_LOCAL + "')");
            s.execute("UPDATE etims_serials SET serial = '" + LOCAL_PREFIX + "-' || substr(serial, 5) "
                    + "WHERE serial LIKE 'KRA-%' AND status = '" + STATUS_LOCAL + "'");
        } catch (Exception e) { e.printStackTrace(); }
    }
}
