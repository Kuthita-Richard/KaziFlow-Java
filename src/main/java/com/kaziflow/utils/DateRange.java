package com.kaziflow.utils;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * DateRange — an INCLUSIVE period of days ("this month", "last quarter", "1 Mar – 15 Apr"), with open ends allowed
 * ({@link #ALL} = no limit). One definition used by exports now and by every analytics page and report next
 * (docs/design/product-depth-plan.md §5: a period control on every page, with "compare with previous period").
 *
 * <p>Pure JDK. Weeks run Monday–Sunday and quarters are calendar quarters. {@code today} is always passed in, so
 * every preset is testable on a fixed date.
 *
 * <p>SQL: {@link #whereClause(String)} + {@link #bind} produce a bound-parameter filter on a date/datetime column
 * ({@code date(col)} is applied so "2026-03-31 18:45:00" is inside a range ending on 2026-03-31).
 */
public record DateRange(LocalDate from, LocalDate to) {

    public static final DateRange ALL = new DateRange(null, null);
    private static final DateTimeFormatter NICE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public DateRange {
        if (from != null && to != null && from.isAfter(to))
            throw new IllegalArgumentException("The start date (" + from + ") is after the end date (" + to + ").");
    }

    public enum Preset {
        TODAY("Today"), YESTERDAY("Yesterday"), THIS_WEEK("This week (Mon–Sun)"), LAST_WEEK("Last week"),
        THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_QUARTER("This quarter"), LAST_QUARTER("Last quarter"),
        THIS_YEAR("This year"), LAST_YEAR("Last year"), LAST_7_DAYS("Last 7 days"), LAST_30_DAYS("Last 30 days"),
        ALL_TIME("All time"), CUSTOM("Custom dates…");
        private final String label;
        Preset(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }

    /** The range a preset means on {@code today}. {@link Preset#CUSTOM} has no fixed range; it returns {@link #ALL}. */
    public static DateRange of(Preset p, LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDate quarterStart = today.withDayOfMonth(1).withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1);
        return switch (p) {
            case TODAY -> new DateRange(today, today);
            case YESTERDAY -> new DateRange(today.minusDays(1), today.minusDays(1));
            case THIS_WEEK -> new DateRange(monday, monday.plusDays(6));
            case LAST_WEEK -> new DateRange(monday.minusWeeks(1), monday.minusDays(1));
            case THIS_MONTH -> new DateRange(firstOfMonth, firstOfMonth.with(TemporalAdjusters.lastDayOfMonth()));
            case LAST_MONTH -> new DateRange(firstOfMonth.minusMonths(1), firstOfMonth.minusDays(1));
            case THIS_QUARTER -> new DateRange(quarterStart, quarterStart.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth()));
            case LAST_QUARTER -> new DateRange(quarterStart.minusMonths(3), quarterStart.minusDays(1));
            case THIS_YEAR -> new DateRange(today.withDayOfYear(1), today.with(TemporalAdjusters.lastDayOfYear()));
            case LAST_YEAR -> new DateRange(today.withDayOfYear(1).minusYears(1), today.withDayOfYear(1).minusDays(1));
            case LAST_7_DAYS -> new DateRange(today.minusDays(6), today);
            case LAST_30_DAYS -> new DateRange(today.minusDays(29), today);
            case ALL_TIME, CUSTOM -> ALL;
        };
    }

    public boolean isAll() { return from == null && to == null; }

    public boolean contains(LocalDate d) {
        return (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to));
    }

    /** Number of days covered, or -1 if open-ended. */
    public long days() { return from == null || to == null ? -1 : ChronoUnit.DAYS.between(from, to) + 1; }

    /**
     * The period to compare against: a whole calendar month/quarter/year → the previous calendar one; anything else →
     * the same number of days immediately before. {@link #ALL} and open-ended ranges have no previous period (returns ALL).
     */
    public DateRange previousPeriod() {
        if (from == null || to == null) return ALL;
        boolean monthAligned = from.getDayOfMonth() == 1 && to.equals(to.with(TemporalAdjusters.lastDayOfMonth()));
        if (monthAligned) {
            long months = ChronoUnit.MONTHS.between(from, to.plusDays(1));
            if (months >= 1) return new DateRange(from.minusMonths(months), from.minusDays(1));
        }
        long n = days();
        return new DateRange(from.minusDays(n), from.minusDays(1));
    }

    /** The same dates one year earlier (Feb 29 → Feb 28). */
    public DateRange sameLastYear() {
        return new DateRange(from == null ? null : from.minusYears(1), to == null ? null : to.minusYears(1));
    }

    /** "All time", "1 Mar 2026 – 31 Mar 2026", "From 1 Mar 2026", "Up to 31 Mar 2026". */
    public String label() {
        if (isAll()) return "All time";
        if (from == null) return "Up to " + NICE.format(to);
        if (to == null) return "From " + NICE.format(from);
        return from.equals(to) ? NICE.format(from) : NICE.format(from) + " – " + NICE.format(to);
    }

    /** Safe to put in a file name. */
    public String fileSuffix() {
        if (isAll()) return "all-time";
        if (from == null) return "until_" + to;
        if (to == null) return "from_" + from;
        return from.equals(to) ? from.toString() : from + "_to_" + to;
    }

    /** " AND date(col) >= ? AND date(col) <= ?" (only the bounds that exist). {@code column} must be a trusted identifier. */
    public String whereClause(String column) {
        if (!column.matches("[A-Za-z_][A-Za-z0-9_.]*")) throw new IllegalArgumentException("Bad column: " + column);
        StringBuilder sb = new StringBuilder();
        if (from != null) sb.append(" AND date(").append(column).append(") >= ?");
        if (to != null) sb.append(" AND date(").append(column).append(") <= ?");
        return sb.toString();
    }

    /** Binds the values for {@link #whereClause} starting at {@code index}; returns the next free index. */
    public int bind(PreparedStatement ps, int index) throws SQLException {
        if (from != null) ps.setString(index++, from.toString());
        if (to != null) ps.setString(index++, to.toString());
        return index;
    }

    /** "November 2026" — the label for a calendar month, so no screen types a month name. */
    public static String monthLabel(java.time.YearMonth ym) {
        return ym.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear();
    }
}
