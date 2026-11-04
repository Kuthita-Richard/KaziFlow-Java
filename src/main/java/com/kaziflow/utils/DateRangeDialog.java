package com.kaziflow.utils;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.Optional;

/** "Which period?" for an export: a {@link DateRangePicker} in a dialog whose Export button is off while the choice is invalid. */
public final class DateRangeDialog {
    private DateRangeDialog() {}

    /** Shows the dialog (default: all time). Empty = the user cancelled. */
    public static Optional<DateRange> ask(Window owner, String reportName) {
        return create(owner, reportName, new DateRangePicker(DateRange.Preset.ALL_TIME, LocalDate::now)).showAndWait();
    }

    /** Builds the dialog without showing it (so it can be tested). Its result is the picker's range, or null on cancel. */
    public static Dialog<DateRange> create(Window owner, String reportName, DateRangePicker picker) {
        Dialog<DateRange> d = new Dialog<>();
        if (owner != null) d.initOwner(owner);
        d.setTitle("Choose a period");
        d.setHeaderText(reportName + " — which period should the export cover?");
        ButtonType export = new ButtonType("Export", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(export, ButtonType.CANCEL);
        picker.setPadding(new Insets(8, 0, 0, 0));
        d.getDialogPane().setContent(picker);
        var ok = d.getDialogPane().lookupButton(export);
        ok.setDisable(!picker.isValid());
        picker.rangeProperty().addListener((o, a, b) -> ok.setDisable(b == null));
        d.setResultConverter(b -> b == export ? picker.getRange() : null);
        return d;
    }
}
