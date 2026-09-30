// Manual runtime check (needs a display: run under xvfb-run — see tools/sandbox-verify.sh).
// Not part of the JUnit suite on purpose: constructing JavaFX controls needs a started toolkit.
import com.kaziflow.utils.PasswordFieldUtils;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.event.ActionEvent;
import java.util.concurrent.atomic.AtomicInteger;

public class PasswordFieldUtilsCheck {
    static int pass = 0, fail = 0;
    static void check(String name, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + name); if (ok) pass++; else fail++; }
    public static void main(String[] a) throws Exception {
        javafx.application.Platform.startup(() -> {});
        javafx.application.Platform.runLater(() -> run());
        Thread.sleep(600000);
    }
    static void run() {
        PasswordField pf = new PasswordField();
        pf.setPromptText("Password");
        pf.setStyle("-fx-font-size:13px;");
        pf.getStyleClass().add("login-field");
        pf.setPrefWidth(380);
        AtomicInteger fired = new AtomicInteger();
        HBox box = PasswordFieldUtils.withToggle(pf);
        pf.setOnAction(e -> fired.incrementAndGet());   // set AFTER wrapping: must still propagate

        StackPane stack = (StackPane) box.getChildren().get(0);
        Button toggle = (Button) box.getChildren().get(1);
        TextField plain = (TextField) stack.getChildren().get(1);

        check("initially concealed (pf visible/managed, mirror hidden/unmanaged)",
              pf.isVisible() && pf.isManaged() && !plain.isVisible() && !plain.isManaged());
        check("not revealed initially", !PasswordFieldUtils.isRevealed(pf));
        pf.setText("secret1");
        check("pf -> mirror text", "secret1".equals(plain.getText()));
        plain.setText("secret2");
        check("mirror -> pf text (source of truth stays pf)", "secret2".equals(pf.getText()));
        check("prompt text mirrored", "Password".equals(plain.getPromptText()));
        pf.setPromptText("Changed later");
        check("prompt text follows later change", "Changed later".equals(plain.getPromptText()));
        check("style mirrored", "-fx-font-size:13px;".equals(plain.getStyle()));
        check("custom style class copied", plain.getStyleClass().contains("login-field"));
        check("password-field class NOT copied to mirror", !plain.getStyleClass().contains("password-field"));
        pf.getStyleClass().add("late-class");
        check("style class added later is copied", plain.getStyleClass().contains("late-class"));
        check("pref width mirrored", plain.getPrefWidth() == 380);

        toggle.fire();
        check("after click: revealed", PasswordFieldUtils.isRevealed(pf));
        check("after click: mirror visible/managed, pf hidden/unmanaged",
              plain.isVisible() && plain.isManaged() && !pf.isVisible() && !pf.isManaged());
        check("tooltip says Hide", "Hide password".equals(toggle.getTooltip().getText()));
        plain.setText("typed while revealed");
        check("typing while revealed reaches pf", "typed while revealed".equals(pf.getText()));
        plain.fireEvent(new ActionEvent());
        check("Enter in revealed mirror fires pf's onAction", fired.get() == 1);

        toggle.fire();
        check("second click conceals again", !PasswordFieldUtils.isRevealed(pf) && pf.isVisible() && !plain.isVisible());
        check("text preserved across toggling", "typed while revealed".equals(pf.getText()));
        check("tooltip says Show", "Show password".equals(toggle.getTooltip().getText()));

        PasswordFieldUtils.setRevealed(pf, true);
        check("setRevealed(true) works programmatically", PasswordFieldUtils.isRevealed(pf) && plain.isVisible());
        PasswordFieldUtils.setRevealed(pf, false);
        check("setRevealed(false) re-conceals", !PasswordFieldUtils.isRevealed(pf) && pf.isVisible());

        pf.setDisable(true);
        check("disable propagates to mirror and toggle", plain.isDisable() && toggle.isDisable());
        pf.setDisable(false);
        pf.setEditable(false);
        check("editable propagates", !plain.isEditable());

        PasswordField raw = new PasswordField();
        check("isRevealed on never-wrapped field is false", !PasswordFieldUtils.isRevealed(raw));
        PasswordFieldUtils.setRevealed(raw, true);
        PasswordFieldUtils.requestFocus(raw);
        check("helpers are safe no-ops on never-wrapped field", true);

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
