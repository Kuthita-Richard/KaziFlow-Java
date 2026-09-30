package com.kaziflow.services;

import com.kaziflow.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SpecialPermissions — the DATA-DRIVEN catalog of named, non-CRUD actions
 * (table {@code permission_actions}). The Positions editor renders one
 * checkbox per catalog row, so adding a new special action means
 * {@link #register} one row (or INSERT it) — no new UI code, and
 * {@link PermissionService#canPerform(String)} already handles any key.
 *
 * <p>The constants below are only identifiers for CALL SITES that enforce a
 * specific action (e.g. InventoryView asks {@code canPerform(VIEW_COST_PRICE)});
 * they are not a list of what exists — the table is.
 *
 * <p>Every seeded action below has a real enforcement point in the app. A
 * catalog entry nothing ever checks would be exactly the "settings UI that
 * saves data nothing reads" bug this project keeps hitting, so don't seed
 * an action without wiring at least one check.
 */
public final class SpecialPermissions {

    public static final String VIEW_COST_PRICE = "view_cost_price";
    public static final String APPLY_DISCOUNT  = "apply_discount";
    public static final String VOID_SALE       = "void_sale";

    /** Action keys: lowercase snake_case, so they are safe as identifiers in code and data. */
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_]{1,63}");

    public record Definition(String key, String label, String description, int sortOrder) {}

    /** Starting set, seeded once per install by {@link PermissionGroupSetup}. */
    static final List<Definition> DEFAULTS = List.of(
        new Definition(VOID_SALE, "Void sales / process returns",
            "Reverse a completed sale by processing a return and refund.", 10),
        new Definition(APPLY_DISCOUNT, "Apply discounts",
            "Redeem loyalty points as a discount at checkout.", 20),
        new Definition(VIEW_COST_PRICE, "View cost prices",
            "See what products cost to buy, and edit their cost price.", 30)
    );

    private SpecialPermissions() {}

    public static boolean isValidKey(String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }

    public static List<Definition> list() {
        try (Connection c = DatabaseManager.getInstance().getConnection()) {
            return list(c);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    static List<Definition> list(Connection c) throws SQLException {
        List<Definition> out = new ArrayList<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT action_key, label, description, sort_order FROM permission_actions ORDER BY sort_order, label")) {
            while (rs.next()) out.add(new Definition(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
        }
        return out;
    }

    /** Adds a new special action to the catalog (no-op if the key already exists). Returns true if a row was added. */
    public static boolean register(String key, String label, String description) throws SQLException {
        if (!isValidKey(key)) throw new IllegalArgumentException(
            "Action key must be lowercase letters, digits and underscores, starting with a letter (e.g. approve_refund).");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Action needs a label.");
        try (Connection c = DatabaseManager.getInstance().getConnection()) {
            return insertIfAbsent(c, new Definition(key, label.trim(), description, 1000));
        }
    }

    static boolean insertIfAbsent(Connection c, Definition d) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO permission_actions (action_key, label, description, sort_order) VALUES (?,?,?,?)")) {
            ps.setString(1, d.key());
            ps.setString(2, d.label());
            ps.setString(3, d.description());
            ps.setInt(4, d.sortOrder());
            return ps.executeUpdate() > 0;
        }
    }

    static void seedDefaults(Connection c) throws SQLException {
        for (Definition d : DEFAULTS) insertIfAbsent(c, d);
    }
}
