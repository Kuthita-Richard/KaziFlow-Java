// R1 step C: document numbers. Atomic under contention, gapless on rollback, no collision after deletes, continues after existing
// documents, configurable per type — and the real DAOs (checkout first) use it.
import com.kaziflow.dao.*;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.*;
import com.kaziflow.services.NumberSequence;
import com.kaziflow.services.NumberSequence.DocType;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class NumberSequenceCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static Connection db() throws SQLException { return DatabaseManager.getInstance().getConnection(); }
    static void sql(String s) throws SQLException { try (Connection c = db(); Statement st = c.createStatement()) { st.execute(s); } }
    static long n(String q) throws SQLException { try (Connection c = db(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(q)) { return rs.next() ? rs.getLong(1) : 0; } }
    static String alloc(DocType t) throws SQLException { try (Connection c = db()) { c.setAutoCommit(false); String x = NumberSequence.next(c, t); c.commit(); return x; } }
    static String peek(DocType t) throws SQLException { try (Connection c = db()) { return NumberSequence.peek(c, t); } }
    static long counter(DocType t) throws SQLException { return n("SELECT next_value FROM number_sequences WHERE doc_type='" + t.name() + "'"); }
    /** Deletes a sale and every row that references it (looked up from the schema's foreign keys). */
    static void deleteSale(String number) throws Exception {
        Thread.sleep(600);                                    // let the background eTIMS thread finish writing its row for this sale
        long id = n("SELECT id FROM sales WHERE sale_number='" + number + "'");
        List<String[]> children = new ArrayList<>();          // {table, column} pairs, collected with every cursor closed
        try (Connection c = db()) {
            List<String> names = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet tabs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) { while (tabs.next()) names.add(tabs.getString(1)); }
            for (String t : names) try (Statement st = c.createStatement(); ResultSet fk = st.executeQuery("PRAGMA foreign_key_list(" + t + ")")) {
                while (fk.next()) if (fk.getString("table").equals("sales")) children.add(new String[]{t, fk.getString("from")});
            }
        }
        for (String[] ch : children) sql("DELETE FROM " + ch[0] + " WHERE " + ch[1] + " = " + id);
        sql("DELETE FROM sales WHERE id = " + id);
    }
    static int suffix(String num) { return Integer.parseInt(num.substring(num.lastIndexOf('-') + 1)); }

    static Sale sale(int productId) {
        Sale s = new Sale(); s.setCustomerName("T"); s.setSubtotal(100); s.setTotalAmount(100); s.setVatAmount(0); s.setDiscountAmount(0);
        s.setAmountPaid(100); s.setChangeAmount(0); s.setPaymentMethod("cash"); s.setStatus("completed"); s.setServedBy(1); s.setServedByName("Admin");
        SaleItem it = new SaleItem(); it.setProductId(productId); it.setProductName("P"); it.setQuantity(1); it.setUnitPrice(100); it.setCostPrice(60); it.setLineTotal(100); it.setDiscount(0);
        s.setItems(new ArrayList<>(List.of(it)));
        return s;
    }

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        com.kaziflow.database.SchemaMigrations.runAll();     // what App.start() does: without it sales has no delivery_charge column
        com.kaziflow.services.ETIMSService.getInstance();    // creates etims_serials, as App.start() does

        // ── sequence semantics ──
        check("first quotation number is QT-0001", alloc(DocType.QUOTATION).equals("QT-0001"));
        new QuotationDAO().ensureTables();
        check("fresh database: sales invoices start at INV-0001 and count up", alloc(DocType.SALE).equals("INV-0001") && alloc(DocType.SALE).equals("INV-0002"));
        String rolled;
        try (Connection c = db()) { c.setAutoCommit(false); rolled = NumberSequence.next(c, DocType.SALE); c.rollback(); }
        check("rollback gives the number back: the next invoice reuses it (no gaps)", rolled.equals("INV-0003") && alloc(DocType.SALE).equals("INV-0003"));
        sql("INSERT INTO employees (employee_number, name) VALUES ('EMP-005','A'),('EMP-012','B'),('EMP-xyz','C'),('XYZ-099','D')");
        check("first use on a database that already has documents continues after the highest number (EMP-013), ignoring other prefixes and non-numeric suffixes", alloc(DocType.EMPLOYEE).equals("EMP-013"));
        String p1 = peek(DocType.SALE), p2 = peek(DocType.SALE); long before = counter(DocType.SALE);
        check("peek shows the next number without using it", p1.equals(p2) && counter(DocType.SALE) == before);
        check("peek equals what next then hands out", alloc(DocType.SALE).equals(p1) && peek(DocType.SALE).equals(NumberSequence.format("INV-", 4, suffix(p1) + 1)));
        long nowNext = counter(DocType.SALE);
        try (Connection c = db()) { c.setAutoCommit(false); NumberSequence.configure(c, DocType.SALE, "SAL/", 6); c.commit(); }
        check("configure changes the format of future numbers and the counter continues", alloc(DocType.SALE).equals("SAL/" + String.format("%06d", nowNext)));
        try (Connection c = db()) { c.setAutoCommit(false); NumberSequence.configure(c, DocType.SALE, "INV-", 4); c.commit(); }
        check("configure back: INV- format again, still counting", alloc(DocType.SALE).equals(NumberSequence.format("INV-", 4, nowNext + 1)));
        boolean bad1 = false, bad2 = false, bad3 = false;
        try (Connection c = db()) {
            try { NumberSequence.configure(c, DocType.SALE, "bad prefix!", 4); } catch (IllegalArgumentException e) { bad1 = true; }
            try { NumberSequence.configure(c, DocType.SALE, "OK-", 0); } catch (IllegalArgumentException e) { bad2 = true; }
            try { NumberSequence.configure(c, DocType.SALE, "OK-", 10); } catch (IllegalArgumentException e) { bad3 = true; }
        }
        check("configure rejects a prefix with spaces/symbols and padding outside 1–9", bad1 && bad2 && bad3);
        Set<String> prefixes = new HashSet<>(); boolean tablesOk = true;
        for (DocType t : DocType.values()) { prefixes.add(t.defaultPrefix); try { n("SELECT " + t.column + " FROM " + t.table + " LIMIT 1"); } catch (SQLException e) { tablesOk = false; } }
        check("every document type has its own prefix and a real table/column", prefixes.size() == DocType.values().length && tablesOk);

        // ── contention on the raw sequence: 8 threads, own connections, own transactions ──
        long base = counter(DocType.PURCHASE);   // untouched type: a clean sequence for this test
        ConcurrentLinkedQueue<String> got = new ConcurrentLinkedQueue<>(); AtomicInteger errors = new AtomicInteger();
        int threads = 8, per = 20; CountDownLatch go = new CountDownLatch(1); ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<?>> fs = new ArrayList<>();
        for (int t = 0; t < threads; t++) fs.add(ex.submit(() -> { go.await(); for (int i = 0; i < per; i++) {
            for (int attempt = 1; attempt <= 6; attempt++) {
                try (Connection c = db()) {
                    c.setAutoCommit(false);
                    try { String num = NumberSequence.next(c, DocType.PURCHASE); c.commit(); got.add(num); break; }
                    catch (SQLException e) { c.rollback(); if (attempt == 6 || !NumberSequence.isBusy(e)) { errors.incrementAndGet(); e.printStackTrace(); break; } }
                } catch (SQLException e) { errors.incrementAndGet(); break; }
            } } return null; }));
        go.countDown(); for (Future<?> f : fs) f.get(120, TimeUnit.SECONDS); ex.shutdown();
        Set<String> uniq = new HashSet<>(got);
        List<Integer> nums = new ArrayList<>(); for (String s : got) nums.add(suffix(s)); Collections.sort(nums);
        check("contention (8 threads x 20 allocations): no errors", errors.get() == 0 && got.size() == threads * per);
        check("contention: every number is unique", uniq.size() == threads * per);
        check("contention: the numbers are exactly 1..160 — no gaps, no skips", nums.get(0) == 1 && nums.get(nums.size() - 1) == threads * per);

        // ── the real DAOs ──
        sql("INSERT INTO products (sku, name, selling_price, cost_price, stock_quantity, status) VALUES ('NP1','P',100,60,100000,'active')");
        int pid = (int) n("SELECT id FROM products WHERE sku='NP1'");
        SaleDAO sd = new SaleDAO();
        long salesBefore = n("SELECT COUNT(*) FROM sales");
        String firstExpected = peek(DocType.SALE);
        Sale s1 = sd.save(sale(pid)), s2 = sd.save(sale(pid));
        check("SaleDAO.save: consecutive invoices get consecutive numbers, starting at the previewed one", s1 != null && s2 != null && s1.getSaleNumber().equals(firstExpected) && suffix(s2.getSaleNumber()) == suffix(s1.getSaleNumber()) + 1);
        String expectedNext = peek(DocType.SALE);
        Sale bad = sd.save(sale(999999));
        check("SaleDAO.save: a sale that fails (unknown product) saves nothing", bad == null && n("SELECT COUNT(*) FROM sales") == salesBefore + 2);
        check("SaleDAO.save: …and its invoice number is NOT used up (the counter rolled back)", peek(DocType.SALE).equals(expectedNext) && sd.save(sale(pid)).getSaleNumber().equals(expectedNext));
        deleteSale(s1.getSaleNumber());
        Sale afterDelete = sd.save(sale(pid));
        check("SaleDAO.save after an earlier invoice was deleted: no collision with an existing number (COUNT+1 reused a live number here)", afterDelete != null && n("SELECT COUNT(*) FROM sales WHERE sale_number='" + afterDelete.getSaleNumber() + "'") == 1);

        long soldBefore = n("SELECT COUNT(*) FROM sales"); ConcurrentLinkedQueue<String> sn = new ConcurrentLinkedQueue<>(); AtomicInteger nullSales = new AtomicInteger();
        CountDownLatch go2 = new CountDownLatch(1); ExecutorService ex2 = Executors.newFixedThreadPool(8); List<Future<?>> f2 = new ArrayList<>();
        for (int t = 0; t < 8; t++) f2.add(ex2.submit(() -> { go2.await(); SaleDAO d = new SaleDAO(); for (int i = 0; i < 6; i++) { Sale r = d.save(sale(pid)); if (r == null) nullSales.incrementAndGet(); else sn.add(r.getSaleNumber()); } return null; }));
        go2.countDown(); for (Future<?> f : f2) f.get(180, TimeUnit.SECONDS); ex2.shutdown();
        List<Integer> sns = new ArrayList<>(); for (String s : sn) sns.add(suffix(s)); Collections.sort(sns);
        check("checkout under contention (8 cashiers x 6 sales): every sale saved, none lost to a busy database", nullSales.get() == 0 && sn.size() == 48 && n("SELECT COUNT(*) FROM sales") == soldBefore + 48);
        check("checkout under contention: 48 distinct invoice numbers, consecutive", new HashSet<>(sn).size() == 48 && sns.get(sns.size() - 1) - sns.get(0) == 47);

        Thread.sleep(5000);                                   // the eTIMS serials are written by background threads after each sale
        long unstamped = n("SELECT COUNT(*) FROM sales WHERE etims_serial IS NULL OR etims_serial = ''");
        long serials = n("SELECT COUNT(*) FROM etims_serials"), distinct = n("SELECT COUNT(DISTINCT sequence) FROM etims_serials"), distinctSerial = n("SELECT COUNT(DISTINCT serial) FROM etims_serials");
        check("eTIMS: every sale saved under contention carries its local serial (none left ETIMS-PENDING by a busy database)", unstamped == 0 && n("SELECT COUNT(*) FROM sales") > 48);
        check("eTIMS: the local serial sequence has no duplicates (it was MAX+1 read before the write)", serials > 0 && distinct == serials && distinctSerial == serials);
        check("eTIMS: the serial counter has no skipped numbers (at most the one whose sale this check deleted is missing; the counter is one past the last)", (n("SELECT MAX(sequence) FROM etims_serials") - n("SELECT MIN(sequence) FROM etims_serials") + 1) - serials <= 1 && n("SELECT MAX(sequence) FROM etims_serials") == counter(DocType.ETIMS_LOCAL) - 1);
        boolean notConfigurable = false;
        try (Connection c = db()) { NumberSequence.configure(c, DocType.ETIMS_LOCAL, "X-", 3); } catch (IllegalArgumentException e) { notConfigurable = true; }
        check("the eTIMS serial counter cannot be reformatted into something non-numeric", notConfigurable);

        sql("INSERT INTO suppliers (code, name) VALUES ('NPS','Sup')");
        int supId = (int) n("SELECT id FROM suppliers WHERE code='NPS'");
        PurchaseDAO pd = new PurchaseDAO();
        String previewBefore = pd.generatePurchaseNumber(), previewAgain = pd.generatePurchaseNumber();
        check("PurchaseDAO.generatePurchaseNumber (the form preview) does not use the number up", previewBefore.equals(previewAgain) && previewBefore.equals(peek(DocType.PURCHASE)));
        Purchase po = new Purchase(); po.setSupplierId(supId); po.setSubtotal(10); po.setTotalAmount(10); po.setBalance(10); po.setStatus("pending"); po.setPaymentStatus("unpaid"); po.setCreatedBy(1); po.setReceivedBy(1);
        PurchaseItem pi = new PurchaseItem(); pi.setProductId(pid); pi.setProductName("P"); pi.setSku("NP1"); pi.setQuantity(1); pi.setUnitCost(10); pi.setLineTotal(10); po.setItems(new ArrayList<>(List.of(pi)));
        Purchase saved = pd.save(po);
        check("PurchaseDAO.save: the order gets exactly the previewed number", saved != null && previewBefore.equals(saved.getPurchaseNumber()));

        QuotationDAO qd = new QuotationDAO();
        int q1 = qd.createQuotation("A", null, null, null, 7, 1), q2 = qd.createQuotation("B", null, null, null, 7, 1);
        sql("DELETE FROM quotations WHERE id = " + q1);
        int q3 = qd.createQuotation("C", null, null, null, 7, 1);
        check("QuotationDAO.createQuotation: unique numbers, and still unique after one was deleted", q1 > 0 && q2 > 0 && q3 > 0 && n("SELECT COUNT(DISTINCT quote_number) FROM quotations") == 2);

        EmployeeDAO ed = new EmployeeDAO();
        Employee e1 = new Employee(); e1.setName("E1"); e1.setDepartmentId(1); e1.setSalary(1); e1.setPosition("x"); Employee e2 = new Employee(); e2.setName("E2"); e2.setDepartmentId(1); e2.setSalary(1); e2.setPosition("x");
        boolean ok1 = ed.save(e1), ok2 = ed.save(e2);
        sql("DELETE FROM employees WHERE name = 'E1'");
        Employee e3 = new Employee(); e3.setName("E3"); e3.setDepartmentId(1); e3.setSalary(1); e3.setPosition("x");
        check("EmployeeDAO.save: consecutive numbers (EMP-014, EMP-015) and no collision after a delete", ok1 && ok2 && ed.save(e3)
                && n("SELECT COUNT(*) FROM employees WHERE employee_number='EMP-014'") == 0 && n("SELECT COUNT(*) FROM employees WHERE employee_number='EMP-015'") == 1 && n("SELECT COUNT(*) FROM employees WHERE employee_number='EMP-016'") == 1);

        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
