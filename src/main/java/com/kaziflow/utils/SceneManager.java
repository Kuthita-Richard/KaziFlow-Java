package com.kaziflow.utils;

import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import com.kaziflow.views.MainLayout;
import com.kaziflow.views.LoginView;

import java.util.HashMap;
import java.util.Map;

/**
 * SceneManager — handles navigation and view lifecycle.
 *
 * Views are CACHED after first build so navigating back is instant
 * and stateful views (e.g. POS cart) are preserved.
 *
 * Call refreshView("module") to force a rebuild after save operations.
 * Which modules skip the cache is determined by ModuleRegistry.getNoCacheModules().
 */
public class SceneManager {

    private static SceneManager instance;
    private Stage primaryStage;
    private MainLayout mainLayout;

    /** Cached view roots, keyed by module name. */
    private final Map<String, Region> viewCache = new HashMap<>();

    private SceneManager() {}

    public static SceneManager getInstance() {
        if (instance == null) instance = new SceneManager();
        return instance;
    }

    public void init(Stage stage) {
        this.primaryStage = stage;
    }

    public Stage getStage() { return primaryStage; }

    public void showLogin() {
        viewCache.clear();          // drop all cached views on logout
        LoginView login = new LoginView();
        Scene scene = new Scene(login.getRoot(), 1200, 720);
        var cssUrl = getClass().getResource("/styles/main.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        ThemeManager.getInstance().registerScene(scene);
        primaryStage.setScene(scene);
    }

    public void showMainApp() {
        viewCache.clear();
        mainLayout = new MainLayout();
        Scene scene = new Scene(mainLayout.getRoot(), 1366, 768);
        var cssUrl = getClass().getResource("/styles/main.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        ThemeManager.getInstance().registerScene(scene);
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
        wireKeyboardShortcuts(scene);
        // Land on the dashboard if this user may open it, otherwise the first module they can.
        String landing = com.kaziflow.modules.ModuleRegistry.getInstance().getFirstAccessibleModuleId();
        if (landing != null) navigateTo(landing); else showNoAccess();
    }

    /** Shown when a signed-in user has been given no access to anything (no Positions, or none that grant a module). */
    private void showNoAccess() {
        javafx.scene.control.Label title = new javafx.scene.control.Label("No access assigned yet");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        javafx.scene.control.Label body = new javafx.scene.control.Label(
            "Your account isn't assigned to any Position that grants access to a module.\n"
          + "Ask an administrator to assign you a Position in Settings → Users & Access.");
        body.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px; -fx-text-alignment: center;");
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(8, title, body);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        javafx.scene.layout.StackPane pane = new javafx.scene.layout.StackPane(box);
        pane.setStyle("-fx-background-color: #f8fafc;");
        mainLayout.setContent(pane);
    }

    /** Show first-run industry profile selector. */
    public void showProfileSelector() {
        viewCache.clear();
        // Completion routes to Login, not showMainApp() — going straight into the
        // app here would leave SessionManager with no logged-in user, breaking
        // anything downstream that calls getCurrentUser().
        com.kaziflow.modules.ProfileSelector selector =
            new com.kaziflow.modules.ProfileSelector(this::showLogin);
        Scene scene = new Scene(selector.getRoot(), 1366, 768);
        var cssUrl = getClass().getResource("/styles/main.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        ThemeManager.getInstance().registerScene(scene);
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
    }

    /**
     * Global keyboard shortcuts — active whenever the main app is open.
     *
     * Ctrl+N  → Sales / POS
     * Ctrl+I  → Inventory
     * Ctrl+P  → Purchases
     * Ctrl+E  → Employees
     * Ctrl+F  → Finance
     * Ctrl+R  → Reports
     * Ctrl+B  → Manual backup (fires in background)
     * F5      → Refresh current view (force rebuild)
     * F11     → Toggle fullscreen
     * Ctrl+Q  → Quit
     */
    private void wireKeyboardShortcuts(Scene scene) {
        scene.setOnKeyPressed(event -> {
            boolean ctrl = event.isControlDown();
            switch (event.getCode()) {
                case N -> { if (ctrl) navigateTo("sales"); }
                case I -> { if (ctrl) navigateTo("inventory"); }
                case P -> { if (ctrl) navigateTo("purchases"); }
                case E -> { if (ctrl) navigateTo("employees"); }
                case F -> { if (ctrl) navigateTo("finance"); }
                case R -> { if (ctrl) navigateTo("reports"); }
                case B -> { if (ctrl) triggerBackup(); }
                case Q -> { if (ctrl) javafx.application.Platform.exit(); }
                case A -> { if (ctrl) triggerAIToggle(); }
                case K -> { if (ctrl) com.kaziflow.views.CalculatorDialog.show(primaryStage); }
                case F5  -> refreshCurrentView();
                case F11 -> primaryStage.setFullScreen(!primaryStage.isFullScreen());
                default  -> {}
            }
        });
    }

    /** Toggle the AI assistant panel via Ctrl+A */
    private void triggerAIToggle() {
        if (mainLayout != null) mainLayout.toggleAI();
    }

    /** Refresh whichever module is currently displayed. */
    private void refreshCurrentView() {
        if (mainLayout == null) return;
        String current = mainLayout.getActiveModule();
        if (current != null) {
            refreshView(current);
            navigateTo(current);
        }
    }

    /** Trigger manual backup silently and show a Toast on completion. */
    private void triggerBackup() {
        com.kaziflow.utils.AsyncTask.run(
            () -> { com.kaziflow.services.BackupService.getInstance().backupToDirectory(); return null; },
            ignored -> com.kaziflow.utils.Toast.success(primaryStage, "Backup complete", "Database backed up successfully"),
            err     -> com.kaziflow.utils.Toast.error(primaryStage, "Backup failed", err)
        );
    }

    public void navigateTo(String module) {
        if (mainLayout == null) return;

        com.kaziflow.modules.ModuleRegistry reg = com.kaziflow.modules.ModuleRegistry.getInstance();

        // Defense in depth: the sidebar already hides what a user can't open, but keyboard shortcuts
        // (Ctrl+N etc.), dashboard quick-links and F5 all arrive here without going through it.
        if (!reg.isAccessible(module)) { denyNavigation(module, reg); return; }

        boolean skipCache = reg.getNoCacheModules().contains(module);

        Region content;
        if (skipCache) {
            content = reg.buildView(module);
        } else {
            content = viewCache.computeIfAbsent(module, reg::buildView);
        }

        mainLayout.setContent(content);
        mainLayout.setActiveNav(module);
    }

    private void denyNavigation(String module, com.kaziflow.modules.ModuleRegistry reg) {
        if (reg.isEnabled(module)) {
            // Enabled on this install, but this user's Positions don't grant it — an authorization event worth logging.
            com.kaziflow.services.AuditLog.log("ACCESS_DENIED", "Blocked from opening module: " + module, module, null);
            com.kaziflow.utils.Toast.warning(primaryStage, "You don't have access to that section.");
        } else {
            com.kaziflow.utils.Toast.info(primaryStage, "That module isn't enabled for this business (Settings → System Modules).");
        }
    }

    // ── Public helpers ───────────────────────────────────────────────────

    /**
     * Force a module to rebuild on next navigation.
     * Call after save operations that change what the view shows.
     */
    public void refreshView(String module) {
        viewCache.remove(module);
    }

    /** Rebuild all cached views (e.g. after bulk data import). */
    public void refreshAll() {
        viewCache.clear();
    }
}

