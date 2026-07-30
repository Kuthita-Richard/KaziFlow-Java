package com.kaziflow.utils;

import com.kaziflow.services.CurrencyService;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Despite the name (kept for backward compatibility with the ~100 existing call
 * sites across the app), this formatter is currency-aware as of Phase 10E:
 * format()/formatShort() convert the KES amount passed in to whatever display
 * currency is active in Settings, via CurrencyService. All stored amounts
 * remain KES — only the label shown here changes.
 *
 * formatNumber() deliberately stays currency-agnostic: it's used for plain
 * quantities (stock counts, chart axis ticks) as well as money, so converting
 * it here would silently misconvert non-monetary numbers.
 */
public class KESFormatter {

    private static final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);

    static {
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(2);
    }

    public static String format(double kesAmount) {
        CurrencyService currency = CurrencyService.getInstance();
        double displayAmount = currency.toDisplayAmount(kesAmount);
        return currency.getActiveSymbol() + " " + formatter.format(displayAmount);
    }

    public static String formatShort(double kesAmount) {
        CurrencyService currency = CurrencyService.getInstance();
        double amount = currency.toDisplayAmount(kesAmount);
        String symbol = currency.getActiveSymbol();
        if (amount >= 1_000_000) {
            return String.format("%s %.1fM", symbol, amount / 1_000_000);
        } else if (amount >= 1_000) {
            return String.format("%s %.1fK", symbol, amount / 1_000);
        }
        return format(kesAmount);
    }

    public static String formatNumber(double amount) {
        return formatter.format(amount);
    }
}
