package com.kaziflow.utils;

import com.kaziflow.services.PaymentMethods;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

/**
 * The one payment-method picker. Its VALUE is the method's code (what gets stored), what people SEE is its label; the choices come
 * from the payment_methods master data (Settings → Payment Methods), never from a typed list.
 */
public final class PaymentMethodCombo {
    private PaymentMethodCombo() {}

    /** The "everything" entry of a filter box. */
    public static final String ALL = "*";

    /** Active methods offered for a scope; {@code initial} is preselected when it is among them (otherwise the first one). */
    public static ComboBox<String> create(PaymentMethods.Scope scope, String initial) {
        ComboBox<String> cb = new ComboBox<>();
        for (PaymentMethods.Method m : PaymentMethods.list(scope, true)) cb.getItems().add(m.code());
        cb.setConverter(converter(null));
        cb.setValue(cb.getItems().contains(initial) ? initial : (cb.getItems().isEmpty() ? null : cb.getItems().get(0)));
        return cb;
    }

    /** A filter box: {@link #ALL} (shown as {@code allLabel}) first, then every method — inactive ones too, old documents still carry them. */
    public static ComboBox<String> filter(String allLabel) {
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().add(ALL);
        for (PaymentMethods.Method m : PaymentMethods.all()) cb.getItems().add(m.code());
        cb.setConverter(converter(allLabel));
        cb.setValue(ALL);
        return cb;
    }

    private static StringConverter<String> converter(String allLabel) {
        return new StringConverter<>() {
            @Override public String toString(String code) { return code == null ? "" : (ALL.equals(code) ? allLabel : PaymentMethods.label(code)); }
            @Override public String fromString(String s) { return s; }
        };
    }
}
