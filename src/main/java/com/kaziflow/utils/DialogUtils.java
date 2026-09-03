package com.kaziflow.utils;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Window;

/**
 * DialogUtils — the shared dialog-construction helper this codebase should
 * have had from the start. Before this existed, every one of the ~76
 * dialogs across ~31 files was hand-built from scratch, each repeating the
 * same 3-4 lines: `new Dialog<>()`, `setTitle(...)`,
 * `getDialogPane().setStyle("-fx-background-color:white;")`,
 * `getDialogPane().setPrefWidth(...)`. That's exactly the kind of
 * duplication the project's "make it dynamic, build reusable components"
 * principle is against — when a project-wide concern like "dialogs should
 * be resizable" comes up, it should be a one-line fix in one file, not a
 * mechanical sweep across 76 call sites.
 *
 * Current scope is deliberately narrow: this standardizes dialog
 * construction (title/style/width/resizability) without trying to
 * refactor the actual *content* every dialog builds, which varies too much
 * from dialog to dialog to safely generalize in one pass. New dialogs
 * should call styledDialog() instead of `new Dialog<>()` directly. A future
 * pass could migrate the existing 76 call sites over to this — flagged in
 * the project guide as a real but non-trivial refactor, not rushed here
 * given how much a careless mechanical sweep across that many files could
 * go wrong (see docs/changelog/v11.19 for a concrete example of exactly
 * that kind of mistake happening once already).
 */
public class DialogUtils {

    private static final String STANDARD_STYLE = "-fx-background-color:white;";

    /** Standard dialog: titled, styled, sized, and resizable by dragging edges/corners. */
    public static Dialog<ButtonType> styledDialog(String title, double prefWidth) {
        return styledDialog(title, prefWidth, -1);
    }

    /** Same as above, plus an explicit preferred height for dialogs that need one. */
    public static Dialog<ButtonType> styledDialog(String title, double prefWidth, double prefHeight) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(title);
        d.getDialogPane().setStyle(STANDARD_STYLE);
        if (prefWidth > 0) d.getDialogPane().setPrefWidth(prefWidth);
        if (prefHeight > 0) d.getDialogPane().setPrefHeight(prefHeight);
        makeResizable(d);
        return d;
    }

    /**
     * Makes any existing Dialog resizable by dragging its edges/corners — JavaFX dialogs
     * default to fixed-size. Call this on a dialog built the old way (`new Dialog<>()`)
     * without migrating it to styledDialog() yet.
     */
    public static void makeResizable(Dialog<?> dialog) {
        dialog.setResizable(true);
        // Also apply to the underlying Window directly — belt-and-braces, since some JavaFX
        // versions/skins have been inconsistent about Dialog.setResizable() alone taking
        // effect on every platform.
        Window window = dialog.getDialogPane().getScene() != null ? dialog.getDialogPane().getScene().getWindow() : null;
        if (window instanceof javafx.stage.Stage stage) {
            stage.setResizable(true);
        }
    }
}
