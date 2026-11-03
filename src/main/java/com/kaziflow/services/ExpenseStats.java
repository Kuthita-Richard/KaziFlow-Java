package com.kaziflow.services;

import com.kaziflow.models.Expense;
import com.kaziflow.utils.TopShare;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Headline figures for the expense cards, computed only from the expense rows themselves (pure: no UI, no database),
 * so the Finance and Expenses screens show the same numbers and neither has to invent one.
 *
 * {@code avgPerMonthLast12} = spend in the last 12 calendar months (including the current one) ÷ 12; null when there is
 * no spend in that window. {@code topCategory} is null when there are no expenses.
 */
public record ExpenseStats(double total, double thisMonth, TopShare topCategory, Double avgPerMonthLast12, int count) {

    public static ExpenseStats of(List<Expense> all, LocalDate today) {
        double total = 0, month = 0, window = 0;
        YearMonth now = YearMonth.from(today), first = now.minusMonths(11);
        Map<String, Double> byCategory = new LinkedHashMap<>();
        int n = 0;
        if (all != null) for (Expense e : all) {
            if (e == null) continue;
            n++;
            double a = e.getAmount();
            total += a;
            String cat = e.getCategory() == null || e.getCategory().isBlank() ? "Uncategorised" : e.getCategory();
            byCategory.merge(cat, a, Double::sum);
            if (e.getExpenseDate() != null) {
                YearMonth ym = YearMonth.from(e.getExpenseDate());
                if (ym.equals(now)) month += a;
                if (!ym.isBefore(first) && !ym.isAfter(now)) window += a;
            }
        }
        return new ExpenseStats(total, month, TopShare.of(byCategory), window > 0 ? window / 12.0 : null, n);
    }

    /**
     * This month's spend per category, largest first. If there are more than {@code maxRows} categories the smallest are
     * folded into "Other" so the result never has more than {@code maxRows} entries (minimum 2). Empty when nothing was spent.
     */
    public static Map<String, Double> monthByCategory(List<Expense> all, LocalDate today, int maxRows) {
        YearMonth now = YearMonth.from(today);
        Map<String, Double> by = new LinkedHashMap<>();
        if (all != null) for (Expense e : all) {
            if (e == null || e.getExpenseDate() == null || !YearMonth.from(e.getExpenseDate()).equals(now)) continue;
            String cat = e.getCategory() == null || e.getCategory().isBlank() ? "Uncategorised" : e.getCategory();
            by.merge(cat, e.getAmount(), Double::sum);
        }
        List<Map.Entry<String, Double>> sorted = new java.util.ArrayList<>(by.entrySet());
        sorted.sort((a, b) -> { int c = Double.compare(b.getValue(), a.getValue()); return c != 0 ? c : a.getKey().compareTo(b.getKey()); });
        int keep = Math.max(2, maxRows) - 1;
        Map<String, Double> out = new LinkedHashMap<>();
        double rest = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.size() > keep + 1 && i >= keep) rest += sorted.get(i).getValue();
            else out.put(sorted.get(i).getKey(), sorted.get(i).getValue());
        }
        if (rest > 0) out.put("Other", rest);
        return out;
    }
}
