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
        Text logoIcon = new Text("▣");
        logoIcon.setStyle("-fx-font-size: 18px; -fx-fill: white;");
        logoBox.getChildren().add(logoIcon);

        Label brandLabel = new Label("KaziFlow ERP");
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
        HBox trustRow = new HBox(8);
        trustRow.setAlignment(Pos.CENTER_LEFT);
        trustRow.setPadding(new Insets(0, 0, 0, 0));

        HBox avatars = new HBox(-8);
        String[] colors = {"#2563eb", "#16a34a", "#d97706"};
        String[] initials = {"D", "G", "S"};
        for (int i = 0; i < 3; i++) {
            StackPane avatar = new StackPane();
            Circle circle = new Circle(14);
            circle.setFill(Color.web(colors[i]));
            circle.setStroke(Color.web("#0d1b2a"));
            circle.setStrokeWidth(2);
            Label initial = new Label(initials[i]);
            initial.setStyle("-fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;");
            avatar.getChildren().addAll(circle, initial);
            avatars.getChildren().add(avatar);
        }

        Label trustText = new Label("Trusted by 500+ businesses in Nairobi & Mombasa");
        trustText.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        trustRow.getChildren().addAll(avatars, trustText);

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

        passRow.getChildren().add(passwordField);
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
        emailField.setOnAction(e -> passwordField.requestFocus());

        // Divider
        HBox dividerRow = new HBox(10);
        dividerRow.setAlignment(Pos.CENTER);
        Region d1 = new Region(); d1.setPrefHeight(1); d1.setStyle("-fx-background-color: #e2e8f0;");
        HBox.setHgrow(d1, Priority.ALWAYS);
        Label divText = new Label("OR CONTINUE WITH");
        divText.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        Region d2 = new Region(); d2.setPrefHeight(1); d2.setStyle("-fx-background-color: #e2e8f0;");
        HBox.setHgrow(d2, Priority.ALWAYS);
        dividerRow.getChildren().addAll(d1, divText, d2);
        dividerRow.setStyle("-fx-alignment: center;");

        // Google sign in — not yet implemented (no OAuth client configured).
        // Wired to an informative message rather than left as a dead click.
        Button googleBtn = new Button("G   Sign in with Google");
        googleBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-pref-height: 44px; -fx-font-size: 14px; -fx-cursor: hand;");
        googleBtn.setPrefWidth(380);
        googleBtn.setOnAction(e -> com.kaziflow.utils.Toast.info(
            com.kaziflow.utils.SceneManager.getInstance().getStage(),
            "Google Sign-In is not yet available. Please sign in with your email and password."));

        // Contact support
        HBox supportRow = new HBox(4);
        supportRow.setAlignment(Pos.CENTER);
        Label supportText = new Label("Don't have an account?");
        supportText.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        Label supportLink = new Label("Contact Support");
        supportLink.setStyle("-fx-text-fill: #2563eb; -fx-cursor: hand; -fx-font-size: 13px;");
        supportLink.setOnMouseClicked(e -> {
            try {
                String subject = java.net.URLEncoder.encode("KaziFlow ERP — Account Help", java.nio.charset.StandardCharsets.UTF_8);
                java.awt.Desktop.getDesktop().mail(new java.net.URI(
                    "mailto:support@kaziflow.co.ke?subject=" + subject));
            } catch (Exception ex) {
                com.kaziflow.utils.Toast.error(
                    com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Could not open mail client",
                    "Please email support@kaziflow.co.ke");
            }
        });
        supportRow.getChildren().addAll(supportText, supportLink);

        // Copyright (year computed dynamically so it never goes stale)
        Label copyright = new Label("© " + java.time.Year.now().getValue() + " KaziFlow Systems Ltd. All rights reserved.");
        copyright.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        form.getChildren().addAll(
            title, subtitle,
            new Region() {{ setPrefHeight(8); }},
            emailBox, passBox, optionsRow, errorLabel, signInBtn,
            dividerRow, googleBtn, supportRow
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
                    passwordField.clear();
                }
            });
        }).start();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }
}
