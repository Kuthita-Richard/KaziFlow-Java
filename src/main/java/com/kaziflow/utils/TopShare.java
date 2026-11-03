package com.kaziflow.utils;

import java.util.Map;

/**
 * The largest entry of a name→amount map and its share of the whole — the one place "top category / top payment
 * method / % of total" is computed, so a screen never has to type such a figure. Pure (no UI, no database).
 * {@link #of} returns {@code null} when there is nothing to rank (empty map or a total of zero or less).
 */
public record TopShare(String key, double value, double share, double total) {

    public static TopShare of(Map<String, ? extends Number> amounts) {
        if (amounts == null || amounts.isEmpty()) return null;
        double total = 0;
        String bestKey = null;
        double best = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, ? extends Number> e : amounts.entrySet()) {
            double v = e.getValue() == null ? 0 : e.getValue().doubleValue();
            total += v;
            if (v > best) { best = v; bestKey = e.getKey(); }
        }
        if (bestKey == null || total <= 0 || best <= 0) return null;
        return new TopShare(bestKey, best, best / total * 100.0, total);
    }

    /** Whole-number percentage text, e.g. "56%". */
    public String percentText() { return String.format(java.util.Locale.ROOT, "%.0f%%", share); }
}
