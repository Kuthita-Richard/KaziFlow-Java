package com.kaziflow.views;

import com.kaziflow.utils.SceneManager;
import com.kaziflow.utils.SessionManager;
import com.kaziflow.utils.ThemeManager;
import com.kaziflow.views.AIAssistantView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.util.HashMap;
import java.util.Map;

public class MainLayout {

    private BorderPane root;
    private VBox sidebar;
    private StackPane contentHolder;
    private AIAssistantView aiView;
    private Button aiBtn;
    private Map<String, Button> navButtons = new HashMap<>();

    public MainLayout() {
        buildUI();
    }

    public BorderPane getRoot() {
        return root;
    }

    public void setContent(Region content) {
        contentHolder.getChildren().setAll(content);
        StackPane.setAlignment(content, Pos.TOP_LEFT);
    }

    public void setActiveNav(String module) {
        activeModule = module;
        navButtons.forEach((key, btn) -> btn.setStyle(STYLE_INACTIVE));
        Button active = navButtons.get(module);
        if (active != null) active.setStyle(STYLE_ACTIVE);
    }

    public String getActiveModule() { return activeModule; }

    /** Toggle AI assistant panel — called by Ctrl+A shortcut */
    public void toggleAI() {
        if (aiView == null || aiBtn == null) return;
        aiBtn.fire();
    }

