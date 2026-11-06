// The demo company is opt-in: new databases start with NO sample data; DemoDataService loads/removes it on request,
// idempotently, without touching real data and without deleting rows that real records use.
import com.kaziflow.dao.TransactionDAO;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.services.DemoDataService;
import com.kaziflow.services.DemoDataService.Counts;
import com.kaziflow.services.DemoDataService.RemoveResult;

import java.sql.*;

public class DemoCompanyCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static void sql(String s) throws SQLException {
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement st = c.createStatement()) { st.execute(s); }
    }
    static double n(String q) throws SQLException {
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(q)) { return rs.next() ? rs.getDouble(1) : 0; }
    }
    static String s(String q) throws SQLException {
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(q)) { return rs.next() ? rs.getString(1) : null; }
    }
    static void launchAgain() throws Exception {      // what the next app start does to the same database
        var m = DatabaseManager.class.getDeclaredMethod("seedDefaultData", Statement.class); m.setAccessible(true);
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement st = c.createStatement()) { m.invoke(DatabaseManager.getInstance(), st); }
    }

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();

        // ── a new database is the customer's own, empty ──
        String[] tables = {"suppliers", "employees", "products", "transactions", "expenses", "sales", "customers"};
        boolean empty = true; for (String t : tables) empty &= n("SELECT COUNT(*) FROM " + t) == 0;
        check("new database: no suppliers, employees, products, customers, transactions, expenses or sales", empty);
        check("new database: DemoDataService sees no demo data", !DemoDataService.isPresent() && DemoDataService.countPresent().total() == 0);
        launchAgain(); launchAgain();
        empty = true; for (String t : tables) empty &= n("SELECT COUNT(*) FROM " + t) == 0;
        check("starting the app again (x2) adds nothing (was +8 expenses and +10 transactions per launch)", empty);
        check("finance sees no revenue or expenses on a new database", new TransactionDAO().getMonthRevenue() == 0 && new TransactionDAO().getMonthExpenses() == 0
                && n("SELECT COUNT(*) FROM transactions WHERE transaction_type='income'") == 0);
        check("structural defaults are still seeded (roles, admin user, categories, departments, accounts, settings)",
                n("SELECT COUNT(*) FROM roles") > 0 && n("SELECT COUNT(*) FROM users") > 0 && n("SELECT COUNT(*) FROM categories") > 0
                && n("SELECT COUNT(*) FROM departments") > 0 && n("SELECT COUNT(*) FROM accounts") > 0 && n("SELECT COUNT(*) FROM settings") > 0);

        // ── a real business exists first; then the owner asks for the demo company ──
        sql("INSERT INTO suppliers (code, name) VALUES ('REAL-1', 'My Real Supplier')");
        sql("INSERT INTO expenses (description, category, amount, expense_date) VALUES ('My real rent','Rent',55000,date('now'))");
        sql("INSERT INTO transactions (reference, description, transaction_type, category, amount, transaction_date) VALUES ('MY-1','My real income','income','Sales Revenue',777,date('now'))");
        Counts added = DemoDataService.load();
        check("load(): adds 5 suppliers, 6 employees, 15 products, 5 customers, 10 transactions, 8 expenses", added.suppliers() == 5 && added.employees() == 6 && added.products() == 15 && added.customers() == 5 && added.transactions() == 10 && added.expenses() == 8 && added.total() == 49);
        check("load(): countPresent matches what was added", DemoDataService.countPresent().equals(added) && DemoDataService.isPresent());
        Counts again = DemoDataService.load();
        check("load() twice adds nothing (idempotent)", again.total() == 0 && n("SELECT COUNT(*) FROM expenses") == 9 && n("SELECT COUNT(*) FROM transactions") == 11 && n("SELECT COUNT(*) FROM products") == 15);
        launchAgain();
        check("app restart with demo data present adds nothing more", n("SELECT COUNT(*) FROM expenses") == 9 && n("SELECT COUNT(*) FROM transactions") == 11);
        check("demo products link to their suppliers by CODE even though a real supplier took row id 1",
                "East Africa Cement Co.".equals(s("SELECT s.name FROM products p JOIN suppliers s ON p.supplier_id=s.id WHERE p.sku='CEM-001'"))
                && "Devki Steel Mills".equals(s("SELECT s.name FROM products p JOIN suppliers s ON p.supplier_id=s.id WHERE p.sku='STEEL-002'")));
        check("demo products and employees reference real categories/departments", n("SELECT COUNT(*) FROM products WHERE category_id IS NULL") == 0 && n("SELECT COUNT(*) FROM employees WHERE department_id IS NULL") == 0);
        check("demo transactions carry an account and a creating user", n("SELECT COUNT(*) FROM transactions WHERE reference LIKE 'TXN-%' AND (account_id IS NULL OR created_by IS NULL)") == 0);
        check("demo data does reach finance (it is the owner's choice now)", new TransactionDAO().getMonthRevenue() >= 0 && n("SELECT COUNT(*) FROM transactions WHERE transaction_type='income'") > 1);

        // ── remove: only the demo rows go; nothing in use is force-deleted ──
        sql("INSERT INTO sales (sale_number, subtotal, total_amount, payment_method, status) VALUES ('REAL-S1',780,780,'cash','completed')");
        sql("INSERT INTO sale_items (sale_id, product_id, product_name, quantity, unit_price, cost_price, line_total) "
          + "SELECT (SELECT id FROM sales WHERE sale_number='REAL-S1'), id, name, 1, 780, 620, 780 FROM products WHERE sku='CEM-001'");
        RemoveResult r = DemoDataService.remove();
        check("remove(): deletes the demo rows nothing uses (14 products, 6 employees, 10 transactions, 8 expenses, 4 suppliers)",
                r.removed().products() == 14 && r.removed().employees() == 6 && r.removed().transactions() == 10 && r.removed().expenses() == 8 && r.removed().suppliers() == 4 && r.removed().customers() == 5);
        check("remove(): the demo product that a real sale used is KEPT, with its supplier, and reported as such",
                r.keptBecauseInUse().products() == 1 && r.keptBecauseInUse().suppliers() == 1 && n("SELECT COUNT(*) FROM products WHERE sku='CEM-001'") == 1);
        check("remove(): the real sale and its line are intact", n("SELECT COUNT(*) FROM sale_items si JOIN sales s ON s.id=si.sale_id WHERE s.sale_number='REAL-S1'") == 1);
        check("remove(): the owner's own supplier, expense and transaction are untouched",
                n("SELECT COUNT(*) FROM suppliers WHERE code='REAL-1'") == 1 && n("SELECT COUNT(*) FROM expenses WHERE description='My real rent'") == 1 && n("SELECT COUNT(*) FROM transactions WHERE reference='MY-1'") == 1);

        // ── a database from before this change: demo rows duplicated on every launch ──
        sql("DELETE FROM sale_items"); sql("DELETE FROM sales"); DemoDataService.remove();
        for (int i = 0; i < 3; i++) {
            sql("INSERT INTO expenses (description, category, amount, expense_date) VALUES ('Monthly rent - Tom Mboya Street','Rent',85000,date('now'))");
            sql("INSERT INTO transactions (reference, description, transaction_type, category, amount, transaction_date) VALUES ('TXN-005','Rent - CBD October','expense','Rent',85000,date('now'))");
        }
        RemoveResult legacy = DemoDataService.remove();
        check("legacy cleanup: all 3 duplicated copies of a demo expense and transaction are removed", legacy.removed().expenses() == 3 && legacy.removed().transactions() == 3
                && n("SELECT COUNT(*) FROM expenses WHERE description='Monthly rent - Tom Mboya Street'") == 0);
        sql("INSERT INTO expenses (description, category, amount, expense_date) VALUES ('Monthly rent - Tom Mboya Street','Rent',90000,date('now'))");
        DemoDataService.remove();
        check("legacy cleanup: matching is exact — same words but another amount (90,000 vs the demo's 85,000) is not deleted", n("SELECT COUNT(*) FROM expenses WHERE amount=90000") == 1);
        check("after all that the owner's own rows are still there", n("SELECT COUNT(*) FROM suppliers WHERE code='REAL-1'") == 1 && n("SELECT COUNT(*) FROM expenses WHERE description='My real rent'") == 1);

        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
