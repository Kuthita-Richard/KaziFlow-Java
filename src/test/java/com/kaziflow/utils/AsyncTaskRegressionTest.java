package com.kaziflow.utils;

import com.kaziflow.dao.PurchaseDAO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the two most impactful BuildFixLog_001 fixes:
 *
 *   Issue 2 — AsyncTask.run() ambiguous overload (23 compile errors across 15 files)
 *     Fix: removed the unused run(Runnable, Runnable, Consumer<String>) overload.
 *
 *   Issue 7 — PurchaseDAO.generatePurchaseNumber() private + required Connection arg
 *     Fix: added public String generatePurchaseNumber() no-arg overload.
 *
 * Like SaleDAOTest, these use Java Reflection — no DB required.
 */
@DisplayName("API regression — BuildFixLog_001 Issues 2 and 7")
class AsyncTaskRegressionTest {

    // ── Issue 2: AsyncTask.run() must have exactly ONE public overload ────

    @Test
    @DisplayName("AsyncTask has exactly 1 public 'run' overload (ambiguity fix)")
    void asyncTaskHasExactlyOneRunOverload() {
        long count = Arrays.stream(AsyncTask.class.getDeclaredMethods())
            .filter(m -> m.getName().equals("run") && java.lang.reflect.Modifier.isPublic(m.getModifiers()))
            .count();
        assertEquals(1, count,
            "AsyncTask must have exactly 1 public run() overload. " +
            "BuildFixLog_001 Issue 2: 2 overloads caused 23 ambiguous-reference errors. " +
            "Actual public run() count: " + count);
    }

    @Test
    @DisplayName("AsyncTask.run() takes Supplier as first argument")
    void asyncTaskRunTakesSupplier() throws NoSuchMethodException {
        // The remaining overload must be the Supplier<T> one
        Method runMethod = Arrays.stream(AsyncTask.class.getDeclaredMethods())
            .filter(m -> m.getName().equals("run") && java.lang.reflect.Modifier.isPublic(m.getModifiers()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No public run() found in AsyncTask"));

        Class<?>[] params = runMethod.getParameterTypes();
        assertEquals(3, params.length,
            "AsyncTask.run() must have 3 parameters");
        assertEquals(Supplier.class, params[0],
            "AsyncTask.run() first param must be Supplier<T>");
        assertEquals(Consumer.class, params[1],
            "AsyncTask.run() second param must be Consumer<T>");
        assertEquals(Consumer.class, params[2],
            "AsyncTask.run() third param must be Consumer<String>");
    }

    // ── Issue 7: PurchaseDAO.generatePurchaseNumber() public no-arg ──────

    @Test
    @DisplayName("PurchaseDAO.generatePurchaseNumber() public no-arg overload exists")
    void purchaseDAOHasPublicGeneratePurchaseNumber() throws NoSuchMethodException {
        Method m = PurchaseDAO.class.getMethod("generatePurchaseNumber");
        assertNotNull(m,
            "PurchaseDAO.generatePurchaseNumber() public no-arg overload must exist. " +
            "BuildFixLog_001 Issue 7: only the private Connection-arg version existed.");
        assertEquals(String.class, m.getReturnType(),
            "PurchaseDAO.generatePurchaseNumber() must return String");
        assertEquals(0, m.getParameterCount(),
            "PurchaseDAO.generatePurchaseNumber() must take 0 args (the public overload)");
    }

    @Test
    @DisplayName("PurchaseDAO still has private generatePurchaseNumber(Connection) for internal use")
    void purchaseDAORetainsPrivateConnectionOverload() {
        boolean hasPrivate = Arrays.stream(PurchaseDAO.class.getDeclaredMethods())
            .anyMatch(m -> m.getName().equals("generatePurchaseNumber")
                       && m.getParameterCount() == 1
                       && java.lang.reflect.Modifier.isPrivate(m.getModifiers()));
        assertTrue(hasPrivate,
            "PurchaseDAO must retain the private generatePurchaseNumber(Connection) " +
            "overload for use inside save() transactions.");
    }
}
