// R1 sweep: stat cards show real figures or "—", never an invented one. Pure helpers (TopShare, ExpenseStats) on fixed dates,
// then the real Reports / Sales / Expenses / Finance screens rendered with an EMPTY database (no invented number may appear)
// and with seeded data (the numbers must be the ones the data produces).
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.Expense;
import com.kaziflow.models.User;
import com.kaziflow.modules.ModuleRegistry;
import com.kaziflow.services.ExpenseStats;
import com.kaziflow.services.VATConfig;
import com.kaziflow.utils.DateRange;
import com.kaziflow.utils.KESFormatter;
import com.kaziflow.utils.SessionManager;
import com.kaziflow.utils.TopShare;
import com.kaziflow.views.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.*;

public class InventedFiguresCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static <T> T onFx(Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(c.call()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.get(40, TimeUnit.SECONDS);
    }
    static void collect(Node n, List<String> out) {
        if (n instanceof Labeled l && l.getText() != null && !l.getText().isBlank()) out.add(l.getText());
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) collect(c, out);
    }
    static String text(Node root) { List<String> l = new ArrayList<>(); collect(root, l); return String.join(" | ", l); }
    static Node priv(Object view, String method) throws Exception {
        var m = view.getClass().getDeclaredMethod(method); m.setAccessible(true); return (Node) m.invoke(view);
    }
    static void sql(String s) throws SQLException {
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement st = c.createStatement()) { st.execute(s); }
    }
    static double scalar(String q) throws SQLException {
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(q)) { return rs.next() ? rs.getDouble(1) : 0; }
    }
    /** What the next app launch does: re-run the seeding step against the same database. */
    static void seedAgain() throws Exception {
        var m = DatabaseManager.class.getDeclaredMethod("seedDefaultData", Statement.class); m.setAccessible(true);
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement st = c.createStatement()) { m.invoke(DatabaseManager.getInstance(), st); }
    }
    static Expense ex(String cat, double amt, LocalDate d) { Expense e = new Expense(); e.setCategory(cat); e.setAmount(amt); e.setExpenseDate(d); return e; }

    /** Every screen text we render, gathered once per scenario. */
    static Map<String, String> screens() throws Exception {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("reports", onFx(() -> text(new ReportsView().getRoot())));
        m.put("sales", onFx(() -> text(priv(new SalesView(), "buildSalesHistory"))));
        m.put("expenses", onFx(() -> text(new ExpenseView().getRoot())));
        m.put("inventory", onFx(() -> text(new InventoryView().getRoot())));
        m.put("finance-expenses", onFx(() -> { FinanceView v = new FinanceView(); return text(priv(v, "buildExpensesBody")); }));
        m.put("finance-vat", onFx(() -> { FinanceView v = new FinanceView(); return text(priv(v, "buildVatBody")); }));
        return m;
    }

    public static void main(String[] a) throws Exception {
        // ── pure: TopShare ──
        check("TopShare: null and empty rank nothing", TopShare.of(null) == null && TopShare.of(new LinkedHashMap<>()) == null);
        Map<String, Integer> mix = new LinkedHashMap<>(); mix.put("cash", 5); mix.put("mpesa", 3); mix.put("card", 2);
        TopShare t = TopShare.of(mix);
        check("TopShare: top key, value, share and total from the data", t != null && t.key().equals("cash") && t.value() == 5 && t.total() == 10 && Math.abs(t.share() - 50) < 1e-9 && t.percentText().equals("50%"));
        Map<String, Double> zeros = new LinkedHashMap<>(); zeros.put("a", 0.0); zeros.put("b", 0.0);
        check("TopShare: all-zero amounts rank nothing (no '0% of …')", TopShare.of(zeros) == null);
        Map<String, Integer> tie = new LinkedHashMap<>(); tie.put("x", 4); tie.put("y", 4);
        check("TopShare: a tie resolves to the first entry, deterministically", TopShare.of(tie).key().equals("x"));

        // ── pure: ExpenseStats on a fixed date ──
        LocalDate today = LocalDate.of(2026, 11, 3);
        List<Expense> es = List.of(ex("Rent", 600, LocalDate.of(2026, 11, 1)), ex("Rent", 200, LocalDate.of(2026, 10, 15)),
                ex("Fuel", 200, LocalDate.of(2026, 11, 2)), ex("Old", 1000, LocalDate.of(2025, 10, 31)), ex(null, 100, LocalDate.of(2026, 1, 10)));
        ExpenseStats s = ExpenseStats.of(es, today);
        check("ExpenseStats: total and this-month come from the rows (2100 / 800)", s.total() == 2100 && s.thisMonth() == 800 && s.count() == 5);
        check("ExpenseStats: top category by total is 'Old' at 48%", s.topCategory() != null && s.topCategory().key().equals("Old") && s.topCategory().percentText().equals("48%"));
        check("ExpenseStats: last-12-months average ignores older spend (1100/12)", s.avgPerMonthLast12() != null && Math.abs(s.avgPerMonthLast12() - 1100.0 / 12) < 1e-9);
        ExpenseStats none = ExpenseStats.of(List.of(), today);
        check("ExpenseStats: no expenses → no category, no average", none.total() == 0 && none.topCategory() == null && none.avgPerMonthLast12() == null);
        check("ExpenseStats: only old spend → no average rather than 0", ExpenseStats.of(List.of(ex("Old", 50, LocalDate.of(2020, 1, 1))), today).avgPerMonthLast12() == null);
        check("DateRange.monthLabel", DateRange.monthLabel(YearMonth.of(2026, 11)).equals("November 2026"));
        Map<String, Double> mc = ExpenseStats.monthByCategory(es, today, 6);
        check("monthByCategory: only this month's rows, largest first (Rent 600, Fuel 200)", mc.size() == 2 && new ArrayList<>(mc.keySet()).equals(List.of("Rent", "Fuel")) && mc.get("Rent") == 600 && mc.get("Fuel") == 200);
        List<Expense> many = List.of(ex("A", 50, today), ex("B", 40, today), ex("C", 30, today), ex("D", 20, today), ex("E", 10, today));
        Map<String, Double> cap = ExpenseStats.monthByCategory(many, today, 3);
        check("monthByCategory: more than maxRows categories fold the smallest into 'Other' without losing money",
                cap.size() == 3 && cap.containsKey("Other") && cap.get("Other") == 60 && cap.values().stream().mapToDouble(Double::doubleValue).sum() == 150);
        check("monthByCategory: nothing this month → empty (no invented rows)", ExpenseStats.monthByCategory(List.of(ex("Old", 5, LocalDate.of(2020, 1, 1))), today, 6).isEmpty());

        // ── UI ──
        DatabaseManager.getInstance().initialize();
        ModuleRegistry.getInstance().init();
        User u = new User(); u.setId(1); u.setName("Admin"); SessionManager.getInstance().login(u);
        Platform.startup(() -> {});
        // a new database holds no demo data and a restart adds none (loading/removing it is DemoCompanyCheck's job)
        check("seed: a brand-new database has no demo expenses", scalar("SELECT COUNT(*) FROM expenses") == 0);
        seedAgain();
        check("seed: starting the app again adds none (was +8 expenses on every launch)", scalar("SELECT COUNT(*) FROM expenses") == 0);
        sql("DELETE FROM sale_items"); sql("DELETE FROM sales"); sql("DELETE FROM expenses"); sql("DELETE FROM products");
        VATConfig.getInstance().save(16.0, true);

        // the old invented numbers, rendered exactly as the app would have rendered them (so this cannot drift from KESFormatter)
        List<String> bannedList = new ArrayList<>(List.of("+12% vs last month", "1,240 SKUs", "42%", "Operations & Payroll", "October 2024", "56% of sales", "+2.4%", "81%"));
        for (double v : new double[]{4200000, 1800000, 850000, 12500000, 1240000, 320000, 103333}) { bannedList.add(KESFormatter.formatShort(v)); bannedList.add(KESFormatter.format(v)); }
        String[] banned = bannedList.toArray(new String[0]);
        Map<String, String> empty = screens();
        for (var e : empty.entrySet()) {
            String bad = null; for (String b : banned) if (e.getValue().contains(b)) { bad = b; break; }
            check("empty DB, " + e.getKey() + ": no invented figure" + (bad == null ? "" : " (found '" + bad + "')"), bad == null);
        }
        String month = DateRange.monthLabel(YearMonth.now());
        check("empty DB, reports: revenue card is labelled with the real current month", empty.get("reports").contains(month));
        check("empty DB, finance expenses: 'This Month' is labelled with the real current month", empty.get("finance-expenses").contains(month));
        check("empty DB, finance expenses: Top Category is '—' with an honest note", empty.get("finance-expenses").contains("No expenses yet") && !empty.get("finance-expenses").contains("Payroll"));
        check("empty DB, expenses screen: no made-up category names, and the breakdown says so", !empty.get("expenses").contains("Payroll") && empty.get("expenses").contains("No expenses recorded this month"));
        check("empty DB, sales: top payment method says there are no sales", empty.get("sales").contains("No sales this month"));
        check("empty DB, reports: net profit says there are no sales instead of a margin", empty.get("reports").contains("No sales this month"));

        // seeded
        sql("INSERT INTO expenses (description, category, amount, expense_date) VALUES ('r','Rent',600,date('now'))");
        sql("INSERT INTO expenses (description, category, amount, expense_date) VALUES ('f','Fuel',200,date('now'))");
        sql("INSERT INTO sales (sale_number, subtotal, total_amount, payment_method, status) VALUES ('T-1',100,100,'mpesa','completed')");
        sql("INSERT INTO sales (sale_number, subtotal, total_amount, payment_method, status) VALUES ('T-2',100,100,'mpesa','completed')");
        sql("INSERT INTO sales (sale_number, subtotal, total_amount, payment_method, status) VALUES ('T-3',100,100,'cash','completed')");
        Map<String, String> data = screens();
        check("data, sales: top payment method is M-Pesa at 67% of this month's sales", data.get("sales").contains("M-Pesa") && data.get("sales").contains("67% of this month's sales"));
        check("data, finance expenses: top category is Rent at 75% of total", data.get("finance-expenses").contains("Rent") && data.get("finance-expenses").contains("75% of total"));
        check("data, expenses screen: top category Rent (75%)", data.get("expenses").contains("Rent (75%)"));
        check("data, expenses screen: breakdown shows the real rows (Rent 600 at 75%, Fuel 200 at 25%)", data.get("expenses").contains(KESFormatter.format(600)) && data.get("expenses").contains("75%") && data.get("expenses").contains("25%") && data.get("expenses").contains("Fuel"));
        check("data, finance expenses: this-month total is the sum of the rows", data.get("finance-expenses").contains(KESFormatter.formatShort(800)));
        check("data, reports: revenue is the real month's sales total", data.get("reports").contains(KESFormatter.formatShort(300)));
        check("data, reports: expenses card is the real month's expenses", data.get("reports").contains(KESFormatter.formatShort(800)));

        // VAT rate comes from settings
        VATConfig.getInstance().save(14.0, true);
        check("VAT card follows the configured rate (14%)", onFx(() -> text(priv(new FinanceView(), "buildVatBody"))).contains("14%"));
        VATConfig.getInstance().save(16.0, false);
        check("VAT card says Off when VAT is switched off", onFx(() -> text(priv(new FinanceView(), "buildVatBody"))).contains("Off"));

        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
