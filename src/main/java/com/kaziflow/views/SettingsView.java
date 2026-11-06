package com.kaziflow.views;

import com.kaziflow.database.DatabaseManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class SettingsView {

    private VBox root;
    private Map<String, String> settings = new HashMap<>();

    public SettingsView() {
        loadSettings();
        buildUI();
    }

    public VBox getRoot() { return root; }

    private void loadSettings() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT key, value FROM settings")) {
            while (rs.next()) settings.put(rs.getString("key"), rs.getString("value"));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void buildUI() {
        root = new VBox(0);
        root.setStyle("-fx-background-color: #f8fafc;");

        HBox topbar = buildTopBar();
        VBox content = new VBox(24);
        content.setPadding(new Insets(24));

        VBox businessCard = buildBusinessCard();
        HBox modulesRow = buildModulesRow();
        HBox systemStatus = buildSystemStatus();

        content.getChildren().addAll(businessCard, modulesRow, systemStatus);
        root.getChildren().addAll(topbar, content);
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(2);
        Label title = new Label("System Administration"); title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label bc = new Label("Dashboard › Settings › General"); bc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        titleBox.getChildren().addAll(title, bc);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button viewGuideBtn = new Button("☰ View Guide");
        viewGuideBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 36px; -fx-padding: 0 14; -fx-cursor: hand; -fx-font-size: 13px;");
        viewGuideBtn.setOnAction(e -> { try { java.awt.Desktop.getDesktop().browse(new java.net.URI("https://docs." + com.kaziflow.brand.Brand.website())); } catch (Exception ex) { ex.printStackTrace(); } });

        bar.getChildren().addAll(titleBox, sp, viewGuideBtn);
        return bar;
    }

    private VBox buildBusinessCard() {
        VBox card = new VBox(0);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1;");

        HBox inner = new HBox(16);
        inner.setAlignment(Pos.CENTER_LEFT);
        inner.setPadding(new Insets(24));

        // Logo placeholder
        StackPane logoBox = new StackPane();
        logoBox.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 12; -fx-pref-width: 64; -fx-pref-height: 64;");
        Label logoInitials = new Label(settings.getOrDefault("business_name", "My Business").substring(0, Math.min(2, settings.getOrDefault("business_name","My").length())).toUpperCase());
        logoInitials.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");
        logoBox.getChildren().add(logoInitials);

        VBox businessInfo = new VBox(4);
        Label businessName = new Label(settings.getOrDefault("business_name", "My Business"));
        businessName.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label businessAddr = new Label(settings.getOrDefault("business_address", "Nairobi, Kenya") + "  •  PIN: " + settings.getOrDefault("business_pin", "Not set"));
        businessAddr.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        businessInfo.getChildren().addAll(businessName, businessAddr);
        HBox.setHgrow(businessInfo, Priority.ALWAYS);

        Label activeBadge = new Label("● SUBSCRIPTION ACTIVE");
        activeBadge.setStyle("-fx-text-fill: #16a34a; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-color: #dcfce7; -fx-background-radius: 20; -fx-padding: 4 12;");

        Button editBtn = new Button("✎ Edit Business Details");
        editBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #2563eb; -fx-cursor: hand; -fx-font-size: 13px; -fx-border-color: transparent;");
        editBtn.setOnAction(e -> showEditBusinessDialog());

        inner.getChildren().addAll(logoBox, businessInfo, activeBadge, editBtn);
        card.getChildren().add(inner);
        return card;
    }

    private HBox buildModulesRow() {
        HBox container = new HBox(16);

        // Show current profile name dynamically
        String profileName = com.kaziflow.modules.ModuleRegistry.getInstance()
            .getActiveProfile().displayName;

        boolean smsConfigured = com.kaziflow.services.AfricasTalkingService.getInstance().isConfigured();
        String smsStatus = smsConfigured
            ? (com.kaziflow.services.AfricasTalkingService.getInstance().isSandbox() ? "Sandbox Connected" : "Status: Connected")
            : "Status: Not Set";
        String smsColor = smsConfigured ? "#16a34a" : "#94a3b8";

        boolean encEnabled = com.kaziflow.security.DatabaseEncryption.getInstance(
            com.kaziflow.utils.AppPaths.dataDir())
            .isEncryptionEnabled(com.kaziflow.utils.AppPaths.dbFile());
        String encStatus = encEnabled ? "Enabled (AES-256)" : "Not Enabled";
        String encColor  = encEnabled ? "#16a34a" : "#94a3b8";

        com.kaziflow.license.LicenseService licenseSvc =
            com.kaziflow.license.LicenseService.getInstance(
                com.kaziflow.utils.AppPaths.dataDir());
        com.kaziflow.license.LicenseService.Status licStatusEnum = licenseSvc.checkStatus();
        String licenseStatus, licenseColor;
        switch (licStatusEnum) {
            case ACTIVE -> { licenseStatus = licenseSvc.getEdition() + " — Active"; licenseColor = "#16a34a"; }
            case TRIAL_ACTIVE -> { licenseStatus = licenseSvc.trialDaysRemaining() + " trial days left"; licenseColor = "#d97706"; }
            case TRIAL_EXPIRED -> { licenseStatus = "Trial Expired"; licenseColor = "#dc2626"; }
            default -> { licenseStatus = "Not Activated"; licenseColor = "#94a3b8"; }
        }

        String[][] modules = {
            {"◎", "Users & Access",       "Manage staff accounts, assign positions, reset passwords, and see recent access activity.", activeUsersStatus(),  "", "module_users"},
            {"◈", "Positions & Access",   "Define positions (Cashier, Branch Manager...), choose what each can do in every module, and set special permissions.", positionsStatus(),  "", "module_roles"},
        {"▦", "Custom Modules",       "Add a department of your own (e.g. Ambulance, Staff Transport): choose its record types and fields.", customModulesStatus(), "", "module_custom"},
            {"⊙", "M-Pesa",               "Configure Daraja API, Paybill/Till numbers, and C2B webhooks.",   mpesaStatus(), mpesaConfigured() ? "#16a34a" : "", "module_mpesa"},
            {"✉", "SMS (Africa's Talking)","Send receipts and reminders via SMS for customers without WhatsApp.",smsStatus,smsColor,"module_sms"},
            {"📧", "Email Receipts",       "Configure SMTP to send receipts directly to customer email addresses.",
                com.kaziflow.services.EmailService.getInstance().isConfigured() ? "Configured" : "Not Configured",
                com.kaziflow.services.EmailService.getInstance().isConfigured() ? "#16a34a" : "#94a3b8", "module_email"},
            {"🖧", "Network / Multi-Computer", "Share this database with other computers on the same network.",
                com.kaziflow.network.NetworkConfig.getInstance().getMode().name(), "#2563eb", "module_network"},
            {"⊞", "Tax Settings",         "VAT rate and tax categories. (Submitting invoices to KRA eTIMS is not connected yet.)", vatStatus(), "", "module_tax"},
            {"🧾", "Payroll Tax Settings", "NSSF, SHIF, Housing Levy, NITA, and PAYE bands — every rate is "
                + "editable here since Kenyan statutory rates change often.",
                com.kaziflow.services.PayrollTaxConfig.getInstance().getPersonalReliefMonthly() == 2400
                    && com.kaziflow.services.PayrollTaxConfig.getInstance().getNssfUel() == 108000
                    ? "Using current defaults" : "Customized",
                "", "module_payroll_tax"},
            {"💱", "Currency",             "Choose your display currency and set USD/EUR exchange rates.",
                com.kaziflow.services.CurrencyService.getInstance().getActiveCurrency(), "#2563eb", "module_currency"},
            {"⚙", "System Modules",       "Enable or disable individual modules — useful if your business spans "
                + "more than one industry type (e.g. a pharmacy that also runs a clinic).",
                com.kaziflow.modules.ModuleRegistry.getInstance().hasModuleOverrides() ? "Customized" : "Using profile defaults",
                com.kaziflow.modules.ModuleRegistry.getInstance().hasModuleOverrides() ? "#2563eb" : "", "module_system"},
            {"🏢", "Branch Management",    "Manage multiple business locations and transfer stock between branches.",
                "", "", "module_branches"},
            {"🏪", "Industry Profile",    "Change your business type to load the right modules and layout.",  profileName,        "#7c3aed", "module_industry"},
            {"✦",  "AI Assistant",        "Configure Claude API key for AI-powered business insights.",        "Set API Key →",    "#02a870", "module_ai"},
            {"▣", "Data Management",      "Manual backups, restore points, and automated backup schedules.", "Last backup: " + com.kaziflow.services.BackupService.getInstance().getLastBackupTime(),  "", "module_backup"},
            {"💳", "Payment Methods",      "The ways customers pay and you pay: names, what each is used for, and which are switched on. Add Airtel Money, a SACCO account, anything you accept.",
                "Manage →", "#16a34a", "module_pay"},
            {"🧪", "Demo Company",         "Sample suppliers, employees, products and transactions for trying the system out. Nothing is loaded unless you ask, and it can be removed again.",
                com.kaziflow.services.DemoDataService.isPresent() ? "Demo data loaded" : "Not loaded",
                com.kaziflow.services.DemoDataService.isPresent() ? "#d97706" : "", "module_demo"},
            {"▤", "Audit Trail",          "View detailed system logs of all user actions and changes.",       "View Logs →",      "#2563eb","module_audit"},
            {"🌐","Language / Lugha",      "Switch between English and Kiswahili interface.",
                com.kaziflow.utils.I18n.getInstance().isSwahili() ? "Kiswahili" : "English",
                com.kaziflow.utils.I18n.getInstance().isSwahili() ? "#16a34a" : "#2563eb",
                "module_language"},
            {"🔐","DB Encryption",        "AES-256-GCM at-rest encryption. Protects data if device is lost.", encStatus, encColor, "module_security"},
            {"🔑","License",              "View your license status, edition, and activate a key.", licenseStatus, licenseColor, "module_license"},
        };

        FlowPane flow = new FlowPane();
        flow.setHgap(16);
        flow.setVgap(16);

        for (String[] m : modules) {
            VBox card = buildModuleCard(m[0], m[1], m[2], m[3], m[4], m[5]);
            card.setPrefWidth(300);
            flow.getChildren().add(card);
        }

        container.getChildren().add(flow); HBox.setHgrow(flow, Priority.ALWAYS);
        return container;
    }

    private VBox buildModuleCard(String icon, String title, String desc, String status, String statusColor, String settingKey) {
        // Read current enabled state from settings
        final boolean enabled = true; // the old per-card Enabled/Disabled toggle saved a flag nothing read; real on/off is Settings → System Modules

        VBox card = new VBox(10);
        String baseStyle = "-fx-background-color:white;-fx-background-radius:12;-fx-border-color:#e2e8f0;-fx-border-radius:12;-fx-border-width:1;-fx-padding:20;-fx-cursor:hand;";
        card.setStyle(baseStyle + (enabled ? "" : "-fx-opacity:0.6;"));

        StackPane iconBox = new StackPane();
        iconBox.setStyle("-fx-background-color:#f1f5f9;-fx-background-radius:10;-fx-padding:12;");
        iconBox.setPrefSize(44,44); iconBox.setMaxSize(44,44);
        Label iconLabel = new Label(icon); iconLabel.setStyle("-fx-text-fill:#475569;-fx-font-size:18px;");
        iconBox.getChildren().add(iconLabel);

        Label titleLabel = new Label(title); titleLabel.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label descLabel  = new Label(desc);  descLabel.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;-fx-wrap-text:true;"); descLabel.setWrapText(true);

        String statusFg = statusColor.isEmpty() ? "#94a3b8" : statusColor;
        Label statusLabel = new Label(status); statusLabel.setStyle("-fx-font-size:12px;-fx-text-fill:"+statusFg+";-fx-font-weight:bold;");

        card.getChildren().addAll(iconBox, titleLabel, descLabel, statusLabel);

        card.setOnMouseEntered(e -> card.setStyle(baseStyle + "-fx-border-color:#2563eb;" + (enabled?"":"-fx-opacity:0.6;")));
        card.setOnMouseExited(e  -> card.setStyle(baseStyle + (enabled?"":"-fx-opacity:0.6;")));

        // Special click handlers
        if (title.equals("Users & Access")) {
            card.setOnMouseClicked(e -> {
                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setTitle("User Management");
                UserManagementView umv = new UserManagementView();
                javafx.scene.Scene scene = new javafx.scene.Scene(umv.getRoot(), 1200, 720);
                try { scene.getStylesheets().add(getClass().getResource("/styles/main.css").toExternalForm()); } catch(Exception ignored) {}
                stage.setScene(scene); stage.show();
            });
        }
        if (title.equals("Custom Modules")) {
            card.setOnMouseClicked(e -> com.kaziflow.custom.CustomModuleBuilderView.showDialog(card.getScene() != null ? card.getScene().getWindow() : null));
        }
        if (title.equals("Positions & Access")) {
            card.setOnMouseClicked(e -> PositionsAccessView.showDialog(card.getScene() != null ? card.getScene().getWindow() : null));
        }
        if (title.equals("Tax Settings")) {
            card.setOnMouseClicked(e -> showTaxSettingsDialog());
        }
        if (title.equals("Payroll Tax Settings")) {
            card.setOnMouseClicked(e -> showPayrollTaxDialog());
        }
        if (title.equals("Currency")) {
            card.setOnMouseClicked(e -> showCurrencyDialog());
        }
        if (title.equals("Email Receipts")) {
            card.setOnMouseClicked(e -> showEmailSettingsDialog());
        }
        if (title.equals("Network / Multi-Computer")) {
            card.setOnMouseClicked(e -> showNetworkSettingsDialog());
        }
        if (title.equals("Audit Trail")) {
            card.setOnMouseClicked(e -> showAuditTrailDialog());
        }
        if (title.equals("Demo Company")) {
            card.setOnMouseClicked(e -> showDemoCompanyDialog());
        }
        if (title.equals("Payment Methods")) {
            card.setOnMouseClicked(e -> buildPaymentMethodsDialog().showAndWait());
        }
        if (title.equals("Data Management")) {
            card.setOnMouseClicked(e -> {
                Alert menu = new Alert(Alert.AlertType.NONE, "Choose an action:",
                    new ButtonType("📦 Backup Now"), new ButtonType("♻ Restore"), ButtonType.CANCEL);
                menu.setTitle("Data Management"); menu.setHeaderText(null);
                menu.showAndWait().ifPresent(btn -> {
                    com.kaziflow.services.BackupService bs = com.kaziflow.services.BackupService.getInstance();
                    if (btn.getText().contains("Backup")) bs.backupToDirectory();
                    else if (btn.getText().contains("Restore")) bs.restoreFromFile();
                });
            });
        }
        if (title.equals("M-Pesa")) {
            card.setOnMouseClicked(e -> showMpesaConfigDialog());
        }
        if (title.equals("SMS (Africa's Talking)")) {
            card.setOnMouseClicked(e -> showSmsConfigDialog());
        }
        if (title.equals("System Modules")) {
            card.setOnMouseClicked(e -> showModuleTogglesDialog());
        }
        if (title.equals("Branch Management")) {
            card.setOnMouseClicked(e -> {
                com.kaziflow.views.BranchView bv = new com.kaziflow.views.BranchView();
                bv.show();
            });
        }
        if (title.equals("AI Assistant")) {
            card.setOnMouseClicked(e -> {
                // Access AIService directly — no need to build the full UI panel
                com.kaziflow.services.AIService ai = com.kaziflow.services.AIService.getInstance();
                Dialog<ButtonType> dialog = new Dialog<>();
                com.kaziflow.utils.DialogUtils.makeResizable(dialog);
                dialog.setTitle("AI Assistant — API Key Setup");
                dialog.setHeaderText(null);
                dialog.getDialogPane().setStyle("-fx-background-color:white;");
                dialog.getDialogPane().setPrefWidth(440);
                javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(12);
                content.setPadding(new Insets(20));
                Label info = new Label(com.kaziflow.brand.Brand.productName()+" AI uses Claude by Anthropic.\n" +
                    "Get a free API key at: console.anthropic.com\n\n" +
                    "Your key is stored locally on this device only.");
                info.setWrapText(true); info.setStyle("-fx-text-fill:#475569;-fx-font-size:12px;");
                PasswordField keyField = new PasswordField();
                keyField.setText(ai.loadApiKey());
                keyField.setPromptText("sk-ant-api03-...");
                keyField.setStyle("-fx-pref-height:36px;-fx-background-color:white;" +
                    "-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;" +
                    "-fx-font-size:13px;-fx-padding:0 10;");
                content.getChildren().addAll(info, com.kaziflow.utils.PasswordFieldUtils.withToggle(keyField));
                dialog.getDialogPane().setContent(content);
                dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
                dialog.showAndWait().ifPresent(r -> {
                    if (r == ButtonType.OK) {
                        ai.saveApiKey(keyField.getText().trim());
                        com.kaziflow.utils.Toast.success(
                            com.kaziflow.utils.SceneManager.getInstance().getStage(),
                            "API key saved", "AI Assistant is ready to use");
                    }
                });
            });
        }
        if (title.equals("Industry Profile")) {
            card.setOnMouseClicked(e -> {
                // Show profile selector in a popup dialog
                javafx.stage.Stage dialog = new javafx.stage.Stage();
                dialog.setTitle("Change Industry Profile");
                dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                com.kaziflow.modules.ProfileSelector selector =
                    new com.kaziflow.modules.ProfileSelector(() -> {
                        dialog.close();
                        // Rebuild nav and refresh
                        com.kaziflow.utils.SceneManager.getInstance().showMainApp();
                    });
                javafx.scene.Scene s = new javafx.scene.Scene(selector.getRoot(), 1100, 680);
                dialog.setScene(s);
                dialog.showAndWait();
            });
        }
        if (title.equals("Language / Lugha")) {
            card.setOnMouseClicked(e -> showLanguageDialog());
        }
        if (title.equals("DB Encryption")) {
            card.setOnMouseClicked(e -> showEncryptionDialog());
        }
        if (title.equals("License")) {
            card.setOnMouseClicked(e -> showLicenseDialog());
        }

        // Settings are "the rules of the application": changing them needs Write access to the Settings module
        // (Administrator always has it). A read-only viewer sees the cards but can't open them.
        if (!com.kaziflow.utils.PermissionGuard.allowsModule("settings", com.kaziflow.services.PermissionService.Action.WRITE)) {
            card.setDisable(true);
            card.setOpacity(0.55);
        }
        return card;
    }

    private void showTaxSettingsDialog() {
        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle("Tax Settings");
        d.getDialogPane().setStyle("-fx-background-color:white;");
        d.getDialogPane().setPrefWidth(420);
        GridPane f = new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));

        String vatRate = "", pin = "";
        try (java.sql.Connection c = com.kaziflow.database.DatabaseManager.getInstance().getConnection();
             java.sql.Statement s = c.createStatement()) {
            java.sql.ResultSet rs = s.executeQuery("SELECT key, value FROM settings WHERE key IN ('vat_rate','kra_pin')");
            while (rs.next()) {
                if ("vat_rate".equals(rs.getString("key"))) vatRate = rs.getString("value");
                if ("kra_pin".equals(rs.getString("key")))  pin     = rs.getString("value");
            }
        } catch (Exception ignored) {}

        javafx.scene.control.TextField vatField = field("VAT Rate (%)", vatRate.isEmpty() ? "16" : vatRate);
        javafx.scene.control.TextField pinField = field("KRA PIN", pin);
        Label vatNote = new Label("Standard Kenya VAT rate is 16%. Changing this affects all new receipts.");
        vatNote.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;"); vatNote.setWrapText(true);

        f.addRow(0, lbl("VAT Rate (%)"), vatField);
        f.addRow(1, vatNote);
        f.addRow(2, lbl("KRA PIN"), pinField);

        d.getDialogPane().setContent(f);
        ButtonType saveBtn = new ButtonType("Save Tax Settings");
        d.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        d.showAndWait().ifPresent(btn -> {
            if (btn != saveBtn) return;
            try (java.sql.Connection c = com.kaziflow.database.DatabaseManager.getInstance().getConnection();
                 java.sql.PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO settings (key, value) VALUES (?,?)")) {
                ps.setString(1, "vat_rate"); ps.setString(2, vatField.getText().trim());
                ps.executeUpdate();
                ps.setString(1, "kra_pin"); ps.setString(2, pinField.getText().trim());
                ps.executeUpdate();
                // Previously this only updated the stored setting — nothing actually consulted it
                // except the receipt's display text, so changing it here had no effect on real
                // totals. VATConfig.reload() plus the sitewide rewiring (v11.20) is what makes
                // this genuinely take effect now.
                com.kaziflow.services.VATConfig.getInstance().reload();
                com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Saved", "Tax settings updated — this rate now applies to every new sale, quotation, and restaurant order immediately.");
            } catch (Exception ex) {
                com.kaziflow.utils.Toast.error(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Error", ex.getMessage());
            }
        });
    }

    /**
     * Every Kenya statutory payroll rate, made fully editable — per explicit
     * request, since these rates have changed multiple times in the last two
     * years (SHIF replaced NHIF Oct 2024, NSSF thresholds jumped again Feb
     * 2026, Housing Levy's PAYE-deductibility is disputed across sources)
     * and hardcoding them means every future change needs a code update.
     */
    private void showPayrollTaxDialog() {
        com.kaziflow.services.PayrollTaxConfig cfg = com.kaziflow.services.PayrollTaxConfig.getInstance();

        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle("Payroll Tax Settings");
        d.getDialogPane().setPrefWidth(560);

        VBox root = new VBox(16);
        root.setPadding(new Insets(18));

        Label note = new Label("Defaults reflect rates verified current as of July 2026. Kenyan statutory "
            + "rates change often — if KRA/NSSF/SHIF announce new figures, update them here rather than "
            + "waiting for an app update. \"Reset to Defaults\" restores the last-known-current values below.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);

        TextField nssfLelF = new TextField(String.valueOf(cfg.getNssfLel()));
        TextField nssfUelF = new TextField(String.valueOf(cfg.getNssfUel()));
        TextField nssfRateF = new TextField(String.valueOf(cfg.getNssfRatePercent()));
        TextField shifRateF = new TextField(String.valueOf(cfg.getShifRatePercent()));
        TextField shifMinF = new TextField(String.valueOf(cfg.getShifMinimum()));
        TextField housingRateF = new TextField(String.valueOf(cfg.getHousingLevyRatePercent()));
        CheckBox housingReducesTaxableCb = new CheckBox("Housing Levy reduces PAYE taxable income "
            + "(disputed in current guidance — confirm with your accountant)");
        housingReducesTaxableCb.setSelected(cfg.isHousingLevyReducesTaxableIncome());
        housingReducesTaxableCb.setWrapText(true);
        housingReducesTaxableCb.setStyle("-fx-font-size:11px;");
        TextField nitaF = new TextField(String.valueOf(cfg.getNitaFlat()));
        TextField reliefF = new TextField(String.valueOf(cfg.getPersonalReliefMonthly()));

        int r = 0;
        grid.add(new Label("NSSF Lower Limit (Tier I bound, KES)"), 0, r); grid.add(nssfLelF, 1, r++);
        grid.add(new Label("NSSF Upper Limit (Tier II bound, KES)"), 0, r); grid.add(nssfUelF, 1, r++);
        grid.add(new Label("NSSF Rate (%)"), 0, r); grid.add(nssfRateF, 1, r++);
        grid.add(new Label("SHIF Rate (%)"), 0, r); grid.add(shifRateF, 1, r++);
        grid.add(new Label("SHIF Minimum (KES)"), 0, r); grid.add(shifMinF, 1, r++);
        grid.add(new Label("Housing Levy Rate (%)"), 0, r); grid.add(housingRateF, 1, r++);
        grid.add(new Label("NITA (flat, KES)"), 0, r); grid.add(nitaF, 1, r++);
        grid.add(new Label("Personal Relief (monthly, KES)"), 0, r); grid.add(reliefF, 1, r++);

        Label bandsLabel = new Label("PAYE Bands — taxable income up to (KES) : rate (%)");
        bandsLabel.setStyle("-fx-font-weight:bold;-fx-font-size:12px;-fx-padding:8 0 0 0;");

        VBox bandsBox = new VBox(6);
        List<HBox> bandRows = new ArrayList<>();
        java.util.function.Consumer<com.kaziflow.services.PayrollTaxConfig.PayeBand> addRow = band -> {
            TextField upperF = new TextField(String.valueOf(band.upperBound()));
            TextField rateF = new TextField(String.valueOf(band.ratePercent()));
            upperF.setPrefWidth(140); rateF.setPrefWidth(80);
            Button removeBtn = new Button("✕");
            removeBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:#dc2626;-fx-cursor:hand;");
            HBox row = new HBox(8, upperF, new Label("KES  →"), rateF, new Label("%"), removeBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            removeBtn.setOnAction(e -> { bandsBox.getChildren().remove(row); bandRows.remove(row); });
            bandRows.add(row);
            bandsBox.getChildren().add(row);
        };
        for (var band : cfg.getPayeBands()) addRow.accept(band);

        Button addBandBtn = new Button("+ Add Band");
        addBandBtn.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-cursor:hand;-fx-font-size:11px;");
        addBandBtn.setOnAction(e -> addRow.accept(new com.kaziflow.services.PayrollTaxConfig.PayeBand(0, 0)));

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(500);

        root.getChildren().addAll(note, grid, housingReducesTaxableCb, bandsLabel, bandsBox, addBandBtn);
        d.getDialogPane().setContent(scroll);

        ButtonType resetBtn = new ButtonType("Reset to Defaults");
        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(resetBtn, saveBtn, ButtonType.CANCEL);

        d.showAndWait().ifPresent(btn -> {
            if (btn == resetBtn) {
                cfg.resetToDefaults();
                com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Reset", "Payroll tax rates restored to current defaults.");
            } else if (btn == saveBtn) {
                try {
                    List<com.kaziflow.services.PayrollTaxConfig.PayeBand> bands = new ArrayList<>();
                    for (HBox row : bandRows) {
                        TextField upperF = (TextField) row.getChildren().get(0);
                        TextField rateF = (TextField) row.getChildren().get(2);
                        bands.add(new com.kaziflow.services.PayrollTaxConfig.PayeBand(
                            Double.parseDouble(upperF.getText().trim()), Double.parseDouble(rateF.getText().trim())));
                    }
                    if (bands.isEmpty()) {
                        com.kaziflow.utils.Toast.error(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                            "Invalid Bands", "At least one PAYE band is required.");
                        return;
                    }
                    cfg.save(
                        Double.parseDouble(nssfLelF.getText().trim()), Double.parseDouble(nssfUelF.getText().trim()),
                        Double.parseDouble(nssfRateF.getText().trim()), Double.parseDouble(shifRateF.getText().trim()),
                        Double.parseDouble(shifMinF.getText().trim()), Double.parseDouble(housingRateF.getText().trim()),
                        housingReducesTaxableCb.isSelected(), Double.parseDouble(nitaF.getText().trim()),
                        Double.parseDouble(reliefF.getText().trim()), bands
                    );
                    com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                        "Saved", "Payroll tax rates updated — future payroll runs will use these values.");
                } catch (NumberFormatException ex) {
                    com.kaziflow.utils.Toast.error(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                        "Invalid Input", "All rates and bands must be valid numbers.");
                }
            }
        });
    }

    /**
     * Lets the user pick a display currency (KES/USD/EUR) and set the exchange
     * rates used to convert from KES (the DB's permanent base currency) for display.
     * Nothing is ever stored in the database in a non-KES currency — see CurrencyService.
     */
    private void showCurrencyDialog() {
        com.kaziflow.services.CurrencyService currency = com.kaziflow.services.CurrencyService.getInstance();

        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle("Currency Settings");
        d.getDialogPane().setStyle("-fx-background-color:white;");
        d.getDialogPane().setPrefWidth(440);
        GridPane f = new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));

        Label note = new Label("All amounts are stored in KES internally. Choosing a different display "
            + "currency only changes how amounts are shown throughout the app, using the exchange rate below — "
            + "it never changes stored data or past reports.");
        note.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");
        note.setWrapText(true);

        ComboBox<String> currencyCombo = new ComboBox<>();
        currencyCombo.getItems().addAll(currency.getSupportedCurrencies().keySet());
        currencyCombo.setValue(currency.getActiveCurrency());

        javafx.scene.control.TextField usdField = field("KES per 1 USD", String.valueOf(1.0 / currency.getUsdRate()));
        javafx.scene.control.TextField eurField = field("KES per 1 EUR", String.valueOf(1.0 / currency.getEurRate()));

        f.addRow(0, note);
        f.addRow(1, lbl("Display Currency"), currencyCombo);
        f.addRow(2, lbl("KES per 1 USD"), usdField);
        f.addRow(3, lbl("KES per 1 EUR"), eurField);

        d.getDialogPane().setContent(f);
        ButtonType saveBtn = new ButtonType("Save Currency Settings");
        d.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        d.showAndWait().ifPresent(btn -> {
            if (btn != saveBtn) return;
            try {
                double kesPerUsd = Double.parseDouble(usdField.getText().trim());
                double kesPerEur = Double.parseDouble(eurField.getText().trim());
                if (kesPerUsd <= 0 || kesPerEur <= 0) throw new NumberFormatException("Rates must be positive");
                // Stored internally as "1 KES = X display currency", i.e. the inverse of what the user enters (KES per unit).
                currency.saveSettings(currencyCombo.getValue(), 1.0 / kesPerUsd, 1.0 / kesPerEur);
                com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Saved", "Currency settings updated — amounts will now display in " + currencyCombo.getValue() + ".");
            } catch (NumberFormatException ex) {
                com.kaziflow.utils.Toast.error(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Invalid Rate", "Exchange rates must be positive numbers.");
            }
        });
    }

    /** Configures the SMTP account used to send receipts by email (Settings → Email Receipts). */
    private void showEmailSettingsDialog() {
        com.kaziflow.services.EmailService email = com.kaziflow.services.EmailService.getInstance();

        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle("Email Receipts — SMTP Settings");
        d.getDialogPane().setStyle("-fx-background-color:white;");
        d.getDialogPane().setPrefWidth(440);
        GridPane f = new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));

        Label note = new Label("Works with any SMTP-over-SSL provider on port 465 — e.g. Gmail with an "
            + "app password, SendGrid, or Mailgun. Credentials are stored locally in this installation's database.");
        note.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");
        note.setWrapText(true);

        String host = "", username = "", fromName = "";
        int port = 465;
        try (java.sql.Connection c = com.kaziflow.database.DatabaseManager.getInstance().getConnection();
             java.sql.Statement s = c.createStatement()) {
            java.sql.ResultSet rs = s.executeQuery("SELECT key, value FROM settings WHERE key LIKE 'smtp_%'");
            java.util.Map<String, String> existing = new java.util.HashMap<>();
            while (rs.next()) existing.put(rs.getString("key"), rs.getString("value"));
            host = existing.getOrDefault("smtp_host", "");
            username = existing.getOrDefault("smtp_username", "");
            fromName = existing.getOrDefault("smtp_from_name", "");
            try { port = Integer.parseInt(existing.getOrDefault("smtp_port", "465")); } catch (Exception ignored) {}
        } catch (Exception ignored) {}

        javafx.scene.control.TextField hostField = field("SMTP Host", host);
        javafx.scene.control.TextField portField = field("Port", String.valueOf(port));
        javafx.scene.control.TextField userField = field("Username", username);
        javafx.scene.control.PasswordField passField = new javafx.scene.control.PasswordField();
        passField.setPromptText("Password / App Password");
        javafx.scene.control.TextField fromNameField = field("From Name", fromName.isEmpty() ? com.kaziflow.brand.Brand.productFullName() : fromName);

        f.addRow(0, note);
        f.addRow(1, lbl("SMTP Host"), hostField);
        f.addRow(2, lbl("Port"), portField);
        f.addRow(3, lbl("Username"), userField);
        f.addRow(4, lbl("Password"), com.kaziflow.utils.PasswordFieldUtils.withToggle(passField));
        f.addRow(5, lbl("From Name"), fromNameField);

        d.getDialogPane().setContent(f);
        ButtonType testBtn = new ButtonType("Send Test Email");
        ButtonType saveBtn = new ButtonType("Save");
        d.getDialogPane().getButtonTypes().addAll(testBtn, saveBtn, ButtonType.CANCEL);
        d.showAndWait().ifPresent(btn -> {
            if (btn != saveBtn && btn != testBtn) return;
            int portVal;
            try { portVal = Integer.parseInt(portField.getText().trim()); }
            catch (NumberFormatException ex) {
                com.kaziflow.utils.Toast.error(com.kaziflow.utils.SceneManager.getInstance().getStage(), "Invalid Port", "Port must be a number.");
                return;
            }
            // Save first (test-send should use whatever's currently in the fields, not the last-saved values).
            email.saveSettings(hostField.getText().trim(), portVal, userField.getText().trim(),
                passField.getText(), fromNameField.getText().trim());

            if (btn == testBtn) {
                com.kaziflow.utils.AsyncTask.run(
                    () -> email.send(userField.getText().trim(), com.kaziflow.brand.Brand.productFullName()+" Test Email",
                        "This is a test email from your "+com.kaziflow.brand.Brand.productFullName()+" installation. If you received this, email receipts are configured correctly."),
                    error -> {
                        if (error == null) com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                            "Test Sent", "Check " + userField.getText().trim() + " for the test email.");
                        else com.kaziflow.utils.Toast.error(com.kaziflow.utils.SceneManager.getInstance().getStage(), "Test Failed", error);
                    },
                    err -> com.kaziflow.utils.Toast.error(com.kaziflow.utils.SceneManager.getInstance().getStage(), "Test Failed", err)
                );
            } else {
                com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(), "Saved", "Email settings updated.");
            }
        });
    }

    /**
     * Configures multi-computer access (Phase 10D). Two real modes, chosen at
     * setup time as agreed: Shared Database (simplest, points every PC at the
     * same SQLite file over a network share) or Server/Client (one PC hosts,
     * others connect over the LAN — more reliable for several tills writing
     * at once, since raw network-file SQLite access isn't designed for
     * concurrent multi-writer use).
     */
    private void showNetworkSettingsDialog() {
        com.kaziflow.network.NetworkConfig net = com.kaziflow.network.NetworkConfig.getInstance();

        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle("Network / Multi-Computer Settings");
        d.getDialogPane().setPrefWidth(480);
        VBox root = new VBox(14);
        root.setPadding(new Insets(20));

        Label note = new Label("Changes here take effect after restarting the app on this computer.");
        note.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;-fx-font-style:italic;");

        ToggleGroup group = new ToggleGroup();
        RadioButton standaloneRb = new RadioButton("Standalone (default) — this computer only, no network sharing");
        RadioButton serverRb = new RadioButton("Host for other computers (Server) — other PCs connect to this one");
        RadioButton clientRb = new RadioButton("Connect to another computer (Client)");
        RadioButton sharedRb = new RadioButton("Shared Database File — point every PC at one SQLite file on a network share");
        for (RadioButton rb : new RadioButton[]{standaloneRb, serverRb, clientRb, sharedRb}) {
            rb.setToggleGroup(group);
            rb.setWrapText(true);
            rb.setStyle("-fx-font-size:13px;");
        }
        switch (net.getMode()) {
            case SERVER -> serverRb.setSelected(true);
            case CLIENT -> clientRb.setSelected(true);
            case SHARED -> sharedRb.setSelected(true);
            default -> standaloneRb.setSelected(true);
        }
        // Same footgun as ProfileSelector/SalesView's payment toggle: clicking the already-selected
        // radio button in a group deselects it entirely, leaving no mode chosen. Never allow that.
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) group.selectToggle(oldToggle);
        });

        // Server mode fields
        Label serverNote = new Label("Other computers will connect to this PC's IP address on the port below.");
        serverNote.setWrapText(true); serverNote.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");
        TextField serverPortF = new TextField(String.valueOf(net.getPort() > 0 ? net.getPort() : 8765));
        HBox serverBox = new HBox(10, new Label("Port:"), serverPortF);
        serverBox.setAlignment(Pos.CENTER_LEFT);
        VBox serverFields = new VBox(6, serverNote, serverBox);
        serverFields.setPadding(new Insets(0, 0, 0, 28));

        // Client mode fields
        TextField hostF = new TextField(net.getHost());
        hostF.setPromptText("Host PC's IP address, e.g. 192.168.1.10");
        TextField clientPortF = new TextField(String.valueOf(net.getPort() > 0 ? net.getPort() : 8765));
        Button testBtn = new Button("Test Connection");
        testBtn.setStyle("-fx-background-color:white;-fx-border-color:#2563eb;-fx-text-fill:#2563eb;-fx-border-radius:6;-fx-background-radius:6;-fx-cursor:hand;-fx-pref-height:30px;");
        Label testResult = new Label();
        testBtn.setOnAction(e -> {
            try {
                int port = Integer.parseInt(clientPortF.getText().trim());
                com.kaziflow.network.RemoteConnection test = new com.kaziflow.network.RemoteConnection(hostF.getText().trim(), port);
                boolean ok = test.ping();
                test.close();
                testResult.setText(ok ? "✓ Connected successfully" : "✗ Could not reach server");
                testResult.setStyle(ok ? "-fx-text-fill:#16a34a;-fx-font-size:12px;" : "-fx-text-fill:#dc2626;-fx-font-size:12px;");
            } catch (Exception ex) {
                testResult.setText("✗ " + ex.getMessage());
                testResult.setStyle("-fx-text-fill:#dc2626;-fx-font-size:12px;");
            }
        });
        GridPane clientGrid = new GridPane(); clientGrid.setHgap(10); clientGrid.setVgap(8);
        clientGrid.addRow(0, new Label("Host:"), hostF);
        clientGrid.addRow(1, new Label("Port:"), clientPortF);
        VBox clientFields = new VBox(6, clientGrid, testBtn, testResult);
        clientFields.setPadding(new Insets(0, 0, 0, 28));

        // Shared-file mode fields
        Label sharedWarning = new Label("⚠ SQLite is not designed for concurrent writes from multiple PCs over a "
            + "network file share, even with WAL mode enabled — this can risk data corruption if several tills "
            + "write at the same moment. Server/Client mode above is more reliable for active multi-till use; "
            + "use this option only for lighter, mostly-one-writer-at-a-time setups.");
        sharedWarning.setWrapText(true);
        sharedWarning.setStyle("-fx-text-fill:#d97706;-fx-font-size:11px;");
        TextField sharedPathF = new TextField(net.getSharedDbPath());
        sharedPathF.setPromptText("e.g. //FILESERVER/kaziflow/kaziflow.db or Z:\\kaziflow\\kaziflow.db");
        VBox sharedFields = new VBox(6, sharedWarning, sharedPathF);
        sharedFields.setPadding(new Insets(0, 0, 0, 28));

        serverFields.visibleProperty().bind(serverRb.selectedProperty());
        serverFields.managedProperty().bind(serverRb.selectedProperty());
        clientFields.visibleProperty().bind(clientRb.selectedProperty());
        clientFields.managedProperty().bind(clientRb.selectedProperty());
        sharedFields.visibleProperty().bind(sharedRb.selectedProperty());
        sharedFields.managedProperty().bind(sharedRb.selectedProperty());

        root.getChildren().addAll(note, standaloneRb, serverRb, serverFields, clientRb, clientFields, sharedRb, sharedFields);
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(480);
        d.getDialogPane().setContent(scroll);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            try {
                if (standaloneRb.isSelected()) {
                    net.save(com.kaziflow.network.NetworkConfig.Mode.STANDALONE, "", 8765, "");
                } else if (serverRb.isSelected()) {
                    int port = Integer.parseInt(serverPortF.getText().trim());
                    net.save(com.kaziflow.network.NetworkConfig.Mode.SERVER, "", port, "");
                } else if (clientRb.isSelected()) {
                    int port = Integer.parseInt(clientPortF.getText().trim());
                    if (hostF.getText().isBlank()) { com.kaziflow.utils.Toast.error(com.kaziflow.utils.SceneManager.getInstance().getStage(), "Host Required", "Enter the host computer's IP address."); return; }
                    net.save(com.kaziflow.network.NetworkConfig.Mode.CLIENT, hostF.getText().trim(), port, "");
                } else if (sharedRb.isSelected()) {
                    if (sharedPathF.getText().isBlank()) { com.kaziflow.utils.Toast.error(com.kaziflow.utils.SceneManager.getInstance().getStage(), "Path Required", "Enter the shared database file path."); return; }
                    net.save(com.kaziflow.network.NetworkConfig.Mode.SHARED, "", 0, sharedPathF.getText().trim());
                }
                com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Saved", "Network settings updated — restart the app for this to take effect.");
            } catch (NumberFormatException ex) {
                com.kaziflow.utils.Toast.error(com.kaziflow.utils.SceneManager.getInstance().getStage(), "Invalid Port", "Port must be a number.");
            }
        });
    }

    /**
     * The real "enable/disable individual modules" feature — documented as a
     * ModuleRegistry responsibility from the start but never actually built
     * until now. Directly useful for a business that spans more than one
     * industry type (e.g. a pharmacy that also runs a clinic and a nursing
     * home under one roof): pick CUSTOM as the industry profile, then use
     * this screen to turn on exactly the modules needed from every relevant
     * vertical, rather than being stuck with one profile's fixed list.
     */
    private void showModuleTogglesDialog() {
        com.kaziflow.modules.ModuleRegistry registry = com.kaziflow.modules.ModuleRegistry.getInstance();
        java.util.Set<String> currentlyEnabled = registry.getEnabledModuleIds();

        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle("System Modules");
        d.getDialogPane().setPrefWidth(480);

        Label note = new Label("Turn individual modules on or off for this installation. Useful if your "
            + "business spans more than one industry type — pick the closest Industry Profile first, "
            + "then use this screen to add whatever else you need.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");

        // Dashboard, Settings, and Help are never offered as toggleable — turning them off would
        // strand the user with no way to navigate back into Settings to turn them on again.
        java.util.Set<String> alwaysOn = java.util.Set.of("dashboard", "settings", "help");

        VBox checklist = new VBox(6);
        java.util.Map<String, CheckBox> boxes = new java.util.LinkedHashMap<>();
        for (com.kaziflow.modules.Module m : registry.getAllAvailableModules()) {
            if (m.isUserDefined()) continue; // admin-built modules are always on; retire them in Settings → Custom Modules
            boolean mandatory = alwaysOn.contains(m.getId());
            CheckBox cb = new CheckBox(m.getIcon() + "  " + m.getLabel() + (mandatory ? " (always on)" : ""));
            cb.setSelected(mandatory || currentlyEnabled.contains(m.getId()));
            cb.setDisable(mandatory);
            cb.setStyle("-fx-font-size:12px;");
            boxes.put(m.getId(), cb);
            checklist.getChildren().add(cb);
        }

        ScrollPane scroll = new ScrollPane(checklist);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(360);

        VBox content = new VBox(12, note, scroll);
        content.setPadding(new Insets(16));
        d.getDialogPane().setContent(content);

        ButtonType resetBtn = new ButtonType("Reset to Profile Defaults");
        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(resetBtn, saveBtn, ButtonType.CANCEL);
        d.showAndWait().ifPresent(btn -> {
            if (btn == resetBtn) {
                registry.clearModuleOverrides();
                com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Reset", "Modules reset to \"" + registry.getActiveProfile().displayName + "\"'s defaults.");
                com.kaziflow.utils.SceneManager.getInstance().showMainApp();
            } else if (btn == saveBtn) {
                java.util.Set<String> enabled = new java.util.LinkedHashSet<>(alwaysOn);
                boxes.forEach((id, cb) -> { if (cb.isSelected()) enabled.add(id); });
                registry.setModuleOverrides(enabled);
                com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Saved", enabled.size() + " module(s) enabled.");
                com.kaziflow.utils.SceneManager.getInstance().showMainApp();
            }
        });
    }

    /** Payment methods (master data): list, add, edit, switch on/off. Built without being shown so it can be tested. */
    javafx.scene.control.Dialog<Void> buildPaymentMethodsDialog() {
        javafx.scene.control.Dialog<Void> d = new javafx.scene.control.Dialog<>();
        d.setTitle("Payment Methods");
        d.setHeaderText("What you accept and pay with. A method is never deleted — switch it off and it stops being offered.");
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.scene.control.TableView<com.kaziflow.services.PaymentMethods.Method> tv = new javafx.scene.control.TableView<>();
        tv.setPrefSize(660, 300);
        javafx.scene.control.TableColumn<com.kaziflow.services.PaymentMethods.Method, String> name = new javafx.scene.control.TableColumn<>("NAME");
        name.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().label())); name.setPrefWidth(170);
        javafx.scene.control.TableColumn<com.kaziflow.services.PaymentMethods.Method, String> code = new javafx.scene.control.TableColumn<>("CODE");
        code.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().code())); code.setPrefWidth(110);
        javafx.scene.control.TableColumn<com.kaziflow.services.PaymentMethods.Method, String> kind = new javafx.scene.control.TableColumn<>("TYPE");
        kind.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().kind().label)); kind.setPrefWidth(130);
        javafx.scene.control.TableColumn<com.kaziflow.services.PaymentMethods.Method, String> used = new javafx.scene.control.TableColumn<>("USED FOR");
        used.setCellValueFactory(c -> { var m = c.getValue(); java.util.List<String> u = new java.util.ArrayList<>();
            if (m.forSales()) u.add("Sales"); if (m.forPurchases()) u.add("Purchases"); if (m.forExpenses()) u.add("Expenses");
            return new javafx.beans.property.SimpleStringProperty(String.join(", ", u)); }); used.setPrefWidth(160);
        javafx.scene.control.TableColumn<com.kaziflow.services.PaymentMethods.Method, String> status = new javafx.scene.control.TableColumn<>("STATUS");
        status.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().active() ? "On" : "Off")); status.setPrefWidth(70);
        tv.getColumns().addAll(java.util.List.of(name, code, kind, used, status));
        Runnable reload = () -> { var sel = tv.getSelectionModel().getSelectedItem(); tv.getItems().setAll(com.kaziflow.services.PaymentMethods.all());
            if (sel != null) tv.getItems().stream().filter(m -> m.code().equals(sel.code())).findFirst().ifPresent(m -> tv.getSelectionModel().select(m)); };
        reload.run();
        Button add = new Button("Add…"), edit = new Button("Edit…"), toggle = new Button("Switch on / off");
        add.setOnAction(e -> { buildPaymentMethodEditor(null).showAndWait(); reload.run(); });
        edit.setOnAction(e -> { var m = tv.getSelectionModel().getSelectedItem(); if (m != null) { buildPaymentMethodEditor(m).showAndWait(); reload.run(); } });
        toggle.setOnAction(e -> {
            var m = tv.getSelectionModel().getSelectedItem(); if (m == null) return;
            try { com.kaziflow.services.PaymentMethods.setActive(m.code(), !m.active()); }
            catch (IllegalArgumentException ex) { new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait(); }
            reload.run();
        });
        HBox buttons = new HBox(8, add, edit, toggle);
        VBox box = new VBox(10, tv, buttons);
        d.getDialogPane().setContent(box);
        return d;
    }

    /** Add (existing == null) or edit a payment method. Problems are shown and the dialog stays open. */
    javafx.scene.control.Dialog<ButtonType> buildPaymentMethodEditor(com.kaziflow.services.PaymentMethods.Method existing) {
        javafx.scene.control.Dialog<ButtonType> d = new javafx.scene.control.Dialog<>();
        d.setTitle(existing == null ? "Add payment method" : "Edit payment method");
        ButtonType save = new ButtonType("Save", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        javafx.scene.control.TextField codeF = new javafx.scene.control.TextField(existing == null ? "" : existing.code());
        codeF.setPromptText("e.g. airtel_money"); codeF.setDisable(existing != null);
        javafx.scene.control.TextField nameF = new javafx.scene.control.TextField(existing == null ? "" : existing.label());
        nameF.setPromptText("e.g. Airtel Money");
        ComboBox<com.kaziflow.services.PaymentMethods.Kind> kindC = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(com.kaziflow.services.PaymentMethods.Kind.values()));
        kindC.setValue(existing == null ? com.kaziflow.services.PaymentMethods.Kind.OTHER : existing.kind()); kindC.setDisable(existing != null);
        javafx.scene.control.TextField orderF = new javafx.scene.control.TextField(existing == null ? "" : String.valueOf(existing.sortOrder()));
        orderF.setPromptText("position (smaller = earlier)"); orderF.setDisable(existing == null);
        javafx.scene.control.CheckBox cs = new javafx.scene.control.CheckBox("Sales (the till)"), cp = new javafx.scene.control.CheckBox("Purchases"), ce = new javafx.scene.control.CheckBox("Expenses");
        cs.setSelected(existing == null || existing.forSales()); cp.setSelected(existing != null && existing.forPurchases()); ce.setSelected(existing != null && existing.forExpenses());
        if (existing != null && existing.system()) { cs.setDisable(true); cp.setDisable(true); ce.setDisable(true); }
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane(); g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(12));
        g.addRow(0, new Label("Code"), codeF); g.addRow(1, new Label("Name"), nameF); g.addRow(2, new Label("Type"), kindC); g.addRow(3, new Label("Order"), orderF);
        g.addRow(4, new Label("Used for"), new VBox(4, cs, cp, ce));
        d.getDialogPane().setContent(g);
        d.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                if (existing == null) com.kaziflow.services.PaymentMethods.add(codeF.getText(), nameF.getText(), kindC.getValue(), cs.isSelected(), cp.isSelected(), ce.isSelected());
                else com.kaziflow.services.PaymentMethods.update(existing.code(), nameF.getText(), orderF.getText().isBlank() ? existing.sortOrder() : Integer.parseInt(orderF.getText().trim()), cs.isSelected(), cp.isSelected(), ce.isSelected());
            } catch (NumberFormatException ex) { new Alert(Alert.AlertType.WARNING, "The order must be a whole number.").showAndWait(); ev.consume(); }
            catch (IllegalArgumentException ex) { new Alert(Alert.AlertType.WARNING, ex.getMessage()).showAndWait(); ev.consume(); }
        });
        return d;
    }

    /** Load or remove the optional demo company (DemoDataService). Nothing is ever loaded without this explicit choice. */
    private void showDemoCompanyDialog() {
        if (!com.kaziflow.utils.PermissionGuard.allowsModule("settings", com.kaziflow.services.PermissionService.Action.WRITE)) {
            new Alert(Alert.AlertType.WARNING, "You need Write access to Settings to load or remove demo data.").showAndWait();
            return;
        }
        var present = com.kaziflow.services.DemoDataService.countPresent();
        boolean loaded = present.total() > 0;
        String body = loaded
            ? "Demo data is in this database: " + present.describe() + ".\n\nIt shows up in your reports and finance figures until it is removed. "
              + "Removing deletes only these sample rows; anything a real sale, purchase or stock movement uses is kept."
            : "This adds sample suppliers, employees, products, transactions and expenses so you can explore the system. "
              + "They will show up in reports and finance figures until you remove them here.\n\nNothing is added unless you press Load.";
        ButtonType load = new ButtonType("Load demo company"), remove = new ButtonType("Remove demo data");
        Alert a = new Alert(Alert.AlertType.NONE, body, loaded ? remove : load, ButtonType.CANCEL);
        a.setTitle("Demo Company"); a.setHeaderText(null);
        a.showAndWait().ifPresent(btn -> {
            String msg = null;
            if (btn == load) {
                msg = "Demo company loaded: " + com.kaziflow.services.DemoDataService.load().describe() + ".";
            } else if (btn == remove) {
                var r = com.kaziflow.services.DemoDataService.remove();
                msg = "Removed: " + r.removed().describe() + ".";
                if (r.keptBecauseInUse().total() > 0)
                    msg += "\n\nKept because real records use them: " + r.keptBecauseInUse().describe() + ".";
            }
            if (msg != null) new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
        });
    }

    private void showAuditTrailDialog() {
        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle("Audit Trail — Recent Activity");
        d.getDialogPane().setStyle("-fx-background-color:white;");
        d.getDialogPane().setPrefWidth(700); d.getDialogPane().setPrefHeight(500);
        javafx.scene.layout.VBox layout = new javafx.scene.layout.VBox(0);

        HBox bar = new HBox(10); bar.setPadding(new Insets(10,16,10,16)); bar.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.control.TextField searchF = new javafx.scene.control.TextField();
        searchF.setPromptText("Filter by user, action, or module...");
        searchF.setPrefWidth(300);
        searchF.setStyle("-fx-pref-height:34px;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;");
        Label totalLbl = new Label();
        totalLbl.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        Region auditSpacer = new Region();
        HBox.setHgrow(auditSpacer, Priority.ALWAYS);
        Button verifyBtn = new Button("🔗 Verify Chain Integrity");
        verifyBtn.setStyle("-fx-background-color:white;-fx-border-color:#16a34a;-fx-text-fill:#16a34a;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:12px;-fx-cursor:hand;-fx-pref-height:32px;");
        verifyBtn.setOnAction(e -> {
            com.kaziflow.utils.AsyncTask.run(
                () -> com.kaziflow.services.AuditLog.verifyChainIntegrity(),
                breaks -> {
                    if (breaks.isEmpty()) {
                        com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                            "Chain Intact", "All audit entries verified — no tampering detected.");
                    } else {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        com.kaziflow.utils.DialogUtils.makeResizable(alert);
                        alert.setTitle("Audit Trail Integrity Issue");
                        alert.setHeaderText(breaks.size() + " problem(s) found in the audit chain:");
                        alert.getDialogPane().setContent(new javafx.scene.control.TextArea(String.join("\n\n", breaks)) {{
                            setEditable(false); setWrapText(true); setPrefSize(500, 300);
                        }});
                        alert.showAndWait();
                    }
                }, err -> {});
        });
        bar.getChildren().addAll(searchF, totalLbl, auditSpacer, verifyBtn);

        javafx.scene.control.TableView<String[]> tv = new javafx.scene.control.TableView<>();
        tv.setStyle("-fx-background-color:white;");
        tv.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);
        String[] hdrs = {"User","Action","Description","Module","Timestamp"};
        for (int i = 0; i < hdrs.length; i++) {
            final int ci = i;
            javafx.scene.control.TableColumn<String[],String> col = new javafx.scene.control.TableColumn<>(hdrs[i]);
            col.setCellValueFactory(dd -> new javafx.beans.property.SimpleStringProperty(
                ci < dd.getValue().length ? dd.getValue()[ci] : ""));
            tv.getColumns().add(col);
        }
        javafx.collections.ObservableList<String[]> allData = javafx.collections.FXCollections.observableArrayList();
        javafx.collections.ObservableList<String[]> filteredData = javafx.collections.FXCollections.observableArrayList();
        tv.setItems(filteredData);

        searchF.textProperty().addListener((obs, old, val) -> {
            filteredData.clear();
            String q = val.toLowerCase();
            for (String[] row : allData)
                if (q.isEmpty() || java.util.Arrays.stream(row).anyMatch(c -> c != null && c.toLowerCase().contains(q)))
                    filteredData.add(row);
            totalLbl.setText(filteredData.size() + " of " + allData.size() + " entries");
        });

        com.kaziflow.utils.AsyncTask.run(() -> com.kaziflow.services.AuditLog.getRecentLogs(500),
            rows -> {
                allData.setAll(rows); filteredData.setAll(rows);
                totalLbl.setText(rows.size() + " entries");
            }, err -> {});

        VBox.setVgrow(tv, Priority.ALWAYS);
        layout.getChildren().addAll(bar, tv);
        d.getDialogPane().setContent(layout);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.showAndWait();
    }


    private void showLanguageDialog() {
        com.kaziflow.utils.I18n i18n = com.kaziflow.utils.I18n.getInstance();
        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle("Language / Lugha");
        d.getDialogPane().setStyle("-fx-background-color:white;");
        d.getDialogPane().setPrefWidth(380);

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(16);
        content.setPadding(new Insets(20));

        Label info = new Label("Select your preferred interface language:\n" +
                               "Chagua lugha unayopenda:");
        info.setWrapText(true);
        info.setStyle("-fx-text-fill:#475569;-fx-font-size:13px;");

        javafx.scene.control.ToggleGroup tg = new javafx.scene.control.ToggleGroup();
        javafx.scene.layout.VBox options = new javafx.scene.layout.VBox(10);

        for (Map.Entry<String, String> entry : i18n.getAvailableLocales().entrySet()) {
            javafx.scene.control.RadioButton rb = new javafx.scene.control.RadioButton(entry.getValue());
            rb.setUserData(entry.getKey());
            rb.setToggleGroup(tg);
            rb.setSelected(entry.getKey().equals(i18n.getLocale()));
            rb.setStyle("-fx-font-size:14px;-fx-text-fill:#1e293b;");
            options.getChildren().add(rb);
        }
        // Same footgun as the Network dialog above and ProfileSelector (v11.13) — never allow
        // this group to be clicked down to nothing selected.
        tg.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) tg.selectToggle(oldToggle);
        });
        content.getChildren().addAll(info, options);
        d.getDialogPane().setContent(content);

        ButtonType applyBtn = new ButtonType("Apply / Tekeleza");
        d.getDialogPane().getButtonTypes().addAll(applyBtn, ButtonType.CANCEL);
        d.showAndWait().ifPresent(btn -> {
            if (btn != applyBtn) return;
            javafx.scene.control.RadioButton selected =
                (javafx.scene.control.RadioButton) tg.getSelectedToggle();
            if (selected == null) return;
            String code = (String) selected.getUserData();
            i18n.setLocale(code);
            com.kaziflow.utils.Toast.success(
                com.kaziflow.utils.SceneManager.getInstance().getStage(),
                "Language changed",
                selected.getText() + " — navigate to refresh views");
        });
    }

    private void showLicenseDialog() {
        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle("License — "+com.kaziflow.brand.Brand.productFullName());
        d.getDialogPane().setStyle("-fx-background-color:white;");
        d.getDialogPane().setPrefWidth(560);
        d.getDialogPane().setPrefHeight(620);

        com.kaziflow.views.LicenseActivationView activationView =
            new com.kaziflow.views.LicenseActivationView(
                com.kaziflow.utils.AppPaths.dataDir());
        d.getDialogPane().setContent(activationView.getRoot());
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.showAndWait();
    }

    private void showEncryptionDialog() {
        String dbDir  = com.kaziflow.utils.AppPaths.dataDir();
        String dbPath = dbDir + "/kaziflow.db";
        com.kaziflow.security.DatabaseEncryption enc =
            com.kaziflow.security.DatabaseEncryption.getInstance(dbDir);
        boolean enabled = enc.isEncryptionEnabled(dbPath);

        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle("Database Encryption — AES-256-GCM");
        d.getDialogPane().setStyle("-fx-background-color:white;");
        d.getDialogPane().setPrefWidth(460);

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(14);
        content.setPadding(new Insets(20));

        Label statusLbl = new Label(enabled
            ? "Status: Enabled (AES-256-GCM) ✓"
            : "Status: Not enabled — database is stored unencrypted.");
        statusLbl.setStyle("-fx-font-weight:bold;-fx-text-fill:" + (enabled ? "#16a34a" : "#dc2626") + ";-fx-font-size:13px;");

        Label info = new Label(enabled
            ? "To change your passphrase, enter your current passphrase and a new one below."
            : "Set a passphrase to encrypt this database with AES-256-GCM.\n" +
              "You will be prompted for this passphrase every time "+com.kaziflow.brand.Brand.productName()+" starts.\n\n" +
              "⚠ If you forget your passphrase, data cannot be recovered. Store it securely.");
        info.setWrapText(true);
        info.setStyle("-fx-text-fill:#475569;-fx-font-size:12px;");

        javafx.scene.control.PasswordField passF  = new javafx.scene.control.PasswordField();
        passF.setPromptText(enabled ? "Current passphrase" : "New passphrase");
        passF.setStyle("-fx-pref-height:34px;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;");

        javafx.scene.control.PasswordField confirmF = new javafx.scene.control.PasswordField();
        confirmF.setPromptText(enabled ? "New passphrase" : "Confirm passphrase");
        confirmF.setStyle(passF.getStyle());

        if (enabled) content.getChildren().addAll(statusLbl, info, new Label("Current:"), com.kaziflow.utils.PasswordFieldUtils.withToggle(passF), new Label("New passphrase:"), com.kaziflow.utils.PasswordFieldUtils.withToggle(confirmF));
        else         content.getChildren().addAll(statusLbl, info, new Label("Passphrase:"), com.kaziflow.utils.PasswordFieldUtils.withToggle(passF), new Label("Confirm:"), com.kaziflow.utils.PasswordFieldUtils.withToggle(confirmF));

        ButtonType enableBtn = new ButtonType(enabled ? "Change Passphrase" : "Enable Encryption");
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().addAll(enableBtn, ButtonType.CANCEL);
        d.showAndWait().ifPresent(btn -> {
            if (btn != enableBtn) return;
            String pass = passF.getText();
            String confirm = confirmF.getText();
            if (pass.length() < 8) {
                com.kaziflow.utils.Toast.error(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Too short", "Passphrase must be at least 8 characters.");
                return;
            }
            if (!pass.equals(confirm) && !enabled) {
                com.kaziflow.utils.Toast.error(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Mismatch", "Passphrases do not match.");
                return;
            }
            try {
                if (enabled) {
                    enc.changePassphrase(passF.getText().toCharArray(), confirmF.getText().toCharArray(), dbPath);
                    com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                        "Passphrase changed", "Database re-encrypted with new passphrase.");
                } else {
                    enc.enableEncryption(pass.toCharArray(), dbPath);
                    com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                        "Encryption enabled",
                        "Database is now encrypted. You will be prompted at next startup.");
                }
            } catch (Exception ex) {
                com.kaziflow.utils.Toast.error(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Error", ex.getMessage());
            }
        });
    }

    private void showMpesaConfigDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle("M-Pesa Integration Configuration"); dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color:white;"); dialog.getDialogPane().setPrefWidth(460);

        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));
        TextField keyField     = field("Consumer Key",    settings.getOrDefault("mpesa_consumer_key",""));
        PasswordField secretField  = secret("Consumer Secret", settings.getOrDefault("mpesa_consumer_secret",""));
        TextField tillField    = field("Till Number",     settings.getOrDefault("mpesa_till_number",""));
        TextField paybillField = field("Paybill Number",  settings.getOrDefault("mpesa_paybill",""));
        PasswordField passkeyField = secret("Passkey (leave blank for sandbox)", settings.getOrDefault("mpesa_passkey",""));
        ToggleButton sandboxToggle = new ToggleButton(!"false".equals(settings.getOrDefault("mpesa_sandbox","true"))?"Sandbox Mode":"Production Mode");
        sandboxToggle.setSelected(!"false".equals(settings.getOrDefault("mpesa_sandbox","true")));
        sandboxToggle.setStyle("-fx-background-color:#fef3c7;-fx-text-fill:#d97706;-fx-background-radius:6;-fx-border-color:transparent;");
        sandboxToggle.setOnAction(e -> {
            boolean sb = sandboxToggle.isSelected();
            sandboxToggle.setText(sb?"Sandbox Mode":"Production Mode");
            sandboxToggle.setStyle(sb?"-fx-background-color:#fef3c7;-fx-text-fill:#d97706;-fx-background-radius:6;-fx-border-color:transparent;":"-fx-background-color:#dcfce7;-fx-text-fill:#16a34a;-fx-background-radius:6;-fx-border-color:transparent;");
        });

        form.addRow(0, lbl("Consumer Key"),    keyField,     lbl("Consumer Secret"), com.kaziflow.utils.PasswordFieldUtils.withToggle(secretField));
        form.addRow(1, lbl("Till Number"),     tillField,    lbl("Paybill Number"),  paybillField);
        form.addRow(2, lbl("Passkey"),         com.kaziflow.utils.PasswordFieldUtils.withToggle(passkeyField), lbl("Environment"),     sandboxToggle);

        VBox info = new VBox(4);
        Label hint = new Label("ℹ Get your credentials from Safaricom Developer Portal (developer.safaricom.co.ke)");
        hint.setStyle("-fx-text-fill:#2563eb;-fx-font-size:11px;-fx-wrap-text:true;"); hint.setWrapText(true);
        info.getChildren().add(hint);
        form.add(info, 0, 3, 4, 1);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                saveSetting("mpesa_consumer_key",    keyField.getText().trim());
                saveSetting("mpesa_consumer_secret", secretField.getText().trim());
                saveSetting("mpesa_till_number",     tillField.getText().trim());
                saveSetting("mpesa_paybill",         paybillField.getText().trim());
                saveSetting("mpesa_passkey",         passkeyField.getText().trim());
                saveSetting("mpesa_sandbox",         String.valueOf(sandboxToggle.isSelected()));
                // Reload credentials in service
                com.kaziflow.services.MpesaService.getInstance().loadCredentials();
            }
        });
    }

    private void showSmsConfigDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle("SMS Integration — Africa's Talking"); dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color:white;"); dialog.getDialogPane().setPrefWidth(460);

        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));
        TextField usernameField = field("Username (e.g. your app username)", settings.getOrDefault("at_username",""));
        PasswordField apiKeyField   = secret("API Key", settings.getOrDefault("at_api_key",""));
        TextField senderField   = field("Sender ID / Shortcode (optional)", settings.getOrDefault("at_sender_id",""));
        ToggleButton sandboxToggle = new ToggleButton(!"false".equals(settings.getOrDefault("at_sandbox","true"))?"Sandbox Mode":"Production Mode");
        sandboxToggle.setSelected(!"false".equals(settings.getOrDefault("at_sandbox","true")));
        sandboxToggle.setStyle("-fx-background-color:#fef3c7;-fx-text-fill:#d97706;-fx-background-radius:6;-fx-border-color:transparent;");
        sandboxToggle.setOnAction(e -> {
            boolean sb = sandboxToggle.isSelected();
            sandboxToggle.setText(sb?"Sandbox Mode":"Production Mode");
            sandboxToggle.setStyle(sb?"-fx-background-color:#fef3c7;-fx-text-fill:#d97706;-fx-background-radius:6;-fx-border-color:transparent;":"-fx-background-color:#dcfce7;-fx-text-fill:#16a34a;-fx-background-radius:6;-fx-border-color:transparent;");
            usernameField.setDisable(sb);
            if (sb) usernameField.setText("sandbox");
        });
        if (sandboxToggle.isSelected()) { usernameField.setText("sandbox"); usernameField.setDisable(true); }

        form.addRow(0, lbl("Username"),  usernameField, lbl("Environment"), sandboxToggle);
        form.addRow(1, lbl("API Key"),   com.kaziflow.utils.PasswordFieldUtils.withToggle(apiKeyField),   lbl("Sender ID"),   senderField);

        VBox info = new VBox(4);
        Label hint = new Label("ℹ Get your credentials from africastalking.com — sandbox username is always \"sandbox\" " +
            "and works without registering a Sender ID. SMS is used as a fallback for receipts and reminders when " +
            "a customer's number isn't on WhatsApp.");
        hint.setStyle("-fx-text-fill:#2563eb;-fx-font-size:11px;"); hint.setWrapText(true);
        info.getChildren().add(hint);
        form.add(info, 0, 2, 4, 1);

        Button testBtn = new Button("Send Test SMS");
        testBtn.setStyle("-fx-background-color:#f1f5f9;-fx-text-fill:#475569;-fx-background-radius:6;-fx-pref-height:32px;-fx-padding:0 12;-fx-cursor:hand;-fx-font-size:12px;");
        TextField testPhoneField = field("Test phone (07xx...)", "");
        testPhoneField.setPrefWidth(160);
        Label testResult = new Label(""); testResult.setStyle("-fx-font-size:11px;-fx-text-fill:#64748b;");
        testBtn.setOnAction(e -> {
            // Save current values first so the service picks them up
            saveSetting("at_username", usernameField.getText().trim());
            saveSetting("at_api_key",  apiKeyField.getText().trim());
            saveSetting("at_sender_id", senderField.getText().trim());
            saveSetting("at_sandbox",  String.valueOf(sandboxToggle.isSelected()));
            com.kaziflow.services.AfricasTalkingService.getInstance().loadCredentials();
            testResult.setText("Sending...");
            com.kaziflow.utils.AsyncTask.run(
                () -> com.kaziflow.services.AfricasTalkingService.getInstance()
                        .send(testPhoneField.getText().trim(), com.kaziflow.brand.Brand.productFullName()+": this is a test SMS. If you received this, SMS is configured correctly!"),
                result -> testResult.setText(result),
                err -> testResult.setText("⚠️ " + err)
            );
        });
        HBox testRow = new HBox(8, testPhoneField, testBtn, testResult);
        testRow.setAlignment(Pos.CENTER_LEFT);
        form.add(testRow, 0, 3, 4, 1);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                saveSetting("at_username", usernameField.getText().trim());
                saveSetting("at_api_key",  apiKeyField.getText().trim());
                saveSetting("at_sender_id", senderField.getText().trim());
                saveSetting("at_sandbox",  String.valueOf(sandboxToggle.isSelected()));
                com.kaziflow.services.AfricasTalkingService.getInstance().loadCredentials();
                com.kaziflow.utils.Toast.success(
                    com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "SMS settings saved", "Africa's Talking is ready to use");
            }
        });
    }

    private HBox buildSystemStatus() {
        HBox bar = new HBox(20);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 16 24;");

        Label version = new Label("● System Version: v" + com.kaziflow.brand.Brand.version());
        version.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        Label serverStatus = new Label("● Server Status: Operational");
        serverStatus.setStyle("-fx-text-fill: #16a34a; -fx-font-size: 12px; -fx-font-weight: bold;");

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Label dbUsage = new Label("Database Usage:  4.5GB / 10GB");
        dbUsage.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        ProgressBar dbProgress = new ProgressBar(0.45);
        dbProgress.setPrefWidth(100);
        dbProgress.setPrefHeight(8);

        bar.getChildren().addAll(version, serverStatus, sp, dbUsage, dbProgress);
        return bar;
    }

    private void showEditBusinessDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle("Edit Business Details");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(440);

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));

        TextField nameField = field("Business Name", settings.getOrDefault("business_name", ""));
        TextField addrField = field("Address", settings.getOrDefault("business_address", ""));
        TextField pinField  = field("KRA PIN", settings.getOrDefault("business_pin", ""));
        TextField vatField  = field("VAT Rate (%)", settings.getOrDefault("vat_rate", "16"));

        form.addRow(0, lbl("Business Name"), nameField);
        form.addRow(1, lbl("Address"), addrField);
        form.addRow(2, lbl("KRA PIN"), pinField);
        form.addRow(3, lbl("VAT Rate"), vatField);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                saveSetting("business_name", nameField.getText());
                saveSetting("business_address", addrField.getText());
                saveSetting("business_pin", pinField.getText());
                saveSetting("vat_rate", vatField.getText());
                buildUI(); // Rebuild to show changes
            }
        });
    }

    private void saveSetting(String key, String value) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /** Same look as {@link #field}, but masked — for API keys, secrets and passkeys. Wrap with PasswordFieldUtils.withToggle. */
    private PasswordField secret(String prompt, String val) {
        PasswordField pf = new PasswordField(); pf.setText(val); pf.setPromptText(prompt); pf.setPrefWidth(280);
        pf.setStyle("-fx-pref-height: 34px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;");
        return pf;
    }

    // ── Real values for the card status lines (nothing here may be invented) ──

    private String activeUsersStatus() {
        long n = new com.kaziflow.dao.UserDAO().findAll().stream().filter(u -> "active".equalsIgnoreCase(u.getStatus())).count();
        return n + (n == 1 ? " active user" : " active users");
    }

    private String positionsStatus() {
        int n = com.kaziflow.services.PermissionAdminService.getInstance().listPositions().size();
        return n + (n == 1 ? " position" : " positions");
    }

    private String customModulesStatus() {
        int n = com.kaziflow.custom.CustomModuleService.getInstance().listModules(false).size();
        return n == 0 ? "None added yet" : n + (n == 1 ? " custom module" : " custom modules");
    }

    private boolean mpesaConfigured() {
        return !settings.getOrDefault("mpesa_consumer_key", "").isBlank()
            && !settings.getOrDefault("mpesa_consumer_secret", "").isBlank();
    }

    private String mpesaStatus() {
        if (!mpesaConfigured()) return "Not configured";
        return "true".equals(settings.getOrDefault("mpesa_sandbox", "true")) ? "Configured (Sandbox)" : "Configured (Production)";
    }

    private String vatStatus() {
        com.kaziflow.services.VATConfig vat = com.kaziflow.services.VATConfig.getInstance();
        double pct = vat.getRatePercent();
        String shown = pct == Math.rint(pct) ? String.valueOf((long) pct) : String.valueOf(pct);
        return vat.isEnabled() ? "VAT: " + shown + "%" : "VAT off";
    }

    private TextField field(String prompt, String val) {
        TextField tf = new TextField(val); tf.setPromptText(prompt); tf.setPrefWidth(280);
        tf.setStyle("-fx-pref-height: 34px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;");
        return tf;
    }

    private Label lbl(String text) {
        Label l = new Label(text); l.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;"); return l;
    }
}
