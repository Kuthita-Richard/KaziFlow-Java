package com.kaziflow.utils;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Lightweight toast notification system.
 *
 * Usage:
 *   Toast.show(stage, Toast.Type.SUCCESS, "Sale completed", "KES 12,500 recorded successfully");
 *   Toast.show(stage, Toast.Type.ERROR, "Save failed", "Check your connection");
 *   Toast.info(stage, "3 items low on stock");
 */
public class Toast {

    public enum Type {
        SUCCESS("#16a34a", "#dcfce7", "✓"),
        ERROR  ("#dc2626", "#fee2e2", "✗"),
        WARNING("#d97706", "#fef3c7", "⚠"),
        INFO   ("#2563eb", "#eff6ff", "ℹ");

        final String accent, bg, icon;
        Type(String accent, String bg, String icon) {
            this.accent = accent; this.bg = bg; this.icon = icon;
        }
    }

    /** Show a toast with title + message */
    public static void show(Stage owner, Type type, String title, String message) {
        Platform.runLater(() -> {
            Popup popup = new Popup();
            popup.setAutoHide(true);

            // ── Card ──────────────────────────────────────────────────
            HBox card = new HBox(12);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setPadding(new Insets(14, 18, 14, 14));
            card.setStyle(
                "-fx-background-color: " + type.bg + ";" +
                "-fx-border-color: " + type.accent + ";" +
                "-fx-border-width: 0 0 0 4;" +
                "-fx-border-radius: 0 8 8 0;" +
                "-fx-background-radius: 0 8 8 0;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 12, 0, 0, 4);"
            );
            card.setMinWidth(300);
            card.setMaxWidth(380);

            // Icon circle
            StackPane iconBox = new StackPane();
            iconBox.setMinSize(32, 32);
            iconBox.setMaxSize(32, 32);
            iconBox.setStyle(
                "-fx-background-color: " + type.accent + ";" +
                "-fx-background-radius: 16;"
            );
            Label iconLbl = new Label(type.icon);
            iconLbl.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
            iconBox.getChildren().add(iconLbl);

            // Text
            VBox textBox = new VBox(2);
            Label titleLbl = new Label(title);
            titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");

            card.getChildren().add(iconBox);

            if (message != null && !message.isBlank()) {
                Label msgLbl = new Label(message);
                msgLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
                msgLbl.setWrapText(true);
                msgLbl.setMaxWidth(280);
                textBox.getChildren().addAll(titleLbl, msgLbl);
            } else {
                textBox.getChildren().add(titleLbl);
            }
            card.getChildren().add(textBox);

            // Close button
            Label closeBtn = new Label("×");
            closeBtn.setStyle(
                "-fx-font-size: 16px; -fx-text-fill: #94a3b8; -fx-cursor: hand; -fx-padding: 0 0 0 8;"
            );
            closeBtn.setOnMouseClicked(e -> popup.hide());

            HBox.setHgrow(textBox, javafx.scene.layout.Priority.ALWAYS);
            card.getChildren().add(closeBtn);

            popup.getContent().add(card);

            // ── Animation ─────────────────────────────────────────────
            card.setOpacity(0);
            popup.show(owner);

            // Position: bottom-right of window
            double x = owner.getX() + owner.getWidth() - 400;
            double y = owner.getY() + owner.getHeight() - 120;
            popup.setX(x);
            popup.setY(y);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), card);
            fadeIn.setToValue(1.0);

            PauseTransition pause = new PauseTransition(Duration.seconds(3.5));

            FadeTransition fadeOut = new FadeTransition(Duration.millis(400), card);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> popup.hide());

            SequentialTransition seq = new SequentialTransition(fadeIn, pause, fadeOut);
            seq.play();
        });
    }

    /** Show a simple single-line toast */
    public static void show(Stage owner, Type type, String message) {
        show(owner, type, message, null);
    }

    // ── Convenience methods ───────────────────────────────────────────────────

    public static void success(Stage owner, String message) {
        show(owner, Type.SUCCESS, message);
    }

    public static void error(Stage owner, String message) {
        show(owner, Type.ERROR, message);
    }

    public static void warning(Stage owner, String message) {
        show(owner, Type.WARNING, message);
    }

    public static void info(Stage owner, String message) {
        show(owner, Type.INFO, message);
    }

    public static void success(Stage owner, String title, String message) {
        show(owner, Type.SUCCESS, title, message);
    }

    public static void error(Stage owner, String title, String message) {
        show(owner, Type.ERROR, title, message);
    }

    /** Get the primary stage from SceneManager for convenience */
    public static Stage stage() {
        return SceneManager.getInstance().getStage();
    }
}
