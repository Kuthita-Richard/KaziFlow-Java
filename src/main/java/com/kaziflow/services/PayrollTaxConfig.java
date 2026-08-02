package com.kaziflow.services;

import com.kaziflow.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * PayrollTaxConfig — every Kenya statutory payroll rate, made editable
 * instead of hardcoded (Phase 10E follow-up). Defaults below reflect rates
 * verified current as of July 2026 (NSSF Year 4 from Feb 2026, SHIF since
 * Oct 2024 replacing NHIF, Housing Levy since Jul 2023) — but these numbers
 * have changed multiple times in the last two years and will change again,
 * so nothing here should ever require a code update to adjust. Business
 * owners (or their accountant) can update every figure from Settings →
 * Payroll Tax Settings.
 *
 * PAYE bands are stored as a single CSV string: "upperBound:ratePercent,..."
 * with the final band's upperBound conventionally set very high (no
 * practical ceiling) to represent "and above."
 */
public class PayrollTaxConfig {

    public record PayeBand(double upperBound, double ratePercent) {}

    private static PayrollTaxConfig instance;
    public static synchronized PayrollTaxConfig getInstance() {
        if (instance == null) instance = new PayrollTaxConfig();
        return instance;
    }

    // NSSF
    private double nssfLel = 9000;       // Tier I lower/only bound
    private double nssfUel = 108000;     // Tier II upper bound
    private double nssfRatePercent = 6;

    // SHIF
    private double shifRatePercent = 2.75;
    private double shifMinimum = 300;

    // Housing Levy (AHL)
    private double housingLevyRatePercent = 1.5;
    // Whether AHL reduces PAYE's taxable income — genuinely disputed across sources as of
    // this writing (see PayrollDAO's class header). Defaults to false (not deductible), the
    // more specific/consistently-cited claim, but the business can flip this if their
    // accountant confirms otherwise for their filing period.
    private boolean housingLevyReducesTaxableIncome = false;

    // NITA & personal relief
    private double nitaFlat = 50;
    private double personalReliefMonthly = 2400;

    // PAYE bands — defaults match the 2024/2025/2026 bands (unchanged across that whole period, per research)
    private List<PayeBand> payeBands = List.of(
        new PayeBand(24000, 10),
        new PayeBand(32333, 25),
        new PayeBand(500000, 30),
        new PayeBand(800000, 32.5),
        new PayeBand(999_999_999, 35)
    );

    private PayrollTaxConfig() { reload(); }

    public void reload() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT key, value FROM settings WHERE key LIKE 'payroll_tax_%'")) {
            while (rs.next()) {
                String key = rs.getString("key");
                String value = rs.getString("value");
                if (value == null) continue;
                try {
                    switch (key) {
                        case "payroll_tax_nssf_lel" -> nssfLel = Double.parseDouble(value);
                        case "payroll_tax_nssf_uel" -> nssfUel = Double.parseDouble(value);
                        case "payroll_tax_nssf_rate" -> nssfRatePercent = Double.parseDouble(value);
                        case "payroll_tax_shif_rate" -> shifRatePercent = Double.parseDouble(value);
                        case "payroll_tax_shif_min" -> shifMinimum = Double.parseDouble(value);
                        case "payroll_tax_housing_rate" -> housingLevyRatePercent = Double.parseDouble(value);
                        case "payroll_tax_housing_reduces_taxable" -> housingLevyReducesTaxableIncome = Boolean.parseBoolean(value);
                        case "payroll_tax_nita" -> nitaFlat = Double.parseDouble(value);
                        case "payroll_tax_relief" -> personalReliefMonthly = Double.parseDouble(value);
                        case "payroll_tax_paye_bands" -> payeBands = parseBands(value);
                    }
                } catch (Exception ignored) { /* malformed value — keep the previous/default one rather than crash payroll */ }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private List<PayeBand> parseBands(String csv) {
        List<PayeBand> bands = new ArrayList<>();
        for (String pair : csv.split(",")) {
            String[] parts = pair.split(":");
            if (parts.length != 2) continue;
            bands.add(new PayeBand(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())));
        }
        return bands.isEmpty() ? payeBands : bands; // never accept an empty band list — payroll would break
    }

    private String bandsToCsv(List<PayeBand> bands) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bands.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(bands.get(i).upperBound()).append(":").append(bands.get(i).ratePercent());
        }
        return sb.toString();
    }

    public void save(double nssfLel, double nssfUel, double nssfRate, double shifRate, double shifMin,
                      double housingRate, boolean housingReducesTaxable, double nita, double relief, List<PayeBand> bands) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?,?)")) {
            Object[][] kv = {
                {"payroll_tax_nssf_lel", nssfLel}, {"payroll_tax_nssf_uel", nssfUel}, {"payroll_tax_nssf_rate", nssfRate},
                {"payroll_tax_shif_rate", shifRate}, {"payroll_tax_shif_min", shifMin},
                {"payroll_tax_housing_rate", housingRate}, {"payroll_tax_housing_reduces_taxable", housingReducesTaxable},
                {"payroll_tax_nita", nita}, {"payroll_tax_relief", relief},
                {"payroll_tax_paye_bands", bandsToCsv(bands)},
            };
            for (Object[] pair : kv) {
                ps.setString(1, (String) pair[0]);
                ps.setString(2, String.valueOf(pair[1]));
                ps.executeUpdate();
            }
        } catch (Exception e) { e.printStackTrace(); }
        reload();
    }

    /** Restores every rate to the current-as-of-July-2026 defaults documented above. */
    public void resetToDefaults() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement s = conn.createStatement()) {
            s.execute("DELETE FROM settings WHERE key LIKE 'payroll_tax_%'");
        } catch (Exception e) { e.printStackTrace(); }
        // Reset in-memory fields to their original hardcoded defaults, since reload() only
        // overwrites fields it finds a row for — a deleted row wouldn't otherwise revert one.
        nssfLel = 9000; nssfUel = 108000; nssfRatePercent = 6;
        shifRatePercent = 2.75; shifMinimum = 300;
        housingLevyRatePercent = 1.5; housingLevyReducesTaxableIncome = false;
        nitaFlat = 50; personalReliefMonthly = 2400;
        payeBands = List.of(
            new PayeBand(24000, 10), new PayeBand(32333, 25), new PayeBand(500000, 30),
            new PayeBand(800000, 32.5), new PayeBand(999_999_999, 35)
        );
    }

    // ── Getters ──────────────────────────────────────────────────────────
    public double getNssfLel() { return nssfLel; }
    public double getNssfUel() { return nssfUel; }
    public double getNssfRatePercent() { return nssfRatePercent; }
    public double getShifRatePercent() { return shifRatePercent; }
    public double getShifMinimum() { return shifMinimum; }
    public double getHousingLevyRatePercent() { return housingLevyRatePercent; }
    public boolean isHousingLevyReducesTaxableIncome() { return housingLevyReducesTaxableIncome; }
    public double getNitaFlat() { return nitaFlat; }
    public double getPersonalReliefMonthly() { return personalReliefMonthly; }
    public List<PayeBand> getPayeBands() { return payeBands; }
}
