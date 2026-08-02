package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * PayrollDAO — Kenya statutory payroll calculations.
 *
 * Deductions applied, verified current as of July 2026:
 *   NSSF Tier I  : 6% of gross up to KES 9,000 (max KES 540) — Year 4 rates, effective Feb 2026
 *   NSSF Tier II : 6% of gross from KES 9,001 to KES 108,000 (max KES 5,940) — Year 4 rates, effective Feb 2026
 *   SHIF         : 2.75% of gross, minimum KES 300, no upper cap — replaced NHIF entirely on 1 Oct 2024.
 *                  Deductible before PAYE (reduces taxable income), consistently confirmed across sources.
 *   Housing Levy : 1.5% of gross (AHL), employer matches 1.5%. In force since July 2023.
 *                  NOTE: whether AHL reduces PAYE's taxable income base is genuinely disputed across
 *                  secondary sources as of this writing — some say a Dec 2024 amendment made it
 *                  deductible, others say an earlier relief was repealed and it is NOT deductible,
 *                  calculated on remaining taxable salary instead. Implemented here as NOT reducing
 *                  taxable income (the more specific, more consistently-cited claim), but this is a
 *                  genuinely contested point — confirm the current treatment with KRA or an accountant
 *                  before relying on this for real payroll filing.
 *   NITA         : KES 50 flat (National Industrial Training Authority)
 *   PAYE         : Progressive tax on taxable income after NSSF + SHIF deductions
 *                  Personal relief: KES 2,400/month
 */
public class PayrollDAO {

