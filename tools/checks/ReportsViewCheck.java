// R1 step B: ReportsView — no fake controls; the period picker drives the KPI cards and the exports; the trend chart shows the
// LATEST 6 calendar months with revenue and expenses on the same month keys.
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.User;
import com.kaziflow.modules.ModuleRegistry;
import com.kaziflow.services.ReportExportService;
import com.kaziflow.services.ReportExportService.ReportType;
import com.kaziflow.utils.DateRange;
import com.kaziflow.utils.DateRange.Preset;
import com.kaziflow.utils.DateRangePicker;
import com.kaziflow.utils.KESFormatter;
import com.kaziflow.utils.SessionManager;
import com.kaziflow.views.ReportsView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.*;

public class ReportsViewCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static <T> T onFx(Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(c.call()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.get(30, TimeUnit.SECONDS);
    }
    static <T extends Node> List<T> all(Node n, Class<T> k) { List<T> out = new ArrayList<>(); walk(n, k, out); return out; }
    static <T extends Node> void walk(Node n, Class<T> k, List<T> out) {
        if (k.isInstance(n)) out.add(k.cast(n));
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) walk(c, k, out);
    }
    static String text(Node root) { List<String> l = new ArrayList<>(); for (Labeled x : all(root, Labeled.class)) if (x.getText() != null && !x.getText().isBlank()) l.add(x.getText()); return String.join(" | ", l); }
    static void sql(String s) throws SQLException {
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement st = c.createStatement()) { st.execute(s); }
    }
    static LocalDate d(String s) { return LocalDate.parse(s); }
    static String label(YearMonth ym) { return ym.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " " + String.format("%02d", ym.getYear() % 100); }

    static class Probe extends ReportExportService {
        final List<Object[]> calls = new ArrayList<>();
        @Override public void exportCSV(ReportType type, DateRange range) { calls.add(new Object[]{type, range}); }
    }
    static Button button(Node root, String contains) { for (Button b : all(root, Button.class)) if (b.getText() != null && b.getText().contains(contains)) return b; return null; }
    @SuppressWarnings("unchecked")
    static LineChart<String, Number> trend(Node root) { List<LineChart> l = all(root, LineChart.class); return l.isEmpty() ? null : (LineChart<String, Number>) l.get(0); }
    static double[] series(LineChart<String, Number> c, int idx) { var s = c.getData().get(idx); double[] v = new double[s.getData().size()]; for (int i = 0; i < v.length; i++) v[i] = s.getData().get(i).getYValue().doubleValue(); return v; }

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        ModuleRegistry.getInstance().init();
        User u = new User(); u.setId(1); u.setName("Admin"); SessionManager.getInstance().login(u);
        Platform.startup(() -> {});
        LocalDate today = LocalDate.now();
        YearMonth now = YearMonth.from(today);

        // ── empty database ──
        Parent root = onFx(() -> new ReportsView().getRoot());
        String t0 = onFx(() -> text(root));
        check("no fake controls: no branch box, no 'Date Range:' box, no 'Export PDF', no 'Schedule Report'",
                !t0.contains("Nairobi HQ") && !t0.contains("All Branches") && !t0.contains("Date Range:") && !t0.contains("Export PDF") && !t0.contains("Schedule Report"));
        check("a real period picker is on the screen, starting on 'This month'", onFx(() -> { var p = all(root, DateRangePicker.class); return p.size() == 1 && p.get(0).getPreset() == Preset.THIS_MONTH && p.get(0).isValid(); }));
        check("the export buttons are honest: 'Sales CSV' and 'Finance summary CSV' (it never produced a PDF)", button(root, "Sales CSV") != null && button(root, "Finance summary CSV") != null);
        LineChart<String, Number> ch0 = onFx(() -> trend(root));
        check("trend chart: 6 months, labelled with real month names ending this month", ch0 != null && ch0.getData().size() == 2 && ch0.getData().get(0).getData().size() == 6
                && ch0.getData().get(0).getData().get(5).getXValue().equals(label(now)) && ch0.getData().get(0).getData().get(0).getXValue().equals(label(now.minusMonths(5))));
        check("trend chart on an empty database is flat zero (no invented trend)", Arrays.stream(series(ch0, 0)).sum() == 0 && Arrays.stream(series(ch0, 1)).sum() == 0);

        // ── seed: revenue now / last month / 5 months ago / 8 months ago (outside the chart); expenses now / 2 months ago / 8 months ago ──
        for (int i = 1; i <= 3; i++) sql("INSERT INTO sales (sale_number, subtotal, total_amount, payment_method, status, created_at) VALUES ('N-" + i + "',100,100,'cash','completed',datetime('now'))");
        sql("INSERT INTO sales (sale_number, subtotal, total_amount, payment_method, status, created_at) VALUES ('L-1',100,100,'cash','completed',datetime('now','start of month','-1 months','+9 days','+10 hours'))");
        sql("INSERT INTO sales (sale_number, subtotal, total_amount, payment_method, status, created_at) VALUES ('F-1',50,50,'cash','completed',datetime('now','start of month','-5 months','+3 days'))");
        sql("INSERT INTO sales (sale_number, subtotal, total_amount, payment_method, status, created_at) VALUES ('OLD-1',999,999,'cash','completed',datetime('now','start of month','-8 months','+3 days'))");
        sql("INSERT INTO sales (sale_number, subtotal, total_amount, payment_method, status, created_at) VALUES ('X-1',500,500,'cash','refunded',datetime('now'))");
        sql("INSERT INTO expenses (description, category, amount, expense_date) VALUES ('e1','Rent',80,date('now'))");
        sql("INSERT INTO expenses (description, category, amount, expense_date) VALUES ('e2','Rent',20,date('now','start of month','-2 months','+1 days'))");
        sql("INSERT INTO expenses (description, category, amount, expense_date) VALUES ('e3','Rent',777,date('now','start of month','-8 months','+1 days'))");
        sql("INSERT INTO products (sku, name, selling_price, cost_price, stock_quantity, status) VALUES ('P1','Prod',10,6,5,'active')");

        Probe probe = new Probe();
        Parent rv = onFx(() -> { ReportsView v = new ReportsView(); var f = ReportsView.class.getDeclaredField("exporter"); f.setAccessible(true); f.set(v, probe); return v.getRoot(); });
        DateRangePicker pk = onFx(() -> all(rv, DateRangePicker.class).get(0));

        // trend chart
        LineChart<String, Number> ch = onFx(() -> trend(rv));
        double[] rev = series(ch, 0), exp = series(ch, 1);
        check("trend chart: it is the LATEST 6 months — the 8-months-ago sales (999) and expenses (777) are not in it (it used to show the oldest six)",
                Arrays.stream(rev).sum() == 450 && Arrays.stream(exp).sum() == 100);
        check("trend chart: revenue sits on its own months (now 300, last month 100, 5 months ago 50)", rev[5] == 300 && rev[4] == 100 && rev[0] == 50 && rev[1] == 0 && rev[2] == 0 && rev[3] == 0);
        check("trend chart: expenses sit on THEIR months, not paired by position with revenue (now 80, 2 months ago 20)", exp[5] == 80 && exp[3] == 20 && exp[0] == 0 && exp[4] == 0);
        check("trend chart: refunded sales are not revenue", rev[5] == 300);

        // KPI cards follow the period
        String thisMonth = onFx(() -> text(rv));
        check("this month: revenue 300, +200% vs the previous period (last month 100)", thisMonth.contains("REVENUE | " + KESFormatter.formatShort(300)) && thisMonth.contains("+200% vs previous period"));
        check("this month: expenses 80 and net profit 220 (73% of revenue)", thisMonth.contains("EXPENSES | " + KESFormatter.formatShort(80)) && thisMonth.contains("NET PROFIT | " + KESFormatter.formatShort(220)) && thisMonth.contains("73% of revenue"));
        check("this month: inventory value is today's stock (10 x 5 = 50) and says it is as of today", thisMonth.contains("INVENTORY VALUE | " + KESFormatter.formatShort(50)) && thisMonth.contains("as of today"));
        onFx(() -> { pk.setPreset(Preset.LAST_MONTH); return null; });
        String last = onFx(() -> text(rv));
        check("last month: revenue 100 with the real dates as its caption (the month before had no sales, so no % is invented)",
                last.contains("REVENUE | " + KESFormatter.formatShort(100)) && last.contains(DateRange.of(Preset.LAST_MONTH, today).label()) && !last.contains("vs previous period"));
        check("last month: expenses 0 (the 20 was two months ago), net profit 100 (100% of revenue)", last.contains("EXPENSES | " + KESFormatter.formatShort(0)) && last.contains("NET PROFIT | " + KESFormatter.formatShort(100)) && last.contains("100% of revenue"));
        check("last month: inventory value unchanged by the period", last.contains("INVENTORY VALUE | " + KESFormatter.formatShort(50)));
        onFx(() -> { pk.setPreset(Preset.ALL_TIME); return null; });
        String allT = onFx(() -> text(rv));
        check("all time: revenue 1,449 (300+100+50+999), expenses 877, no comparison shown", allT.contains("REVENUE | " + KESFormatter.formatShort(1449)) && allT.contains("EXPENSES | " + KESFormatter.formatShort(877)) && !allT.contains("vs previous period"));
        LocalDate day = today.withDayOfMonth(1).minusMonths(1).plusDays(9);
        onFx(() -> { pk.setCustom(day, day); return null; });
        check("custom single day: revenue is just that day's sale (100)", onFx(() -> text(rv)).contains("REVENUE | " + KESFormatter.formatShort(100)));
        onFx(() -> { pk.setCustom(day.plusDays(1), day.minusDays(1)); return null; });
        check("invalid custom range: the cards keep the last valid figures and the exports are disabled",
                onFx(() -> text(rv)).contains("REVENUE | " + KESFormatter.formatShort(100)) && onFx(() -> button(rv, "Sales CSV").isDisable() && button(rv, "Finance summary CSV").isDisable()));

        // exports use the selected period
        onFx(() -> { pk.setPreset(Preset.THIS_MONTH); return null; });
        onFx(() -> { button(rv, "Sales CSV").fire(); button(rv, "Finance summary CSV").fire(); return null; });
        DateRange tm = DateRange.of(Preset.THIS_MONTH, today);
        check("exports: 'Sales CSV' exports SALES for the selected period, 'Finance summary CSV' exports FINANCE_SUMMARY for it",
                probe.calls.size() == 2 && probe.calls.get(0)[0] == ReportType.SALES && tm.equals(probe.calls.get(0)[1]) && probe.calls.get(1)[0] == ReportType.FINANCE_SUMMARY && tm.equals(probe.calls.get(1)[1]));
        check("exports: enabled again once the period is valid", onFx(() -> !button(rv, "Sales CSV").isDisable()));
        check("the stock pie is still drawn (data exists) and is not period-driven", onFx(() -> !all(rv, PieChart.class).isEmpty()));

        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
