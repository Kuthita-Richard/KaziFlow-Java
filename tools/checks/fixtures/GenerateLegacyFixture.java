// Generates tools/checks/fixtures/legacy-v11.42-populated.db: a POPULATED database in the schema as it was BEFORE v11.43
// (sales.payment_method has CHECK(IN ('cash','mpesa','card','bank'))). PaymentMethodsCheck migrates a copy of it.
// It can only be regenerated with code from before v11.43 (git show 47626c1); the fixture file is the artefact to keep.
import com.kaziflow.dao.*;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.*;
import com.kaziflow.services.DemoDataService;

import java.io.File;
import java.sql.*;
import java.util.*;

public class GenerateLegacyFixture {
    static Connection db() throws SQLException { return DatabaseManager.getInstance().getConnection(); }
    static void sql(String s) throws SQLException { try (Connection c = db(); Statement st = c.createStatement()) { st.execute(s); } }
    static int id(String q) throws SQLException { try (Connection c = db(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(q)) { return rs.next() ? rs.getInt(1) : 0; } }

    public static void main(String[] a) throws Exception {
        String home = a[0];
        System.setProperty("user.home", home);
        DatabaseManager.getInstance().initialize();
        com.kaziflow.database.SchemaMigrations.runAll();
        com.kaziflow.services.ETIMSService.getInstance();
        DemoDataService.load();                                   // suppliers, employees, products, customers, transactions, expenses
        sql("INSERT INTO products (sku, name, selling_price, cost_price, stock_quantity, status) VALUES ('FX-1','Fixture A',250,150,100000,'active'),('FX-2','Fixture B',90,50,100000,'active')");
        int p1 = id("SELECT id FROM products WHERE sku='FX-1'"), p2 = id("SELECT id FROM products WHERE sku='FX-2'");
        String[] pay = {"cash", "mpesa", "card", "bank"};
        List<Integer> custIds = new ArrayList<>();
        try (Connection c = db(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id FROM customers")) { while (rs.next()) custIds.add(rs.getInt(1)); }
        SaleDAO sd = new SaleDAO(); int saved = 0; String firstSale = null;
        for (int i = 0; i < 40; i++) {
            Sale s = new Sale(); s.setCustomerName("Fx " + i); s.setPaymentMethod(pay[i % 4]); s.setStatus("completed"); s.setServedBy(1); s.setServedByName("Admin");
            if (i % 3 == 0 && !custIds.isEmpty()) s.setCustomerId(custIds.get(i % custIds.size()));
            List<SaleItem> items = new ArrayList<>(); double total = 0;
            for (int k = 0; k <= i % 3; k++) {
                SaleItem it = new SaleItem(); int pid = k % 2 == 0 ? p1 : p2; double price = k % 2 == 0 ? 250 : 90;
                it.setProductId(pid); it.setProductName(k % 2 == 0 ? "Fixture A" : "Fixture B"); it.setQuantity(k + 1); it.setUnitPrice(price); it.setCostPrice(price * 0.6); it.setDiscount(0); it.setLineTotal(price * (k + 1));
                total += price * (k + 1); items.add(it);
            }
            s.setItems(items); s.setSubtotal(total); s.setTotalAmount(total); s.setVatAmount(0); s.setDiscountAmount(0); s.setAmountPaid(total); s.setChangeAmount(0);
            Sale r = sd.save(s); if (r != null) { saved++; if (firstSale == null) firstSale = r.getSaleNumber(); }
        }
        Thread.sleep(3000);                                       // background eTIMS threads
        sql("UPDATE sales SET created_at = datetime('now','-' || (id % 90) || ' days') WHERE id % 2 = 0");
        int saleId = id("SELECT id FROM sales ORDER BY id LIMIT 1");
        new SaleReturnDAO().processReturn(saleId, firstSale, p1, "Fixture A", 1, 250, "Fixture return", 1);
        sql("INSERT INTO suppliers (code, name) VALUES ('FXS','Fixture Supplier')");
        int sup = id("SELECT id FROM suppliers WHERE code='FXS'");
        for (int i = 0; i < 3; i++) {
            Purchase po = new Purchase(); po.setSupplierId(sup); po.setSubtotal(100 * (i + 1)); po.setTotalAmount(100 * (i + 1)); po.setBalance(100 * (i + 1)); po.setStatus("pending"); po.setPaymentStatus("unpaid"); po.setCreatedBy(1); po.setReceivedBy(1);
            PurchaseItem pi = new PurchaseItem(); pi.setProductId(p1); pi.setProductName("Fixture A"); pi.setSku("FX-1"); pi.setQuantity(i + 1); pi.setUnitCost(100); pi.setLineTotal(100 * (i + 1));
            po.setItems(new ArrayList<>(List.of(pi))); new PurchaseDAO().save(po);
        }
        QuotationDAO qd = new QuotationDAO();
        for (int i = 0; i < 2; i++) { int q = qd.createQuotation("Q" + i, null, null, null, 7, 1); qd.addItem(q, p1, "Fixture A", 2, 250, 0); }
        sql("PRAGMA wal_checkpoint(TRUNCATE)");
        System.out.println("generated: sales saved " + saved + "; sales rows " + id("SELECT COUNT(*) FROM sales") + "; sale_items " + id("SELECT COUNT(*) FROM sale_items")
            + "; etims " + id("SELECT COUNT(*) FROM etims_serials") + "; returns " + id("SELECT COUNT(*) FROM sale_returns") + "; purchases " + id("SELECT COUNT(*) FROM purchases") + "; quotations " + id("SELECT COUNT(*) FROM quotations"));
        System.exit(0);
    }
}
