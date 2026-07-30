package com.kaziflow.views;

import com.kaziflow.license.LicenseService;
import com.kaziflow.utils.SceneManager;
import com.kaziflow.utils.Toast;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * LicenseActivationView — shown when the trial period has expired, or
 * accessible any time via Settings -> License.
 *
 * Lets the user:
 *   - See their machine fingerprint (to send to KaziFlow support for a key)
 *   - Paste in a license key and activate
 *   - See current edition / trial days remaining / masked active key
 *   - Continue in trial/limited mode (never hard-blocks data access)
 */
public class LicenseActivationView {

    private final LicenseService license;
    private VBox root;
    private TextArea keyInput;
    private Label statusLabel;

    public LicenseActivationView(String dataDir) {
        this.license = LicenseService.getInstance(dataDir);
        buildUI();
    }

    public VBox getRoot() { return root; }

    private void buildUI() {
        root = new VBox(20);
        root.setPadding(new Insets(32));
        root.setMaxWidth(480);
        root.setStyle("-fx-background-color: white; -fx-background-radius: 12; " +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1;");

        Label icon = new Label("🔑");
        icon.setFont(Font.font(40));

        Label title = new Label("Activate KaziFlow ERP");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        title.setStyle("-fx-text-fill: #1e293b;");

        statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-size: 13px;");
        refreshStatusLabel();

        // Machine fingerprint box (to send to support)
        VBox fpBox = new VBox(6);
        Label fpTitle = new Label("Your Machine ID (send this when requesting a license):");
        fpTitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-font-weight: bold;");
        HBox fpRow = new HBox(8);
        TextField fpField = new TextField(license.machineFingerprint());
        fpField.setEditable(false);
        fpField.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 14px; -fx-font-weight: bold; " +
            "-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6; " +
            "-fx-background-radius: 6; -fx-padding: 8 12;");
        HBox.setHgrow(fpField, Priority.ALWAYS);
        Button copyBtn = new Button("Copy");
        copyBtn.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #e2e8f0; " +
            "-fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 8 14;");
        copyBtn.setOnAction(e -> {
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(license.machineFingerprint());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            Toast.success(SceneManager.getInstance().getStage(), "Copied", "Machine ID copied to clipboard");
        });
        fpRow.getChildren().addAll(fpField, copyBtn);
        fpBox.getChildren().addAll(fpTitle, fpRow);

        // License key input
        Label keyLabel = new Label("Enter License Key:");
        keyLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-font-weight: bold;");
        keyInput = new TextArea();
        keyInput.setPromptText("KAZI-PRO-20271231-A1B2C3D4-XXXXXXXXXXXXXXXX");
        keyInput.setPrefRowCount(2);
        keyInput.setWrapText(true);
        keyInput.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 13px;");

        Button activateBtn = new Button("Activate License");
        activateBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; " +
            "-fx-background-radius: 8; -fx-pref-height: 42px; -fx-pref-width: 200px; -fx-cursor: hand; " +
            "-fx-font-size: 14px;");
        activateBtn.setOnAction(e -> handleActivate());

        Button supportBtn = new Button("📧 Contact Support to Get a License");
        supportBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #2563eb; " +
            "-fx-border-color: #2563eb; -fx-border-radius: 8; -fx-pref-height: 42px; " +
            "-fx-cursor: hand; -fx-font-size: 13px;");
        supportBtn.setOnAction(e -> openSupportEmail());

        HBox btnRow = new HBox(10, activateBtn, supportBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(icon, title, statusLabel, new Separator(),
            fpBox, keyLabel, keyInput, btnRow);
    }

    private void refreshStatusLabel() {
        LicenseService.Status status = license.checkStatus();
        switch (status) {
            case ACTIVE -> {
                statusLabel.setText("✓ Licensed — " + license.getEdition() + " Edition\nKey: " + license.getMaskedKey());
                statusLabel.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold; -fx-font-size: 13px;");
            }
            case TRIAL_ACTIVE -> {
                int days = license.trialDaysRemaining();
                statusLabel.setText("⏱ Trial Mode — " + days + " day" + (days == 1 ? "" : "s") + " remaining\n" +
                    "All features unlocked during trial. Activate anytime to continue after trial ends.");
                statusLabel.setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold; -fx-font-size: 13px;");
            }
            case TRIAL_EXPIRED -> {
                statusLabel.setText("⚠ Trial period has ended.\nYour data is safe and accessible — activate a license to remove this notice.");
                statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 13px;");
            }
            case INVALID -> {
                statusLabel.setText("✕ License key is invalid or bound to a different machine.\nPlease contact support.");
                statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 13px;");
            }
            default -> {
                statusLabel.setText("Not yet activated.");
                statusLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
            }
        }
    }

    private void handleActivate() {
        String key = keyInput.getText().trim().toUpperCase();
        if (key.isEmpty()) {
            Toast.error(SceneManager.getInstance().getStage(), "Required", "Please enter a license key.");
            return;
        }
        boolean ok = license.activate(key);
        if (ok) {
            Toast.success(SceneManager.getInstance().getStage(), "Activated!",
                "KaziFlow ERP is now licensed. Thank you!");
            refreshStatusLabel();
            keyInput.clear();
        } else {
            Toast.error(SceneManager.getInstance().getStage(), "Activation failed",
                "Invalid key, or this key is bound to a different machine. " +
                "Check the key or contact support with your Machine ID above.");
        }
    }

    private void openSupportEmail() {
        try {
            String subject = java.net.URLEncoder.encode("KaziFlow ERP License Request", java.nio.charset.StandardCharsets.UTF_8);
            String body = java.net.URLEncoder.encode(
                "Machine ID: " + license.machineFingerprint() + "\n" +
                "Edition requested: \n" +
                "Business name: \n", java.nio.charset.StandardCharsets.UTF_8);
            java.awt.Desktop.getDesktop().mail(new java.net.URI(
                "mailto:support@kaziflow.co.ke?subject=" + subject + "&body=" + body));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.error(SceneManager.getInstance().getStage(), "Could not open mail client",
                "Please email support@kaziflow.co.ke with your Machine ID.");
        }
    }
}
