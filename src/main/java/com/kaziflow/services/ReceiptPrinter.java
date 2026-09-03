package com.kaziflow.services;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.Sale;
import com.kaziflow.models.SaleItem;
import com.kaziflow.utils.KESFormatter;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates formatted receipts for completed sales.
 *
 * Supports:
 *  - On-screen preview (JavaFX dialog)
 *  - Save as .txt file
 *  - Print via system default printer (AWT Desktop)
 */
public class ReceiptPrinter {

    private static final int WIDTH = 48; // characters wide
    private Map<String, String> settings = new HashMap<>();

    public ReceiptPrinter() {
        loadSettings();
    }

    private void loadSettings() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT key, value FROM settings")) {
            while (rs.next()) settings.put(rs.getString("key"), rs.getString("value"));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ─── Receipt Generation ─────────────────────────────────────────────────

    public String generateReceipt(Sale sale) {
        StringBuilder sb = new StringBuilder();
        String businessName = settings.getOrDefault("business_name", "KaziFlow Business");
        String address      = settings.getOrDefault("business_address", "Nairobi, Kenya");
        String pin          = settings.getOrDefault("business_pin", "");
        String footer       = settings.getOrDefault("receipt_footer", "Thank you for your business!");
        String paybill      = settings.getOrDefault("mpesa_paybill", "");
        String till         = settings.getOrDefault("mpesa_till_number", "");
        // Routed through VATConfig (not parsed inline from the settings map) so this also
        // respects the VAT-enabled toggle, and so there's exactly one source of truth for the
        // rate across the whole app — previously this was the only place vat_rate was actually
        // read; every calculation site hardcoded 16% directly, which VATConfig now fixes too.
        double vatRate      = com.kaziflow.services.VATConfig.getInstance().getRatePercent();

        // ── Header ──
        sb.append(center("★ " + businessName + " ★")).append("\n");
        sb.append(center(address)).append("\n");
        if (!pin.isEmpty())     sb.append(center("KRA PIN: " + pin)).append("\n");
        if (!paybill.isEmpty()) sb.append(center("M-Pesa Paybill: " + paybill)).append("\n");
        if (!till.isEmpty())    sb.append(center("Till No: " + till)).append("\n");
        sb.append(divider('═')).append("\n");
        sb.append(center("** OFFICIAL RECEIPT **")).append("\n");
        sb.append(divider('─')).append("\n");

        // Receipt info
        if (sale.getSaleNumber() != null)
            sb.append(line("Receipt No:", sale.getSaleNumber())).append("\n");
        if (sale.getCreatedAt() != null)
            sb.append(line("Date:", sale.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")))).append("\n");
        sb.append(line("Cashier:", sale.getServedByName() != null ? sale.getServedByName() : "—")).append("\n");
        sb.append(line("Customer:", sale.getCustomerName() != null ? sale.getCustomerName() : "Walk-in")).append("\n");

        // ETIMS serial — fetch from DB if available
        String etimsSerial = "PENDING";
        if (sale.getId() > 0) {
            String stored = com.kaziflow.services.ETIMSService.getInstance().getSerial(sale.getId());
            if (stored != null) etimsSerial = stored;
        }
        sb.append(line("ETIMS Serial:", etimsSerial)).append("\n");

        sb.append(divider('─')).append("\n");

        // ── Items ──
        sb.append(padRight("ITEM", 26)).append(padLeft("QTY", 5)).append(padLeft("KES", 8)).append(padLeft("TOTAL", 9)).append("\n");
        sb.append(divider('─')).append("\n");

        if (sale.getItems() != null) {
            for (SaleItem item : sale.getItems()) {
                String name  = truncate(item.getProductName(), 24);
                String qty   = String.format("%.0f", item.getQuantity());
                String price = KESFormatter.formatNumber(item.getUnitPrice());
                String total = KESFormatter.formatNumber(item.getLineTotal());
                sb.append(padRight(name, 26)).append(padLeft(qty, 5)).append(padLeft(price, 8)).append(padLeft(total, 9)).append("\n");
            }
        }

        sb.append(divider('─')).append("\n");

        // ── Totals ──
        sb.append(line("Subtotal:", "KES " + KESFormatter.formatNumber(sale.getSubtotal()))).append("\n");
        if (sale.getDiscountAmount() > 0)
            sb.append(line("Discount:", "- KES " + KESFormatter.formatNumber(sale.getDiscountAmount()))).append("\n");
        sb.append(line("VAT (" + (int) vatRate + "%):", "KES " + KESFormatter.formatNumber(sale.getVatAmount()))).append("\n");
        sb.append(divider('═')).append("\n");
        sb.append(line("TOTAL DUE:", "KES " + KESFormatter.formatNumber(sale.getTotalAmount()))).append("\n");
        sb.append(divider('═')).append("\n");

        // ── Payment ──
        String payMethod = sale.getPaymentMethod() != null ? sale.getPaymentMethod().toUpperCase() : "CASH";
        sb.append(line("Payment Method:", payMethod)).append("\n");
        if (sale.getAmountPaid() > 0)
            sb.append(line("Amount Paid:", "KES " + KESFormatter.formatNumber(sale.getAmountPaid()))).append("\n");
        if (sale.getChangeAmount() > 0) {
            sb.append(divider('─')).append("\n");
            sb.append(line("*** CHANGE:", "KES " + KESFormatter.formatNumber(sale.getChangeAmount()) + " ***")).append("\n");
            sb.append(divider('─')).append("\n");
        }
        if (sale.getMpesaRef() != null && !sale.getMpesaRef().isEmpty())
            sb.append(line("M-Pesa Ref:", sale.getMpesaRef())).append("\n");

        // ── Footer ──
        sb.append(divider('═')).append("\n");
        sb.append(center(footer)).append("\n");
        if (!pin.isEmpty()) sb.append(center("VAT Reg No: " + pin)).append("\n");
        sb.append(center("Items: " + (sale.getItems() != null ? sale.getItems().size() : 0)
            + "   |   " + sale.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy")))).append("\n");
        sb.append(divider('─')).append("\n");
        sb.append(center("KaziFlow ERP — kaziflow.co.ke")).append("\n");
        sb.append(center("Offline-first ERP for Kenyan SMEs")).append("\n");

        return sb.toString();
    }

    // ─── Display Actions ────────────────────────────────────────────────────

    /** Show receipt in an on-screen preview dialog */
    public void showPreview(Sale sale) {
        String receipt = generateReceipt(sale);

        Alert dialog = new Alert(Alert.AlertType.NONE);
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle("Receipt - " + sale.getSaleNumber());
        dialog.setHeaderText(null);
        dialog.getButtonTypes().addAll(
            new ButtonType("🖨 Print"),
            new ButtonType("💾 Save"),
            new ButtonType("📱 WhatsApp"),
            new ButtonType("✉ SMS"),
            new ButtonType("📧 Email"),
            ButtonType.CLOSE
        );

        TextArea area = new TextArea(receipt);
        area.setEditable(false);
        area.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 12px;");
        area.setPrefWidth(420);
        area.setPrefHeight(560);

        VBox content = new VBox(area);
        VBox.setVgrow(area, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");

        dialog.showAndWait().ifPresent(btn -> {
            if (btn.getText().contains("Print")) print(sale);
            else if (btn.getText().contains("Save")) saveToFile(sale, receipt);
            else if (btn.getText().contains("WhatsApp")) sendWhatsAppReceipt(sale);
            else if (btn.getText().contains("SMS")) sendSmsReceipt(sale);
            else if (btn.getText().contains("Email")) sendEmailReceipt(sale);
        });
    }

    // ─── WhatsApp Receipt ───────────────────────────────────────────────────

    /**
     * Generates a compact, WhatsApp-friendly receipt (no monospace alignment,
     * uses emoji and short lines since WhatsApp doesn't render fixed-width fonts).
     */
    public String generateWhatsAppReceipt(Sale sale) {
        String businessName = settings.getOrDefault("business_name", "KaziFlow Business");
        String footer       = settings.getOrDefault("receipt_footer", "Thank you for your business!");

        StringBuilder sb = new StringBuilder();
        sb.append("*").append(businessName).append("*\n");
        sb.append("🧾 Receipt: ").append(sale.getSaleNumber() != null ? sale.getSaleNumber() : "—").append("\n");
        if (sale.getCreatedAt() != null) {
            sb.append("📅 ").append(sale.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))).append("\n");
        }
        sb.append("\n");

        if (sale.getItems() != null) {
            for (SaleItem item : sale.getItems()) {
                sb.append("• ").append(item.getProductName())
                  .append(" x").append((int) item.getQuantity())
                  .append(" — KES ").append(KESFormatter.formatNumber(item.getLineTotal()))
                  .append("\n");
            }
        }

        sb.append("\n");
        sb.append("Subtotal: KES ").append(KESFormatter.formatNumber(sale.getSubtotal())).append("\n");
        if (sale.getDiscountAmount() > 0)
            sb.append("Discount: -KES ").append(KESFormatter.formatNumber(sale.getDiscountAmount())).append("\n");
        sb.append("VAT: KES ").append(KESFormatter.formatNumber(sale.getVatAmount())).append("\n");
        sb.append("*TOTAL: KES ").append(KESFormatter.formatNumber(sale.getTotalAmount())).append("*\n");
        sb.append("\nPayment: ").append(sale.getPaymentMethod() != null ? sale.getPaymentMethod().toUpperCase() : "CASH").append("\n");

        sb.append("\n").append(footer);
        return sb.toString();
    }

    /**
     * Prompts the user for a customer phone number, then opens WhatsApp Web/Desktop
     * with a pre-filled receipt message via the wa.me deep link.
     * Phone numbers starting with 0 are converted to Kenya's 254 prefix.
     */
    public void sendWhatsAppReceipt(Sale sale) {
        javafx.scene.control.TextInputDialog phoneDialog = new javafx.scene.control.TextInputDialog();
        phoneDialog.setTitle("Send Receipt via WhatsApp");
        phoneDialog.setHeaderText("Enter customer's WhatsApp number");
        phoneDialog.setContentText("Phone (e.g. 0712345678):");
        phoneDialog.showAndWait().ifPresent(phoneInput -> {
            String phone = phoneInput.replaceAll("[^0-9]", "");
            if (phone.isEmpty()) return;
            if (phone.startsWith("0")) phone = "254" + phone.substring(1);
            else if (phone.startsWith("7") || phone.startsWith("1")) phone = "254" + phone;

            if (phone.length() < 11) {
                showError("Invalid phone number. Please include the full number, e.g. 0712345678.");
                return;
            }

            String message = generateWhatsAppReceipt(sale);
            String url = "https://wa.me/" + phone + "?text=" +
                java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
            try {
                Desktop.getDesktop().browse(new java.net.URI(url));
            } catch (Exception e) {
                e.printStackTrace();
                showError("Could not open WhatsApp. Please check your default browser settings.");
            }
        });
    }

    // ─── SMS Receipt ────────────────────────────────────────────────────────

    /**
     * Generates a short SMS-friendly receipt. SMS is billed per 160-character
     * segment, so this trims item lists for long receipts and keeps only the
     * essentials: business name, receipt number, total, and payment method.
     */
    public String generateSmsReceipt(Sale sale) {
        String businessName = settings.getOrDefault("business_name", "KaziFlow");

        StringBuilder sb = new StringBuilder();
        sb.append(businessName).append(": Receipt ")
          .append(sale.getSaleNumber() != null ? sale.getSaleNumber() : "—");

        if (sale.getItems() != null && !sale.getItems().isEmpty()) {
            int n = sale.getItems().size();
            sb.append(", ").append(n).append(n == 1 ? " item" : " items");
        }

        sb.append(". Total KES ").append(KESFormatter.formatNumber(sale.getTotalAmount()));
        sb.append(". Paid via ").append(sale.getPaymentMethod() != null ? sale.getPaymentMethod().toUpperCase() : "CASH");
        sb.append(". Thank you!");
        return sb.toString();
    }

    /**
     * Prompts for a customer phone number and sends the receipt via SMS
     * using AfricasTalkingService. Runs the network call on a background
     * thread via AsyncTask.
     */
    public void sendSmsReceipt(Sale sale) {
        var sms = com.kaziflow.services.AfricasTalkingService.getInstance();
        if (!sms.isConfigured()) {
            showError("SMS is not configured yet. Go to Settings → SMS (Africa's Talking) to add your API key.");
            return;
        }

        javafx.scene.control.TextInputDialog phoneDialog = new javafx.scene.control.TextInputDialog();
        phoneDialog.setTitle("Send Receipt via SMS");
        phoneDialog.setHeaderText("Enter customer's phone number");
        phoneDialog.setContentText("Phone (e.g. 0712345678):");
        phoneDialog.showAndWait().ifPresent(phoneInput -> {
            String message = generateSmsReceipt(sale);
            com.kaziflow.utils.AsyncTask.run(
                () -> sms.send(phoneInput.trim(), message),
                result -> {
                    if (result.startsWith("✅")) {
                        showInfo("SMS Sent", "Receipt sent to " + phoneInput.trim() + "\n" + result);
                    } else {
                        showError(result);
                    }
                },
                err -> showError("SMS error: " + err)
            );
        });
    }

    /**
     * Prompts for a customer email address and sends a plain-text receipt via
     * EmailService. Runs the SMTP call on a background thread via AsyncTask,
     * same convention as sendSmsReceipt.
     */
    public void sendEmailReceipt(Sale sale) {
        var email = com.kaziflow.services.EmailService.getInstance();
        if (!email.isConfigured()) {
            showError("Email is not configured yet. Go to Settings → Email Receipts to add your SMTP details.");
            return;
        }

        javafx.scene.control.TextInputDialog emailDialog = new javafx.scene.control.TextInputDialog();
        emailDialog.setTitle("Send Receipt via Email");
        emailDialog.setHeaderText("Enter customer's email address");
        emailDialog.setContentText("Email:");
        emailDialog.showAndWait().ifPresent(toEmail -> {
            String subject = "Receipt " + (sale.getSaleNumber() != null ? sale.getSaleNumber() : "");
            String body = generateReceipt(sale);
            com.kaziflow.utils.AsyncTask.run(
                () -> email.send(toEmail.trim(), subject, body),
                error -> {
                    if (error == null) showInfo("Email Sent", "Receipt sent to " + toEmail.trim());
                    else showError(error);
                },
                err -> showError("Email error: " + err)
            );
        });
    }

    /** Save receipt to a .txt file chosen by user */
    public void saveToFile(Sale sale, String receipt) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Receipt");
        chooser.setInitialFileName("Receipt_" + sale.getSaleNumber() + ".txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File file = chooser.showSaveDialog(null);
        if (file != null) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.print(receipt);
                showInfo("Saved", "Receipt saved to:\n" + file.getAbsolutePath());
            } catch (Exception e) {
                showError("Save failed: " + e.getMessage());
            }
        }
    }

    /** Print receipt via system default printer */
    public void print(Sale sale) {
        try {
            // Write to temp file then print
            File tempFile = File.createTempFile("receipt_", ".txt");
            tempFile.deleteOnExit();
            try (PrintWriter pw = new PrintWriter(new FileWriter(tempFile))) {
                pw.print(generateReceipt(sale));
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.PRINT)) {
                Desktop.getDesktop().print(tempFile);
            } else {
                // Fallback: open in default text editor
                Desktop.getDesktop().open(tempFile);
            }
        } catch (Exception e) {
            showError("Print failed: " + e.getMessage());
        }
    }

    // ─── Formatting Helpers ─────────────────────────────────────────────────

    private String center(String text) {
        if (text == null || text.isEmpty()) return "";
        int padding = Math.max(0, (WIDTH - text.length()) / 2);
        return " ".repeat(padding) + text;
    }

    private String divider(char ch) {
        return String.valueOf(ch).repeat(WIDTH);
    }

    private String line(String label, String value) {
        int space = WIDTH - label.length() - value.length();
        if (space < 1) space = 1;
        return label + " ".repeat(space) + value;
    }

    private String padRight(String text, int width) {
        if (text.length() >= width) return text.substring(0, width);
        return text + " ".repeat(width - text.length());
    }

    private String padLeft(String text, int width) {
        if (text.length() >= width) return text.substring(0, width);
        return " ".repeat(width - text.length()) + text;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 1) + "…" : text;
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        com.kaziflow.utils.DialogUtils.makeResizable(a);
        a.setTitle(title); a.setHeaderText(null); a.show();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        com.kaziflow.utils.DialogUtils.makeResizable(a);
        a.setTitle("Error"); a.setHeaderText(null); a.show();
    }
}
