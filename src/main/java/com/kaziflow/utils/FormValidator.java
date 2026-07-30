package com.kaziflow.utils;

import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple validation utility for forms.
 * Call validate() before saving, shows red borders on invalid fields.
 */
public class FormValidator {

    private final List<String> errors = new ArrayList<>();

    public void reset() { errors.clear(); }

    /** Marks field red if empty. Returns false if invalid. */
    public boolean requireText(TextField field, String fieldName) {
        if (field.getText() == null || field.getText().isBlank()) {
            markInvalid(field);
            errors.add(fieldName + " is required.");
            return false;
        }
        markValid(field);
        return true;
    }

    /** Validates field is a positive number. */
    public boolean requirePositiveNumber(TextField field, String fieldName) {
        try {
            double val = Double.parseDouble(field.getText().trim());
            if (val < 0) throw new NumberFormatException();
            markValid(field);
            return true;
        } catch (NumberFormatException e) {
            markInvalid(field);
            errors.add(fieldName + " must be a valid positive number.");
            return false;
        }
    }

    /** Validates field is a number (can be zero). */
    public boolean requireNumber(TextField field, String fieldName) {
        try {
            Double.parseDouble(field.getText().trim());
            markValid(field);
            return true;
        } catch (NumberFormatException e) {
            markInvalid(field);
            errors.add(fieldName + " must be a valid number.");
            return false;
        }
    }

    /** Validates email format. */
    public boolean requireEmail(TextField field, String fieldName) {
        String val = field.getText() == null ? "" : field.getText().trim();
        if (!val.matches("^[\\w.%+\\-]+@[\\w.\\-]+\\.[A-Za-z]{2,}$")) {
            markInvalid(field);
            errors.add(fieldName + " must be a valid email address.");
            return false;
        }
        markValid(field);
        return true;
    }

    /** Validates phone number (Kenyan format). */
    public boolean requirePhone(TextField field, String fieldName) {
        String val = field.getText() == null ? "" : field.getText().replaceAll("[\\s\\-()]", "");
        if (!val.matches("^(\\+254|254|0)[17][0-9]{8}$")) {
            markInvalid(field);
            errors.add(fieldName + " must be a valid Kenyan phone number.");
            return false;
        }
        markValid(field);
        return true;
    }

    /** Validates ComboBox has a selection. */
    public boolean requireSelection(ComboBox<?> combo, String fieldName) {
        if (combo.getValue() == null) {
            combo.setStyle("-fx-border-color: #dc2626; -fx-border-radius: 6; -fx-background-radius: 6;");
            errors.add(fieldName + " is required.");
            return false;
        }
        combo.setStyle("");
        return true;
    }

    public boolean hasErrors() { return !errors.isEmpty(); }

    public String getErrorMessage() {
        return String.join("\n", errors);
    }

    public void showErrors(Label errorLabel) {
        if (!errors.isEmpty()) {
            errorLabel.setText("Please fix the following:\n" + getErrorMessage());
            errorLabel.setTextFill(Color.web("#dc2626"));
            errorLabel.setVisible(true);
        } else {
            errorLabel.setVisible(false);
        }
    }

    private void markInvalid(TextField field) {
        field.setStyle(field.getStyle()
            .replaceAll("-fx-border-color:[^;]+;", "")
            + " -fx-border-color: #dc2626; -fx-border-width: 1.5;");
    }

    private void markValid(TextField field) {
        field.setStyle(field.getStyle()
            .replaceAll("-fx-border-color:[^;]+;", "")
            .replaceAll("-fx-border-width:[^;]+;", "")
            + " -fx-border-color: #22c55e; -fx-border-width: 1.5;");
    }

    // ── Static helpers ───────────────────────────────────────────────────────

    public static double parseDouble(TextField field) {
        try { return Double.parseDouble(field.getText().trim()); } catch (Exception e) { return 0; }
    }

    public static int parseInt(TextField field) {
        try { return Integer.parseInt(field.getText().trim()); } catch (Exception e) { return 0; }
    }
}
