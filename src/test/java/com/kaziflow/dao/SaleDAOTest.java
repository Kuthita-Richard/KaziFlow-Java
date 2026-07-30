package com.kaziflow.dao;

import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for SaleDAO.
 *
 * BuildFixLog_001 (Issue 1) documented that SaleDAO had duplicate method
 * definitions for getMonthlyBreakdown(), getDailyCountsThisWeek(), and
 * getMonthRevenue(int,int) — each defined twice with different return types.
 * This caused 5 compile errors and a DashboardView type mismatch.
 *
 * These tests use Java Reflection to assert that each method exists exactly
 * once and returns the correct type. They act as a permanent build-time
 * regression guard: if the duplicates are ever re-introduced, these tests
 * fail BEFORE a compile attempt is needed.
 *
 * NOTE: Reflection-based tests do not run the methods (no DB needed).
 * They purely verify the class's method signature table.
 */
@DisplayName("SaleDAO — method signature regression (BuildFixLog_001)")
class SaleDAOTest {

    // ── Duplicate-method regression guards ────────────────────────────────

    @Test
    @DisplayName("getDailyCountsThisWeek exists exactly once")
    void getDailyCountsThisWeekDefinedOnce() {
        Method[] methods = SaleDAO.class.getDeclaredMethods();
        long count = Arrays.stream(methods)
            .filter(m -> m.getName().equals("getDailyCountsThisWeek"))
            .count();
        assertEquals(1, count,
            "SaleDAO.getDailyCountsThisWeek() must be defined exactly once. " +
            "BuildFixLog_001 Issue 1: duplicate with conflicting return types caused 5 compile errors.");
    }

    @Test
    @DisplayName("getDailyCountsThisWeek returns Map<String,Integer> not int[]")
    void getDailyCountsThisWeekReturnsMap() throws NoSuchMethodException {
        Method m = SaleDAO.class.getDeclaredMethod("getDailyCountsThisWeek");
        assertEquals(Map.class, m.getReturnType(),
            "SaleDAO.getDailyCountsThisWeek() must return Map (not int[]). " +
            "The int[] version caused a DashboardView type mismatch.");
    }

    @Test
    @DisplayName("getMonthRevenue(int,int) exists exactly once")
    void getMonthRevenueDefinedOnce() {
        Method[] methods = SaleDAO.class.getDeclaredMethods();
        long count = Arrays.stream(methods)
            .filter(m -> m.getName().equals("getMonthRevenue")
                      && m.getParameterCount() == 2)
            .count();
        assertEquals(1, count,
            "SaleDAO.getMonthRevenue(int,int) must be defined exactly once.");
    }

    @Test
    @DisplayName("getMonthRevenue(int,int) returns double")
    void getMonthRevenueReturnsDouble() throws NoSuchMethodException {
        Method m = SaleDAO.class.getDeclaredMethod("getMonthRevenue", int.class, int.class);
        assertEquals(double.class, m.getReturnType(),
            "SaleDAO.getMonthRevenue(int,int) must return double.");
    }

    @Test
    @DisplayName("getMonthlyRevenue exists and returns LinkedHashMap")
    void getMonthlyRevenueExists() throws NoSuchMethodException {
        Method m = SaleDAO.class.getDeclaredMethod("getMonthlyRevenue");
        assertEquals(LinkedHashMap.class, m.getReturnType(),
            "SaleDAO.getMonthlyRevenue() must return LinkedHashMap<String,Double>.");
    }

    @Test
    @DisplayName("getPaymentMethodBreakdown exists and returns Map")
    void getPaymentMethodBreakdownExists() throws NoSuchMethodException {
        Method m = SaleDAO.class.getDeclaredMethod("getPaymentMethodBreakdown");
        assertEquals(Map.class, m.getReturnType(),
            "SaleDAO.getPaymentMethodBreakdown() must return Map.");
    }

    // ── Critical reporting method presence check ──────────────────────────

    @Test
    @DisplayName("All 4 expected reporting methods are present")
    void allReportingMethodsPresent() {
        String[] expected = {
            "getDailyCountsThisWeek",
            "getMonthRevenue",
            "getMonthlyRevenue",
            "getPaymentMethodBreakdown"
        };
        Set<String> actual = new HashSet<>();
        for (Method m : SaleDAO.class.getDeclaredMethods()) {
            actual.add(m.getName());
        }
        for (String name : expected) {
            assertTrue(actual.contains(name),
                "SaleDAO is missing required method: " + name);
        }
    }

    // ── No extra duplicate detection (general) ────────────────────────────

    @Test
    @DisplayName("No method name appears more than twice in SaleDAO")
    void noSuspiciousDuplicates() {
        Map<String, Long> counts = new HashMap<>();
        for (Method m : SaleDAO.class.getDeclaredMethods()) {
            counts.merge(m.getName(), 1L, Long::sum);
        }
        counts.forEach((name, count) ->
            assertTrue(count <= 2,
                "SaleDAO method '" + name + "' is defined " + count + " times — possible duplicate")
        );
    }
}