    private Connection conn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS payroll_runs (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    run_number      TEXT NOT NULL UNIQUE,
                    month           TEXT NOT NULL,
                    year            INTEGER NOT NULL,
                    total_gross     REAL DEFAULT 0,
                    total_nssf      REAL DEFAULT 0,
                    total_nhif      REAL DEFAULT 0,
                    total_paye      REAL DEFAULT 0,
                    total_nita      REAL DEFAULT 0,
                    total_net       REAL DEFAULT 0,
                    employee_count  INTEGER DEFAULT 0,
                    status          TEXT DEFAULT 'draft'
                                        CHECK(status IN ('draft','approved','paid')),
                    created_by      INTEGER REFERENCES users(id),
                    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS payroll_entries (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    run_id          INTEGER NOT NULL REFERENCES payroll_runs(id) ON DELETE CASCADE,
                    employee_id     INTEGER NOT NULL REFERENCES employees(id),
                    employee_name   TEXT NOT NULL,
                    employee_number TEXT NOT NULL,
                    department      TEXT,
                    position        TEXT,
                    gross_salary    REAL NOT NULL,
                    nssf_employee   REAL NOT NULL DEFAULT 0,
                    nssf_employer   REAL NOT NULL DEFAULT 0,
                    nhif            REAL NOT NULL DEFAULT 0,
                    nita            REAL NOT NULL DEFAULT 0,
                    paye            REAL NOT NULL DEFAULT 0,
                    total_deductions REAL NOT NULL DEFAULT 0,
                    net_pay         REAL NOT NULL DEFAULT 0,
                    allowances      REAL NOT NULL DEFAULT 0,
                    deductions_other REAL NOT NULL DEFAULT 0,
                    notes           TEXT
                )
            """);
            // Additive migration: SHIF replaced NHIF (Oct 2024) and the Housing Levy (in force since
            // Jul 2023) was never tracked at all. Added as new columns rather than renaming/dropping
            // the old 'nhif' ones, following the same idempotent-migration pattern used elsewhere in
            // this codebase (loyalty_points, delivery_charge, etc.) — 'nhif' stays present for any
            // historical rows but is no longer populated with new data going forward.
            for (String stmt : new String[]{
                "ALTER TABLE payroll_entries ADD COLUMN shif REAL NOT NULL DEFAULT 0",
                "ALTER TABLE payroll_entries ADD COLUMN housing_levy REAL NOT NULL DEFAULT 0",
                "ALTER TABLE payroll_entries ADD COLUMN housing_levy_employer REAL NOT NULL DEFAULT 0",
                "ALTER TABLE payroll_runs ADD COLUMN total_shif REAL DEFAULT 0",
                "ALTER TABLE payroll_runs ADD COLUMN total_housing_levy REAL DEFAULT 0",
            }) {
                try { s.execute(stmt); } catch (SQLException ignored) { /* already exists on repeat runs */ }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Kenya Tax Calculations (rates are configurable — see PayrollTaxConfig, Settings → Payroll Tax) ──

    /** NSSF employee contribution (Tier I + Tier II), using whatever rates are currently configured. */
    public static double calculateNSSF(double gross) {
        com.kaziflow.services.PayrollTaxConfig cfg = com.kaziflow.services.PayrollTaxConfig.getInstance();
        double rate = cfg.getNssfRatePercent() / 100.0;
        double tierI  = Math.min(gross * rate, cfg.getNssfLel() * rate);
        double tierII = Math.min(Math.max(gross - cfg.getNssfLel(), 0) * rate, (cfg.getNssfUel() - cfg.getNssfLel()) * rate);
        return Math.round((tierI + tierII) * 100.0) / 100.0;
    }

    /** NSSF employer contribution — matches employee */
    public static double calculateNSSFEmployer(double gross) {
        return calculateNSSF(gross);
    }

    /** SHIF (Social Health Insurance Fund) — replaced NHIF entirely on 1 October 2024. Rate/minimum are configurable. */
    public static double calculateSHIF(double gross) {
        com.kaziflow.services.PayrollTaxConfig cfg = com.kaziflow.services.PayrollTaxConfig.getInstance();
        double shif = gross * (cfg.getShifRatePercent() / 100.0);
        return Math.round(Math.max(shif, cfg.getShifMinimum()) * 100.0) / 100.0;
    }

    /** Affordable Housing Levy (AHL) — employee share. Rate is configurable. */
    public static double calculateHousingLevy(double gross) {
        double rate = com.kaziflow.services.PayrollTaxConfig.getInstance().getHousingLevyRatePercent() / 100.0;
        return Math.round(gross * rate * 100.0) / 100.0;
    }

    /** Affordable Housing Levy — employer share, matches employee. */
    public static double calculateHousingLevyEmployer(double gross) {
        return calculateHousingLevy(gross);
    }

    /**
     * PAYE — progressive tax on configurable bands (Settings → Payroll Tax → PAYE Bands).
     * Whether Housing Levy reduces taxable income is also a configurable choice — see
     * PayrollTaxConfig and this class's header for why that specific point is genuinely
     * disputed in current guidance rather than settled.
     */
    public static double calculatePAYE(double gross) {
        com.kaziflow.services.PayrollTaxConfig cfg = com.kaziflow.services.PayrollTaxConfig.getInstance();
        double nssf = calculateNSSF(gross);
        double shif = calculateSHIF(gross);
        double taxable = gross - nssf - shif;
        if (cfg.isHousingLevyReducesTaxableIncome()) {
            taxable -= calculateHousingLevy(gross);
        }

        double tax = 0;
        double previousBound = 0;
        double cumulativeTax = 0;
        for (com.kaziflow.services.PayrollTaxConfig.PayeBand band : cfg.getPayeBands()) {
            if (taxable <= band.upperBound()) {
                tax = cumulativeTax + (taxable - previousBound) * (band.ratePercent() / 100.0);
                break;
            }
            cumulativeTax += (band.upperBound() - previousBound) * (band.ratePercent() / 100.0);
            previousBound = band.upperBound();
        }
        if (tax == 0 && !cfg.getPayeBands().isEmpty() && taxable > previousBound) {
            // taxable income exceeded even the last configured band's upper bound (shouldn't
            // normally happen since the default top band's bound is set absurdly high, but a
            // user could configure a lower final bound) — apply the last band's rate to the remainder
            var lastBand = cfg.getPayeBands().get(cfg.getPayeBands().size() - 1);
            tax = cumulativeTax + (taxable - previousBound) * (lastBand.ratePercent() / 100.0);
        }

        // Personal relief — configurable
        tax = Math.max(tax - cfg.getPersonalReliefMonthly(), 0);
        return Math.round(tax * 100.0) / 100.0;
    }

    /** NITA — configurable flat rate, defaults to KES 50 */
    public static double getNita() {
        return com.kaziflow.services.PayrollTaxConfig.getInstance().getNitaFlat();
    }

    /**
     * Full payslip calculation for one employee.
     * Returns [gross, nssfEmployee, nssfEmployer, shif, paye, nita, housingLevy, housingLevyEmployer, totalDeductions, netPay].
     */
    public static double[] calculate(double gross, double allowances, double otherDeductions) {
        double totalGross   = gross + allowances;
        double nssf         = calculateNSSF(totalGross);
        double nssfEmployer = calculateNSSFEmployer(totalGross);
        double shif         = calculateSHIF(totalGross);
        double paye         = calculatePAYE(totalGross);
        double nita         = getNita();
        double housingLevy         = calculateHousingLevy(totalGross);
        double housingLevyEmployer = calculateHousingLevyEmployer(totalGross);
        double totalDeductions = nssf + shif + paye + nita + housingLevy + otherDeductions;
        double netPay       = totalGross - totalDeductions;
        return new double[]{totalGross, nssf, nssfEmployer, shif, paye, nita, housingLevy, housingLevyEmployer, totalDeductions, netPay};
    }

    // ── Payroll Run ───────────────────────────────────────────────────────

    /** Create a payroll run for the given month/year, calculating all active employees. */
    public int createRun(int month, int year, int createdBy) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                // Generate run number
                int count = 0;
                try (Statement s = c.createStatement()) {
                    ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM payroll_runs");
                    if (rs.next()) count = rs.getInt(1);
                }
                String monthStr = String.format("%04d-%02d", year, month);
                String runNum   = String.format("PAY-%04d", count + 1);

                // Create run header
                int runId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO payroll_runs (run_number,month,year,created_by) VALUES (?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, runNum);
                    ps.setString(2, monthStr);
                    ps.setInt(3, year);
                    ps.setInt(4, createdBy);
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    runId = keys.next() ? keys.getInt(1) : -1;
                }
                if (runId < 0) { c.rollback(); return -1; }

                // Process each active employee
                double totalGross = 0, totalNSSF = 0, totalSHIF = 0,
                       totalPAYE = 0, totalNITA = 0, totalHousingLevy = 0, totalNet = 0;
                int empCount = 0;

                try (Statement s = c.createStatement()) {
                    ResultSet rs = s.executeQuery("""
                        SELECT e.id, e.name, e.employee_number, d.name as dept, e.position, e.salary
                        FROM employees e
                        LEFT JOIN departments d ON d.id = e.department_id
                        WHERE e.status = 'active' AND e.salary > 0
                    """);
                    while (rs.next()) {
                        double gross  = rs.getDouble("salary");
                        double[] calc = calculate(gross, 0, 0);
                        // calc = [gross, nssfEmployee, nssfEmployer, shif, paye, nita, housingLevy, housingLevyEmployer, totalDeductions, netPay]

                        try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO payroll_entries
                            (run_id,employee_id,employee_name,employee_number,department,position,
                             gross_salary,nssf_employee,nssf_employer,shif,nita,paye,
                             housing_levy,housing_levy_employer,total_deductions,net_pay)
                            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """)) {
                            ps.setInt(1, runId);
                            ps.setInt(2, rs.getInt("id"));
                            ps.setString(3, rs.getString("name"));
                            ps.setString(4, rs.getString("employee_number"));
                            ps.setString(5, rs.getString("dept"));
                            ps.setString(6, rs.getString("position"));
                            ps.setDouble(7, calc[0]);  // gross
                            ps.setDouble(8, calc[1]);  // nssf employee
                            ps.setDouble(9, calc[2]);  // nssf employer
                            ps.setDouble(10, calc[3]); // shif
                            ps.setDouble(11, calc[5]); // nita
                            ps.setDouble(12, calc[4]); // paye
                            ps.setDouble(13, calc[6]); // housing levy employee
                            ps.setDouble(14, calc[7]); // housing levy employer
                            ps.setDouble(15, calc[8]); // total deductions
                            ps.setDouble(16, calc[9]); // net pay
                            ps.executeUpdate();
                        }

                        totalGross += calc[0]; totalNSSF += calc[1];
                        totalSHIF  += calc[3]; totalPAYE += calc[4];
                        totalNITA  += calc[5]; totalHousingLevy += calc[6];
                        totalNet   += calc[9];
                        empCount++;
                    }
                }

                // Update run totals
                try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE payroll_runs SET total_gross=?,total_nssf=?,total_shif=?,
                    total_paye=?,total_nita=?,total_housing_levy=?,total_net=?,employee_count=? WHERE id=?
                """)) {
                    ps.setDouble(1, totalGross); ps.setDouble(2, totalNSSF);
                    ps.setDouble(3, totalSHIF);  ps.setDouble(4, totalPAYE);
                    ps.setDouble(5, totalNITA);  ps.setDouble(6, totalHousingLevy);
                    ps.setDouble(7, totalNet);   ps.setInt(8, empCount); ps.setInt(9, runId);
                    ps.executeUpdate();
                }

                c.commit();
                return runId;
            } catch (Exception ex) {
                c.rollback(); ex.printStackTrace(); return -1;
            }
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public List<String[]> findAllRuns() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("""
                SELECT id, run_number, month, employee_count,
                       total_gross, total_net, total_paye, status, created_at
                FROM payroll_runs ORDER BY created_at DESC
            """);
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("run_number"),
                    rs.getString("month"),
                    String.valueOf(rs.getInt("employee_count")),
                    String.format("%.2f", rs.getDouble("total_gross")),
                    String.format("%.2f", rs.getDouble("total_net")),
                    String.format("%.2f", rs.getDouble("total_paye")),
                    rs.getString("status"),
                    rs.getString("created_at")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public List<String[]> getEntries(int runId) {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM payroll_entries WHERE run_id=? ORDER BY employee_name")) {
            ps.setInt(1, runId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("employee_id")),
                    rs.getString("employee_number"),
                    rs.getString("employee_name"),
                    rs.getString("department") != null ? rs.getString("department") : "—",
                    rs.getString("position") != null ? rs.getString("position") : "—",
                    String.format("%.2f", rs.getDouble("gross_salary")),
                    String.format("%.2f", rs.getDouble("nssf_employee")),
                    String.format("%.2f", rs.getDouble("shif")),
                    String.format("%.2f", rs.getDouble("paye")),
                    String.format("%.2f", rs.getDouble("nita")),
                    String.format("%.2f", rs.getDouble("total_deductions")),
                    String.format("%.2f", rs.getDouble("net_pay")),
                    String.format("%.2f", rs.getDouble("housing_levy")) // appended, not inserted — keeps every existing index stable for callers
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public boolean approveRun(int runId) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE payroll_runs SET status='approved' WHERE id=?")) {
            ps.setInt(1, runId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public boolean markPaid(int runId) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE payroll_runs SET status='paid' WHERE id=?")) {
            ps.setInt(1, runId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public String[] getRunById(int runId) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM payroll_runs WHERE id=?")) {
            ps.setInt(1, runId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("run_number"),
                    rs.getString("month"),
                    String.valueOf(rs.getInt("employee_count")),
                    String.format("%.2f", rs.getDouble("total_gross")),
                    String.format("%.2f", rs.getDouble("total_nssf")),
                    String.format("%.2f", rs.getDouble("total_shif")),
                    String.format("%.2f", rs.getDouble("total_paye")),
                    String.format("%.2f", rs.getDouble("total_nita")),
                    String.format("%.2f", rs.getDouble("total_net")),
                    rs.getString("status"),
                    String.format("%.2f", rs.getDouble("total_housing_levy")) // appended, not inserted
                };
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }
}
