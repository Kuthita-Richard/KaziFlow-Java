// R1 step A: period selection. Presets on fixed dates (incl. year boundary and leap year), SQL filtering on a real SQLite,
// the picker's state machine, the dialog, and the export flow (period-based reports ask for a period; cancel aborts).
import com.kaziflow.services.ReportExportService;
import com.kaziflow.services.ReportExportService.ReportType;
import com.kaziflow.utils.DateRange;
import com.kaziflow.utils.DateRange.Preset;
import com.kaziflow.utils.DateRangeDialog;
import com.kaziflow.utils.DateRangePicker;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

public class DateRangePickerCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static <T> T onFx(Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(c.call()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.get(30, TimeUnit.SECONDS);
    }
    static LocalDate d(String s) { return LocalDate.parse(s); }
    static boolean is(DateRange r, String from, String to) { return r != null && Objects.equals(r.from(), from == null ? null : d(from)) && Objects.equals(r.to(), to == null ? null : d(to)); }

    public static void main(String[] a) throws Exception {
        // ── presets on a fixed Wednesday ──
        LocalDate t = d("2026-11-04");
        check("TODAY / YESTERDAY", is(DateRange.of(Preset.TODAY, t), "2026-11-04", "2026-11-04") && is(DateRange.of(Preset.YESTERDAY, t), "2026-11-03", "2026-11-03"));
        check("THIS_WEEK is Mon–Sun (2–8 Nov), LAST_WEEK 26 Oct–1 Nov", is(DateRange.of(Preset.THIS_WEEK, t), "2026-11-02", "2026-11-08") && is(DateRange.of(Preset.LAST_WEEK, t), "2026-10-26", "2026-11-01"));
        check("THIS_MONTH 1–30 Nov (30 days), LAST_MONTH 1–31 Oct (31 days)", is(DateRange.of(Preset.THIS_MONTH, t), "2026-11-01", "2026-11-30") && DateRange.of(Preset.THIS_MONTH, t).days() == 30
                && is(DateRange.of(Preset.LAST_MONTH, t), "2026-10-01", "2026-10-31") && DateRange.of(Preset.LAST_MONTH, t).days() == 31);
        check("THIS_QUARTER 1 Oct–31 Dec, LAST_QUARTER 1 Jul–30 Sep", is(DateRange.of(Preset.THIS_QUARTER, t), "2026-10-01", "2026-12-31") && is(DateRange.of(Preset.LAST_QUARTER, t), "2026-07-01", "2026-09-30"));
        check("THIS_YEAR / LAST_YEAR", is(DateRange.of(Preset.THIS_YEAR, t), "2026-01-01", "2026-12-31") && is(DateRange.of(Preset.LAST_YEAR, t), "2025-01-01", "2025-12-31"));
        check("LAST_7_DAYS (7 days incl. today) and LAST_30_DAYS (30 days incl. today)", is(DateRange.of(Preset.LAST_7_DAYS, t), "2026-10-29", "2026-11-04") && DateRange.of(Preset.LAST_7_DAYS, t).days() == 7
                && is(DateRange.of(Preset.LAST_30_DAYS, t), "2026-10-06", "2026-11-04") && DateRange.of(Preset.LAST_30_DAYS, t).days() == 30);
        check("ALL_TIME is open on both ends", DateRange.of(Preset.ALL_TIME, t).isAll());
        // year boundary and leap year
        LocalDate ny = d("2026-01-03");
        check("year boundary: THIS_WEEK spans 29 Dec–4 Jan; LAST_MONTH is December; LAST_QUARTER is Oct–Dec; LAST_YEAR is 2025",
                is(DateRange.of(Preset.THIS_WEEK, ny), "2025-12-29", "2026-01-04") && is(DateRange.of(Preset.LAST_MONTH, ny), "2025-12-01", "2025-12-31")
                && is(DateRange.of(Preset.LAST_QUARTER, ny), "2025-10-01", "2025-12-31") && is(DateRange.of(Preset.LAST_YEAR, ny), "2025-01-01", "2025-12-31"));
        check("leap year: on 31 Mar 2024 LAST_MONTH is 1–29 Feb; on 29 Feb THIS_MONTH has 29 days",
                is(DateRange.of(Preset.LAST_MONTH, d("2024-03-31")), "2024-02-01", "2024-02-29") && DateRange.of(Preset.THIS_MONTH, d("2024-02-29")).days() == 29);
        // comparison periods
        check("previousPeriod: month → previous calendar month; quarter → previous quarter; year → previous year",
                is(DateRange.of(Preset.THIS_MONTH, t).previousPeriod(), "2026-10-01", "2026-10-31") && is(DateRange.of(Preset.THIS_QUARTER, t).previousPeriod(), "2026-07-01", "2026-09-30")
                && is(DateRange.of(Preset.THIS_YEAR, t).previousPeriod(), "2025-01-01", "2025-12-31"));
        check("previousPeriod: other ranges → the same number of days immediately before; ALL/open-ended → ALL",
                is(DateRange.of(Preset.LAST_7_DAYS, t).previousPeriod(), "2026-10-22", "2026-10-28") && is(new DateRange(d("2026-11-10"), d("2026-11-20")).previousPeriod(), "2026-10-30", "2026-11-09")
                && DateRange.ALL.previousPeriod().isAll() && new DateRange(d("2026-01-01"), null).previousPeriod().isAll());
        check("sameLastYear maps 29 Feb 2024 to 28 Feb 2023", is(new DateRange(d("2024-02-29"), d("2024-02-29")).sameLastYear(), "2023-02-28", "2023-02-28"));
        boolean threw = false; try { new DateRange(d("2026-02-02"), d("2026-02-01")); } catch (IllegalArgumentException e) { threw = true; }
        check("a range whose start is after its end cannot be built", threw);

        // ── SQL filter against a real SQLite (whole days, end date inclusive) ──
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:"); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE t (created_at TEXT)");
            for (String s : new String[]{"2026-10-31 23:59:59", "2026-11-01 00:00:00", "2026-11-15 12:00:00", "2026-11-30 23:59:59", "2026-12-01 00:00:00"})
                st.execute("INSERT INTO t VALUES ('" + s + "')");
            DateRange month = DateRange.of(Preset.THIS_MONTH, t);
            check("whereClause/bind: a month includes its first and last second and nothing outside (3 of 5 rows)", countWhere(c, month) == 3);
            check("whereClause: ALL adds no condition (5 of 5)", countWhere(c, DateRange.ALL) == 5 && DateRange.ALL.whereClause("created_at").isEmpty());
            check("whereClause: open-ended 'from 15 Nov' (3 rows) and 'up to 31 Oct' (1 row)", countWhere(c, new DateRange(d("2026-11-15"), null)) == 3 && countWhere(c, new DateRange(null, d("2026-10-31"))) == 1);
        }

        // ── picker state machine (today fixed to 4 Nov 2026) ──
        Platform.startup(() -> {});
        DateRangePicker p = onFx(() -> new DateRangePicker(Preset.THIS_MONTH, () -> t));
        check("picker: starts on the requested preset with its dates shown and read-only", onFx(() -> p.isValid() && is(p.getRange(), "2026-11-01", "2026-11-30") && !p.datesEditable() && d("2026-11-01").equals(p.shownFrom()) && d("2026-11-30").equals(p.shownTo())));
        check("picker: the caption names the dates and the length", onFx(() -> p.captionText().contains("2026") && p.captionText().contains("30 days")));
        check("picker: changing the preset re-resolves (Last month)", onFx(() -> { p.setPreset(Preset.LAST_MONTH); return is(p.getRange(), "2026-10-01", "2026-10-31") && p.captionText().contains("31 days"); }));
        check("picker: All time shows no dates and no day count", onFx(() -> { p.setPreset(Preset.ALL_TIME); return p.getRange().isAll() && p.shownFrom() == null && p.shownTo() == null && p.captionText().equals("All time"); }));
        check("picker: Custom enables the date fields", onFx(() -> { p.setPreset(Preset.CUSTOM); return p.datesEditable(); }));
        check("picker: custom with no dates is invalid and says why", onFx(() -> !p.isValid() && p.getRange() == null && p.captionText().contains("Pick")));
        check("picker: custom with start after end is invalid", onFx(() -> { p.setCustom(d("2026-11-20"), d("2026-11-10")); return !p.isValid() && p.captionText().contains("after"); }));
        check("picker: custom with both dates is valid (11 days)", onFx(() -> { p.setCustom(d("2026-11-10"), d("2026-11-20")); return is(p.getRange(), "2026-11-10", "2026-11-20") && p.captionText().contains("11 days"); }));
        check("picker: a single-day custom range reads '1 day'", onFx(() -> { p.setCustom(d("2026-11-10"), d("2026-11-10")); return p.isValid() && p.captionText().contains("(1 day)"); }));
        check("picker: custom with only a start date is valid and open-ended ('From …')", onFx(() -> { p.setCustom(d("2026-11-10"), null); return is(p.getRange(), "2026-11-10", null) && p.captionText().startsWith("From"); }));
        check("picker: back to a preset locks the fields again", onFx(() -> { p.setPreset(Preset.THIS_WEEK); return !p.datesEditable() && is(p.getRange(), "2026-11-02", "2026-11-08"); }));
        check("picker: rangeProperty notifies listeners (null while invalid)", onFx(() -> { List<DateRange> seen = new ArrayList<>(); p.rangeProperty().addListener((o, x, y) -> seen.add(y)); p.setCustom(d("2026-11-20"), d("2026-11-10")); p.setCustom(d("2026-11-01"), d("2026-11-02")); return seen.size() == 2 && seen.get(0) == null && seen.get(1) != null; }));

        // ── typed dates are read day-first and shown human-readably ──
        check("parseDate: '4 Nov 2026', '04/11/2026', '4/11/2026', '4-11-2026' and ISO all mean 4 November",
                d("2026-11-04").equals(DateRangePicker.parseDate("4 Nov 2026")) && d("2026-11-04").equals(DateRangePicker.parseDate("04/11/2026"))
                && d("2026-11-04").equals(DateRangePicker.parseDate("4/11/2026")) && d("2026-11-04").equals(DateRangePicker.parseDate("4-11-2026")) && d("2026-11-04").equals(DateRangePicker.parseDate("2026-11-04")));
        check("parseDate: 11/10/2026 is 11 October (day-first), never 10 November", d("2026-10-11").equals(DateRangePicker.parseDate("11/10/2026")));
        check("parseDate: blank → null; garbage, 31/02/2026 and 13/13/2026 → null", DateRangePicker.parseDate("  ") == null && DateRangePicker.parseDate("soon") == null
                && DateRangePicker.parseDate("31/02/2026") == null && DateRangePicker.parseDate("13/13/2026") == null);
        check("picker fields display dates as '4 Nov 2026'", onFx(() -> {
            DateRangePicker q = new DateRangePicker(Preset.THIS_MONTH, () -> t);
            javafx.stage.Stage stage = new javafx.stage.Stage(); stage.setScene(new javafx.scene.Scene(q)); stage.show(); q.applyCss(); q.layout();   // the editor text is synced by the skin
            javafx.scene.control.DatePicker f = (javafx.scene.control.DatePicker) ((javafx.scene.layout.Pane) ((javafx.scene.layout.Pane) q.getChildren().get(1)).getChildren().get(0)).getChildren().get(1);
            return f.getConverter().toString(d("2026-11-04")).equals("4 Nov 2026") && f.getEditor().getText().equals("1 Nov 2026"); }));

        // ── dialog ──
        Dialog<DateRange> dlg = onFx(() -> DateRangeDialog.create(null, "Sales", new DateRangePicker(Preset.THIS_MONTH, () -> t)));
        DateRangePicker dp = (DateRangePicker) dlg.getDialogPane().getContent();
        ButtonType export = dlg.getDialogPane().getButtonTypes().get(0);
        check("dialog: has Export and Cancel, Export enabled for a valid period", dlg.getDialogPane().getButtonTypes().size() == 2 && !onFx(() -> dlg.getDialogPane().lookupButton(export).isDisabled()));
        check("dialog: Export is disabled while the period is invalid and re-enabled when fixed", onFx(() -> {
            dp.setCustom(d("2026-11-20"), d("2026-11-10")); boolean off = dlg.getDialogPane().lookupButton(export).isDisabled();
            dp.setCustom(d("2026-11-01"), d("2026-11-10")); return off && !dlg.getDialogPane().lookupButton(export).isDisabled(); }));
        check("dialog: Export returns the chosen range, Cancel returns nothing", onFx(() -> is(dlg.getResultConverter().call(export), "2026-11-01", "2026-11-10") && dlg.getResultConverter().call(ButtonType.CANCEL) == null));
        check("dialog: the header names the report", dlg.getHeaderText().contains("Sales"));

        // ── export flow (seams replace the dialog and the file chooser) ──
        for (ReportType rt : ReportType.values()) {
            Probe pr = new Probe(Optional.of(DateRange.of(Preset.THIS_MONTH, t)));
            onFx(() -> { pr.exportCSV(rt); return null; });
            boolean period = rt == ReportType.SALES || rt == ReportType.EXPENSES || rt == ReportType.PURCHASES || rt == ReportType.FINANCE_SUMMARY;
            check("export " + rt + ": " + (period ? "asks for a period" : "does not ask (no dates in the data)") + " and then asks where to save",
                    (pr.asked == (period ? 1 : 0)) && pr.chosen == 1);
            if (period) check("export " + rt + ": the file name carries the chosen period", pr.fileName.contains("2026-11-01_to_2026-11-30"));
            else check("export " + rt + ": the file name has no period", !pr.fileName.contains("_to_"));
        }
        Probe cancelled = new Probe(Optional.empty());
        onFx(() -> { cancelled.exportCSV(ReportType.SALES); return null; });
        check("export: cancelling the period dialog exports nothing and never opens the file chooser", cancelled.asked == 1 && cancelled.chosen == 0);
        Probe all = new Probe(Optional.of(DateRange.ALL));
        onFx(() -> { all.exportCSV(ReportType.SALES); return null; });
        check("export: choosing All time keeps the plain file name", all.chosen == 1 && !all.fileName.contains("_to_") && !all.fileName.contains("from_"));

        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    static int countWhere(Connection c, DateRange r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM t WHERE 1=1" + r.whereClause("created_at"))) {
            int next = r.bind(ps, 1);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : -1; }
        }
    }

    static class Probe extends ReportExportService {
        final Optional<DateRange> answer; int asked, chosen; String fileName = "";
        Probe(Optional<DateRange> answer) { this.answer = answer; }
        @Override protected Optional<DateRange> askPeriod(ReportType type) { asked++; return answer; }
        @Override protected File chooseFile(String name) { chosen++; fileName = name; return null; }
    }
}
