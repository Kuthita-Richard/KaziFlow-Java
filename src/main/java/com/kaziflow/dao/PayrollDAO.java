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
 * Deductions applied (2024/2025 Kenya rates):
 *   NSSF Tier I  : 6% of gross up to KES 7,000 (max KES 420)
 *   NSSF Tier II : 6% of gross from KES 7,001 to KES 36,000 (max KES 1,740)
 *   NHIF         : Graduated scale KES 150 – 1,700 based on gross
 *   NITA         : KES 50 flat (National Industrial Training Authority)
 *   PAYE         : Progressive tax on taxable income after deductions
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
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Kenya Tax Calculations ────────────────────────────────────────────

    /** NSSF employee contribution (Tier I + Tier II) */
    public static double calculateNSSF(double gross) {
        double tierI  = Math.min(gross * 0.06, 420);           // max KES 420
        double tierII = Math.min(Math.max(gross - 7000, 0) * 0.06, 1740); // max KES 1,740
        return Math.round((tierI + tierII) * 100.0) / 100.0;
    }

    /** NSSF employer contribution — matches employee */
    public static double calculateNSSFEmployer(double gross) {
        return calculateNSSF(gross);
    }

    /** NHIF contribution — 2024 graduated scale */
    public static double calculateNHIF(double gross) {
        if (gross <= 5999)   return 150;
        if (gross <= 7999)   return 300;
        if (gross <= 11999)  return 400;
        if (gross <= 14999)  return 500;
        if (gross <= 19999)  return 600;
        if (gross <= 24999)  return 750;
        if (gross <= 29999)  return 850;
        if (gross <= 34999)  return 900;
        if (gross <= 39999)  return 950;
        if (gross <= 44999)  return 1000;
        if (gross <= 49999)  return 1100;
        if (gross <= 59999)  return 1200;
        if (gross <= 69999)  return 1300;
        if (gross <= 79999)  return 1400;
        if (gross <= 89999)  return 1500;
        if (gross <= 99999)  return 1600;
        return 1700;
    }

    /** PAYE — 2024 Kenya progressive tax bands */
    public static double calculatePAYE(double gross) {
        // Taxable income = gross - NSSF employee - NHIF (partial deductible)
        double nssf = calculateNSSF(gross);
        double taxable = gross - nssf;

        double tax = 0;
        if (taxable <= 24000)       tax = taxable * 0.10;
        else if (taxable <= 32333)  tax = 2400 + (taxable - 24000) * 0.25;
        else if (taxable <= 500000) tax = 4483 + (taxable - 32333) * 0.30;
        else if (taxable <= 800000) tax = 144716 + (taxable - 500000) * 0.325;
        else                        tax = 242216 + (taxable - 800000) * 0.35;

        // Personal relief KES 2,400/month
        tax = Math.max(tax - 2400, 0);
        return Math.round(tax * 100.0) / 100.0;
    }

    /** NITA flat rate */
    public static double NITA = 50.0;

    /** Full payslip calculation for one employee */
    public static double[] calculate(double gross, double allowances, double otherDeductions) {
        double totalGross   = gross + allowances;
        double nssf         = calculateNSSF(totalGross);
        double nssfEmployer = calculateNSSFEmployer(totalGross);
        double nhif         = calculateNHIF(totalGross);
        double paye         = calculatePAYE(totalGross);
        double totalDeductions = nssf + nhif + paye + NITA + otherDeductions;
        double netPay       = totalGross - totalDeductions;
        // [gross, nssf, nssfEmployer, nhif, paye, nita, totalDeductions, netPay]
        return new double[]{totalGross, nssf, nssfEmployer, nhif, paye, NITA, totalDeductions, netPay};
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
                double totalGross = 0, totalNSSF = 0, totalNHIF = 0,
                       totalPAYE = 0, totalNITA = 0, totalNet = 0;
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

                        try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO payroll_entries
                            (run_id,employee_id,employee_name,employee_number,department,position,
                             gross_salary,nssf_employee,nssf_employer,nhif,nita,paye,
                             total_deductions,net_pay)
                            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
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
                            ps.setDouble(10, calc[3]); // nhif
                            ps.setDouble(11, calc[4]); // paye
                            ps.setDouble(12, calc[5]); // nita
                            ps.setDouble(13, calc[6]); // total deductions
                            ps.setDouble(14, calc[7]); // net pay
                            ps.executeUpdate();
                        }

                        totalGross += calc[0]; totalNSSF += calc[1];
                        totalNHIF  += calc[3]; totalPAYE += calc[4];
                        totalNITA  += calc[5]; totalNet  += calc[7];
                        empCount++;
                    }
                }

                // Update run totals
                try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE payroll_runs SET total_gross=?,total_nssf=?,total_nhif=?,
                    total_paye=?,total_nita=?,total_net=?,employee_count=? WHERE id=?
                """)) {
                    ps.setDouble(1, totalGross); ps.setDouble(2, totalNSSF);
                    ps.setDouble(3, totalNHIF);  ps.setDouble(4, totalPAYE);
                    ps.setDouble(5, totalNITA);  ps.setDouble(6, totalNet);
                    ps.setInt(7, empCount);      ps.setInt(8, runId);
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
                    String.format("%.2f", rs.getDouble("nhif")),
                    String.format("%.2f", rs.getDouble("paye")),
                    String.format("%.2f", rs.getDouble("nita")),
                    String.format("%.2f", rs.getDouble("total_deductions")),
                    String.format("%.2f", rs.getDouble("net_pay"))
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
                    String.format("%.2f", rs.getDouble("total_nhif")),
                    String.format("%.2f", rs.getDouble("total_paye")),
                    String.format("%.2f", rs.getDouble("total_nita")),
                    String.format("%.2f", rs.getDouble("total_net")),
                    rs.getString("status")
                };
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }
}
