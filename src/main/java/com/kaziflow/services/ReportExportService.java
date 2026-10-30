package com.kaziflow.services;

import com.kaziflow.dao.SaleDAO;
import com.kaziflow.dao.ProductDAO;
import com.kaziflow.dao.ExpenseDAO;
import com.kaziflow.dao.EmployeeDAO;
import com.kaziflow.dao.TransactionDAO;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.utils.KESFormatter;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;

import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Report generation service.
 * Generates formatted CSV reports for all modules.
 * JasperReports PDF generation is available when jasperreports.jar is on classpath.
 */
public class ReportExportService {

    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TS_FMT    = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter FILE_FMT  = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // ─── Public API ──────────────────────────────────────────────────────────

    public enum ReportType { SALES, INVENTORY, EMPLOYEES, EXPENSES, PURCHASES, FINANCE_SUMMARY }

    /**
     * Shows a file chooser and exports the chosen report type as CSV.
     */
    public void exportCSV(ReportType type) { exportCSV(type, com.kaziflow.utils.DateRange.ALL); }

    /** Reports whose rows belong to a date, so a period (this month, last quarter…) is meaningful. */
    public static boolean isPeriodBased(ReportType type) {
        return type == ReportType.SALES || type == ReportType.EXPENSES || type == ReportType.PURCHASES || type == ReportType.FINANCE_SUMMARY;
    }

