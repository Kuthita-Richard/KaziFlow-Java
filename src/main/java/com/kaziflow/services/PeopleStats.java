package com.kaziflow.services;

import com.kaziflow.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

/**
 * Attendance and leave headline counts, read from the attendance and leave_requests tables — the one place the People
 * screens get these figures, so none of them has to type a number. Dates are stored as ISO text (yyyy-MM-dd).
 */
public final class PeopleStats {
    private PeopleStats() {}

    /** Attendance rows on a date with the given status ("present", "absent", "late", "on-leave"). */
    public static int attendanceCount(LocalDate date, String status) {
        return count("SELECT COUNT(*) FROM attendance WHERE date = ? AND status = ?", date.toString(), status);
    }

    /** Attendance rows of any status on a date: 0 means nobody has been marked yet today. */
    public static int attendanceMarked(LocalDate date) {
        return count("SELECT COUNT(*) FROM attendance WHERE date = ?", date.toString());
    }

    /** Leave requests with a status whose leave starts in the given year. */
    public static int leaveCount(String status, int year) {
        return count("SELECT COUNT(*) FROM leave_requests WHERE status = ? AND substr(start_date, 1, 4) = ?", status, String.valueOf(year));
    }

    /** Leave requests of any year with a status (e.g. everything still pending). */
    public static int leaveCount(String status) {
        return count("SELECT COUNT(*) FROM leave_requests WHERE status = ?", status);
    }

    /** Employees on approved leave on a date. */
    public static int onLeaveOn(LocalDate date) {
        return count("SELECT COUNT(*) FROM leave_requests WHERE status = 'approved' AND start_date <= ? AND end_date >= ?", date.toString(), date.toString());
    }

    /** Total days of approved leave starting in the given year. */
    public static int approvedLeaveDays(int year) {
        return count("SELECT COALESCE(SUM(days_count), 0) FROM leave_requests WHERE status = 'approved' AND substr(start_date, 1, 4) = ?", String.valueOf(year));
    }

    private static int count(String sql, String... params) {
        try (Connection c = DatabaseManager.getInstance().getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }
}
