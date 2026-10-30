package com.kaziflow.views;

import com.kaziflow.dao.UserDAO;
import com.kaziflow.models.User;
import com.kaziflow.utils.SceneManager;
import com.kaziflow.utils.SessionManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

public class LoginView {

    private HBox root;
    private TextField emailField;
    private PasswordField passwordField;
    private Label errorLabel;
    private Button signInBtn;

    public LoginView() {
        buildUI();
    }

    public HBox getRoot() {
        return root;
    }

    private void buildUI() {
        root = new HBox();
        root.getStyleClass().add("login-root");
        root.setStyle("-fx-background-color: white;");

        // ── Left Panel ──
        VBox leftPanel = buildLeftPanel();
        leftPanel.setPrefWidth(560);
        leftPanel.setMinWidth(400);
        HBox.setHgrow(leftPanel, Priority.SOMETIMES);

        // ── Right Panel ──
        VBox rightPanel = buildRightPanel();
        rightPanel.setPrefWidth(640);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        root.getChildren().addAll(leftPanel, rightPanel);
    }

    private VBox buildLeftPanel() {
        VBox panel = new VBox();
        panel.getStyleClass().add("login-left");
        panel.setStyle("-fx-background-color: #0d1b2a;");
        panel.setSpacing(0);

        // Brand row
        HBox brand = new HBox(10);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(0, 0, 60, 0));

        StackPane logoBox = new StackPane();
        logoBox.setStyle("-fx-background-color: #1e2d3d; -fx-background-radius: 8; -fx-padding: 8;");
        javafx.scene.image.ImageView markImg = com.kaziflow.brand.BrandAssets.mark(true, 38);
        if (markImg != null) {
            logoBox.setStyle("-fx-padding: 0;");
            logoBox.getChildren().add(markImg);
        } else {   // assets missing: fall back to the old glyph rather than show an empty box
            Text logoIcon = new Text("▣");
            logoIcon.setStyle("-fx-font-size: 18px; -fx-fill: white;");
            logoBox.getChildren().add(logoIcon);
        }

        Label brandLabel = new Label(com.kaziflow.brand.Brand.productFullName());
        brandLabel.getStyleClass().add("login-brand-text");

        brand.getChildren().addAll(logoBox, brandLabel);

        // Spacer
        Region topSpacer = new Region();
        VBox.setVgrow(topSpacer, Priority.ALWAYS);

        // Headline
        Label headline = new Label("Empowering\nKenyan Business");
        headline.getStyleClass().add("login-headline");
        headline.setStyle("-fx-font-size: 38px; -fx-font-weight: bold; -fx-text-fill: white; -fx-wrap-text: true;");

        Label subtitle = new Label("Streamline your inventory, manage sales, and grow your retail or workshop business with our comprehensive enterprise solution designed for local SMEs.");
        subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-wrap-text: true;");
        subtitle.setWrapText(true);
        subtitle.setPadding(new Insets(16, 0, 0, 0));

        // Bottom spacer
        Region bottomSpacer = new Region();
        VBox.setVgrow(bottomSpacer, Priority.ALWAYS);

        // Trust row
        // Only true statements here. (An earlier version claimed "Trusted by 500+ businesses in Nairobi & Mombasa"
        // beside invented customer avatars — nothing in the product supports that, so it was removed.)
        VBox trustRow = new VBox(3);
        trustRow.setAlignment(Pos.CENTER_LEFT);
        Label taglineLbl = new Label(com.kaziflow.brand.Brand.tagline());
        taglineLbl.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12px; -fx-font-weight: bold;");
        Label lineageLbl = new Label(com.kaziflow.brand.Brand.lineage());
        lineageLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        trustRow.getChildren().addAll(taglineLbl, lineageLbl);

