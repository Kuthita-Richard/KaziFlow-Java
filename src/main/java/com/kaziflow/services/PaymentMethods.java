package com.kaziflow.services;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.database.PaymentMethodMigration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Payment methods as master data (Settings → Payment Methods): what the till, purchases and expenses offer, how a method is
 * named, and how it behaves. Replaces five hardcoded lists that disagreed with each other (and a CHECK on {@code sales}).
 *
 * <p>A method has a stable {@code code} (what is stored on documents; never changes), a {@code label} (what people see), a
 * {@link Kind} (what it IS — "cash" gets the tendered/change screen) and an optional {@code integration} ("mpesa" triggers the STK
 * push). Methods are never deleted — old documents refer to their code — only deactivated. {@code cash} is the system method (the
 * default of a sale and of a refund) and cannot be deactivated.
 */
public final class PaymentMethods {
    private PaymentMethods() {}

    public enum Scope {
        SALES("for_sales"), PURCHASES("for_purchases"), EXPENSES("for_expenses");
        final String column;
        Scope(String column) { this.column = column; }
    }

    public enum Kind {
        CASH("cash", "Cash"), MOBILE_MONEY("mobile_money", "Mobile money"), CARD("card", "Card"), BANK("bank", "Bank transfer"),
        CREDIT("credit", "Credit (pay later)"), CHEQUE("cheque", "Cheque"), OTHER("other", "Other");
        public final String code, label;
        Kind(String code, String label) { this.code = code; this.label = label; }
        static Kind of(String code) { for (Kind k : values()) if (k.code.equals(code)) return k; return OTHER; }
        @Override public String toString() { return label; }
    }

    public record Method(String code, String label, Kind kind, String integration, boolean active, int sortOrder,
                         boolean forSales, boolean forPurchases, boolean forExpenses, boolean system) {
        public boolean in(Scope s) { return switch (s) { case SALES -> forSales; case PURCHASES -> forPurchases; case EXPENSES -> forExpenses; }; }
        /** ComboBox-friendly: shows the label. */
        @Override public String toString() { return label; }
    }

    private static java.util.function.BooleanSupplier salesReady = PaymentMethodMigration::isSalesTableMigrated;

    /** Tests only: replaces "has this database finished its payment-method upgrade?" (null restores the real check). */
    public static void setSalesReadyCheck(java.util.function.BooleanSupplier check) {
        salesReady = check == null ? PaymentMethodMigration::isSalesTableMigrated : check;
    }

    private static final String COLS = "code, label, kind, integration, active, sort_order, for_sales, for_purchases, for_expenses, is_system";

    /** Methods in display order. {@code scope == null}: all scopes. */
    public static List<Method> list(Scope scope, boolean activeOnly) {
        String sql = "SELECT " + COLS + " FROM payment_methods WHERE 1=1" + (scope == null ? "" : " AND " + scope.column + " = 1") + (activeOnly ? " AND active = 1" : "") + " ORDER BY sort_order, label";
        List<Method> out = new ArrayList<>();
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(read(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    public static List<Method> all() { return list(null, false); }

    /**
     * The method a stored value means. Accepts the code ("mpesa") and also the free-text spellings older purchases and expenses
     * stored ("M-Pesa", "bank transfer", "Cash"): case, spaces and punctuation are ignored, and a type name ("bank transfer") finds the
     * first method of that type even after it was renamed. Null if nothing matches.
     */
    public static Method find(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = normalise(raw);
        for (Method m : all()) if (m.code().equals(raw)) return m;
        List<Method> all = all();
        for (Method m : all) if (normalise(m.code()).equals(key) || normalise(m.label()).equals(key)) return m;
        // old free-text values name a TYPE of method ("bank transfer"); they must keep resolving after the owner renames the method
        for (Kind k : Kind.values()) if (normalise(k.label).equals(key) || normalise(k.code).equals(key)) for (Method m : all) if (m.kind() == k) return m;
        return null;
    }

    /** What to show for a stored value: the method's label, or the stored text itself when it is not a known method. */
    public static String label(String raw) {
        Method m = find(raw);
        return m != null ? m.label() : (raw == null ? "" : raw);
    }

    public static boolean isCash(String raw) { Method m = find(raw); return m != null && m.kind() == Kind.CASH; }
    public static boolean isMpesa(String raw) { Method m = find(raw); return m != null && "mpesa".equals(m.integration()); }

    /** Adds a method. Throws IllegalArgumentException with a message fit to show the user. */
    public static Method add(String code, String label, Kind kind, boolean sales, boolean purchases, boolean expenses) {
        code = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        label = label == null ? "" : label.trim();
        if (!code.matches("[a-z][a-z0-9_]{1,19}")) throw new IllegalArgumentException("The code must be 2–20 lowercase letters, digits or _ and start with a letter.");
        if (label.isEmpty() || label.length() > 30) throw new IllegalArgumentException("Give the method a name of up to 30 characters.");
        if (!sales && !purchases && !expenses) throw new IllegalArgumentException("Choose where it can be used (sales, purchases, expenses).");
        for (Method m : all()) {
            if (m.code().equals(code)) throw new IllegalArgumentException("There is already a payment method with the code '" + code + "'.");
            if (normalise(m.label()).equals(normalise(label))) throw new IllegalArgumentException("There is already a payment method called '" + m.label() + "'.");
        }
        if (sales && !salesReady.getAsBoolean())
            throw new IllegalArgumentException("This database has not finished its upgrade for custom sales payment methods (see the start-up log). It can be used for purchases and expenses only.");
        int sort = 10;
        try (Connection c = DatabaseManager.getInstance().getConnection()) {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(sort_order), 0) FROM payment_methods")) { if (rs.next()) sort = rs.getInt(1) + 10; }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO payment_methods (" + COLS + ") VALUES (?,?,?,NULL,1,?,?,?,?,0)")) {
                ps.setString(1, code); ps.setString(2, label); ps.setString(3, kind.code); ps.setInt(4, sort);
                ps.setInt(5, sales ? 1 : 0); ps.setInt(6, purchases ? 1 : 0); ps.setInt(7, expenses ? 1 : 0);
                ps.executeUpdate();
            }
        } catch (SQLException e) { throw new IllegalArgumentException("Could not save the payment method: " + e.getMessage()); }
        AuditLog.log("PAYMENT_METHOD_ADDED", "Added payment method " + label + " (" + code + ")");
        return find(code);
    }

