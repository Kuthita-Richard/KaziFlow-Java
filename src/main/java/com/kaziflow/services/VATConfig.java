package com.kaziflow.services;

import com.kaziflow.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * VATConfig — the missing piece behind Settings → Tax Settings' VAT Rate
 * field. That field already saved to settings.vat_rate and its own label
 * claimed "Changing this affects all new receipts" — but nothing except
 * ReceiptPrinter's display text ever actually read the value back. Every
 * real calculation (SalesView checkout, QuotationView, RestaurantDAO,
 * ReportExportService, FinanceView) hardcoded 0.16 directly, so changing
 * the setting did nothing to actual totals. This closes that gap: every
 * one of those sites now reads from here instead.
 *
 * Also supports being switched off entirely (vatEnabled=false) for a
 * business that isn't VAT-registered, rather than assuming every business
 * using this app charges VAT.
 */
public class VATConfig {

    private static VATConfig instance;
    public static synchronized VATConfig getInstance() {
        if (instance == null) instance = new VATConfig();
        return instance;
    }

    private double ratePercent = 16.0; // Kenya's standard rate — the long-standing default this app already assumed
    private boolean enabled = true;

    private VATConfig() { reload(); }

    public void reload() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             java.sql.Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT key, value FROM settings WHERE key IN ('vat_rate','vat_enabled')")) {
            while (rs.next()) {
                String key = rs.getString("key");
                String value = rs.getString("value");
                if (value == null) continue;
                try {
                    if ("vat_rate".equals(key)) ratePercent = Double.parseDouble(value);
                    else if ("vat_enabled".equals(key)) enabled = Boolean.parseBoolean(value);
                } catch (NumberFormatException ignored) { /* keep previous value rather than crash on a malformed setting */ }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void save(double ratePercent, boolean enabled) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?,?)")) {
            ps.setString(1, "vat_rate"); ps.setString(2, String.valueOf(ratePercent)); ps.executeUpdate();
            ps.setString(1, "vat_enabled"); ps.setString(2, String.valueOf(enabled)); ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
        reload();
    }

    /** The rate as a decimal multiplier (e.g. 0.16) — what every calculation site actually needs. */
    public double getRate() {
        return enabled ? ratePercent / 100.0 : 0.0;
    }

    public double getRatePercent() { return ratePercent; }
    public boolean isEnabled() { return enabled; }
}
