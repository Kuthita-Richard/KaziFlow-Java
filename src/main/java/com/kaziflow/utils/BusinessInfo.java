package com.kaziflow.utils;

import com.kaziflow.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * BusinessInfo — facts about the BUSINESS using the software (its own name, address, PIN), read from the
 * {@code settings} table. Not to be confused with {@link com.kaziflow.brand.Brand}, which is the identity of the
 * software vendor. Anything a customer sees that speaks FOR the business (a debtor SMS, a receipt heading) uses
 * this; anything that speaks about the software uses Brand.
 *
 * <p>Pure JDK. Returns {@code fallback} if the setting is missing or the database can't be read.
 */
public final class BusinessInfo {

    private BusinessInfo() {}

    public static String get(String key, String fallback) {
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getString(1) != null && !rs.getString(1).isBlank()) return rs.getString(1).trim();
        } catch (Exception ignored) { }
        return fallback;
    }

    /** The business's own name, or {@code fallback} if it hasn't been set. */
    public static String name(String fallback) { return get("business_name", fallback); }
}
