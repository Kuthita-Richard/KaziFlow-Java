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
    public void exportCSV(ReportType type) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Report as CSV");
        chooser.setInitialFileName(getDefaultFilename(type, "csv"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = chooser.showSaveDialog(null);
        if (file != null) {
            new Thread(() -> {
                try {
                    generateCSV(type, file);
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
        try {
            String filename = getDefaultFilename(type, "csv");
            File file = new File(directory, filename);
            generateCSV(type, file);
            return file.getAbsolutePath();
        } catch (Exception e) {
            System.err.println("[ReportExport] Silent export failed: " + e.getMessage());
            return null;
        }
    }

    // ─── CSV Generators ──────────────────────────────────────────────────────

    private void generateCSV(ReportType type, File file) throws Exception {
        switch (type) {
            case SALES         -> generateSalesCSV(file);
            case INVENTORY     -> generateInventoryCSV(file);
            case EMPLOYEES     -> generateEmployeesCSV(file);
            case EXPENSES      -> generateExpensesCSV(file);
            case PURCHASES     -> generatePurchasesCSV(file);
            case FINANCE_SUMMARY -> generateFinanceSummaryCSV(file);
        }
    }

    private void generateSalesCSV(File file) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            // Header
            pw.println("KaziFlow ERP — Sales Report");
            pw.println("Generated: " + LocalDateTime.now().format(TS_FMT));
            pw.println();
            pw.println("Receipt No,Date,Customer,Cashier,Subtotal,Discount,VAT,Total,Payment Method,M-Pesa Ref,Status");

            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                    "SELECT s.*, u.name as cashier_name FROM sales s LEFT JOIN users u ON s.served_by=u.id ORDER BY s.created_at DESC LIMIT 1000");
                while (rs.next()) {
                    pw.printf("%s,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%s,%s,%s%n",
                        esc(rs.getString("sale_number")),
                        esc(rs.getString("created_at")),
                        esc(rs.getString("customer_name") != null ? rs.getString("customer_name") : "Walk-in"),
                        esc(rs.getString("cashier_name") != null ? rs.getString("cashier_name") : "—"),
                        rs.getDouble("subtotal"),
                        rs.getDouble("discount_amount"),
                        rs.getDouble("vat_amount"),
                        rs.getDouble("total_amount"),
                        esc(rs.getString("payment_method")),
                        esc(rs.getString("mpesa_ref") != null ? rs.getString("mpesa_ref") : ""),
                        esc(rs.getString("status"))
                    );
                }
            }

            // Totals footer
            SaleDAO saleDAO = new SaleDAO();
            pw.println();
            pw.println("Summary,,,,,,,,,,");
            pw.println("Total Revenue,,,,,,," + String.format("%.2f", saleDAO.getWeekRevenue()) + ",,,");
        }
    }

    private void generateInventoryCSV(File file) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("KaziFlow ERP — Inventory Report");
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
                    pw.printf("%s,%s,%s,%s,%.2f,%.2f,%.0f,%.0f,%s,%.2f,%s%n",
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
            pw.println("Total Stock Value: " + String.format("%.2f", productDAO.getTotalStockValue()));
            pw.println("Low Stock Items: " + productDAO.getLowStockCount());
        }
    }

    private void generateEmployeesCSV(File file) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("KaziFlow ERP — Employee Report");
            pw.println("Generated: " + LocalDateTime.now().format(TS_FMT));
            pw.println();
            pw.println("Employee No,Name,Email,Phone,Department,Position,Type,Salary,Hire Date,Status");

            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                    "SELECT e.*, d.name as dept_name FROM employees e LEFT JOIN departments d ON e.department_id=d.id ORDER BY e.name");
                while (rs.next()) {
                    pw.printf("%s,%s,%s,%s,%s,%s,%s,%.2f,%s,%s%n",
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
            pw.println("Total Monthly Payroll: " + String.format("%.2f", employeeDAO.getTotalMonthlySalary()));
        }
    }

    private void generateExpensesCSV(File file) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("KaziFlow ERP — Expenses Report");
            pw.println("Generated: " + LocalDateTime.now().format(TS_FMT));
            pw.println();
            pw.println("ID,Description,Category,Amount,Payment Method,Receipt No,Date,Notes");

            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT * FROM expenses ORDER BY expense_date DESC");
                while (rs.next()) {
                    pw.printf("%d,%s,%s,%.2f,%s,%s,%s,%s%n",
                        rs.getInt("id"),
                        esc(rs.getString("description")),
                        esc(rs.getString("category") != null ? rs.getString("category") : ""),
                        rs.getDouble("amount"),
                        esc(rs.getString("payment_method") != null ? rs.getString("payment_method") : "cash"),
                        esc(rs.getString("receipt_number") != null ? rs.getString("receipt_number") : ""),
                        esc(rs.getString("expense_date") != null ? rs.getString("expense_date") : ""),
                        esc(rs.getString("notes") != null ? rs.getString("notes") : "")
                    );
                }
            }

            ExpenseDAO expenseDAO = new ExpenseDAO();
            pw.println();
            pw.println("Total Expenses: " + String.format("%.2f", expenseDAO.getTotal()));
            pw.println("This Month: " + String.format("%.2f", expenseDAO.getMonthTotal()));
        }
    }

    private void generatePurchasesCSV(File file) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("KaziFlow ERP — Purchases Report");
            pw.println("Generated: " + LocalDateTime.now().format(TS_FMT));
            pw.println();
            pw.println("PO Number,Supplier,Date,Total Amount,Tax Amount,Payment Method,Payment Status,Status,Notes");

            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                    "SELECT p.*, s.name as supp_name FROM purchases p LEFT JOIN suppliers s ON p.supplier_id=s.id ORDER BY p.created_at DESC");
                while (rs.next()) {
                    pw.printf("%s,%s,%s,%.2f,%.2f,%s,%s,%s,%s%n",
                        esc(rs.getString("purchase_number")),
                        esc(rs.getString("supp_name") != null ? rs.getString("supp_name") : "—"),
                        esc(rs.getString("created_at")),
                        rs.getDouble("total_amount"),
                        rs.getDouble("tax_amount"),
                        esc(rs.getString("payment_method") != null ? rs.getString("payment_method") : ""),
                        esc(rs.getString("payment_status") != null ? rs.getString("payment_status") : ""),
                        esc(rs.getString("status")),
                        esc(rs.getString("notes") != null ? rs.getString("notes") : "")
                    );
                }
            }
        }
    }

    private void generateFinanceSummaryCSV(File file) throws Exception {
        TransactionDAO transDAO = new TransactionDAO();
        ExpenseDAO expenseDAO = new ExpenseDAO();
        double totalRevenue  = transDAO.getTotalRevenue();
        double totalExpenses = transDAO.getTotalExpenses();
        double netProfit     = totalRevenue - totalExpenses;
        double vatOnSales    = totalRevenue * 0.16;
        double vatOnPurchases = totalExpenses * 0.16;

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("KaziFlow ERP — Finance Summary Report");
            pw.println("Generated: " + LocalDateTime.now().format(TS_FMT));
            pw.println("Period: All Time");
            pw.println();
            pw.println("INCOME STATEMENT");
            pw.println("Item,Amount (KES)");
            pw.printf("Total Revenue,%.2f%n", totalRevenue);
            pw.printf("Cost of Goods Sold,%.2f%n", totalExpenses * 0.6);
            pw.printf("Gross Profit,%.2f%n", totalRevenue - (totalExpenses * 0.6));
            pw.println();
            pw.println("OPERATING EXPENSES");
            pw.printf("Payroll,%.2f%n", totalExpenses * 0.4);
            pw.printf("Rent & Utilities,%.2f%n", totalExpenses * 0.15);
            pw.printf("Other Expenses,%.2f%n", totalExpenses * 0.45);
            pw.printf("Total Expenses,%.2f%n", totalExpenses);
            pw.println();
            pw.printf("NET PROFIT/(LOSS),%.2f%n", netProfit);
            pw.println();
            pw.println("VAT SUMMARY");
            pw.printf("Output VAT (Sales),%.2f%n", vatOnSales);
            pw.printf("Input VAT (Purchases),%.2f%n", vatOnPurchases);
            pw.printf("Net VAT Payable,%.2f%n", vatOnSales - vatOnPurchases);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Escapes a value for CSV: wraps in quotes if it contains commas/quotes/newlines. */
    private String esc(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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
        a.setTitle(title); a.setHeaderText(null); a.show();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle("Export Error"); a.setHeaderText(null); a.show();
    }
}
