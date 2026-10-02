// Validation rules, query/report engine, saved views, CSV import/export and a 20k-record performance run, on real SQLite.
import com.kaziflow.custom.*;
import com.kaziflow.custom.CustomModuleService.*;
import com.kaziflow.custom.CustomQueryService.*;
import com.kaziflow.custom.CustomRecordService.*;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.services.AuditLog;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class CustomReportingCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    interface R { void run() throws Exception; }
    static boolean fails(String n, R r, String contains) {
        try { r.run(); check(n, false); return false; }
        catch (Exception e) { boolean ok = contains == null || e.getMessage().toLowerCase().contains(contains.toLowerCase()); if (!ok) System.out.println("   got: " + e.getMessage()); check(n, ok); return ok; }
    }
    static FieldDef f(int e, String l, FieldType t, boolean req) { return new FieldDef(0, e, l, t, req, null, List.of(), null, null, null, true, false, 0, false); }
    static FieldDef ruled(int e, String l, FieldType t, boolean req, Double min, Double max, String msg) { return new FieldDef(0, e, l, t, req, null, List.of(), null, null, null, true, false, 0, false, min, max, msg); }
    static Map<String,Object> m(Object... kv) { Map<String,Object> x = new HashMap<>(); for (int i = 0; i < kv.length; i += 2) x.put((String) kv[i], kv[i+1]); return x; }
    static String sql1(String q) throws Exception { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) { return r.next() ? r.getString(1) : null; } }
    static void sql(String q) throws Exception { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement()) { s.execute(q); } }
    static Filter flt(String f, Op op, String v) { return new Filter(f, op, v); }
    static ViewDef view(Filter... fs) { return new ViewDef(List.of(fs), null, null, Bucket.NONE, List.of()); }

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        AuditLog.ensureHashColumns();
        CustomModuleService defs = CustomModuleService.getInstance();
        CustomRecordService recs = CustomRecordService.getInstance();
        CustomQueryService q = CustomQueryService.getInstance();

        int mod = defs.createModule("fleet", "Fleet", "F", "");
        int eVeh = defs.addEntity(mod, "Vehicle", "Vehicles"), eLog = defs.addEntity(mod, "Mileage Log", "Mileage Logs");
        int fReg = defs.addField(ruled(eVeh, "Registration", FieldType.TEXT, true, 3.0, null, null));
        int fLV = defs.addField(new FieldDef(0, eLog, "Vehicle", FieldType.LOOKUP, true, null, List.of(), eVeh, null, null, true, false, 0, false));
        int fDate = defs.addField(f(eLog, "Date", FieldType.DATE, true));
        int fKm = defs.addField(ruled(eLog, "Km", FieldType.DECIMAL, true, 0.0, 5000.0, "Km looks wrong: a single trip is between 0 and 5000."));
        int fL = defs.addField(f(eLog, "Litres", FieldType.DECIMAL, false));
        int fCost = defs.addField(f(eLog, "Cost", FieldType.MONEY, false));
        int fKind = defs.addField(new FieldDef(0, eLog, "Kind", FieldType.CHOICE, false, null, List.of("Patient transfer", "Staff", "Supplies"), null, null, null, true, false, 0, false, 5.0, 9.0, null));
        check("range rule is ignored for a type it doesn't apply to (choice)", defs.getField(fKind).minValue() == null && defs.getField(fKind).maxValue() == null);
        int fKpl = defs.addField(new FieldDef(0, eLog, "Km per litre", FieldType.CALC, false, null, List.of(), null, "ROUND({f_" + fKm + "}/{f_" + fL + "}, 2)", null, true, false, 0, false));
        String kmK = "f_" + fKm, dateK = "f_" + fDate, lvK = "f_" + fLV, litK = "f_" + fL, costK = "f_" + fCost, kplK = "f_" + fKpl, regK = "f_" + fReg;

        // ── validation rules ──
        long v1 = recs.create(eVeh, m(regK, "KDA 123A"), 0), v2 = recs.create(eVeh, m(regK, "KCB 555B"), 0);
        fails("text min-length rule (default message)", () -> recs.create(eVeh, m(regK, "AB"), 0), "at least 3 characters");
        fails("numeric rule: custom message shown when too high", () -> recs.create(eLog, m(lvK, v1, dateK, "2026-09-01", kmK, 6000), 0), "single trip is between 0 and 5000");
        fails("numeric rule: custom message shown when too low", () -> recs.create(eLog, m(lvK, v1, dateK, "2026-09-01", kmK, -1), 0), "single trip");
        check("a value inside the range is accepted", recs.create(eLog, m(lvK, v1, dateK, "2026-08-10", kmK, 100, litK, 10, costK, 1000, "f_" + fKind, "Staff"), 0) > 0);
        fails("min greater than max is rejected", () -> defs.addField(ruled(eLog, "Bad", FieldType.INTEGER, false, 10.0, 1.0, null)), "can't be bigger");
        defs.updateField(fKm, "Km", true, null, List.of(), null, null, true, false);   // the original 9-argument overload
        check("the 9-argument updateField keeps the existing rule", defs.getField(fKm).maxValue() == 5000.0 && defs.getField(fKm).validationMessage().contains("single trip"));
        sql("ALTER TABLE custom_fields DROP COLUMN min_value"); sql("ALTER TABLE custom_fields DROP COLUMN max_value"); sql("ALTER TABLE custom_fields DROP COLUMN validation_message");
        try (Connection c = DatabaseManager.getInstance().getConnection()) { CustomModuleService.ensureSchema(c); }
        check("migration: an older database gets the new columns added (rules start empty, nothing breaks)", defs.listFields(eLog, false).size() == 7 && defs.getField(fKm).maxValue() == null);
        defs.updateField(fKm, "Km", true, null, List.of(), null, null, true, false, 0.0, 5000.0, "Km looks wrong: a single trip is between 0 and 5000.");

        // data for the query tests (one log already exists: v1, 2026-08-10, km 100)
        recs.createBatch(eLog, List.of(
            m(lvK, v1, dateK, "2026-09-05", kmK, 200, litK, 20, costK, 2000, "f_" + fKind, "Patient transfer"),
            m(lvK, v1, dateK, "2026-09-20", kmK, 50,  litK, 5,  costK, 500,  "f_" + fKind, "Staff"),
            m(lvK, v2, dateK, "2026-09-01", kmK, 300, litK, 40, costK, 4000, "f_" + fKind, "Supplies"),
            m(lvK, v2, dateK, "2026-10-01", kmK, 10)), 0);

        // ── list / filters / sort ──
        ListResult all = q.list(eLog, ViewDef.ALL, null, 100);
        check("no filter: 5 rows, 5 matching, Km total 660", all.rows().size() == 5 && all.totalMatching() == 5 && all.totals().get(kmK) == 660.0);
        check("Km > 100 -> 2 rows", q.list(eLog, view(flt(kmK, Op.GT, "100")), null, 100).rows().size() == 2);
        check("Km >= 100 -> 3 rows;  Km < 100 -> 2;  Km <= 100 -> 3", q.list(eLog, view(flt(kmK, Op.GE, "100")), null, 100).totalMatching() == 3
            && q.list(eLog, view(flt(kmK, Op.LT, "100")), null, 100).totalMatching() == 2 && q.list(eLog, view(flt(kmK, Op.LE, "100")), null, 100).totalMatching() == 3);
        check("date between 2026-09-01 and 2026-09-30 -> 3", q.list(eLog, view(flt(dateK, Op.GE, "2026-09-01"), flt(dateK, Op.LE, "2026-09-30")), null, 100).totalMatching() == 3);
        check("link equals vehicle 1 -> 3;  not vehicle 1 -> 2", q.list(eLog, view(flt(lvK, Op.EQ, "" + v1)), null, 100).totalMatching() == 3 && q.list(eLog, view(flt(lvK, Op.NE, "" + v1)), null, 100).totalMatching() == 2);
        check("choice equals 'Staff' -> 2", q.list(eLog, view(flt("f_" + fKind, Op.EQ, "Staff")), null, 100).totalMatching() == 2);
        check("text contains (case-insensitive)", q.list(eVeh, view(flt(regK, Op.CONTAINS, "kda")), null, 100).totalMatching() == 1);
        check("is empty / is not empty (Litres)", q.list(eLog, view(flt(litK, Op.EMPTY, null)), null, 100).totalMatching() == 1 && q.list(eLog, view(flt(litK, Op.NOT_EMPTY, null)), null, 100).totalMatching() == 4);
        check("filters combine (AND): vehicle 1 and Km > 60 -> 2", q.list(eLog, view(flt(lvK, Op.EQ, "" + v1), flt(kmK, Op.GT, "60")), null, 100).totalMatching() == 2);
        check("totals follow the filter (Km>=100 -> 600)", q.list(eLog, view(flt(kmK, Op.GE, "100")), null, 100).totals().get(kmK) == 600.0);
        check("totals cover ALL matches even when rows are capped", q.list(eLog, ViewDef.ALL, null, 2).rows().size() == 2 && q.list(eLog, ViewDef.ALL, null, 2).totals().get(kmK) == 660.0);
        check("sort Km descending: biggest first", ((Number) q.list(eLog, new ViewDef(List.of(), new Sort(kmK, true), null, Bucket.NONE, List.of()), null, 100).rows().get(0).values().get(kmK)).doubleValue() == 300.0);
        check("sort Km ascending: smallest first", ((Number) q.list(eLog, new ViewDef(List.of(), new Sort(kmK, false), null, Bucket.NONE, List.of()), null, 100).rows().get(0).values().get(kmK)).doubleValue() == 10.0);
        check("filter + free-text search together", q.list(eLog, view(flt(lvK, Op.EQ, "" + v2)), "supplies", 100).rows().size() == 1);
        check("calculated column still shows in a filtered list", q.list(eLog, view(flt(kmK, Op.EQ, "200")), null, 10).rows().get(0).values().get(kplK).equals(10.0));

        // ── injection-style and invalid definitions ──
        String rowsBefore = sql1("SELECT COUNT(*) FROM custom_records");
        check("filter value that looks like SQL is just text (matches nothing, table intact)",
              q.list(eVeh, view(flt(regK, Op.EQ, "'; DROP TABLE custom_records;--")), null, 10).totalMatching() == 0 && sql1("SELECT COUNT(*) FROM custom_records").equals(rowsBefore));
        check("CONTAINS treats % and _ literally", q.list(eVeh, view(flt(regK, Op.CONTAINS, "%")), null, 10).totalMatching() == 0);
        fails("a forged field key is rejected", () -> q.list(eLog, view(flt("f_1) OR 1=1 --", Op.EQ, "x")), null, 10), "no longer exists");
        fails("a field from another entity is rejected", () -> q.list(eLog, view(flt(regK, Op.EQ, "x")), null, 10), "no longer exists");
        fails("filtering on a calculated field is rejected with an explanation", () -> q.list(eLog, view(flt(kplK, Op.GT, "5")), null, 10), "calculated");
        fails("sorting by a calculated field is rejected", () -> q.list(eLog, new ViewDef(List.of(), new Sort(kplK, false), null, Bucket.NONE, List.of()), null, 10), "calculated");
        fails("a forged sort key is rejected", () -> q.list(eLog, new ViewDef(List.of(), new Sort("f_1; DROP TABLE users", false), null, Bucket.NONE, List.of()), null, 10), "no longer exists");
        fails("non-numeric value for a number field", () -> q.list(eLog, view(flt(kmK, Op.GT, "abc")), null, 10), "isn't a valid value");
        fails("bad date value", () -> q.list(eLog, view(flt(dateK, Op.GE, "yesterday")), null, 10), "isn't a valid date");
        fails("missing value for a comparison", () -> q.list(eLog, view(flt(kmK, Op.GT, "")), null, 10), "enter a value");

        // ── grouping / reports ──
        ViewDef byVehicle = new ViewDef(List.of(), null, lvK, Bucket.NONE, List.of(new Aggregate(null, Agg.COUNT), new Aggregate(kmK, Agg.SUM), new Aggregate(litK, Agg.SUM), new Aggregate(kmK, Agg.AVG)));
        GroupResult g = q.group(eLog, byVehicle);
        check("group by vehicle: 2 groups with readable names", g.rows().size() == 2 && g.rows().stream().map(GroupRow::label).toList().equals(List.of("KDA 123A", "KCB 555B")));
        GroupRow kda = g.rows().get(0), kcb = g.rows().get(1);
        check("group: counts 3 and 2", kda.count() == 3 && kcb.count() == 2);
        check("group: SUM Km 350 and 310", kda.values().get("SUM:" + kmK) == 350.0 && kcb.values().get("SUM:" + kmK) == 310.0);
        check("group: SUM Litres 35 and 40", kda.values().get("SUM:" + litK) == 35.0 && kcb.values().get("SUM:" + litK) == 40.0);
        check("group: AVG Km (350/3, 310/2)", Math.abs(kda.values().get("AVG:" + kmK) - 116.6667) < 0.001 && kcb.values().get("AVG:" + kmK) == 155.0);
        check("group: grand totals row (5 records, Km 660)", g.totalCount() == 5 && g.totals().get("SUM:" + kmK) == 660.0);
        GroupResult byMonth = q.group(eLog, new ViewDef(List.of(), null, dateK, Bucket.MONTH, List.of(new Aggregate(kmK, Agg.SUM))));
        check("group by month: 2026-08, 2026-09, 2026-10 with Km 100 / 550 / 10", byMonth.rows().stream().map(GroupRow::label).toList().equals(List.of("2026-08", "2026-09", "2026-10"))
              && byMonth.rows().get(0).values().get("SUM:" + kmK) == 100.0 && byMonth.rows().get(1).values().get("SUM:" + kmK) == 550.0 && byMonth.rows().get(2).values().get("SUM:" + kmK) == 10.0);
        check("group by year", q.group(eLog, new ViewDef(List.of(), null, dateK, Bucket.YEAR, List.of())).rows().size() == 1);
        check("group by choice, with 'none' for blanks", q.group(eLog, new ViewDef(List.of(), null, "f_" + fKind, Bucket.NONE, List.of())).rows().stream().anyMatch(r -> r.label().equals("(none)")));
        check("group respects filters (September only: 3 records)", q.group(eLog, new ViewDef(List.of(flt(dateK, Op.GE, "2026-09-01"), flt(dateK, Op.LE, "2026-09-30")), null, lvK, Bucket.NONE, List.of())).totalCount() == 3);
        check("group sorted by an aggregate, descending (Km total)", q.group(eLog, new ViewDef(List.of(), new Sort(kmK, true), lvK, Bucket.NONE, List.of(new Aggregate(kmK, Agg.SUM)))).rows().get(0).label().equals("KDA 123A"));
        fails("can't group by a calculated field", () -> q.group(eLog, new ViewDef(List.of(), null, kplK, Bucket.NONE, List.of())), "calculated");
        fails("month grouping needs a date", () -> q.group(eLog, new ViewDef(List.of(), null, lvK, Bucket.MONTH, List.of())), "needs a date");
        fails("can't total a text field", () -> q.group(eVeh, new ViewDef(List.of(), null, regK, Bucket.NONE, List.of(new Aggregate(regK, Agg.SUM)))), "not a number");
        fails("grouping needs something to group by", () -> q.group(eLog, ViewDef.ALL), "group by");

        // ── date tokens (relative dates keep saved views correct over time) ──
        String today = LocalDate.now().toString();
        recs.create(eLog, m(lvK, v1, dateK, today, kmK, 7), 0);
        check("@today finds a record dated today", q.list(eLog, view(flt(dateK, Op.EQ, "@today")), null, 10).totalMatching() == 1);
        check("@month_start..@month_end includes today's record", q.list(eLog, view(flt(dateK, Op.GE, "@month_start"), flt(dateK, Op.LE, "@month_end")), null, 100).rows().stream().anyMatch(r -> today.equals(r.values().get(dateK))));
        check("@days_ago_30 is a valid token and is before today", q.list(eLog, view(flt(dateK, Op.GE, "@days_ago_30")), null, 100).totalMatching() >= 1);
        check("@year_start is accepted", q.list(eLog, view(flt(dateK, Op.GE, "@year_start")), null, 100).totalMatching() >= 1);
        fails("unknown token rejected", () -> q.list(eLog, view(flt(dateK, Op.GE, "@next_week")), null, 10), "isn't a valid date");

        // ── saved views ──
        ViewDef sep = new ViewDef(List.of(flt(dateK, Op.GE, "@month_start"), flt(kmK, Op.GT, "5")), new Sort(kmK, true), lvK, Bucket.NONE, List.of(new Aggregate(null, Agg.COUNT), new Aggregate(kmK, Agg.SUM)));
        int id = q.saveView(eLog, "This month by vehicle", sep, 1);
        List<SavedView> views = q.listViews(eLog);
        check("saved view is stored and reloads identically", views.size() == 1 && views.get(0).def().equals(sep) && views.get(0).name().equals("This month by vehicle"));
        q.saveView(eLog, "This month by vehicle", ViewDef.ALL, 1);
        check("saving under the same name replaces it", q.listViews(eLog).size() == 1 && q.listViews(eLog).get(0).def().equals(ViewDef.ALL));
        q.saveView(eLog, "Long trips", view(flt(kmK, Op.GT, "100")), 1);
        check("views are per record type", q.listViews(eLog).size() == 2 && q.listViews(eVeh).isEmpty());
        fails("an invalid view can't be saved", () -> q.saveView(eLog, "Bad", view(flt(kplK, Op.GT, "1")), 1), "calculated");
        fails("a view needs a name", () -> q.saveView(eLog, "  ", ViewDef.ALL, 1), "name");
        q.deleteView(q.listViews(eLog).get(0).id());
        check("a view can be deleted", q.listViews(eLog).size() == 1);

        // ── CSV: parser ──
        List<List<String>> t = CustomCsv.parse("\uFEFFName,Note\r\n\"Smith, J\",\"said \"\"hi\"\"\"\r\n\"multi\nline\",x\r\n");
        check("CSV parser: BOM, quoted commas, doubled quotes, embedded newline, CRLF", t.size() == 3 && t.get(0).get(0).equals("Name") && t.get(1).get(0).equals("Smith, J") && t.get(1).get(1).equals("said \"hi\"") && t.get(2).get(0).equals("multi\nline"));
        check("CSV parser: semicolon-delimited files (Excel in some locales) detected", CustomCsv.parse("a;b;c\n1;2;3").get(1).equals(List.of("1", "2", "3")));

        // ── CSV: export ──
        long vq = recs.create(eVeh, m(regK, "=SUM(A1) \"quoted\", and comma"), 0);
        String csv = CustomCsv.exportList(eVeh, recs.list(eVeh, null, 100), true);
        check("export: header is the field labels", csv.startsWith("Registration\r\n"));
        check("export: commas and quotes are escaped", csv.contains("\"'=SUM(A1) \"\"quoted\"\", and comma\""));
        check("export: a leading '=' is defused (no spreadsheet formula injection)", csv.contains("'=SUM"));
        recs.delete(vq);
        String logCsv = CustomCsv.exportList(eLog, q.list(eLog, view(flt(kmK, Op.EQ, "200")), null, 10).rows(), false);
        check("export: links become titles, dates ISO, numbers plain, calc included", logCsv.contains("KDA 123A") && logCsv.contains("2026-09-05") && logCsv.contains(",200,") && logCsv.contains(",10"));
        String grpCsv = CustomCsv.exportGroup(eLog, "Vehicle", g);
        check("export: grouped report with TOTAL row", grpCsv.startsWith("Vehicle,Count,Total Km,Total Litres,Average Km") && grpCsv.contains("TOTAL,5,660"));

        // ── CSV: import ──
        String in = "vehicle,DATE,Km,Litres,Cost,Kind,Notes\n"
            + "kda 123a,05/10/2026,\"1,200\",80,KES 9 500.50,staff,ignored\n"      // ok: dd/mm/yyyy, thousands, KES, case-insensitive link & choice
            + "KCB 555B,2026-10-06,40,,,Supplies,x\n"                                // ok
            + "NOPE 000,2026-10-07,10,,,,x\n"                                       // unknown vehicle
            + "KDA 123A,07/10/2026,abc,,,,x\n"                                      // bad number
            + "KDA 123A,07/10/2026,9000,,,,x\n"                                     // violates the Km rule
            + "KDA 123A,notadate,10,,,,x\n"                                         // bad date
            + "KDA 123A,08/10/2026,10,,,Flying,x\n"                                 // bad choice
            + ",,,,,,\n"                                                            // blank line: skipped
            + "KDA 123A,09/10/2026,15,,,,x\n";                                      // ok
        int before = recs.count(eLog);
        CustomCsv.ImportPlan plan = CustomCsv.plan(eLog, in);
        check("import plan: columns matched by header ignoring case; unknown 'Notes' reported", plan.mappedColumns().size() == 6 && plan.ignoredColumns().equals(List.of("Notes")));
        check("import plan: 3 valid rows, 5 with errors, blank line skipped", plan.validCount() == 3 && plan.errorCount() == 5 && plan.rows().size() == 8);
        check("import plan: errors carry the spreadsheet row number (header is row 1)", plan.rows().stream().filter(r -> r.error() != null).map(CustomCsv.RowPlan::rowNumber).toList().equals(List.of(4, 5, 6, 7, 8)));
        check("import plan: unknown link message names the value", plan.rows().get(2).error().contains("no existing record called \"NOPE 000\""));
        check("import plan: custom validation message is shown", plan.rows().get(4).error().contains("single trip"));
        check("import plan: bad number / date / choice messages", plan.rows().get(3).error().toLowerCase().contains("number") && plan.rows().get(5).error().contains("isn't a date") && plan.rows().get(6).error().contains("isn't one of"));
        check("nothing is written by planning", recs.count(eLog) == before);
        int created = CustomCsv.commit(eLog, plan, 1);
        check("commit writes only the valid rows (3)", created == 3 && recs.count(eLog) == before + 3);
        var imported = q.list(eLog, view(flt(dateK, Op.EQ, "2026-10-05")), null, 10).rows().get(0).values();
        check("imported values converted: 1,200 km; KES 9 500.50 -> 9500.5; link resolved; choice normalised; date from dd/mm/yyyy",
              ((Number) imported.get(kmK)).doubleValue() == 1200.0 && ((Number) imported.get(costK)).doubleValue() == 9500.5 && ((Number) imported.get(lvK)).longValue() == v1 && imported.get("f_" + fKind).equals("Staff"));
        fails("a file missing a required column can't be imported", () -> { CustomCsv.ImportPlan p = CustomCsv.plan(eLog, "Date,Litres\n2026-10-01,5\n"); if (!p.problems().isEmpty()) throw new CustomModuleException(p.problems().get(0)); }, "no \"Vehicle\" column");
        fails("an empty file is rejected", () -> CustomCsv.plan(eLog, ""), "empty");
        int cnt = recs.count(eLog);
        fails("createBatch is all-or-nothing: one bad row stops the whole batch", () -> recs.createBatch(eLog, List.of(m(lvK, v1, dateK, "2026-10-10", kmK, 5), m(lvK, v1, dateK, "2026-10-10", kmK, 99999)), 1), "row 2");
        check("...and wrote nothing", recs.count(eLog) == cnt);
        check("import is audited as one entry", sql1("SELECT COUNT(*) FROM audit_log WHERE action='CUSTOM_RECORDS_IMPORTED'") != null);

        // ── performance: 200 vehicles, 20,000 logs ──
        List<Map<String,Object>> vs = new ArrayList<>(); for (int i = 0; i < 200; i++) vs.add(m(regK, "KV" + String.format("%03d", i)));
        long t0 = System.nanoTime(); recs.createBatch(eVeh, vs, 1);
        List<RecordView> vehicles = recs.list(eVeh, "KV", 500);
        Random rnd = new Random(42); List<Map<String,Object>> logs = new ArrayList<>();
        for (int i = 0; i < 20000; i++) logs.add(m(lvK, vehicles.get(rnd.nextInt(vehicles.size())).id(), dateK, LocalDate.of(2026, 1 + rnd.nextInt(9), 1 + rnd.nextInt(28)).toString(), kmK, 5 + rnd.nextInt(400), litK, 1 + rnd.nextInt(40), costK, 100 + rnd.nextInt(5000)));
        long t1 = System.nanoTime(); recs.createBatch(eLog, logs, 1); long t2 = System.nanoTime();
        System.out.printf("   perf: created 20,000 records in one transaction: %d ms%n", (t2 - t1) / 1_000_000);
        long a1 = System.nanoTime(); GroupResult big = q.group(eLog, new ViewDef(List.of(), null, lvK, Bucket.NONE, List.of(new Aggregate(null, Agg.COUNT), new Aggregate(kmK, Agg.SUM), new Aggregate(costK, Agg.SUM)))); long a2 = System.nanoTime();
        System.out.printf("   perf: report 'km & cost per vehicle' over 20,000 records (200 groups): %d ms%n", (a2 - a1) / 1_000_000);
        long b1 = System.nanoTime(); GroupResult mon = q.group(eLog, new ViewDef(List.of(flt(kmK, Op.GT, "100")), null, dateK, Bucket.MONTH, List.of(new Aggregate(kmK, Agg.SUM)))); long b2 = System.nanoTime();
        System.out.printf("   perf: report 'km per month, trips over 100 km': %d ms%n", (b2 - b1) / 1_000_000);
        long c1 = System.nanoTime(); ListResult lr = q.list(eLog, view(flt(kmK, Op.GT, "350"), flt(dateK, Op.GE, "2026-06-01")), null, 1000); long c2 = System.nanoTime();
        System.out.printf("   perf: filtered list (1000 rows + totals) over 20,000 records: %d ms  [%d match]%n", (c2 - c1) / 1_000_000, lr.totalMatching());
        long d1 = System.nanoTime(); recs.list(eLog, null, 1000); long d2 = System.nanoTime();
        System.out.printf("   perf: plain list, newest 1000 (with calculated column): %d ms%n", (d2 - d1) / 1_000_000);
        long e1 = System.nanoTime(); recs.list(eLog, "zzzznomatch", 1000); long e2 = System.nanoTime();
        System.out.printf("   perf: free-text search that matches nothing (full scan): %d ms%n", (e2 - e1) / 1_000_000);
        check("perf: 20,000 records stored", recs.count(eLog) >= 20005 && big.totalCount() >= 20005);
        check("perf: grouped report over 20,000 records in under 2 s", (a2 - a1) / 1_000_000 < 2000);
        check("perf: filtered list over 20,000 records in under 2 s", (c2 - c1) / 1_000_000 < 2000);
        check("perf: bulk create of 20,000 in under 15 s", (t2 - t1) / 1_000_000 < 15000);
        check("perf: groups add up (sum of group counts == total)", big.rows().stream().mapToLong(GroupRow::count).sum() == big.totalCount());

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
