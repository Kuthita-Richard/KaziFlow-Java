// R1 step D, part 1: the sales table rebuild (drop CHECK(payment_method IN (...)), add a foreign key to payment_methods).
// Runs on a COPY of a populated pre-v11.43 database (tools/checks/fixtures/legacy-v11.42-populated.db) and on injected failures.
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.database.PaymentMethodMigration;
import com.kaziflow.database.SchemaMigrations;

import java.io.File;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class PaymentMigrationCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static final Path FIXTURE = Paths.get("tools/checks/fixtures/legacy-v11.42-populated.db");
    static final String[] BUSINESS = {"sales", "sale_items", "sale_returns", "transactions", "deliveries", "etims_serials", "customers", "products", "suppliers",
            "employees", "purchases", "purchase_items", "quotations", "quotation_items", "expenses", "stock_movements", "users"};

    static long n(Connection c, String q) throws SQLException { try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) { return r.next() ? r.getLong(1) : 0; } }
    static double d(Connection c, String q) throws SQLException { try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) { return r.next() ? r.getDouble(1) : 0; } }
    static String str(Connection c, String q) throws SQLException { try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) { return r.next() ? r.getString(1) : null; } }
    static void x(Connection c, String q) throws SQLException { try (Statement s = c.createStatement()) { s.execute(q); } }
    static List<String> list(Connection c, String q) throws SQLException { List<String> o = new ArrayList<>(); try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) { while (r.next()) o.add(r.getString(1)); } return o; }
    static Connection open(Path db) throws SQLException { return DriverManager.getConnection("jdbc:sqlite:" + db); }
    static Path copyFixture(String name) throws Exception {
        Path dir = Files.createTempDirectory("kzmig-" + name), db = dir.resolve("kaziflow.db"); Files.copy(FIXTURE, db); return db;
    }
    static boolean hasCheck(Connection c) throws SQLException { return str(c, "SELECT sql FROM sqlite_master WHERE name='sales'").contains("CHECK(payment_method IN"); }
    static void paymentMethodsTable(Connection c, boolean withBank) throws SQLException {   // what DatabaseManager creates; enough for the migration's precondition
        x(c, "CREATE TABLE IF NOT EXISTS payment_methods (code TEXT PRIMARY KEY, label TEXT NOT NULL, kind TEXT NOT NULL, integration TEXT, active INTEGER DEFAULT 1, sort_order INTEGER DEFAULT 100, for_sales INTEGER DEFAULT 0, for_purchases INTEGER DEFAULT 0, for_expenses INTEGER DEFAULT 0, is_system INTEGER DEFAULT 0)");
        x(c, "INSERT OR IGNORE INTO payment_methods (code,label,kind) VALUES ('cash','Cash','cash'),('mpesa','M-Pesa','mobile_money'),('card','Card','card')");
        if (withBank) x(c, "INSERT OR IGNORE INTO payment_methods (code,label,kind) VALUES ('bank','Bank transfer','bank')");
    }
    static Map<String, Long> counts(Connection c) throws SQLException { Map<String, Long> m = new LinkedHashMap<>(); for (String t : BUSINESS) m.put(t, n(c, "SELECT COUNT(*) FROM " + t)); return m; }
    static File[] backups(Path db) { return db.getParent().toFile().listFiles((dir, name) -> name.startsWith("kaziflow.db.pre-paymentmethods-") && name.endsWith(".bak")); }

    public static void main(String[] a) throws Exception {
        // ── the whole application start-up on a populated legacy database ──
        Path home = Files.createTempDirectory("kzmig-home");
        Path dbdir = Files.createDirectories(home.resolve("KaziFlowERP")), db = dbdir.resolve("kaziflow.db");
        Files.copy(FIXTURE, db);
        System.setProperty("user.home", home.toString());
        Map<String, Long> before; double sumBefore, itemsBefore; List<String> idxBefore, colsBefore, methodsBefore; long childJoinBefore, maxIdBefore, seqBefore, fkBefore;
        try (Connection c = open(db)) {
            check("fixture: the legacy database really has the CHECK, no payment_methods table, and 40 sales across 4 methods", hasCheck(c) && n(c, "SELECT COUNT(*) FROM sqlite_master WHERE name='payment_methods'") == 0 && n(c, "SELECT COUNT(*) FROM sales") == 40 && n(c, "SELECT COUNT(DISTINCT payment_method) FROM sales") == 4);
            before = counts(c); sumBefore = d(c, "SELECT SUM(total_amount) FROM sales"); itemsBefore = d(c, "SELECT SUM(line_total) FROM sale_items");
            idxBefore = list(c, "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='sales' AND sql IS NOT NULL ORDER BY name");
            colsBefore = list(c, "SELECT name FROM pragma_table_info('sales')"); methodsBefore = list(c, "SELECT payment_method || '=' || c FROM (SELECT payment_method, COUNT(*) c FROM sales GROUP BY payment_method) ORDER BY 1");
            childJoinBefore = n(c, "SELECT COUNT(*) FROM sale_items si JOIN sales s ON s.id = si.sale_id") + n(c, "SELECT COUNT(*) FROM etims_serials e JOIN sales s ON s.id = e.sale_id");
            maxIdBefore = n(c, "SELECT MAX(id) FROM sales"); seqBefore = n(c, "SELECT seq FROM sqlite_sequence WHERE name='sales'"); fkBefore = list(c, "SELECT 1 FROM pragma_foreign_key_check").size();
        }
        DatabaseManager.getInstance().initialize();          // creates payment_methods + seeds it (the app's start-up, step 1)
        SchemaMigrations.runAll();                            // …and the migration (step 2)
        try (Connection c = open(db)) {
            check("the CHECK is gone and sales now references payment_methods(code)", !hasCheck(c) && str(c, "SELECT sql FROM sqlite_master WHERE name='sales'").contains("REFERENCES payment_methods(code)"));
            Map<String, Long> after = counts(c);
            check("every business table has exactly the same number of rows as before (" + before.values().stream().mapToLong(Long::longValue).sum() + " rows in " + before.size() + " tables)", after.equals(before));
            check("sale_items rows survived (an FK-ON drop would have cascade-deleted them): 79 lines, same total", n(c, "SELECT COUNT(*) FROM sale_items") == 79 && Math.abs(d(c, "SELECT SUM(line_total) FROM sale_items") - itemsBefore) < 1e-6);
            check("sales totals and the per-method counts are unchanged", Math.abs(d(c, "SELECT SUM(total_amount) FROM sales") - sumBefore) < 1e-6 && list(c, "SELECT payment_method || '=' || c FROM (SELECT payment_method, COUNT(*) c FROM sales GROUP BY payment_method) ORDER BY 1").equals(methodsBefore));
            check("columns are identical and in the same order (including the ones added by later ALTER TABLEs)", list(c, "SELECT name FROM pragma_table_info('sales')").equals(colsBefore));
            check("every named index on sales was recreated", list(c, "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='sales' AND sql IS NOT NULL ORDER BY name").equals(idxBefore));
            check("children still join to their sales (sale_items, eTIMS serials)", n(c, "SELECT COUNT(*) FROM sale_items si JOIN sales s ON s.id = si.sale_id") + n(c, "SELECT COUNT(*) FROM etims_serials e JOIN sales s ON s.id = e.sale_id") == childJoinBefore);
            check("no foreign-key violations were introduced, and the database is intact", list(c, "SELECT 1 FROM pragma_foreign_key_check").size() <= fkBefore && "ok".equals(str(c, "PRAGMA integrity_check")));
            check("sale_number is still UNIQUE after the rebuild", insertFails(c, "INSERT INTO sales (sale_number, total_amount) SELECT sale_number, 1 FROM sales LIMIT 1"));
            check("AUTOINCREMENT carries on past the old counter (no id is ever reused)", n(c, "SELECT seq FROM sqlite_sequence WHERE name='sales'") >= Math.max(seqBefore, maxIdBefore));
            File[] bk = backups(db);
            check("exactly one pre-migration backup was written next to the database", bk != null && bk.length == 1);
            try (Connection b = open(bk[0].toPath())) {
                check("…and it is the legacy snapshot: CHECK still there, all 40 sales, intact", hasCheck(b) && n(b, "SELECT COUNT(*) FROM sales") == 40 && n(b, "SELECT COUNT(*) FROM sale_items") == 79 && "ok".equals(str(b, "PRAGMA integrity_check")));
            }
            check("foreign keys are back ON for application connections", n(DatabaseManager.getInstance().getConnection(), "PRAGMA foreign_keys") == 1);
        }
        SchemaMigrations.runAll();
        try (Connection c = open(db)) {
            File[] bk2 = backups(db);
            check("running the migrations again changes nothing and writes no second backup", bk2.length == 1 && counts(c).equals(before) && !hasCheck(c));
        }
        // a payment method that did not exist before can now be stored; an unknown one is still refused
        try (Connection c = DatabaseManager.getInstance().getConnection()) {
            x(c, "INSERT INTO payment_methods (code,label,kind,for_sales) VALUES ('airtel_money','Airtel Money','mobile_money',1)");
            x(c, "INSERT INTO sales (sale_number, total_amount, payment_method) VALUES ('MIG-1', 10, 'airtel_money')");
            check("a method added after the migration is accepted on a sale (it was a CHECK failure before)", n(c, "SELECT COUNT(*) FROM sales WHERE payment_method='airtel_money'") == 1);
            check("a code that is not in payment_methods is refused (the foreign key replaces the CHECK)", insertFails(c, "INSERT INTO sales (sale_number, total_amount, payment_method) VALUES ('MIG-2', 10, 'bitcoin')"));
        }

        // ── failure injection (each on its own copy of the fixture, plain connections) ──
        Path p1 = copyFixture("blocked");
        try (Connection c = open(p1)) {
            paymentMethodsTable(c, true); x(c, "CREATE TABLE sales_pm_new (x INTEGER)"); x(c, "PRAGMA foreign_keys = ON");
            long rows = n(c, "SELECT COUNT(*) FROM sales"); boolean threw = false;
            try { PaymentMethodMigration.run(c); } catch (SQLException e) { threw = true; }
            check("failure injection (the temp table name is taken): the migration throws, rolls back and leaves sales exactly as it was", threw && hasCheck(c) && n(c, "SELECT COUNT(*) FROM sales") == rows && n(c, "SELECT COUNT(*) FROM sale_items") == 79);
            check("failure injection: foreign keys are switched back ON after the failure", n(c, "PRAGMA foreign_keys") == 1);
        }
        Path p2 = copyFixture("missing");
        try (Connection c = open(p2)) {
            paymentMethodsTable(c, false);      // 'bank' is missing but 10 sales use it
            boolean threw = false; try { PaymentMethodMigration.run(c); } catch (SQLException e) { threw = true; }
            check("a payment method in use but missing from the master table stops the migration before anything is touched (no backup, CHECK intact)", threw && hasCheck(c) && backups(p2) == null || (threw && hasCheck(c) && backups(p2).length == 0));
        }
        Path p3 = copyFixture("orphans");
        try (Connection c = open(p3)) {
            paymentMethodsTable(c, true);
            x(c, "INSERT INTO sale_items (sale_id, product_id, product_name, quantity, unit_price, cost_price, line_total) VALUES (999999, (SELECT MIN(id) FROM products), 'orphan', 1, 1, 1, 1)");
            long orphansBefore = list(c, "SELECT 1 FROM pragma_foreign_key_check").size();
            boolean ok = PaymentMethodMigration.run(c);
            check("a legacy database that already contains an orphan row is still migrated (the rebuild must not ADD violations, not demand none)", ok && !hasCheck(c) && list(c, "SELECT 1 FROM pragma_foreign_key_check").size() == orphansBefore && orphansBefore >= 1);
        }
        Path p4 = copyFixture("extras");
        try (Connection c = open(p4)) {
            paymentMethodsTable(c, true);
            x(c, "CREATE VIEW v_sales_totals AS SELECT payment_method, SUM(total_amount) AS t FROM sales GROUP BY payment_method");
            x(c, "CREATE TRIGGER trg_sales_audit AFTER INSERT ON sales BEGIN SELECT 1; END");
            x(c, "UPDATE sqlite_sequence SET seq = seq + 500 WHERE name='sales'");
            long seq = n(c, "SELECT seq FROM sqlite_sequence WHERE name='sales'");
            boolean ok = PaymentMethodMigration.run(c);
            check("views and triggers that mention sales survive the rebuild and still work", ok && n(c, "SELECT COUNT(*) FROM sqlite_master WHERE name='trg_sales_audit'") == 1 && n(c, "SELECT COUNT(*) FROM v_sales_totals") == 4);
            x(c, "INSERT INTO sales (sale_number, total_amount) VALUES ('EXTRA-1', 1)");
            check("an AUTOINCREMENT counter that was ahead of the highest id is preserved (the next id is past it)", n(c, "SELECT id FROM sales WHERE sale_number='EXTRA-1'") > seq);
        }
        Path p5 = copyFixture("fresh");
        try (Connection c = open(p5)) {
            paymentMethodsTable(c, true);
            check("isSalesTableMigrated reports false on legacy and true after", !PaymentMethodMigration.isSalesTableMigrated(c) && PaymentMethodMigration.run(c) && PaymentMethodMigration.isSalesTableMigrated(c) && !PaymentMethodMigration.run(c));
        }
        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
    static boolean insertFails(Connection c, String sql) { try { x(c, sql); return false; } catch (SQLException e) { return true; } }
}
