// Exports: periods, completeness (no 1,000-row cap), totals that match the file, honest finance summary, encoding/locale, injection.
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.services.ReportExportService;
import com.kaziflow.services.ReportExportService.ReportType;
import com.kaziflow.utils.DateRange;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class ReportExportCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static void x(String sql) throws Exception { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement()) { s.execute(sql); } }
    static String q(String sql) throws Exception { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { return r.next() ? r.getString(1) : null; } }
    static String read(String path) throws Exception { return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8); }
    static String line(String text, String startsWith) { return Arrays.stream(text.replace("\r", "").split("\n")).filter(l -> l.startsWith(startsWith)).findFirst().orElse(null); }

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        Locale.setDefault(Locale.GERMANY);                       // a PC set to a comma-decimal region: 1234,56
        ReportExportService svc = new ReportExportService();
        File dir = Files.createTempDirectory("exports").toFile();

        x("INSERT INTO sales (sale_number, subtotal, vat_amount, discount_amount, total_amount, payment_method, status, created_at, customer_name) VALUES "
          + "('S-1', 1000, 160, 0, 1160, 'cash', 'completed', '2026-03-01 09:00:00', 'Wanjiku'),"
          + "('S-2', 2000, 320, 100, 2220, 'mpesa', 'completed', '2026-03-15 12:30:00', 'Otieno'),"
          + "('S-3',  500,  80, 0,  580, 'cash', 'completed', '2026-03-31 23:59:00', '=HYPERLINK(\"http://evil\",\"x\")'),"
          + "('S-4', 9999, 1600, 0, 11599, 'cash', 'refunded',  '2026-03-10 10:00:00', 'Refunded Person'),"
          + "('S-5',  700, 112, 0,  812, 'cash', 'completed', '2026-04-01 00:00:00', 'Akinyi — Café')");
        x("INSERT INTO products (sku, name, selling_price, cost_price, stock_quantity) VALUES ('P1','Item',100,60,10)");
        String pid = q("SELECT id FROM products WHERE sku='P1'");
        for (String[] l : new String[][]{{"S-1", "10", "100", "60"}, {"S-2", "20", "100", "70"}, {"S-3", "5", "100", "0"}, {"S-4", "99", "100", "60"}, {"S-5", "7", "100", "60"}}) {
            String id = q("SELECT id FROM sales WHERE sale_number='" + l[0] + "'");
            x("INSERT INTO sale_items (sale_id, product_id, product_name, quantity, unit_price, cost_price, line_total) VALUES (" + id + "," + pid + ",'Item'," + l[1] + "," + l[2] + "," + l[3] + "," + (Integer.parseInt(l[1]) * Integer.parseInt(l[2])) + ")");
        }
        x("INSERT INTO expenses (description, category, amount, expense_date) VALUES ('Rent', 'Rent', 300, '2026-03-05'), ('Power', 'Utilities', 120.50, '2026-03-20'), ('Misc', '', 80, '2026-03-25'), ('April rent', 'Rent', 300, '2026-04-02')");

        DateRange march = new DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        String salesPath = svc.exportCSVSilent(ReportType.SALES, dir.getAbsolutePath(), march);
        String sales = read(salesPath);

        check("the file name carries the period (…_2026-03-01_to_2026-03-31)", salesPath.contains("2026-03-01_to_2026-03-31"));
        check("the report states its period", sales.contains("Period: 1 Mar 2026 – 31 Mar 2026"));
        check("March rows are in, including the last minute of March (23:59 on the 31st)", sales.contains("S-1,") && sales.contains("S-2,") && sales.contains("S-3,"));
        check("April's first instant (00:00 on the 1st) is NOT in a March report", !sales.contains("S-5,"));
        check("a refunded sale is listed (so nothing is hidden)…", sales.contains("S-4,"));
        String total = line(sales, "TOTAL");
        check("…but only COMPLETED sales are totalled: 3 sales, subtotal 3500.00, discount 100.00, VAT 560.00, total 3960.00", total != null && total.startsWith("TOTAL (3 completed sale(s))") && total.contains(",3500.00,100.00,560.00,3960.00"));
        check("the report says one sale was listed but not counted", sales.contains("1 sale(s) with another status"));
        check("numbers use '.' decimals even on a comma-decimal PC (Locale.GERMANY): no '3500,00'", !sales.contains("3500,00") && sales.contains("3500.00"));
        check("the old footer ('Total Revenue' = THIS WEEK's revenue) is gone", !sales.contains("Total Revenue"));
        check("a customer name that looks like a formula is defused with a leading apostrophe", sales.contains("'=HYPERLINK") && !sales.contains(",=HYPERLINK") && !sales.contains("\"=HYPERLINK"));

        try (Connection c = DatabaseManager.getInstance().getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO sales (sale_number, subtotal, vat_amount, total_amount, payment_method, status, created_at) VALUES (?, 10, 1.6, 11.6, 'cash', 'completed', '2026-02-10 10:00:00')")) {
            c.setAutoCommit(false);
            for (int i = 0; i < 2500; i++) { ps.setString(1, "BULK-" + i); ps.executeUpdate(); }
            c.commit();
        }
        String allSales = read(svc.exportCSVSilent(ReportType.SALES, dir.getAbsolutePath()));
        long dataRows = Arrays.stream(allSales.replace("\r", "").split("\n")).filter(l -> l.startsWith("BULK-") || l.startsWith("S-")).count();
        check("all-time export contains EVERY sale: 2,505 rows (the old export was capped at 1,000)", dataRows == 2505);
        check("…and the TOTAL line is the sum of those rows (2,504 completed; 2500×11.60 + 1160 + 2220 + 580 + 812 = 33,772.00)",
              line(allSales, "TOTAL").startsWith("TOTAL (2504 completed sale(s))") && line(allSales, "TOTAL").endsWith(",33772.00,,,"));

        byte[] raw = Files.readAllBytes(new File(svc.exportCSVSilent(ReportType.SALES, dir.getAbsolutePath(), new DateRange(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1)))).toPath());
        check("file starts with a UTF-8 byte-order mark (so Excel reads it as UTF-8)", raw.length > 3 && (raw[0] & 255) == 0xEF && (raw[1] & 255) == 0xBB && (raw[2] & 255) == 0xBF);
        String april1 = new String(raw, StandardCharsets.UTF_8);
        check("accented text and the em dash survive (Akinyi — Café; title uses —)", april1.contains("Akinyi — Café") && april1.contains("— Sales Report"));
        check("a single-day period works (only S-5 on 1 April)", april1.contains("S-5,") && !april1.contains("S-1,") && april1.contains("Period: 1 Apr 2026"));
        check("an empty period says so instead of printing a blank report", read(svc.exportCSVSilent(ReportType.SALES, dir.getAbsolutePath(), new DateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31)))).contains("No sales in this period."));

        String exp = read(svc.exportCSVSilent(ReportType.EXPENSES, dir.getAbsolutePath(), march));
        check("expenses: March only (3 rows; April's rent excluded), TOTAL 500.50", exp.contains("Rent,") && !exp.contains("April rent") && line(exp, "TOTAL").startsWith("TOTAL (3 expense(s))") && line(exp, "TOTAL").contains("500.50"));
        check("expenses: by-category block, blank category shown as 'Uncategorised', shares add up", exp.contains("Uncategorised,80.00,16.0%") && exp.contains("Rent,300.00,59.9%") && exp.contains("Utilities,120.50,24.1%"));

        String fin = read(svc.exportCSVSilent(ReportType.FINANCE_SUMMARY, dir.getAbsolutePath(), march));
        check("finance: 3 completed sales, sales before VAT 3500.00, VAT 560.00, discount 100.00, total received 3960.00",
              fin.contains("Number of sales,3") && fin.contains("Sales before VAT,3500.00") && fin.contains("VAT charged,560.00") && fin.contains("Loyalty discount redeemed,100.00") && fin.contains("Total received from customers,3960.00"));
        check("finance: cost of goods sold = 10×60 + 20×70 + 5×0 = 2000.00 from the cost recorded on each sale line (refunded S-4 excluded)", fin.contains("Cost of goods sold,2000.00"));
        check("finance: gross profit = 3500 − 2000 = 1500.00 and margin = 42.9%", fin.contains("Gross profit,1500.00") && fin.contains("Gross margin %,42.9"));
        check("finance: expenses come from the register by real category (Rent 300, Utilities 120.50, Uncategorised 80), total 500.50", fin.contains("Rent,300.00") && fin.contains("Utilities,120.50") && fin.contains("Uncategorised,80.00") && fin.contains("Total expenses,500.50"));
        check("finance: result = 1500.00 − 500.50 = 999.50", fin.contains("RESULT (gross profit - expenses),999.50"));
        check("finance: NONE of the old invented lines (60% COGS / 40% payroll / 15% rent / 45% other) appear", !fin.contains("Payroll,") && !fin.contains("Rent & Utilities") && !fin.contains("Other Expenses"));
        check("finance: warns that one sale line has no recorded cost (S-3), so profit may be overstated", fin.contains("WARNING: 1 sale line(s) have no cost price recorded"));
        check("finance: says one refunded sale was not counted, and defines every figure", fin.contains("1 sale(s) in this period have another status") && fin.contains("HOW THESE FIGURES ARE WORKED OUT"));

        x("INSERT INTO suppliers (code, name) VALUES ('SUP-T', 'Acme Supplies')");
        String sid = q("SELECT id FROM suppliers WHERE name='Acme Supplies'");
        x("INSERT INTO purchases (purchase_number, supplier_id, subtotal, vat_amount, total_amount, amount_paid, balance, status, created_at) VALUES "
          + "('PO-1', " + sid + ", 4200, 800, 5000, 2000, 3000, 'received', '2026-03-12 08:00:00'), ('PO-2', " + sid + ", 100, 16, 116, 0, 116, 'received', '2026-05-01 08:00:00')");
        String pur = null; String purErr = null;
        try { pur = read(svc.exportCSVSilent(ReportType.PURCHASES, dir.getAbsolutePath(), march)); } catch (Exception e) { purErr = String.valueOf(e); }
        check("purchases: the export WORKS (the original selected a column that doesn't exist and always failed)", pur != null && purErr == null);
        check("purchases: March only, with a TOTAL: subtotal 4200.00, VAT 800.00, total 5000.00, paid 2000.00, owed 3000.00",
              pur != null && pur.contains("PO-1") && !pur.contains("PO-2") && line(pur, "TOTAL").contains("4200.00,800.00,5000.00,2000.00,3000.00"));
        check("inventory and employees exports still generate (snapshot reports, no period)", read(svc.exportCSVSilent(ReportType.INVENTORY, dir.getAbsolutePath())).contains("Inventory") && read(svc.exportCSVSilent(ReportType.EMPLOYEES, dir.getAbsolutePath())).length() > 20);
        check("only the date-based reports are period-aware", ReportExportService.isPeriodBased(ReportType.SALES) && ReportExportService.isPeriodBased(ReportType.FINANCE_SUMMARY) && !ReportExportService.isPeriodBased(ReportType.INVENTORY) && !ReportExportService.isPeriodBased(ReportType.EMPLOYEES));

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