    public void exportCSV(ReportType type, com.kaziflow.utils.DateRange range) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Report as CSV");
        chooser.setInitialFileName(getDefaultFilename(type, "csv", range));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = chooser.showSaveDialog(null);
        if (file != null) {
            new Thread(() -> {
                try {
                    generateCSV(type, file, range);
                    Platform.runLater(() -> showSuccess("Report saved", file.getAbsolutePath()));
                } catch (Exception e) {
                    Platform.runLater(() -> showError("Export failed: " + e.getMessage()));
                }
            }).start();
        }
    }

    /**
     * Exports directly to a file without showing a dialog (e.g., for scheduled exports).
     */
    public String exportCSVSilent(ReportType type, String directory) {
        return exportCSVSilent(type, directory, com.kaziflow.utils.DateRange.ALL);
    }

    /** Same, for a chosen period (the file name carries it). */
    public String exportCSVSilent(ReportType type, String directory, com.kaziflow.utils.DateRange range) {
        try {
            String filename = getDefaultFilename(type, "csv", range);
            File file = new File(directory, filename);
            generateCSV(type, file, range);
            return file.getAbsolutePath();
        } catch (Exception e) {
            System.err.println("[ReportExport] Silent export failed: " + e.getMessage());
            return null;
        }
    }

    // ─── CSV Generators ──────────────────────────────────────────────────────

    private void generateCSV(ReportType type, File file, com.kaziflow.utils.DateRange range) throws Exception {
        switch (type) {
            case SALES         -> generateSalesCSV(file, range);
            case INVENTORY     -> generateInventoryCSV(file);
            case EMPLOYEES     -> generateEmployeesCSV(file);
            case EXPENSES      -> generateExpensesCSV(file, range);
            case PURCHASES     -> generatePurchasesCSV(file, range);
            case FINANCE_SUMMARY -> generateFinanceSummaryCSV(file, range);
        }
    }

    private void generateSalesCSV(File file, com.kaziflow.utils.DateRange range) throws Exception {
        try (PrintWriter pw = openCsv(file)) {
            pw.println(com.kaziflow.brand.Brand.productFullName() + " — Sales Report");
            pw.println("Period: " + range.label());
            pw.println("Generated: " + LocalDateTime.now().format(TS_FMT));
            pw.println();
            pw.println("Receipt No,Date,Customer,Cashier,Subtotal,Discount,VAT,Total,Payment Method,M-Pesa Ref,Status");

            int completed = 0, other = 0, rows = 0;
            double sSub = 0, sDisc = 0, sVat = 0, sTot = 0;
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(
                    // No row cap: an export labelled "Sales Report" must contain every sale in the period.
                    "SELECT s.*, u.name as cashier_name FROM sales s LEFT JOIN users u ON s.served_by=u.id WHERE 1=1"
                    + range.whereClause("s.created_at") + " ORDER BY s.created_at DESC")) {
                range.bind(ps, 1);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    rows++;
                    String status = rs.getString("status");
                    // Totals count completed sales only; refunded / pending sales are listed but not summed.
                    if ("completed".equalsIgnoreCase(status)) {
                        completed++;
                        sSub += rs.getDouble("subtotal"); sDisc += rs.getDouble("discount_amount");
                        sVat += rs.getDouble("vat_amount"); sTot += rs.getDouble("total_amount");
                    } else other++;
                    pw.printf(java.util.Locale.ROOT, "%s,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%s,%s,%s%n",
                        esc(rs.getString("sale_number")),
                        esc(rs.getString("created_at")),
                        esc(rs.getString("customer_name") != null ? rs.getString("customer_name") : "Walk-in"),
                        esc(rs.getString("cashier_name") != null ? rs.getString("cashier_name") : "—"),
                        rs.getDouble("subtotal"), rs.getDouble("discount_amount"), rs.getDouble("vat_amount"), rs.getDouble("total_amount"),
                        esc(rs.getString("payment_method")),
                        esc(rs.getString("mpesa_ref") != null ? rs.getString("mpesa_ref") : ""),
                        esc(status));
                }
            }
            // Totals are summed from the very rows above. (The old footer printed this WEEK's revenue under a
            // "Total Revenue" label, which did not match the file's contents.)
            pw.println();
            pw.printf(java.util.Locale.ROOT, "TOTAL (%d completed sale(s)),,,,%.2f,%.2f,%.2f,%.2f,,,%n", completed, sSub, sDisc, sVat, sTot);
            if (other > 0) pw.println("\"" + other + " sale(s) with another status (refunded or pending) are listed above but not included in the total.\"");
            if (rows == 0) pw.println("\"No sales in this period.\"");
            pw.println("\"Subtotal is before VAT. Total is what the customer was charged: Subtotal + VAT, less any loyalty discount.\"");
        }
    }
    private void generateInventoryCSV(File file) throws Exception {
        try (PrintWriter pw = openCsv(file)) {
            pw.println(com.kaziflow.brand.Brand.productFullName()+" — Inventory Report");
            pw.println("Generated: " + LocalDateTime.now().format(TS_FMT));
            pw.println();
            pw.println("SKU,Product Name,Category,Supplier,Selling Price,Cost Price,Stock Qty,Min Stock,Unit,Stock Value,Status");

            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                    "SELECT p.*, c.name as cat_name, s.name as supp_name FROM products p " +
                    "LEFT JOIN categories c ON p.category_id=c.id " +
                    "LEFT JOIN suppliers s ON p.supplier_id=s.id " +
                    "WHERE p.status='active' ORDER BY p.name");
                while (rs.next()) {
                    double stock = rs.getDouble("stock_quantity");
                    double minStock = rs.getDouble("min_stock_level");
                    double stockValue = stock * rs.getDouble("cost_price");
                    String status = stock <= 0 ? "Out of Stock" : stock <= minStock ? "Low Stock" : "In Stock";
                    pw.printf(java.util.Locale.ROOT, "%s,%s,%s,%s,%.2f,%.2f,%.0f,%.0f,%s,%.2f,%s%n",
                        esc(rs.getString("sku")),
                        esc(rs.getString("name")),
                        esc(rs.getString("cat_name")),
                        esc(rs.getString("supp_name") != null ? rs.getString("supp_name") : "—"),
                        rs.getDouble("selling_price"),
                        rs.getDouble("cost_price"),
                        stock, minStock,
                        esc(rs.getString("unit")),
                        stockValue, status
                    );
                }
            }

            ProductDAO productDAO = new ProductDAO();
            pw.println();
            pw.println("Total Products,,,,,,,,,,");
            pw.println("Total Stock Value: " + String.format(java.util.Locale.ROOT, "%.2f", productDAO.getTotalStockValue()));
            pw.println("Low Stock Items: " + productDAO.getLowStockCount());
        }
    }

    private void generateEmployeesCSV(File file) throws Exception {
        try (PrintWriter pw = openCsv(file)) {
            pw.println(com.kaziflow.brand.Brand.productFullName()+" — Employee Report");
            pw.println("Generated: " + LocalDateTime.now().format(TS_FMT));
            pw.println();
            pw.println("Employee No,Name,Email,Phone,Department,Position,Type,Salary,Hire Date,Status");

            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                    "SELECT e.*, d.name as dept_name FROM employees e LEFT JOIN departments d ON e.department_id=d.id ORDER BY e.name");
                while (rs.next()) {
                    pw.printf(java.util.Locale.ROOT, "%s,%s,%s,%s,%s,%s,%s,%.2f,%s,%s%n",
                        esc(rs.getString("employee_number")),
                        esc(rs.getString("name")),
                        esc(rs.getString("email") != null ? rs.getString("email") : ""),
                        esc(rs.getString("phone") != null ? rs.getString("phone") : ""),
                        esc(rs.getString("dept_name") != null ? rs.getString("dept_name") : ""),
                        esc(rs.getString("position") != null ? rs.getString("position") : ""),
                        esc(rs.getString("employment_type") != null ? rs.getString("employment_type") : ""),
                        rs.getDouble("salary"),
                        esc(rs.getString("hire_date") != null ? rs.getString("hire_date") : ""),
                        esc(rs.getString("status"))
                    );
                }
            }

            EmployeeDAO employeeDAO = new EmployeeDAO();
            pw.println();
            pw.println("Total Employees: " + employeeDAO.getTotalCount());
            pw.println("Total Monthly Payroll: " + String.format(java.util.Locale.ROOT, "%.2f", employeeDAO.getTotalMonthlySalary()));
        }
    }

    private void generateExpensesCSV(File file, com.kaziflow.utils.DateRange range) throws Exception {
        try (PrintWriter pw = openCsv(file)) {
            pw.println(com.kaziflow.brand.Brand.productFullName() + " — Expenses Report");
            pw.println("Period: " + range.label());
            pw.println("Generated: " + LocalDateTime.now().format(TS_FMT));
            pw.println();
            pw.println("ID,Description,Category,Amount,Payment Method,Receipt No,Date,Notes");

            int rows = 0;
            double total = 0;
            java.util.TreeMap<String, Double> byCategory = new java.util.TreeMap<>();
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM expenses WHERE 1=1" + range.whereClause("expense_date") + " ORDER BY expense_date DESC")) {
                range.bind(ps, 1);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    rows++;
                    double amount = rs.getDouble("amount");
                    total += amount;
                    String cat = rs.getString("category") != null && !rs.getString("category").isBlank() ? rs.getString("category") : "Uncategorised";
                    byCategory.merge(cat, amount, Double::sum);
                    pw.printf(java.util.Locale.ROOT, "%d,%s,%s,%.2f,%s,%s,%s,%s%n",
                        rs.getInt("id"),
                        esc(rs.getString("description")),
                        esc(rs.getString("category") != null ? rs.getString("category") : ""),
                        amount,
                        esc(rs.getString("payment_method") != null ? rs.getString("payment_method") : "cash"),
                        esc(rs.getString("receipt_number") != null ? rs.getString("receipt_number") : ""),
                        esc(rs.getString("expense_date") != null ? rs.getString("expense_date") : ""),
                        esc(rs.getString("notes") != null ? rs.getString("notes") : ""));
                }
            }
            pw.println();
            pw.printf(java.util.Locale.ROOT, "TOTAL (%d expense(s)),,,%.2f,,,,%n", rows, total);
            if (rows == 0) pw.println("\"No expenses in this period.\"");
            else {
                pw.println();
                pw.println("BY CATEGORY");
                pw.println("Category,Amount,Share of total");
                final double grand = total;
                byCategory.entrySet().stream().sorted(java.util.Map.Entry.<String, Double>comparingByValue().reversed()).forEach(e ->
                    pw.printf(java.util.Locale.ROOT, "%s,%.2f,%.1f%%%n", esc(e.getKey()), e.getValue(), grand > 0 ? e.getValue() / grand * 100.0 : 0.0));
            }
        }
    }
    private void generatePurchasesCSV(File file, com.kaziflow.utils.DateRange range) throws Exception {
        try (PrintWriter pw = openCsv(file)) {
            pw.println(com.kaziflow.brand.Brand.productFullName() + " — Purchases Report");
            pw.println("Period: " + range.label());
            pw.println("Generated: " + LocalDateTime.now().format(TS_FMT));
            pw.println();
            pw.println("PO Number,Supplier,Date,Subtotal,VAT,Total,Amount Paid,Balance Owed,Payment Method,Payment Status,Status,Notes");

            int rows = 0;
            double sub = 0, vat = 0, total = 0, paid = 0, balance = 0;
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT p.*, s.name as supp_name FROM purchases p LEFT JOIN suppliers s ON p.supplier_id=s.id WHERE 1=1"
                    + range.whereClause("p.created_at") + " ORDER BY p.created_at DESC")) {
                range.bind(ps, 1);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    rows++;
                    sub += rs.getDouble("subtotal"); vat += rs.getDouble("vat_amount"); total += rs.getDouble("total_amount");
                    paid += rs.getDouble("amount_paid"); balance += rs.getDouble("balance");
                    pw.printf(java.util.Locale.ROOT, "%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%s,%s,%s,%s%n",
                        esc(rs.getString("purchase_number")),
                        esc(rs.getString("supp_name") != null ? rs.getString("supp_name") : "—"),
                        esc(rs.getString("created_at")),
                        rs.getDouble("subtotal"), rs.getDouble("vat_amount"), rs.getDouble("total_amount"),
                        rs.getDouble("amount_paid"), rs.getDouble("balance"),
                        esc(rs.getString("payment_method") != null ? rs.getString("payment_method") : ""),
                        esc(rs.getString("payment_status") != null ? rs.getString("payment_status") : ""),
                        esc(rs.getString("status")),
                        esc(rs.getString("notes") != null ? rs.getString("notes") : ""));
                }
            }
            pw.println();
            pw.printf(java.util.Locale.ROOT, "TOTAL (%d purchase(s)),,,%.2f,%.2f,%.2f,%.2f,%.2f,,,,%n", rows, sub, vat, total, paid, balance);
            if (rows == 0) pw.println("\"No purchases in this period.\"");
        }
    }
    /**
     * Finance summary for a period, built ONLY from source records: completed sales, the cost recorded on each sale
     * line, and the expenses register. (The earlier version invented its expense lines — "Cost of Goods Sold = 60% of
     * expenses, Payroll = 40%, Rent = 15%, Other = 45%" — which neither came from the data nor added up.) Every
     * figure's definition is printed on the report, and it warns when the data cannot support a figure.
     */
    private void generateFinanceSummaryCSV(File file, com.kaziflow.utils.DateRange range) throws Exception {
        int sales = 0, otherStatus = 0, linesWithoutCost = 0;
        double before = 0, discount = 0, vat = 0, received = 0, cogs = 0, expensesTotal = 0;
        java.util.LinkedHashMap<String, Double> byCategory = new java.util.LinkedHashMap<>();
        try (java.sql.Connection c = com.kaziflow.database.DatabaseManager.getInstance().getConnection()) {
            try (java.sql.PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*), COALESCE(SUM(subtotal),0), COALESCE(SUM(discount_amount),0), COALESCE(SUM(vat_amount),0), COALESCE(SUM(total_amount),0) "
                  + "FROM sales WHERE status = 'completed'" + range.whereClause("created_at"))) {
                range.bind(ps, 1);
                java.sql.ResultSet rs = ps.executeQuery();
                if (rs.next()) { sales = rs.getInt(1); before = rs.getDouble(2); discount = rs.getDouble(3); vat = rs.getDouble(4); received = rs.getDouble(5); }
            }
            try (java.sql.PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM sales WHERE status <> 'completed'" + range.whereClause("created_at"))) {
                range.bind(ps, 1);
                java.sql.ResultSet rs = ps.executeQuery();
                if (rs.next()) otherStatus = rs.getInt(1);
            }
            try (java.sql.PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(SUM(si.quantity * si.cost_price),0), COALESCE(SUM(CASE WHEN si.cost_price = 0 THEN 1 ELSE 0 END),0) "
                  + "FROM sale_items si JOIN sales s ON s.id = si.sale_id WHERE s.status = 'completed'" + range.whereClause("s.created_at"))) {
                range.bind(ps, 1);
                java.sql.ResultSet rs = ps.executeQuery();
                if (rs.next()) { cogs = rs.getDouble(1); linesWithoutCost = rs.getInt(2); }
            }
            try (java.sql.PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(NULLIF(TRIM(category),''),'Uncategorised') AS cat, COALESCE(SUM(amount),0) AS total FROM expenses WHERE 1=1"
                  + range.whereClause("expense_date") + " GROUP BY 1 ORDER BY 2 DESC")) {
                range.bind(ps, 1);
                java.sql.ResultSet rs = ps.executeQuery();
                while (rs.next()) { byCategory.put(rs.getString("cat"), rs.getDouble("total")); expensesTotal += rs.getDouble("total"); }
            }
        }
        double grossProfit = before - cogs;
        double margin = before > 0 ? grossProfit / before * 100.0 : 0;
        double result = grossProfit - expensesTotal;

        try (PrintWriter pw = openCsv(file)) {
            pw.println(com.kaziflow.brand.Brand.productFullName() + " — Finance Summary");
            pw.println("Period: " + range.label());
            pw.println("Generated: " + LocalDateTime.now().format(TS_FMT));
            pw.println();
            pw.println("SALES (completed sales only)");
            pw.println("Item,Amount (KES)");
            pw.printf(java.util.Locale.ROOT, "Number of sales,%d%n", sales);
            pw.printf(java.util.Locale.ROOT, "Sales before VAT,%.2f%n", before);
            pw.printf(java.util.Locale.ROOT, "VAT charged,%.2f%n", vat);
            pw.printf(java.util.Locale.ROOT, "Loyalty discount redeemed,%.2f%n", discount);
            pw.printf(java.util.Locale.ROOT, "Total received from customers,%.2f%n", received);
            pw.println();
            pw.println("COST AND PROFIT");
            pw.println("Item,Amount (KES)");
            pw.printf(java.util.Locale.ROOT, "Cost of goods sold,%.2f%n", cogs);
            pw.printf(java.util.Locale.ROOT, "Gross profit,%.2f%n", grossProfit);
            pw.printf(java.util.Locale.ROOT, "Gross margin %%,%.1f%n", margin);
            pw.println();
            pw.println("EXPENSES (from the Expenses register)");
            pw.println("Category,Amount (KES)");
            if (byCategory.isEmpty()) pw.println("(no expenses recorded in this period),0.00");
            for (var e : byCategory.entrySet()) pw.println(esc(e.getKey()) + "," + String.format(java.util.Locale.ROOT, "%.2f", e.getValue()));
            pw.printf(java.util.Locale.ROOT, "Total expenses,%.2f%n", expensesTotal);
            pw.println();
            pw.printf(java.util.Locale.ROOT, "RESULT (gross profit - expenses),%.2f%n", result);
            pw.println();
            pw.println("HOW THESE FIGURES ARE WORKED OUT");
            pw.println("\"Sales before VAT = the sum of each completed sale's subtotal (VAT is added on top at checkout).\"");
            pw.println("\"Cost of goods sold = quantity x the cost price recorded on each sale line when it was sold.\"");
            pw.println("\"Loyalty discount is deducted from the VAT-inclusive total at payment; it is shown, not subtracted from gross profit.\"");
            pw.println("\"Expenses are exactly what was entered in the Expenses register. Payroll appears only if it was recorded there.\"");
            pw.println("\"Not included: returns/refunds, input VAT on purchases (see Finance > VAT Summary), and income tax.\"");
            pw.println("\"The Finance screen's Net Profit is taken from the cash transaction log, so it can differ from this report.\"");
            if (otherStatus > 0) pw.println("\"NOTE: " + otherStatus + " sale(s) in this period have another status (e.g. refunded or void) and are not counted.\"");
            if (linesWithoutCost > 0)
                pw.println("\"WARNING: " + linesWithoutCost + " sale line(s) have no cost price recorded, so cost of goods sold is understated and gross profit may be overstated.\"");
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Opens a CSV for writing as UTF-8 with a byte-order mark. (FileWriter used the PC's default charset, which on
     * Windows corrupts "—", "→" and accented names, and Excel cannot detect UTF-8 without the mark.)
     */
    private PrintWriter openCsv(File file) throws java.io.IOException {
        PrintWriter pw = new PrintWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8));
        pw.print('\uFEFF');
        return pw;
    }

    /** Escapes a value for CSV: wraps in quotes if it contains commas/quotes/newlines. */
    private String esc(String value) {
        if (value == null) return "";
        String v = value;
        // A text cell that starts like a formula would be EXECUTED by Excel when the file is opened (CSV injection:
        // a customer named "=HYPERLINK(...)" ). Prefix those with an apostrophe. A leading + or - is left alone when it
        // is followed by a digit/space so phone numbers (+254 7…) and negative numbers keep their value.
        if (!v.isEmpty()) {
            char c0 = v.charAt(0), c1 = v.length() > 1 ? v.charAt(1) : ' ';
            boolean formula = c0 == '=' || c0 == '@' || c0 == '\t' || c0 == '\r'
                    || ((c0 == '+' || c0 == '-') && !(Character.isDigit(c1) || c1 == ' ' || c1 == '.'));
            if (formula) v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private String getDefaultFilename(ReportType type, String ext, com.kaziflow.utils.DateRange range) {
        String base = getDefaultFilename(type, ext);
        if (!isPeriodBased(type) || range.isAll()) return base;
        int dot = base.lastIndexOf('.');
        return base.substring(0, dot) + "_" + range.fileSuffix() + base.substring(dot);
    }

    private String getDefaultFilename(ReportType type, String ext) {
        String prefix = switch (type) {
            case SALES         -> "Sales_Report";
            case INVENTORY     -> "Inventory_Report";
            case EMPLOYEES     -> "Employees_Report";
            case EXPENSES      -> "Expenses_Report";
            case PURCHASES     -> "Purchases_Report";
            case FINANCE_SUMMARY -> "Finance_Summary";
        };
        return prefix + "_" + LocalDateTime.now().format(FILE_FMT) + "." + ext;
    }

    private void showSuccess(String title, String path) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, "Report saved to:\n" + path, ButtonType.OK);
        com.kaziflow.utils.DialogUtils.makeResizable(a);
        a.setTitle(title); a.setHeaderText(null); a.show();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        com.kaziflow.utils.DialogUtils.makeResizable(a);
        a.setTitle("Export Error"); a.setHeaderText(null); a.show();
    }
}
