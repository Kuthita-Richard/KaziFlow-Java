package com.kaziflow.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * CalculatorDialog — quick standalone calculator, opened via Ctrl+K anywhere
 * in the app (Phase 10E). Deliberately independent of the POS numpad in
 * SalesView: this one evaluates arbitrary chained arithmetic for general use
 * (price checks, quick math), not just cash-tendered entry.
 *
 * Kept as a single dialog instance per invocation rather than a singleton —
 * cheap to build, and a fresh instance avoids any stale-state carryover
 * between unrelated calculations.
 */
public class CalculatorDialog {

    private double accumulator = 0;
    private String pendingOperator = null;
    private boolean startNewEntry = true;
    private Label display;

    public static void show(Stage owner) {
        new CalculatorDialog().build(owner);
    }

    private void build(Stage owner) {
        Dialog<Void> dialog = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle("Calculator");
        dialog.initOwner(owner);
        dialog.getDialogPane().setStyle("-fx-background-color: #f8fafc;");

        VBox root = new VBox(10);
        root.setPadding(new Insets(14));
        root.setAlignment(Pos.CENTER);

        display = new Label("0");
        display.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #1e293b; "
            + "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; "
            + "-fx-background-radius: 8; -fx-padding: 14; -fx-min-width: 240px; -fx-alignment: center-right;");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);

        String[][] keys = {
            {"C", "⌫", "%", "÷"},
            {"7", "8", "9", "×"},
            {"4", "5", "6", "−"},
            {"1", "2", "3", "+"},
            {"0", ".", "=", ""}
        };

        for (int r = 0; r < keys.length; r++) {
            for (int c = 0; c < keys[r].length; c++) {
                String key = keys[r][c];
                if (key.isEmpty()) continue;
                Button btn = new Button(key);
                boolean isOperator = "÷×−+=".contains(key);
                btn.setStyle("-fx-pref-width: 56px; -fx-pref-height: 48px; -fx-font-size: 16px; -fx-font-weight: bold; "
                    + "-fx-background-radius: 8; -fx-cursor: hand; "
                    + (isOperator
                        ? "-fx-background-color: #2563eb; -fx-text-fill: white;"
                        : "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-text-fill: #1e293b;"));
                btn.setOnAction(e -> onKey(key));
                if ("0".equals(key)) GridPane.setColumnSpan(btn, 1);
                grid.add(btn, c, r);
            }
        }

        root.getChildren().addAll(display, grid);
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialog.show();
    }

    private void onKey(String key) {
        switch (key) {
            case "C" -> {
                accumulator = 0;
                pendingOperator = null;
                startNewEntry = true;
                display.setText("0");
            }
            case "⌫" -> {
                String text = display.getText();
                if (text.length() > 1) display.setText(text.substring(0, text.length() - 1));
                else display.setText("0");
            }
            case "%" -> {
                double current = currentValue();
                display.setText(formatResult(current / 100.0));
                startNewEntry = true;
            }
            case "÷", "×", "−", "+" -> {
                applyPendingOperator();
                pendingOperator = key;
                startNewEntry = true;
            }
            case "=" -> {
                applyPendingOperator();
                pendingOperator = null;
                startNewEntry = true;
            }
            case "." -> {
                String text = startNewEntry ? "0" : display.getText();
                if (!text.contains(".")) display.setText(text + ".");
                startNewEntry = false;
            }
            default -> { // digits
                String text = startNewEntry ? "" : display.getText();
                if ("0".equals(text)) text = "";
                display.setText(text + key);
                startNewEntry = false;
            }
        }
    }

    private double currentValue() {
        try { return Double.parseDouble(display.getText()); }
        catch (NumberFormatException e) { return 0; }
    }

    /** Folds the value currently on screen into the accumulator using whatever operator is pending. */
    private void applyPendingOperator() {
        double value = currentValue();
        if (pendingOperator == null) {
            accumulator = value;
        } else {
            accumulator = switch (pendingOperator) {
                case "÷" -> value == 0 ? 0 : accumulator / value;
                case "×" -> accumulator * value;
                case "−" -> accumulator - value;
                case "+" -> accumulator + value;
                default -> value;
            };
        }
        display.setText(formatResult(accumulator));
    }

    private String formatResult(double value) {
        // Integer-valued results display without a trailing ".0" — closer to how a real calculator reads.
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value);
    }
}
