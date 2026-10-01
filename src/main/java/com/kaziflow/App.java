package com.kaziflow;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.modules.ModuleRegistry;
import com.kaziflow.security.DatabaseEncryption;
import com.kaziflow.utils.SceneManager;
import javafx.application.Application;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class App extends Application {

    private static final String DB_PATH =
        System.getProperty("user.home") + "/KaziFlowERP/kaziflow.db";
    private static final String DB_DIR =
        System.getProperty("user.home") + "/KaziFlowERP";

    @Override
    public void start(Stage primaryStage) throws Exception {

        // ── 0. Database encryption (if passphrase was previously set) ────
        DatabaseEncryption enc = DatabaseEncryption.getInstance(DB_DIR);
        if (enc.isEncryptionEnabled(DB_PATH)) {
            char[] passphrase = promptPassphrase(primaryStage);
            if (passphrase == null) {
                // User cancelled — shut down safely
                javafx.application.Platform.exit();
                return;
            }
            try {
                enc.unlockWithPassphrase(passphrase);
                enc.decryptDatabase(DB_PATH);
                java.util.Arrays.fill(passphrase, '\0'); // clear from memory
            } catch (javax.crypto.AEADBadTagException e) {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR,
                    "Incorrect passphrase or database file corrupted.\n" +
                    "Please try again or restore from backup.",
                    javafx.scene.control.ButtonType.OK);
                alert.showAndWait();
                javafx.application.Platform.exit();
                return;
            }
        }

        // 1. Initialize SQLite database — creates all 25+ tables + 56 indexes
        DatabaseManager.getInstance().initialize();

        // Multi-computer support (Phase 10D): if this PC is configured as the
        // network host, start listening for other PCs' KaziFlow instances now,
        // right after the local database is ready to serve them.
        com.kaziflow.network.NetworkConfig netConfig = com.kaziflow.network.NetworkConfig.getInstance();
        if (netConfig.getMode() == com.kaziflow.network.NetworkConfig.Mode.SERVER) {
            new com.kaziflow.network.NetworkServer(netConfig.getPort()).start();
        }

        // 2. Initialize ETIMS compliance service
        com.kaziflow.services.ETIMSService.getInstance();

        // 3. Pre-initialize AI service (loads API key from DB)
        com.kaziflow.services.AIService.getInstance();

        // 3. Ensure Phase-2 optional tables exist (safe — all use CREATE IF NOT EXISTS)
        new com.kaziflow.dao.DeniDAO().ensureTables();
        new com.kaziflow.dao.QuotationDAO().ensureTables();
        new com.kaziflow.dao.PurchaseReturnDAO().ensureTables();
        new com.kaziflow.dao.PayrollDAO().ensureTables();
        new com.kaziflow.dao.WorkshopDAO().ensureTables();
        new com.kaziflow.dao.GymDAO().ensureTables();
        new com.kaziflow.dao.LaundryDAO().ensureTables();
        new com.kaziflow.dao.HotelDAO().ensureTables();
        new com.kaziflow.dao.FuelStationDAO().ensureTables();
        new com.kaziflow.dao.SchoolCanteenDAO().ensureTables();
        new com.kaziflow.dao.AppointmentDAO().ensureTables();
        new com.kaziflow.dao.PatientDAO().ensureTables();
        new com.kaziflow.dao.RestaurantDAO().ensureTables();
        new com.kaziflow.dao.SaccoDAO().ensureTables();
        new com.kaziflow.dao.DeliveryDAO().ensureTables();
        new com.kaziflow.dao.StockTakeDAO().ensureTables();
        new com.kaziflow.dao.CustomerDAO().ensureLoyaltyColumn();
        com.kaziflow.services.AuditLog.ensureHashColumns();

        // 4. Auto-mark expired batches on startup (background, non-blocking)
        new Thread(() -> new com.kaziflow.dao.BatchDAO().updateExpiredBatches()).start();

        // 4. Seed demo customers if empty
        new com.kaziflow.dao.CustomerDAO().seedIfEmpty();

        // 4. Initialize ModuleRegistry — loads industry profile + enabled modules
        ModuleRegistry.getInstance().init();

        // 4a. Admin-defined departments (custom modules) become real modules before permissions are seeded.
        try {
            com.kaziflow.custom.CustomModuleLoader.sync();
        } catch (Exception e) {
            System.err.println("[CustomModules] Could not load custom modules: " + e.getMessage());
        }

        // 4b. Permissions: seed the Administrator position + special-action catalog, migrate legacy roles
        // once, and self-heal if no active administrator exists. Idempotent, so safe on every start.
        // A CLIENT PC skips it — the server it connects to owns (and has already seeded) that data.
        if (com.kaziflow.network.NetworkConfig.getInstance().getMode() != com.kaziflow.network.NetworkConfig.Mode.CLIENT) {
            try (java.sql.Connection permConn = DatabaseManager.getInstance().getConnection()) {
                java.util.List<String> moduleIds = ModuleRegistry.getInstance().getAllAvailableModules().stream()
                    .map(com.kaziflow.modules.Module::getId).toList();
                com.kaziflow.services.PermissionGroupSetup.seed(permConn, moduleIds);
            } catch (Exception e) {
                System.err.println("[Permissions] Seeding failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // 5. Schedule daily auto-backup
        com.kaziflow.services.BackupService.getInstance().scheduleDailyBackup();

        // 6. Wire up SceneManager
        SceneManager sm = SceneManager.getInstance();
        sm.init(primaryStage);

        // 7. Shutdown hook — encrypt DB + clean up thread pool on close
        primaryStage.setOnCloseRequest(e -> {
            try {
                com.kaziflow.utils.SessionManager session =
                    com.kaziflow.utils.SessionManager.getInstance();
                if (session != null && session.isLoggedIn()
                        && session.getCurrentUser() != null) {
                    com.kaziflow.services.AuditLog.logLogout(
                        session.getCurrentUser().getEmail());
                }
            } catch (Exception ignored) {}
            com.kaziflow.utils.AsyncTask.shutdown();
            // Re-encrypt the database if a passphrase was set this session
            DatabaseEncryption.getInstance(DB_DIR).shutdownEncrypt(DB_PATH);
        });

        primaryStage.setTitle("KaziFlow ERP");
        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(720);
        primaryStage.centerOnScreen();
        primaryStage.show();

        // 8. Route: first run → ProfileSelector, returning user → Login
        if (ModuleRegistry.getInstance().isFirstRun()) {
            sm.showProfileSelector();
        } else {
            sm.showLogin();
        }

        // 9. Background update check (non-blocking, once per 24 hours)
        com.kaziflow.services.UpdateService.getInstance().checkForUpdates((version, url) -> {
            // Show a non-intrusive notification banner — user can dismiss or open browser
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Update Available");
            alert.setHeaderText("KaziFlow ERP " + version + " is available");
            alert.setContentText("You are on v" + com.kaziflow.services.UpdateService.CURRENT_VERSION +
                ".\nVisit the link below to download the update:\n" + url);
            alert.getButtonTypes().setAll(
                new javafx.scene.control.ButtonType("Open Download Page"),
                new javafx.scene.control.ButtonType("Skip This Version"),
                javafx.scene.control.ButtonType.CANCEL);
            alert.showAndWait().ifPresent(btn -> {
                if (btn.getText().startsWith("Open")) {
                    try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); }
                    catch (Exception ignored) {}
                } else if (btn.getText().startsWith("Skip")) {
                    com.kaziflow.services.UpdateService.getInstance().skipVersion(version);
                }
            });
        });
    }

    /** Prompts the user for the database passphrase at startup. */
    private char[] promptPassphrase(Stage owner) {
        Dialog<char[]> dialog = new Dialog<>();
        dialog.setTitle("KaziFlow ERP — Database Passphrase");
        dialog.setHeaderText("This database is encrypted.\nEnter your passphrase to continue.");
        dialog.initOwner(owner);

        PasswordField pf = new PasswordField();
        pf.setPromptText("Enter passphrase...");
        pf.setStyle("-fx-pref-width:320px;-fx-pref-height:38px;-fx-font-size:14px;");

        VBox content = new VBox(8,
            new Label("Database Passphrase:"),
            com.kaziflow.utils.PasswordFieldUtils.withToggle(pf));
        content.setPadding(new javafx.geometry.Insets(16));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK && !pf.getText().isEmpty())
                return pf.getText().toCharArray();
            return null;
        });

        // Auto-focus the password field
        javafx.application.Platform.runLater(pf::requestFocus);
        return dialog.showAndWait().orElse(null);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
