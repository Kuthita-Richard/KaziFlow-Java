package com.kaziflow.services;

import com.kaziflow.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The optional "demo company": sample suppliers, employees, products, transactions and expenses for exploring the
 * system. NOTHING here runs unless the owner asks for it (Settings → Demo Company); new databases start empty.
 *
 * <p>Why this exists: these rows used to be inserted by {@code DatabaseManager.seedDefaultData} into every real database,
 * so every report, VAT and finance figure included them — and because {@code expenses} and {@code transactions} have no
 * unique key, they were inserted AGAIN on every launch (8 + 10 rows each time).
 *
 * <p>A demo row is identified by its natural identity (supplier code+name, employee number+name, SKU+name, transaction
 * reference+description, expense description+category+amount), not by a flag. That makes {@link #load} idempotent and lets
 * {@link #remove} clean up databases created before this service existed. Rows already used by real records (a demo
 * product that was sold, a demo supplier with a real purchase…) are kept, never force-deleted.
 */
public final class DemoDataService {
    private DemoDataService() {}

    public record Counts(int suppliers, int employees, int products, int transactions, int expenses) {
        public int total() { return suppliers + employees + products + transactions + expenses; }
        public String describe() {
            return suppliers + " suppliers, " + employees + " employees, " + products + " products, "
                 + transactions + " transactions, " + expenses + " expenses";
        }
    }
    public record RemoveResult(Counts removed, Counts keptBecauseInUse) {}

    // Column order — suppliers: code,name,email,phone,address,category,payment_terms,status,outstanding_balance
    // employees: number,name,email,phone,department_id,position,type,salary,hire_date,status
    // products: sku,name,category_id,supplier_id(1-based index into SUPPLIERS),selling,cost,stock,min_stock,unit,status
    // transactions: reference,description,account_id,type,category,amount,payment_method,date offset,created_by
    // expenses: description,category,amount,payment_method,date offset,created_by
    private static final Object[][] SUPPLIERS = {
        {"SUP-001", "East Africa Cement Co.", "sales@eacement.co.ke", "+254711000001", "Industrial Area, Nairobi", "Cement", 30, "active", 450000},
        {"SUP-002", "Crown Paints Kenya", "trade@crownpaints.co.ke", "+254711000002", "Thika Road, Nairobi", "Paints & Finishes", 45, "active", 128000},
        {"SUP-003", "Devki Steel Mills", "info@devkisteel.co.ke", "+254711000003", "Athi River, Machakos", "Steel & Metal", 30, "active", 0},
        {"SUP-004", "Kenya Plumbers Ltd", "orders@kenyaplumbers.co.ke", "+254722000004", "Ngong Road, Nairobi", "Plumbing", 14, "active", 75000},
        {"SUP-005", "Orbit Hardware", "info@orbithardware.co.ke", "+254733000005", "Mombasa Road, Nairobi", "Hardware", 30, "active", 0}
    };

    private static final Object[][] EMPLOYEES = {
        {"EMP-001", "James Kamau", "james.kamau@kaziflow.co.ke", "+254712345601", 1, "General Manager", "full-time", 120000, "2020-01-15", "active"},
        {"EMP-002", "Mary Wanjiku", "mary.wanjiku@kaziflow.co.ke", "+254712345602", 2, "Sales Manager", "full-time", 85000, "2021-03-01", "active"},
        {"EMP-003", "Peter Otieno", "peter.otieno@kaziflow.co.ke", "+254712345603", 2, "Sales Associate", "full-time", 45000, "2022-06-15", "active"},
        {"EMP-004", "Grace Muthoni", "grace.muthoni@kaziflow.co.ke", "+254712345604", 5, "Accountant", "full-time", 70000, "2021-09-01", "active"},
        {"EMP-005", "David Kipchoge", "david.kipchoge@kaziflow.co.ke", "+254712345605", 3, "Workshop Supervisor", "full-time", 65000, "2020-11-01", "active"},
        {"EMP-006", "Faith Achieng", "faith.achieng@kaziflow.co.ke", "+254712345606", 4, "Storekeeper", "full-time", 40000, "2023-01-10", "active"}
    };

    private static final Object[][] PRODUCTS = {
        {"CEM-001", "Bamburi Cement 50kg", 1, 1, 780, 620, 450, 50, "bags", "active"},
        {"CEM-002", "Savanna Cement 50kg", 1, 1, 760, 600, 280, 50, "bags", "active"},
        {"PAINT-001", "Crown Paint Supercover 4L White", 2, 2, 2850, 2100, 85, 10, "pcs", "active"},
        {"PAINT-002", "Crown Paint Supercover 4L Cream", 2, 2, 2850, 2100, 62, 10, "pcs", "active"},
        {"PAINT-003", "Crown Silk Vinyl 20L White", 2, 2, 8500, 6200, 34, 5, "pcs", "active"},
        {"STEEL-001", "6mm Deformed Bar 12m", 6, 3, 680, 530, 320, 30, "pcs", "active"},
        {"STEEL-002", "8mm Deformed Bar 12m", 6, 3, 980, 760, 280, 20, "pcs", "active"},
        {"STEEL-003", "12mm Deformed Bar 12m", 6, 3, 1850, 1450, 150, 15, "pcs", "active"},
        {"PIPE-001", "1/2\" PVC Pipe 6m", 3, 4, 320, 240, 180, 20, "pcs", "active"},
        {"PIPE-002", "1\" PVC Pipe 6m", 3, 4, 480, 360, 120, 15, "pcs", "active"},
        {"PIPE-003", "Water Tank 500L Roto", 3, 4, 12500, 9800, 12, 3, "pcs", "active"},
        {"HARDWARE-001", "Wire Nails 4\" 1kg", 7, 5, 180, 130, 5, 10, "kg", "active"},
        {"HARDWARE-002", "Binding Wire 2kg Roll", 7, 5, 420, 310, 48, 10, "rolls", "active"},
        {"TOOLS-001", "Wheelbarrow Heavy Duty", 4, 5, 4800, 3600, 8, 2, "pcs", "active"},
        {"TOOLS-002", "Trowel Stainless 12\"", 4, 5, 650, 480, 25, 5, "pcs", "active"}
    };

    private static final Object[][] TRANSACTIONS = {
        {"TXN-001", "Opening balance", 2, "income", "Capital", 500000, "bank transfer", "-60 days", 1},
        {"TXN-002", "Cement sales - batch", 10, "income", "Sales Revenue", 234000, "cash", "-30 days", 1},
        {"TXN-003", "Paint & hardware sales", 10, "income", "Sales Revenue", 189500, "mpesa", "-25 days", 1},
        {"TXN-004", "Payroll October", 11, "expense", "Payroll", 425000, "bank transfer", "-20 days", 1},
        {"TXN-005", "Rent - CBD October", 12, "expense", "Rent", 85000, "bank transfer", "-18 days", 1},
        {"TXN-006", "Steel bar sales - contractor", 10, "income", "Sales Revenue", 312000, "mpesa", "-15 days", 1},
        {"TXN-007", "Electricity bill", 12, "expense", "Utilities", 18500, "mpesa", "-10 days", 1},
        {"TXN-008", "Hardware & tools sales", 10, "income", "Sales Revenue", 127500, "cash", "-7 days", 1},
        {"TXN-009", "Supplier payment - Devki Steel", 11, "expense", "Purchases", 156000, "bank transfer", "-5 days", 1},
        {"TXN-010", "Pipe & plumbing sales", 10, "income", "Sales Revenue", 89000, "cash", "-2 days", 1}
    };

    private static final Object[][] EXPENSES = {
        {"Monthly rent - Tom Mboya Street", "Rent", 85000, "bank transfer", "-30 days", 1},
        {"Staff payroll October", "Payroll", 425000, "bank transfer", "-20 days", 1},
        {"KPLC electricity bill", "Utilities", 18500, "mpesa", "-10 days", 1},
        {"Vehicle fuel - delivery truck", "Transport", 12000, "cash", "-8 days", 1},
        {"Office supplies & stationery", "Supplies", 5500, "cash", "-5 days", 1},
        {"Internet & phone bill", "Utilities", 4200, "mpesa", "-3 days", 1},
        {"Nairobi Water & Sewerage", "Utilities", 3800, "mpesa", "-15 days", 1},
        {"Fire extinguisher service", "Maintenance", 8500, "cash", "-25 days", 1}
    };


    /** Rows of the demo company present in the database right now (by identity). */
    public static Counts countPresent() {
        try (Connection c = DatabaseManager.getInstance().getConnection()) { return countPresent(c); }
        catch (SQLException e) { e.printStackTrace(); return new Counts(0, 0, 0, 0, 0); }
    }

    public static boolean isPresent() { return countPresent().total() > 0; }

    /** Inserts the demo rows that are missing. Returns how many rows were added (0 when it is already loaded). */
    public static Counts load() {
        try (Connection c = DatabaseManager.getInstance().getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                Counts added = load(c);
                c.commit();
                if (added.total() > 0) AuditLog.log("DEMO_DATA_LOADED", "Loaded the demo company: " + added.describe());
                return added;
            } catch (SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(auto); }
        } catch (SQLException e) { e.printStackTrace(); return new Counts(0, 0, 0, 0, 0); }
    }

    /** Deletes demo rows nothing real depends on; reports what was removed and what had to stay. */
    public static RemoveResult remove() {
        try (Connection c = DatabaseManager.getInstance().getConnection()) {
            RemoveResult r = remove(c);
            if (r.removed().total() > 0) AuditLog.log("DEMO_DATA_REMOVED", "Removed demo data: " + r.removed().describe()
                    + (r.keptBecauseInUse().total() > 0 ? " (kept because in use: " + r.keptBecauseInUse().describe() + ")" : ""));
            return r;
        } catch (SQLException e) { e.printStackTrace(); return new RemoveResult(new Counts(0, 0, 0, 0, 0), new Counts(0, 0, 0, 0, 0)); }
    }

    // ── connection-level (testable) ──

    static Counts countPresent(Connection c) throws SQLException {
        return new Counts(
            count(c, "SELECT COUNT(*) FROM suppliers WHERE code = ? AND name = ?", SUPPLIERS, 0, 1),
            count(c, "SELECT COUNT(*) FROM employees WHERE employee_number = ? AND name = ?", EMPLOYEES, 0, 1),
            count(c, "SELECT COUNT(*) FROM products WHERE sku = ? AND name = ?", PRODUCTS, 0, 1),
            count(c, "SELECT COUNT(*) FROM transactions WHERE reference = ? AND description = ?", TRANSACTIONS, 0, 1),
            count(c, "SELECT COUNT(*) FROM expenses WHERE description = ? AND category = ? AND amount = ?", EXPENSES, 0, 1, 2));
    }

    static Counts load(Connection c) throws SQLException {
        int s = 0, e = 0, p = 0, t = 0, x = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO suppliers (code, name, email, phone, address, category, payment_terms, status, outstanding_balance) "
              + "SELECT ?,?,?,?,?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM suppliers WHERE code = ?)")) {
            for (Object[] r : SUPPLIERS) { bind(ps, r, 0, 1, 2, 3, 4, 5, 6, 7, 8, 0); s += ps.executeUpdate(); }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO employees (employee_number, name, email, phone, department_id, position, employment_type, salary, hire_date, status) "
              + "SELECT ?,?,?,?,(SELECT id FROM departments WHERE id = ?),?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM employees WHERE employee_number = ?)")) {
            for (Object[] r : EMPLOYEES) { bind(ps, r, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0); e += ps.executeUpdate(); }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO products (sku, name, category_id, supplier_id, selling_price, cost_price, stock_quantity, min_stock_level, unit, status) "
              + "SELECT ?,?,(SELECT id FROM categories WHERE id = ?),(SELECT id FROM suppliers WHERE code = ?),?,?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM products WHERE sku = ?)")) {
            for (Object[] r : PRODUCTS) {
                ps.setObject(1, r[0]); ps.setObject(2, r[1]); ps.setObject(3, r[2]);
                ps.setObject(4, (String) SUPPLIERS[((Number) r[3]).intValue() - 1][0]);       // supplier looked up by code, not by row id
                ps.setObject(5, r[4]); ps.setObject(6, r[5]); ps.setObject(7, r[6]); ps.setObject(8, r[7]); ps.setObject(9, r[8]); ps.setObject(10, r[9]);
                ps.setObject(11, r[0]);
                p += ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO transactions (reference, description, account_id, transaction_type, category, amount, payment_method, transaction_date, created_by) "
              + "SELECT ?,?,(SELECT id FROM accounts WHERE id = ?),?,?,?,?,date('now', ?),(SELECT MIN(id) FROM users) "
              + "WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE reference = ? AND description = ?)")) {
            for (Object[] r : TRANSACTIONS) { bind(ps, r, 0, 1, 2, 3, 4, 5, 6, 7, 0, 1); t += ps.executeUpdate(); }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO expenses (description, category, amount, payment_method, expense_date, created_by) "
              + "SELECT ?,?,?,?,date('now', ?),(SELECT MIN(id) FROM users) "
              + "WHERE NOT EXISTS (SELECT 1 FROM expenses WHERE description = ? AND category = ? AND amount = ?)")) {
            for (Object[] r : EXPENSES) { bind(ps, r, 0, 1, 2, 3, 4, 0, 1, 2); x += ps.executeUpdate(); }
        }
        return new Counts(s, e, p, t, x);
    }

    static RemoveResult remove(Connection c) throws SQLException {
        // children before parents: products reference suppliers
        int p = delete(c, "DELETE FROM products WHERE sku = ? AND name = ?", PRODUCTS, 0, 1);
        int s = delete(c, "DELETE FROM suppliers WHERE code = ? AND name = ?", SUPPLIERS, 0, 1);
        int e = delete(c, "DELETE FROM employees WHERE employee_number = ? AND name = ?", EMPLOYEES, 0, 1);
        int t = delete(c, "DELETE FROM transactions WHERE reference = ? AND description = ?", TRANSACTIONS, 0, 1);
        int x = delete(c, "DELETE FROM expenses WHERE description = ? AND category = ? AND amount = ?", EXPENSES, 0, 1, 2);
        return new RemoveResult(new Counts(s, e, p, t, x), countPresent(c));
    }

    // ── helpers ──

    private static void bind(PreparedStatement ps, Object[] row, int... cols) throws SQLException {
        for (int i = 0; i < cols.length; i++) ps.setObject(i + 1, row[cols[i]]);
    }

    private static int count(Connection c, String sql, Object[][] rows, int... cols) throws SQLException {
        int n = 0;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Object[] r : rows) {
                bind(ps, r, cols);
                try (java.sql.ResultSet rs = ps.executeQuery()) { if (rs.next()) n += rs.getInt(1); }
            }
        }
        return n;
    }

    /** One row at a time, so a row that real data still references (foreign key) is skipped instead of failing the lot. */
    private static int delete(Connection c, String sql, Object[][] rows, int... cols) throws SQLException {
        int n = 0;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Object[] r : rows) {
                bind(ps, r, cols);
                try { n += ps.executeUpdate(); }
                catch (SQLException ex) { if (!isConstraint(ex)) throw ex; }
            }
        }
        return n;
    }

    private static boolean isConstraint(SQLException ex) {
        String m = String.valueOf(ex.getMessage()).toUpperCase();
        return m.contains("CONSTRAINT") || m.contains("FOREIGN KEY");
    }
}
