package com.kaziflow.utils;

import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;

/**
 * PasswordFieldUtils — the ONE place that gives any {@link PasswordField} a
 * show/hide "eye" toggle. Same idea as {@link DialogUtils}: a concern that
 * repeats across many screens (login, user management, settings, API keys,
 * database passphrase...) lives in one reusable component, so changing how
 * it looks or behaves is a one-file change.
 *
 * <h3>Usage</h3>
 * Build and configure the PasswordField exactly as before (prompt text,
 * style, style classes, onAction, pref size), then put the RETURNED node in
 * the layout instead of the field itself:
 * <pre>
 *   PasswordField pf = new PasswordField();
 *   pf.setPromptText("Password");
 *   form.addRow(2, lbl("Password"), PasswordFieldUtils.withToggle(pf));
 *   ...
 *   String typed = pf.getText();   // the original field stays the source of truth
 * </pre>
 *
 * <h3>How it works</h3>
 * A plain TextField "mirror" is bound to the PasswordField (text both ways;
 * prompt text, style, style classes, editable, disable, pref size and the
 * onAction handler one way), so callers keep reading and configuring the
 * PasswordField and never need to know the mirror exists. Only one of the two
 * is visible at a time. The toggle icon shows an open eye when the password is
 * hidden ("click to show") and a crossed eye when it is visible ("click to hide").
 *
 * <p>The toggle button is deliberately not focus-traversable, so Tab moves
 * from the field straight to the next control as it always did.
 *
 * <p>Because the field that is not showing cannot take keyboard focus, code
 * that needs to move focus into the field programmatically should call
 * {@link #requestFocus(PasswordField)} rather than {@code pf.requestFocus()};
 * likewise {@link #setRevealed(PasswordField, boolean)} lets code re-conceal
 * (e.g. after a failed login). Both are safe no-ops/fallbacks on a field
 * that was never wrapped.
 */
public final class PasswordFieldUtils {

    private static final String CONTROLLER_KEY = "kaziflow.passwordToggle";
    private static final Color ICON_COLOR = Color.web("#64748b");

    private PasswordFieldUtils() { }

    /** Wraps {@code pf} with a show/hide eye toggle. Put the returned node in the layout. */
    public static HBox withToggle(PasswordField pf) {
        TextField plain = new TextField();

        // Mirror: text both ways, everything else one way, so callers only ever touch pf.
        plain.textProperty().bindBidirectional(pf.textProperty());
        plain.promptTextProperty().bind(pf.promptTextProperty());
        plain.styleProperty().bind(pf.styleProperty());
        plain.editableProperty().bind(pf.editableProperty());
        plain.disableProperty().bind(pf.disableProperty());
        plain.prefWidthProperty().bind(pf.prefWidthProperty());
        plain.prefHeightProperty().bind(pf.prefHeightProperty());
        plain.onActionProperty().bind(pf.onActionProperty());
        copyStyleClasses(pf, plain);
        pf.getStyleClass().addListener((ListChangeListener<String>) c -> copyStyleClasses(pf, plain));

        pf.setMaxWidth(Double.MAX_VALUE);
        plain.setMaxWidth(Double.MAX_VALUE);
        plain.setVisible(false);
        plain.setManaged(false);

        StackPane stack = new StackPane(pf, plain);
        HBox.setHgrow(stack, Priority.ALWAYS);

        // Icon drawn from plain shapes (no font/emoji/SVG-path dependency, renders identically everywhere).
        Ellipse eye = new Ellipse(9, 5.5);
        eye.setFill(Color.TRANSPARENT);
        eye.setStroke(ICON_COLOR);
        eye.setStrokeWidth(1.6);
        Circle pupil = new Circle(2.6, ICON_COLOR);
        Line slash = new Line(-8, 8, 8, -8);
        slash.setStroke(ICON_COLOR);
        slash.setStrokeWidth(1.8);
        slash.setVisible(false);
        Group icon = new Group(eye, pupil, slash);

        Tooltip tip = new Tooltip("Show password");
        Button toggle = new Button();
        toggle.setGraphic(icon);
        toggle.setTooltip(tip);
        toggle.setFocusTraversable(false);
        toggle.setMinWidth(38);
        toggle.setMaxHeight(Double.MAX_VALUE);
        toggle.setStyle("-fx-background-color:#f8fafc;-fx-border-color:#e2e8f0;-fx-border-width:1 1 1 0;"
                + "-fx-border-radius:0 6 6 0;-fx-background-radius:0 6 6 0;-fx-cursor:hand;-fx-padding:0 8;");
        toggle.disableProperty().bind(pf.disableProperty());

        HBox box = new HBox(0, stack, toggle);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setFillHeight(true);
        HBox.setHgrow(box, Priority.ALWAYS);

        Controller controller = new Controller(pf, plain, slash, tip);
        pf.getProperties().put(CONTROLLER_KEY, controller);
        toggle.setOnAction(e -> {
            controller.setRevealed(!controller.revealed);
            controller.focus();
        });
        return box;
    }

    /** True if {@code pf} was wrapped with {@link #withToggle} and its text is currently shown. */
    public static boolean isRevealed(PasswordField pf) {
        Controller c = controllerOf(pf);
        return c != null && c.revealed;
    }

    /** Show or hide the text of a wrapped field. No-op if {@code pf} was never wrapped. */
    public static void setRevealed(PasswordField pf, boolean revealed) {
        Controller c = controllerOf(pf);
        if (c != null) c.setRevealed(revealed);
    }

    /** Moves keyboard focus to whichever of the two inputs is showing (plain {@code pf.requestFocus()} if never wrapped). */
    public static void requestFocus(PasswordField pf) {
        Controller c = controllerOf(pf);
        if (c != null) c.focus(); else pf.requestFocus();
    }

    private static Controller controllerOf(PasswordField pf) {
        Object o = pf.getProperties().get(CONTROLLER_KEY);
        return o instanceof Controller c ? c : null;
    }

    private static void copyStyleClasses(PasswordField from, TextField to) {
        for (String cls : from.getStyleClass()) {
            if (!"password-field".equals(cls) && !to.getStyleClass().contains(cls)) {
                to.getStyleClass().add(cls);
            }
        }
    }

    /** Holds the state of one wrapped field. */
    private static final class Controller {
        private final PasswordField pf;
        private final TextField plain;
        private final Line slash;
        private final Tooltip tip;
        private boolean revealed = false;

        Controller(PasswordField pf, TextField plain, Line slash, Tooltip tip) {
            this.pf = pf; this.plain = plain; this.slash = slash; this.tip = tip;
        }

        void setRevealed(boolean show) {
            if (show == revealed) return;
            revealed = show;
            plain.setVisible(show);
            plain.setManaged(show);
            pf.setVisible(!show);
            pf.setManaged(!show);
            slash.setVisible(show);
            tip.setText(show ? "Hide password" : "Show password");
        }

        void focus() {
            TextField active = revealed ? plain : pf;
            active.requestFocus();
            active.positionCaret(active.getText() == null ? 0 : active.getText().length());
        }
    }
}
