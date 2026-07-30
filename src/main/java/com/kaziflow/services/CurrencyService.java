package com.kaziflow.services;

import com.kaziflow.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CurrencyService — multi-currency *display* support (Phase 10E).
 *
 * Design decision: every monetary amount in the database (sales, purchases,
 * expenses, etc.) stays stored in KES — that never changes. This service only
 * converts KES → the user's chosen display currency at the moment of
 * formatting, using an editable exchange rate. That keeps every existing
 * calculation (VAT, totals, reports, loyalty points) correct and untouched;
 * only the label shown to the user changes.
 *
 * Singleton with an in-memory cache refreshed from the settings table, so
 * KESFormatter (called from dozens of views) doesn't hit the DB on every
 * single format() call.
 */
public class CurrencyService {

    public record CurrencyInfo(String code, String symbol, String label) {}

    private static final Map<String, CurrencyInfo> SUPPORTED = new LinkedHashMap<>();
    static {
        SUPPORTED.put("KES", new CurrencyInfo("KES", "KES", "Kenyan Shilling"));
        SUPPORTED.put("USD", new CurrencyInfo("USD", "$", "US Dollar"));
        SUPPORTED.put("EUR", new CurrencyInfo("EUR", "€", "Euro"));
    }

    private static CurrencyService instance;
    public static synchronized CurrencyService getInstance() {
        if (instance == null) instance = new CurrencyService();
        return instance;
    }

    private String activeCurrency = "KES";
    private double usdRate = 1.0 / 130.0; // 1 KES = this many USD, i.e. ~130 KES per USD — a sane default, always user-editable
    private double eurRate = 1.0 / 140.0; // 1 KES = this many EUR, i.e. ~140 KES per EUR

    private CurrencyService() {
        reload();
    }

    /** Re-reads currency + exchange rate settings from the DB. Call after Settings saves a change. */
    public void reload() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT key, value FROM settings WHERE key IN "
                 + "('currency','exchange_rate_usd','exchange_rate_eur')")) {
            while (rs.next()) {
                String key = rs.getString("key");
                String value = rs.getString("value");
                if (value == null) continue;
                switch (key) {
                    case "currency" -> { if (SUPPORTED.containsKey(value)) activeCurrency = value; }
                    case "exchange_rate_usd" -> { try { usdRate = Double.parseDouble(value); } catch (NumberFormatException ignored) {} }
                    case "exchange_rate_eur" -> { try { eurRate = Double.parseDouble(value); } catch (NumberFormatException ignored) {} }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void saveSettings(String currency, double usdRate, double eurRate) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?,?)")) {
            ps.setString(1, "currency"); ps.setString(2, currency); ps.executeUpdate();
            ps.setString(1, "exchange_rate_usd"); ps.setString(2, String.valueOf(usdRate)); ps.executeUpdate();
            ps.setString(1, "exchange_rate_eur"); ps.setString(2, String.valueOf(eurRate)); ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
        reload();
    }

    public String getActiveCurrency() { return activeCurrency; }
    public double getUsdRate() { return usdRate; }
    public double getEurRate() { return eurRate; }
    public Map<String, CurrencyInfo> getSupportedCurrencies() { return SUPPORTED; }

    /** Converts a KES amount (the DB's base currency) into the active display currency. */
    public double toDisplayAmount(double kesAmount) {
        return switch (activeCurrency) {
            case "USD" -> kesAmount * usdRate;
            case "EUR" -> kesAmount * eurRate;
            default -> kesAmount;
        };
    }

    public String getActiveSymbol() {
        return SUPPORTED.getOrDefault(activeCurrency, SUPPORTED.get("KES")).symbol();
    }
}