    /** Changes the name, position and where a method is offered. The code and kind never change. */
    public static void update(String code, String label, int sortOrder, boolean sales, boolean purchases, boolean expenses) {
        Method m = find(code);
        if (m == null) throw new IllegalArgumentException("Unknown payment method.");
        label = label == null ? "" : label.trim();
        if (label.isEmpty() || label.length() > 30) throw new IllegalArgumentException("Give the method a name of up to 30 characters.");
        for (Method o : all()) if (!o.code().equals(m.code()) && normalise(o.label()).equals(normalise(label))) throw new IllegalArgumentException("There is already a payment method called '" + o.label() + "'.");
        if (m.system() && !(sales && purchases && expenses)) throw new IllegalArgumentException(m.label() + " is the system method and is always offered everywhere.");
        if (sales && !m.forSales() && !salesReady.getAsBoolean()) throw new IllegalArgumentException("This database has not finished its upgrade for custom sales payment methods.");
        if (!sales && !purchases && !expenses) throw new IllegalArgumentException("Choose where it can be used, or deactivate it instead.");
        writeChecked(m.code(), "UPDATE payment_methods SET label = ?, sort_order = ?, for_sales = ?, for_purchases = ?, for_expenses = ? WHERE code = ?",
                label, sortOrder, sales ? 1 : 0, purchases ? 1 : 0, expenses ? 1 : 0, m.code());
        AuditLog.log("PAYMENT_METHOD_CHANGED", "Changed payment method " + m.code() + ": name '" + label + "', order " + sortOrder);
    }

    /** Activates or deactivates (never deletes). Every scope must keep at least one active method. */
    public static void setActive(String code, boolean active) {
        Method m = find(code);
        if (m == null) throw new IllegalArgumentException("Unknown payment method.");
        if (!active && m.system()) throw new IllegalArgumentException(m.label() + " is the system method and cannot be deactivated.");
        writeChecked(m.code(), "UPDATE payment_methods SET active = ? WHERE code = ?", active ? 1 : 0, m.code());
        AuditLog.log(active ? "PAYMENT_METHOD_ACTIVATED" : "PAYMENT_METHOD_DEACTIVATED", (active ? "Activated " : "Deactivated ") + "payment method " + m.label() + " (" + m.code() + ")");
    }

    /** Runs one UPDATE and rolls it back if any scope would be left without an active method. */
    private static void writeChecked(String code, String sql, Object... params) {
        try (Connection c = DatabaseManager.getInstance().getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(sql)) { for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]); ps.executeUpdate(); }
                for (Scope s : Scope.values()) {
                    try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM payment_methods WHERE active = 1 AND " + s.column + " = 1")) {
                        if (rs.next() && rs.getInt(1) == 0) throw new IllegalArgumentException("At least one payment method must stay active for " + s.name().toLowerCase(Locale.ROOT) + ".");
                    }
                }
                c.commit();
            } catch (RuntimeException | SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new IllegalArgumentException("Could not save: " + e.getMessage()); }
    }

    private static Method read(ResultSet rs) throws SQLException {
        return new Method(rs.getString("code"), rs.getString("label"), Kind.of(rs.getString("kind")), rs.getString("integration"), rs.getInt("active") == 1,
                rs.getInt("sort_order"), rs.getInt("for_sales") == 1, rs.getInt("for_purchases") == 1, rs.getInt("for_expenses") == 1, rs.getInt("is_system") == 1);
    }

    private static String normalise(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
}
