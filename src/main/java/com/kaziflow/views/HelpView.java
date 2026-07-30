package com.kaziflow.views;

import com.kaziflow.utils.AsyncTask;
import com.kaziflow.utils.Toast;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.awt.Desktop;
import java.net.URI;

/**
 * Help & Support view — keyboard shortcuts, feature docs, system info, and support links.
 */
public class HelpView {

    private VBox root;

    public HelpView() {
        buildUI();
    }

    public VBox getRoot() { return root; }

    private void buildUI() {
        root = new VBox(0);
        root.setStyle("-fx-background-color: #f8fafc;");

        // Top bar
        HBox topbar = new HBox(12);
        topbar.setAlignment(Pos.CENTER_LEFT);
        topbar.setPadding(new Insets(0, 24, 0, 24));
        topbar.setPrefHeight(60);
        topbar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        Label pageTitle = new Label("Help & Support");
        pageTitle.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button feedbackBtn = new Button("📧 Send Feedback");
        feedbackBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-size: 12px; -fx-background-radius: 8; -fx-pref-height: 36px; -fx-padding: 0 16; -fx-cursor: hand;");
        feedbackBtn.setOnAction(e -> openEmail());

        topbar.getChildren().addAll(pageTitle, spacer, feedbackBtn);

        // Scrollable content
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));

        // ── Header ────────────────────────────────────────────────────
        VBox header = new VBox(6);
        Label title = new Label("How can we help?");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label subtitle = new Label("KaziFlow ERP v2.4.0 — Built for Kenyan SMEs. Find answers, shortcuts, and contact support below.");
        subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        header.getChildren().addAll(title, subtitle);

        // ── Quick Links ───────────────────────────────────────────────
        HBox quickLinks = new HBox(12);
        quickLinks.getChildren().addAll(
            quickLinkCard("📖", "User Guide",      "Step-by-step guides for every module", "https://docs.kaziflow.co.ke"),
            quickLinkCard("💬", "Community Forum", "Ask questions, share tips with others", "https://community.kaziflow.co.ke"),
            quickLinkCard("🐛", "Report a Bug",    "Found an issue? Let us know instantly",  "https://github.com/kaziflow/erp/issues"),
            quickLinkCard("📞", "Call Support",    "+254 700 KAZI (5294) — Mon–Fri 8am–6pm", null)
        );

        // ── Keyboard Shortcuts ────────────────────────────────────────
        VBox shortcutsCard = card("⌨  Keyboard Shortcuts");

        String[][] shortcuts = {
            {"Ctrl + N",   "New sale (opens POS)"},
            {"Ctrl + P",   "Add purchase order"},
            {"Ctrl + I",   "Add inventory item"},
            {"Ctrl + E",   "Add employee"},
            {"Ctrl + F",   "Finance module"},
            {"Ctrl + R",   "Export reports"},
            {"Ctrl + B",   "Manual backup"},
            {"Ctrl + A",   "Toggle AI Assistant panel"},
            {"Ctrl + K",   "Open calculator"},
            {"F5",         "Refresh current view"},
            {"F11",        "Toggle fullscreen"},
            {"Ctrl + Q",   "Quit application"},
            {"Escape",     "Close dialog / Cancel"},
            {"Tab / Enter","Navigate form fields"},
        };

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(8);
        grid.setPadding(new Insets(8, 0, 0, 0));

        for (int i = 0; i < shortcuts.length; i++) {
            Label key = new Label(shortcuts[i][0]);
            key.setStyle(
                "-fx-font-family: monospace; -fx-font-size: 12px; -fx-text-fill: #1e293b;" +
                "-fx-background-color: #f1f5f9; -fx-border-color: #cbd5e1; -fx-border-radius: 4;" +
                "-fx-background-radius: 4; -fx-padding: 2 8;"
            );
            Label desc = new Label(shortcuts[i][1]);
            desc.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569;");
            grid.add(key, (i < 6 ? 0 : 2), (i < 6 ? i : i - 6));
            grid.add(desc, (i < 6 ? 1 : 3), (i < 6 ? i : i - 6));
        }

        shortcutsCard.getChildren().add(grid);

        // ── Module Overview ───────────────────────────────────────────
        VBox modulesCard = card("📦  Module Guide");
        VBox moduleList = new VBox(0);

        String[][] modules = {
            {"🏪", "Point of Sale",       "Process sales, M-Pesa, cash & card. Receipts printed automatically. Shift totals viewable in Sales History tab."},
            {"📦", "Inventory",           "Add/edit products with SKU, category, supplier, price and stock levels. Low-stock alerts shown on dashboard."},
            {"🚚", "Purchases",           "Manage suppliers and purchase orders. Line-item PO builder auto-fills cost prices and updates stock on save."},
            {"👥", "Employees & HR",      "Full HR suite — employee records, daily attendance check-in, leave requests with approve/reject workflow."},
            {"💰", "Finance",             "Chart of accounts, expense tracking with categories, VAT summary (16%) with monthly KRA filing breakdown."},
            {"📊", "Reports",             "CSV export for Sales, Inventory, Expenses, Purchases, Employees, and Finance Summary. Quick one-click export."},
            {"⚙️", "Settings",           "Business details, M-Pesa Daraja API config, module enable/disable, user management, and data backup."},
        };

        for (String[] m : modules) {
            HBox row = new HBox(14);
            row.setAlignment(Pos.TOP_LEFT);
            row.setPadding(new Insets(12, 0, 12, 0));
            row.setStyle("-fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");

            Label icon = new Label(m[0]);
            icon.setStyle("-fx-font-size: 20px;");
            icon.setMinWidth(32);

            VBox info = new VBox(3);
            Label name = new Label(m[1]);
            name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
            Label desc = new Label(m[2]);
            desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
            desc.setWrapText(true);
            info.getChildren().addAll(name, desc);

            row.getChildren().addAll(icon, info);
            moduleList.getChildren().add(row);
        }
        modulesCard.getChildren().add(moduleList);

        // ── FAQ ───────────────────────────────────────────────────────
        VBox faqCard = card("❓  Frequently Asked Questions");
        VBox faqList = new VBox(8);

        String[][] faqs = {
            {"How do I configure M-Pesa payments?",
             "Go to Settings → M-Pesa Integration. Enter your Safaricom Daraja API Consumer Key, Consumer Secret, and your Paybill or Till number. Toggle from Sandbox to Production when ready to go live."},
            {"How do I reset a user's password?",
             "Go to Settings → Users & Access. Find the user, click the lock icon, and enter a new password. Passwords are hashed with BCrypt — they are never stored in plain text."},
            {"Where is my data stored?",
             "All data is stored locally in an SQLite database at ~/KaziFlowERP/kaziflow.db. No data leaves your computer unless you explicitly use the M-Pesa API. Go to Settings → Data Management to back up."},
            {"How do I process a refund?",
             "Currently, record the refund as a manual expense in Finance → Expenses with category 'Refund', and manually adjust stock in Inventory if goods were returned."},
            {"Can I use KaziFlow offline?",
             "Yes — KaziFlow is fully offline-first. All core features (POS, inventory, HR, finance) work without internet. M-Pesa STK push requires an internet connection."},
            {"How do I add a new product category?",
             "In Inventory, click the category dropdown on the Add Product form. New categories can be added via the CategoryDAO directly, or contact support to add via Settings in a future update."},
        };

        for (String[] faq : faqs) {
            TitledPane tp = new TitledPane(faq[0], null);
            Label ans = new Label(faq[1]);
            ans.setWrapText(true);
            ans.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569; -fx-padding: 8 4;");
            tp.setContent(ans);
            tp.setExpanded(false);
            tp.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
            faqList.getChildren().add(tp);
        }
        faqCard.getChildren().add(faqList);

        // ── System Info ───────────────────────────────────────────────
        VBox sysCard = card("🖥  System Information");
        GridPane sysGrid = new GridPane();
        sysGrid.setHgap(24);
        sysGrid.setVgap(10);
        sysGrid.setPadding(new Insets(8, 0, 0, 0));

        String[][] sysInfo = {
            {"Application",     "KaziFlow ERP v2.4.0"},
            {"Java Version",    System.getProperty("java.version")},
            {"JavaFX Version",  System.getProperty("javafx.version", "21")},
            {"OS",              System.getProperty("os.name") + " " + System.getProperty("os.arch")},
            {"Database",        "SQLite 3.44 (Offline)"},
            {"Data Directory",  System.getProperty("user.home") + "/KaziFlowERP/"},
            {"Timezone",        "Africa/Nairobi (EAT, UTC+3)"},
            {"Currency",        "Kenyan Shilling (KES)"},
            {"VAT Rate",        "16% (Standard)"},
            {"Developer",       "KaziFlow Technologies Ltd"},
            {"Support Email",   "support@kaziflow.co.ke"},
            {"License",         "Commercial — Proprietary"},
        };

        for (int i = 0; i < sysInfo.length; i++) {
            Label key = new Label(sysInfo[i][0]);
            key.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #64748b; -fx-min-width: 160;");
            Label val = new Label(sysInfo[i][1]);
            val.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
            sysGrid.add(key, (i < 6 ? 0 : 2), (i < 6 ? i : i - 6));
            sysGrid.add(val, (i < 6 ? 1 : 3), (i < 6 ? i : i - 6));
        }

        Button copyInfoBtn = new Button("📋 Copy System Info");
        copyInfoBtn.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 12px; -fx-padding: 6 14; -fx-cursor: hand;");
        copyInfoBtn.setOnAction(e -> {
            StringBuilder sb = new StringBuilder("KaziFlow ERP System Info\n");
            for (String[] row : sysInfo) sb.append(row[0]).append(": ").append(row[1]).append("\n");
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(
                new javafx.scene.input.ClipboardContent() {{ putString(sb.toString()); }}
            );
            Toast.info(com.kaziflow.utils.SceneManager.getInstance().getStage(), "System info copied to clipboard");
        });

        sysCard.getChildren().addAll(sysGrid, copyInfoBtn);

        content.getChildren().addAll(header, quickLinks, shortcutsCard, modulesCard, faqCard, sysCard);
        scroll.setContent(content);
        root.getChildren().addAll(topbar, scroll);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private VBox card(String title) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-background-radius: 12; -fx-border-width: 1;");

        Label hdr = new Label(title);
        hdr.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #f1f5f9;");

        card.getChildren().addAll(hdr, sep);
        return card;
    }

    private VBox quickLinkCard(String icon, String title, String desc, String url) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(16));
        card.setStyle(
            "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 10;" +
            "-fx-background-radius: 10; -fx-border-width: 1; -fx-cursor: hand;"
        );
        HBox.setHgrow(card, Priority.ALWAYS);

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 22px;");

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");

        Label descLbl = new Label(desc);
        descLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");
        descLbl.setWrapText(true);

        card.getChildren().addAll(iconLbl, titleLbl, descLbl);

        // Hover effect
        card.setOnMouseEntered(e -> card.setStyle(
            "-fx-background-color: #f8fafc; -fx-border-color: #2563eb; -fx-border-radius: 10;" +
            "-fx-background-radius: 10; -fx-border-width: 1; -fx-cursor: hand;"
        ));
        card.setOnMouseExited(e -> card.setStyle(
            "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 10;" +
            "-fx-background-radius: 10; -fx-border-width: 1; -fx-cursor: hand;"
        ));

        if (url != null) {
            card.setOnMouseClicked(e -> openUrl(url));
        } else {
            // Phone card — copy number
            card.setOnMouseClicked(e -> {
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(
                    new javafx.scene.input.ClipboardContent() {{ putString("+254 700 52946"); }}
                );
                Toast.info(com.kaziflow.utils.SceneManager.getInstance().getStage(), "Phone number copied!");
            });
        }

        return card;
    }

    private void openUrl(String url) {
        AsyncTask.run(() -> {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception e) {
                System.err.println("Cannot open URL: " + e.getMessage());
            }
            return null;
        }, ignored -> {}, err ->
            Toast.warning(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                "Could not open browser. Please visit: " + url)
        );
    }

    private void openEmail() {
        openUrl("mailto:support@kaziflow.co.ke?subject=KaziFlow%20ERP%20Feedback");
    }
}
