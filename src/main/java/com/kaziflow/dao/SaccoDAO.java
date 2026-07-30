package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * SaccoDAO — SACCO / SHG member management, loans, dividends, and
 * merry-go-round (chama) rotating contributions (Phase 10A).
 *
 * Loan interest: reducing balance (interest charged only on the outstanding
 * principal each period) — confirmed via research as the standard method
 * real Kenyan SACCOs use; flat-rate is what predatory mobile lenders use,
 * not what a credible SACCO module should default to.
 */
public class SaccoDAO {

    private Connection conn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS sacco_members (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    member_number TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    phone TEXT,
                    id_number TEXT,
                    share_capital REAL NOT NULL DEFAULT 0,
                    total_savings REAL NOT NULL DEFAULT 0,
                    status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','inactive')),
                    joined_date TEXT DEFAULT (date('now')),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS sacco_savings_transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    member_id INTEGER NOT NULL REFERENCES sacco_members(id),
                    type TEXT NOT NULL CHECK(type IN ('deposit','withdrawal','share_capital')),
                    amount REAL NOT NULL,
                    notes TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS sacco_loans (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    loan_number TEXT NOT NULL UNIQUE,
                    member_id INTEGER NOT NULL REFERENCES sacco_members(id),
                    principal REAL NOT NULL,
                    annual_rate REAL NOT NULL,
                    term_months INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','approved','rejected','disbursed','completed')),
                    purpose TEXT,
                    applied_date TEXT DEFAULT (date('now')),
                    disbursed_date TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS sacco_loan_repayments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    loan_id INTEGER NOT NULL REFERENCES sacco_loans(id) ON DELETE CASCADE,
                    installment_number INTEGER NOT NULL,
                    due_date TEXT NOT NULL,
                    principal_due REAL NOT NULL,
                    interest_due REAL NOT NULL,
                    total_due REAL NOT NULL,
                    amount_paid REAL NOT NULL DEFAULT 0,
                    paid_date TEXT,
                    status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','paid','partial','overdue'))
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS sacco_dividends (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    member_id INTEGER NOT NULL REFERENCES sacco_members(id),
                    year INTEGER NOT NULL,
                    based_on_shares REAL NOT NULL,
                    rate_percent REAL NOT NULL,
                    amount REAL NOT NULL,
                    paid_date TEXT DEFAULT (date('now')),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS sacco_mgr_groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    contribution_amount REAL NOT NULL,
                    frequency TEXT NOT NULL DEFAULT 'monthly' CHECK(frequency IN ('weekly','monthly')),
                    current_cycle INTEGER NOT NULL DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS sacco_mgr_members (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_id INTEGER NOT NULL REFERENCES sacco_mgr_groups(id) ON DELETE CASCADE,
                    member_id INTEGER NOT NULL REFERENCES sacco_members(id),
                    payout_position INTEGER NOT NULL
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS sacco_mgr_contributions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_id INTEGER NOT NULL REFERENCES sacco_mgr_groups(id),
                    member_id INTEGER NOT NULL REFERENCES sacco_members(id),
                    cycle_number INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    paid_date TEXT DEFAULT (date('now'))
                )
            """);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ── Members ─────────────────────────────────────────────────────────

    public int addMember(String name, String phone, String idNumber, double shareCapital) {
        String memberNumber = "MBR-" + System.currentTimeMillis();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO sacco_members (member_number, name, phone, id_number, share_capital) VALUES (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, memberNumber);
            ps.setString(2, name);
            ps.setString(3, phone);
            ps.setString(4, idNumber);
            ps.setDouble(5, shareCapital);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (SQLException e) { e.printStackTrace(); return -1; }
    }

    public List<String[]> findAllMembers() {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT * FROM sacco_members ORDER BY name";
        try (Connection c = conn(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")), rs.getString("member_number"), rs.getString("name"),
                    rs.getString("phone"), String.format("%.2f", rs.getDouble("share_capital")),
                    String.format("%.2f", rs.getDouble("total_savings")), rs.getString("status")
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /** Records a savings deposit/withdrawal and keeps the member's running total_savings in sync. */
    public boolean recordSavingsTransaction(int memberId, String type, double amount, String notes) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO sacco_savings_transactions (member_id, type, amount, notes) VALUES (?,?,?,?)")) {
                ps.setInt(1, memberId); ps.setString(2, type); ps.setDouble(3, amount); ps.setString(4, notes);
                ps.executeUpdate();
            }
            double delta = "withdrawal".equals(type) ? -amount : amount;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE sacco_members SET total_savings = total_savings + ? WHERE id = ?")) {
                ps.setDouble(1, delta); ps.setInt(2, memberId);
                ps.executeUpdate();
            }
            c.commit();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ── Loans (reducing balance) ──────────────────────────────────────────

    public int applyForLoan(int memberId, double principal, double annualRatePercent, int termMonths, String purpose) {
        String loanNumber = "LN-" + System.currentTimeMillis();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO sacco_loans (loan_number, member_id, principal, annual_rate, term_months, purpose) VALUES (?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, loanNumber);
            ps.setInt(2, memberId);
            ps.setDouble(3, principal);
            ps.setDouble(4, annualRatePercent);
            ps.setInt(5, termMonths);
            ps.setString(6, purpose);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (SQLException e) { e.printStackTrace(); return -1; }
    }

    public boolean updateLoanStatus(int loanId, String status) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE sacco_loans SET status=? WHERE id=?")) {
            ps.setString(1, status); ps.setInt(2, loanId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /**
     * Disburses an approved loan: marks it disbursed and generates a full
     * reducing-balance repayment schedule (equal monthly installments, interest
     * recalculated each month on the declining principal — the standard method
     * real Kenyan SACCOs use, per the equal-installment formula below).
     */
    public boolean disburseLoan(int loanId) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            double principal, annualRate;
            int termMonths;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT principal, annual_rate, term_months FROM sacco_loans WHERE id=? AND status='approved'")) {
                ps.setInt(1, loanId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) { c.rollback(); return false; } // must be approved first — no skipping straight from pending
                principal = rs.getDouble("principal");
                annualRate = rs.getDouble("annual_rate");
                termMonths = rs.getInt("term_months");
            }

            double monthlyRate = (annualRate / 100.0) / 12.0;
            // Standard reducing-balance equal-installment formula:
            // payment = P * r * (1+r)^n / ((1+r)^n - 1), falling back to a simple
            // even split if the rate is zero (avoids a divide-by-zero).
            double monthlyPayment;
            if (monthlyRate == 0) {
                monthlyPayment = principal / termMonths;
            } else {
                double factor = Math.pow(1 + monthlyRate, termMonths);
                monthlyPayment = principal * monthlyRate * factor / (factor - 1);
            }

            double remainingPrincipal = principal;
            LocalDate dueDate = LocalDate.now().plusMonths(1);
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO sacco_loan_repayments
                    (loan_id, installment_number, due_date, principal_due, interest_due, total_due)
                    VALUES (?,?,?,?,?,?)
                """)) {
                for (int i = 1; i <= termMonths; i++) {
                    double interestPortion = remainingPrincipal * monthlyRate;
                    double principalPortion = (i == termMonths)
                        ? remainingPrincipal // last installment absorbs any rounding remainder rather than leaving a stray balance
                        : monthlyPayment - interestPortion;
                    double installmentTotal = principalPortion + interestPortion;

                    ps.setInt(1, loanId);
                    ps.setInt(2, i);
                    ps.setString(3, dueDate.toString());
                    ps.setDouble(4, principalPortion);
                    ps.setDouble(5, interestPortion);
                    ps.setDouble(6, installmentTotal);
                    ps.executeUpdate();

                    remainingPrincipal -= principalPortion;
                    dueDate = dueDate.plusMonths(1);
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE sacco_loans SET status='disbursed', disbursed_date=date('now') WHERE id=?")) {
                ps.setInt(1, loanId);
                ps.executeUpdate();
            }
            c.commit();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<String[]> findAllLoans() {
        List<String[]> list = new ArrayList<>();
        String sql = """
            SELECT l.*, m.name as member_name FROM sacco_loans l
            JOIN sacco_members m ON m.id = l.member_id
            ORDER BY l.created_at DESC
        """;
        try (Connection c = conn(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")), rs.getString("loan_number"), rs.getString("member_name"),
                    String.format("%.2f", rs.getDouble("principal")), String.format("%.1f", rs.getDouble("annual_rate")),
                    String.valueOf(rs.getInt("term_months")), rs.getString("status"), rs.getString("purpose")
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<Object[]> getRepaymentSchedule(int loanId) {
        List<Object[]> list = new ArrayList<>();
        String sql = "SELECT * FROM sacco_loan_repayments WHERE loan_id=? ORDER BY installment_number";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, loanId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Object[]{
                    rs.getInt("id"), rs.getInt("installment_number"), rs.getString("due_date"),
                    rs.getDouble("principal_due"), rs.getDouble("interest_due"), rs.getDouble("total_due"),
                    rs.getDouble("amount_paid"), rs.getString("status")
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /** Records a repayment against one installment. Marks it 'paid' if the full amount due is met, else 'partial'. */
    public boolean recordRepayment(int repaymentId, double amountPaid) {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            double totalDue;
            int loanId;
            try (PreparedStatement ps = c.prepareStatement("SELECT total_due, loan_id FROM sacco_loan_repayments WHERE id=?")) {
                ps.setInt(1, repaymentId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) { c.rollback(); return false; }
                totalDue = rs.getDouble("total_due");
                loanId = rs.getInt("loan_id");
            }
            String status = amountPaid >= totalDue ? "paid" : "partial";
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE sacco_loan_repayments SET amount_paid=?, status=?, paid_date=date('now') WHERE id=?")) {
                ps.setDouble(1, amountPaid); ps.setString(2, status); ps.setInt(3, repaymentId);
                ps.executeUpdate();
            }
            // If every installment on this loan is now paid, mark the loan itself completed.
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) as remaining FROM sacco_loan_repayments WHERE loan_id=? AND status != 'paid'")) {
                ps.setInt(1, loanId);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt("remaining") == 0) {
                    try (PreparedStatement up = c.prepareStatement("UPDATE sacco_loans SET status='completed' WHERE id=?")) {
                        up.setInt(1, loanId);
                        up.executeUpdate();
                    }
                }
            }
            c.commit();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ── Dividends ──────────────────────────────────────────────────────

    /** Declares a dividend for every active member, proportional to their share capital, for a given year. */
    public int declareDividends(int year, double ratePercent) {
        int count = 0;
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            List<Integer> ids = new ArrayList<>();
            List<Double> shares = new ArrayList<>();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT id, share_capital FROM sacco_members WHERE status='active' AND share_capital > 0")) {
                while (rs.next()) { ids.add(rs.getInt("id")); shares.add(rs.getDouble("share_capital")); }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO sacco_dividends (member_id, year, based_on_shares, rate_percent, amount) VALUES (?,?,?,?,?)")) {
                for (int i = 0; i < ids.size(); i++) {
                    double shareCapital = shares.get(i);
                    double amount = shareCapital * (ratePercent / 100.0);
                    ps.setInt(1, ids.get(i));
                    ps.setInt(2, year);
                    ps.setDouble(3, shareCapital);
                    ps.setDouble(4, ratePercent);
                    ps.setDouble(5, amount);
                    ps.executeUpdate();
                    count++;
                }
            }
            c.commit();
        } catch (SQLException e) { e.printStackTrace(); }
        return count;
    }

    public List<String[]> getDividendHistory(int year) {
        List<String[]> list = new ArrayList<>();
        String sql = """
            SELECT d.*, m.name FROM sacco_dividends d JOIN sacco_members m ON m.id = d.member_id
            WHERE d.year = ? ORDER BY d.amount DESC
        """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, year);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    rs.getString("name"), String.format("%.2f", rs.getDouble("based_on_shares")),
                    String.format("%.1f", rs.getDouble("rate_percent")), String.format("%.2f", rs.getDouble("amount"))
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // ── Merry-go-round (chama rotating contributions) ─────────────────

    public int createMgrGroup(String name, double contributionAmount, String frequency) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO sacco_mgr_groups (name, contribution_amount, frequency) VALUES (?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name); ps.setDouble(2, contributionAmount); ps.setString(3, frequency);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (SQLException e) { e.printStackTrace(); return -1; }
    }

    public boolean addMemberToMgrGroup(int groupId, int memberId, int payoutPosition) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO sacco_mgr_members (group_id, member_id, payout_position) VALUES (?,?,?)")) {
            ps.setInt(1, groupId); ps.setInt(2, memberId); ps.setInt(3, payoutPosition);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public List<String[]> findAllMgrGroups() {
        List<String[]> list = new ArrayList<>();
        String sql = """
            SELECT g.*, (SELECT COUNT(*) FROM sacco_mgr_members WHERE group_id=g.id) as member_count
            FROM sacco_mgr_groups g ORDER BY g.name
        """;
        try (Connection c = conn(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")), rs.getString("name"),
                    String.format("%.2f", rs.getDouble("contribution_amount")), rs.getString("frequency"),
                    String.valueOf(rs.getInt("current_cycle")), String.valueOf(rs.getInt("member_count"))
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /** Who receives the payout for the group's current cycle — the member at position ((cycle-1) % memberCount) + 1. */
    public String[] getCurrentPayoutRecipient(int groupId) {
        try (Connection c = conn()) {
            int currentCycle;
            try (PreparedStatement ps = c.prepareStatement("SELECT current_cycle FROM sacco_mgr_groups WHERE id=?")) {
                ps.setInt(1, groupId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) return null;
                currentCycle = rs.getInt("current_cycle");
            }
            int memberCount;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) as cnt FROM sacco_mgr_members WHERE group_id=?")) {
                ps.setInt(1, groupId);
                ResultSet rs = ps.executeQuery();
                rs.next();
                memberCount = rs.getInt("cnt");
            }
            if (memberCount == 0) return null;
            int position = ((currentCycle - 1) % memberCount) + 1;
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT mm.member_id, m.name FROM sacco_mgr_members mm
                    JOIN sacco_members m ON m.id = mm.member_id
                    WHERE mm.group_id=? AND mm.payout_position=?
                """)) {
                ps.setInt(1, groupId); ps.setInt(2, position);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return new String[]{ String.valueOf(rs.getInt("member_id")), rs.getString("name") };
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public boolean recordMgrContribution(int groupId, int memberId, int cycleNumber, double amount) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO sacco_mgr_contributions (group_id, member_id, cycle_number, amount) VALUES (?,?,?,?)")) {
            ps.setInt(1, groupId); ps.setInt(2, memberId); ps.setInt(3, cycleNumber); ps.setDouble(4, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /** Advances the group to its next cycle — call once everyone's contributed and the current recipient has been paid out. */
    public boolean advanceMgrCycle(int groupId) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE sacco_mgr_groups SET current_cycle = current_cycle + 1 WHERE id=?")) {
            ps.setInt(1, groupId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }
}