    private void buildUI() {
        root = new BorderPane();
        root.getStyleClass().add("main-root");

        // Sidebar
        sidebar = buildSidebar();
        root.setLeft(sidebar);

        // AI Assistant panel (right side, toggleable)
        aiView = new AIAssistantView();
        VBox aiPanel = aiView.getPanel();

        // Content area
        contentHolder = new StackPane();
        contentHolder.getStyleClass().add("content-area");

        ScrollPane scrollPane = new ScrollPane(contentHolder);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: #f8fafc; -fx-background: #f8fafc;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Top bar with AI toggle button
        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_RIGHT);
        topBar.setPadding(new Insets(6, 12, 6, 12));
        topBar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent " +
            "#e2e8f0 transparent; -fx-border-width: 0 0 1 0;");
        HBox.setHgrow(topBar, Priority.ALWAYS);

        aiBtn = new Button("✦  AI Assistant");
        aiBtn.setStyle(
            "-fx-background-color: linear-gradient(to right, #0d1b2a, #16532b);" +
            "-fx-text-fill: #02a870; -fx-font-weight: bold; -fx-font-size: 12px;" +
            "-fx-background-radius: 20; -fx-padding: 6 16; -fx-cursor: hand;" +
            "-fx-border-color: #02a870; -fx-border-radius: 20; -fx-border-width: 1;"
        );
        aiBtn.setOnAction(e -> {
            aiView.toggle();
            if (aiView.isVisible()) {
                root.setRight(aiPanel);
                aiBtn.setStyle(
                    "-fx-background-color: #02a870; -fx-text-fill: white;" +
                    "-fx-font-weight: bold; -fx-font-size: 12px;" +
                    "-fx-background-radius: 20; -fx-padding: 6 16; -fx-cursor: hand;"
                );
            } else {
                root.setRight(null);
                aiBtn.setStyle(
                    "-fx-background-color: linear-gradient(to right, #0d1b2a, #16532b);" +
                    "-fx-text-fill: #02a870; -fx-font-weight: bold; -fx-font-size: 12px;" +
                    "-fx-background-radius: 20; -fx-padding: 6 16; -fx-cursor: hand;" +
                    "-fx-border-color: #02a870; -fx-border-radius: 20; -fx-border-width: 1;"
                );
            }
        });
        topBar.getChildren().add(aiBtn);

        // Dark mode toggle button
        Button themeBtn = new Button(ThemeManager.getInstance().isDark() ? "☀" : "🌙");
        themeBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:#94a3b8;" +
            "-fx-cursor:hand;-fx-font-size:18px;-fx-border-color:transparent;" +
            "-fx-padding:0 8;");
        themeBtn.setTooltip(new javafx.scene.control.Tooltip("Toggle dark / light mode"));
        themeBtn.setOnAction(e -> {
            ThemeManager.getInstance().toggleTheme();
            themeBtn.setText(ThemeManager.getInstance().isDark() ? "☀" : "🌙");
            // Update topBar inline style to match theme
            String bg = ThemeManager.getInstance().isDark() ? "#1e2535" : "white";
            String border = ThemeManager.getInstance().isDark() ? "#2d3748" : "#e2e8f0";
            topBar.setStyle("-fx-background-color:" + bg +
                ";-fx-border-color:transparent transparent " + border + " transparent;" +
                "-fx-border-width:0 0 1 0;");
        });
        topBar.getChildren().add(themeBtn);

        VBox centerArea = new VBox(0, buildLicenseBanner(), topBar, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        root.setCenter(centerArea);
    }

    /**
     * Shows a dismissible-per-session banner during trial or after trial
     * expiry. Never blocks navigation or data access — only nudges toward
     * activation. Returns an empty (zero-height) Region when fully licensed.
     */
    private Region buildLicenseBanner() {
        String dataDir = System.getProperty("user.home") + "/KaziFlowERP";
        com.kaziflow.license.LicenseService license =
            com.kaziflow.license.LicenseService.getInstance(dataDir);
        com.kaziflow.license.LicenseService.Status status = license.checkStatus();

        if (status == com.kaziflow.license.LicenseService.Status.ACTIVE) {
            return new Region(); // no banner once licensed
        }

        HBox banner = new HBox(12);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(8, 16, 8, 16));

        String message;
        String bg, fg;
        if (status == com.kaziflow.license.LicenseService.Status.TRIAL_ACTIVE) {
            int days = license.trialDaysRemaining();
            message = "⏱ Trial Mode — " + days + " day" + (days == 1 ? "" : "s") +
                " remaining. All features unlocked.";
            bg = "#fef3c7"; fg = "#92400e";
        } else {
            message = "⚠ Trial period has ended. Your data remains safe — activate to remove this notice.";
            bg = "#fee2e2"; fg = "#991b1b";
        }

        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-text-fill: " + fg + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button activateBtn = new Button("Activate Now →");
        activateBtn.setStyle("-fx-background-color: " + fg + "; -fx-text-fill: white; " +
            "-fx-background-radius: 6; -fx-font-size: 11px; -fx-font-weight: bold; " +
            "-fx-cursor: hand; -fx-padding: 4 12;");
        activateBtn.setOnAction(e -> {
            javafx.scene.layout.StackPane wrapper = new javafx.scene.layout.StackPane(
                new LicenseActivationView(dataDir).getRoot());
            wrapper.setStyle("-fx-background-color: rgba(0,0,0,0.5);");
            wrapper.setPadding(new Insets(40));
            javafx.stage.Stage dialogStage = new javafx.stage.Stage();
            dialogStage.initOwner(SceneManager.getInstance().getStage());
            dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Activate KaziFlow ERP");
            dialogStage.setScene(new javafx.scene.Scene(wrapper, 560, 620));
            dialogStage.showAndWait();
        });

        banner.getChildren().addAll(msgLabel, spacer, activateBtn);
        banner.setStyle("-fx-background-color: " + bg + ";");
        return banner;
    }

    private VBox buildSidebar() {
        VBox sb = new VBox(0);
        sb.getStyleClass().add("sidebar");
        sb.setPrefWidth(200);
        sb.setStyle("-fx-background-color: #0d1b2a;");

        // ── Brand ──
        HBox brand = new HBox(10);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(18, 16, 18, 16));
        brand.setStyle("-fx-background-color: #0d1b2a;");

        StackPane logoBox = new StackPane();
        logoBox.setStyle("-fx-background-color: #1e2d3d; -fx-background-radius: 8; -fx-padding: 7;");
        Text logoIcon = new Text("▣");
        logoIcon.setStyle("-fx-font-size: 16px; -fx-fill: white;");
        logoBox.getChildren().add(logoIcon);

        Label brandLabel = new Label("KaziFlow ERP");
        brandLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

        brand.getChildren().addAll(logoBox, brandLabel);

        // Divider
        Region brandDivider = new Region();
        brandDivider.setPrefHeight(1);
        brandDivider.setStyle("-fx-background-color: #1e2d3d;");

        // ── Dynamic Nav from ModuleRegistry ──
        com.kaziflow.modules.ModuleRegistry reg = com.kaziflow.modules.ModuleRegistry.getInstance();

        VBox navItems = new VBox(2);
        navItems.setPadding(new Insets(8, 0, 8, 0));
        for (com.kaziflow.modules.Module m : reg.getNavModules()) {
            navItems.getChildren().add(navBtn(m.getId(), m.getIcon(), m.getLabel()));
        }

        // Spacer
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // ── Bottom Nav (Settings, Help) ──
        VBox bottomNav = new VBox(2);
        bottomNav.setPadding(new Insets(0, 0, 8, 0));
        for (com.kaziflow.modules.Module m : reg.getBottomNavModules()) {
            bottomNav.getChildren().add(navBtn(m.getId(), m.getIcon(), m.getLabel()));
        }

        // ── User Footer ──
        HBox userFooter = buildUserFooter();

        sb.getChildren().addAll(brand, brandDivider, navItems, spacer, bottomNav, userFooter);
        return sb;
    }

    private static final String STYLE_INACTIVE =
        "-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 13px;" +
        "-fx-alignment: center-left; -fx-padding: 0 16; -fx-pref-height: 42px;" +
        "-fx-cursor: hand; -fx-border-color: transparent; -fx-background-radius: 6;";

    private static final String STYLE_HOVER =
        "-fx-background-color: #1e2d3d; -fx-text-fill: white; -fx-font-size: 13px;" +
        "-fx-alignment: center-left; -fx-padding: 0 16; -fx-pref-height: 42px;" +
        "-fx-cursor: hand; -fx-border-color: transparent; -fx-background-radius: 6;";

    private static final String STYLE_ACTIVE =
        "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;" +
        "-fx-alignment: center-left; -fx-padding: 0 16; -fx-pref-height: 42px;" +
        "-fx-cursor: hand; -fx-border-color: transparent; -fx-background-radius: 6;";

    private String activeModule = "dashboard";

    private Button navBtn(String module, String icon, String label) {
        Button btn = new Button(icon + "  " + label);
        btn.setPrefWidth(196);
        btn.setStyle(STYLE_INACTIVE);
        btn.setOnMouseEntered(e -> { if (!module.equals(activeModule)) btn.setStyle(STYLE_HOVER); });
        btn.setOnMouseExited(e  -> { if (!module.equals(activeModule)) btn.setStyle(STYLE_INACTIVE); });
        btn.setOnAction(e -> SceneManager.getInstance().navigateTo(module));
        navButtons.put(module, btn);
        return btn;
    }

    @Override
    public String toString() {
        return "MainLayout";
    }

    private HBox buildUserFooter() {
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(12, 16, 16, 16));
        footer.setStyle("-fx-background-color: #0d1b2a; -fx-border-color: #1e2d3d transparent transparent transparent; -fx-border-width: 1 0 0 0;");

        String userName = "Admin User";
        String userRole = "Administrator";

        try {
            var user = SessionManager.getInstance().getCurrentUser();
            if (user != null) {
                userName = user.getName();
                userRole = user.getRoleName();
            }
        } catch (Exception ignored) {}

        // Avatar circle
        StackPane avatar = new StackPane();
        Circle circle = new Circle(16);
        circle.setFill(Color.web("#2563eb"));
        String initial = userName.isEmpty() ? "A" : String.valueOf(userName.charAt(0)).toUpperCase();
        Label initLabel = new Label(initial);
        initLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
        avatar.getChildren().addAll(circle, initLabel);

        VBox userInfo = new VBox(1);
        Label nameLabel = new Label(userName);
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
        Label roleLabel = new Label(userRole);
        roleLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        userInfo.getChildren().addAll(nameLabel, roleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Logout button
        Button logoutBtn = new Button("↩");
        logoutBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-cursor: hand; -fx-font-size: 16px; -fx-border-color: transparent; -fx-padding: 0;");
        logoutBtn.setOnAction(e -> {
            var user = SessionManager.getInstance().getCurrentUser();
            if (user != null) com.kaziflow.services.AuditLog.logLogout(user.getEmail());
            SessionManager.getInstance().logout();
            SceneManager.getInstance().showLogin();
        });

        footer.getChildren().addAll(avatar, userInfo, spacer, logoutBtn);
        return footer;
    }
}