        panel.getChildren().addAll(brand, topSpacer, headline, subtitle, bottomSpacer, trustRow);
        return panel;
    }

    private VBox buildRightPanel() {
        VBox panel = new VBox();
        panel.getStyleClass().add("login-right");
        panel.setAlignment(Pos.CENTER);
        panel.setSpacing(0);

        VBox form = new VBox(16);
        form.setMaxWidth(380);
        form.setAlignment(Pos.TOP_LEFT);

        // Title
        Label title = new Label("Welcome back");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label subtitle = new Label("Please enter your details to access your dashboard.");
        subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        subtitle.setWrapText(true);

        // Email field
        VBox emailBox = new VBox(6);
        Label emailLabel = new Label("Email Address");
        emailLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-font-size: 13px;");
        emailField = new TextField();
        emailField.setPromptText("name@company.com");
        emailField.getStyleClass().add("login-field");
        emailField.setPrefWidth(380);
        emailBox.getChildren().addAll(emailLabel, emailField);

        // Password field
        VBox passBox = new VBox(6);
        Label passLabel = new Label("Password");
        passLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-font-size: 13px;");

        HBox passRow = new HBox(0);
        passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");
        passwordField.getStyleClass().add("login-field");
        passwordField.setPrefWidth(380);
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        passRow.getChildren().add(com.kaziflow.utils.PasswordFieldUtils.withToggle(passwordField));
        passBox.getChildren().addAll(passLabel, passRow);

        // Remember me + Forgot password row
        HBox optionsRow = new HBox();
        optionsRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox rememberMe = new CheckBox("Remember me");
        rememberMe.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label forgotPw = new Label("Forgot password?");
        forgotPw.setStyle("-fx-text-fill: #2563eb; -fx-cursor: hand; -fx-font-size: 13px;");

        optionsRow.getChildren().addAll(rememberMe, spacer, forgotPw);

        // Error label
        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
        errorLabel.setVisible(false);

        // Sign in button
        signInBtn = new Button("Sign In");
        signInBtn.getStyleClass().add("btn-primary");
        signInBtn.setPrefWidth(380);
        signInBtn.setOnAction(e -> handleLogin());

        // Allow Enter key login
        passwordField.setOnAction(e -> handleLogin());
        emailField.setOnAction(e -> com.kaziflow.utils.PasswordFieldUtils.requestFocus(passwordField));

        // (No "Sign in with Google": there is no OAuth client behind it, and a button that only apologises is worse
        // than none. Add it back, with its divider, when Google sign-in actually exists.)

        // Contact support
        HBox supportRow = new HBox(4);
        supportRow.setAlignment(Pos.CENTER);
        Label supportText = new Label("Don't have an account?");
        supportText.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        Label supportLink = new Label("Contact Support");
        supportLink.setStyle("-fx-text-fill: #2563eb; -fx-cursor: hand; -fx-font-size: 13px;");
        supportLink.setOnMouseClicked(e -> {
            try {
                String subject = java.net.URLEncoder.encode(com.kaziflow.brand.Brand.productFullName()+" — Account Help", java.nio.charset.StandardCharsets.UTF_8);
                java.awt.Desktop.getDesktop().mail(new java.net.URI(
                    "mailto:" + com.kaziflow.brand.Brand.supportEmail() + "?subject=" + subject));
            } catch (Exception ex) {
                com.kaziflow.utils.Toast.error(
                    com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Could not open mail client",
                    "Please email " + com.kaziflow.brand.Brand.supportEmail());
            }
        });
        supportRow.getChildren().addAll(supportText, supportLink);

        // Copyright (year computed dynamically so it never goes stale)
        Label copyright = new Label(com.kaziflow.brand.Brand.copyright() + "\n" + com.kaziflow.brand.Brand.lineage());
        copyright.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        form.getChildren().addAll(
            title, subtitle,
            new Region() {{ setPrefHeight(8); }},
            emailBox, passBox, optionsRow, errorLabel, signInBtn,
            supportRow
        );

        VBox.setVgrow(form, Priority.NEVER);

        VBox wrapper = new VBox(24);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.getChildren().addAll(form, copyright);

        panel.getChildren().add(wrapper);
        return panel;
    }

    private void handleLogin() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter your email and password.");
            return;
        }

        signInBtn.setDisable(true);
        signInBtn.setText("Signing in...");

        // Run DB call off the FX thread
        new Thread(() -> {
            UserDAO dao = new UserDAO();
            User user = dao.authenticate(email, password);
            Platform.runLater(() -> {
                signInBtn.setDisable(false);
                signInBtn.setText("Sign In");
                if (user != null) {
                    SessionManager.getInstance().login(user);
                    com.kaziflow.services.AuditLog.logLogin(user.getEmail());
                    // First run → show industry profile selector; returning user → main app
                    if (com.kaziflow.modules.ModuleRegistry.getInstance().isFirstRun()) {
                        SceneManager.getInstance().showProfileSelector();
                    } else {
                        SceneManager.getInstance().showMainApp();
                    }
                } else {
                    showError("Invalid email or password. Please try again.");
                    com.kaziflow.services.AuditLog.log("LOGIN_FAILED", "Failed sign-in attempt for " + email, "auth", null);
                    passwordField.clear();
                    com.kaziflow.utils.PasswordFieldUtils.setRevealed(passwordField, false);
                }
            });
        }).start();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }
}
