// Runtime check of the custom-module engine (definitions + records + formulas) on a real SQLite DB.
import com.kaziflow.custom.*;
import com.kaziflow.custom.CustomModuleService.*;
import com.kaziflow.custom.CustomRecordService.*;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.services.AuditLog;

import java.sql.*;
import java.util.*;

public class CustomModuleCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static boolean fails(String n, Runnable0 r, String mustContain) {
        try { r.run(); check(n, false); return false; }
        catch (Exception e) { boolean ok = mustContain == null || e.getMessage().toLowerCase().contains(mustContain.toLowerCase()); if (!ok) System.out.println("   got: " + e.getMessage()); check(n, ok); return ok; }
    }
    interface Runnable0 { void run() throws Exception; }
    static FieldDef f(int entity, String label, FieldType t, boolean req) { return new FieldDef(0, entity, label, t, req, null, List.of(), null, null, null, true, false, 0, false); }
    static FieldDef lookup(int entity, String label, int target, boolean req) { return new FieldDef(0, entity, label, FieldType.LOOKUP, req, null, List.of(), target, null, null, true, false, 0, false); }
    static FieldDef calc(int entity, String label, String keyFormula) { return new FieldDef(0, entity, label, FieldType.CALC, false, null, List.of(), null, keyFormula, null, true, false, 0, false); }
    static String sql1(String q) throws Exception { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) { return r.next() ? r.getString(1) : null; } }
    static Map<String,Object> m(Object... kv) { Map<String,Object> x = new HashMap<>(); for (int i = 0; i < kv.length; i += 2) x.put((String) kv[i], kv[i+1]); return x; }

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        AuditLog.ensureHashColumns();
        CustomModuleService defs = CustomModuleService.getInstance();
        CustomRecordService recs = CustomRecordService.getInstance();

        // ── formula engine on its own ──
        FormulaEngine.Context ctx = new FormulaEngine.Context() {
            public Double value(String k) { return switch (k) { case "f_1" -> 10.0; case "f_2" -> 4.0; case "f_3" -> 0.0; default -> null; }; }
            public double sumOf(long e, String k) { return 42; } public double countOf(long e) { return 3; } };
        Object[][] cases = {{"{f_1} + {f_2} * 2", 18.0}, {"({f_1} + {f_2}) * 2", 28.0}, {"-{f_1} + 3", -7.0}, {"ROUND({f_1}/3, 2)", 3.33},
            {"IF({f_1} > {f_2}, 1, 0)", 1.0}, {"MAX({f_1},{f_2},99)", 99.0}, {"MIN({f_1},{f_2})", 4.0}, {"ABS(0-5)", 5.0},
            {"SUMOF(e3, f_9) + COUNTOF(e3)", 45.0}, {"{f_1}/{f_3}", null}, {"{f_1} + {f_99}", null}, {"IF({f_1} <> {f_2}, 7, 8)", 7.0}};
        for (Object[] c : cases) {
            Double r = FormulaEngine.parse((String) c[0]).evaluate(ctx);
            boolean ok = c[1] == null ? r == null : r != null && Math.abs(r - (Double) c[1]) < 1e-9;
            check("formula  " + c[0] + "  =  " + (c[1] == null ? "empty" : c[1]), ok);
        }
        for (String bad : new String[]{"{f_1} +", "(1+2", "SYSTEM('rm -rf /')", "{f_1}; DROP TABLE users", "1 + abc", "{name}", "IF(1,2)", "SUMOF(e3)", "1 2"}) {
            boolean rejected; try { FormulaEngine.parse(bad); rejected = false; } catch (FormulaEngine.FormulaException e) { rejected = true; }
            check("rejected at parse time: " + bad, rejected);
        }

        // ── build the HMIS §37 'Vehicles & Mileage' module as an administrator would ──
        fails("module key with spaces/caps rejected", () -> defs.createModule("My Module!", "X", null, null), "short name");
        int mod = defs.createModule("vehicles", "Vehicles & Mileage", "V", "Ambulances and staff transport");
        check("module created, app id is x_vehicles", defs.getModule(mod).moduleId().equals("x_vehicles"));
        fails("duplicate module key rejected", () -> defs.createModule("vehicles", "Again", null, null), "already exists");
        int eType = defs.addEntity(mod, "Vehicle Type", null), eVeh = defs.addEntity(mod, "Vehicle", null), eLog = defs.addEntity(mod, "Mileage Log", "Mileage Logs");
        check("3 entities in order", defs.listEntities(mod).size() == 3 && defs.listEntities(mod).get(2).pluralLabel().equals("Mileage Logs"));

        int fTypeName = defs.addField(f(eType, "Name", FieldType.TEXT, true));
        check("first text field automatically becomes the record title", defs.getEntity(eType).titleFieldId() == fTypeName);
        int fReg = defs.addField(new FieldDef(0, eVeh, "Registration", FieldType.TEXT, true, null, List.of(), null, null, null, true, true, 0, false));
        check("searchable field created an expression index (the only DDL)", sql1("SELECT name FROM sqlite_master WHERE type='index' AND name='ix_cx_f" + fReg + "'") != null);
        int fVType = defs.addField(lookup(eVeh, "Vehicle Type", eType, true));
        int fCap = defs.addField(f(eVeh, "Capacity", FieldType.INTEGER, false));
        int fStatus = defs.addField(new FieldDef(0, eVeh, "Status", FieldType.CHOICE, false, "In service", List.of("In service", "Under repair", "Retired"), null, null, null, true, false, 0, false));
        int fLV = defs.addField(lookup(eLog, "Vehicle", eVeh, true));
        int fDate = defs.addField(new FieldDef(0, eLog, "Date", FieldType.DATE, true, "today", List.of(), null, null, null, true, false, 0, false));
        int fKm = defs.addField(f(eLog, "Km", FieldType.DECIMAL, true));
        int fL = defs.addField(f(eLog, "Litres", FieldType.DECIMAL, false));
        int fCost = defs.addField(f(eLog, "Cost", FieldType.MONEY, false));
        fails("duplicate field label rejected (case-insensitive)", () -> defs.addField(f(eLog, "km", FieldType.DECIMAL, false)), "already has");
        fails("choice field with no options rejected", () -> defs.addField(f(eVeh, "Colour", FieldType.CHOICE, false)), "option");
        fails("link field without a target rejected", () -> defs.addField(f(eVeh, "Driver", FieldType.LOOKUP, false)), "links to");
        int fKpl = defs.addField(calc(eLog, "Km per litre", defs.labelsToKeys(eLog, "ROUND({Km} / {Litres}, 2)")));
        int fTotKm = defs.addField(calc(eVeh, "Total km", defs.labelsToKeys(eVeh, "SUMOF(Mileage Log, Km)")));
        int fTrips = defs.addField(calc(eVeh, "Trips logged", defs.labelsToKeys(eVeh, "COUNTOF(Mileage Log)")));
        check("labels convert to immutable keys in stored formulas", defs.getField(fKpl).formula().equals("ROUND({f_" + fKm + "} / {f_" + fL + "}, 2)"));
        check("keys convert back to readable labels", defs.keysToLabels(eLog, defs.getField(fKpl).formula()).equals("ROUND({Km} / {Litres}, 2)"));
        fails("formula using a missing field rejected", () -> defs.addField(calc(eLog, "Bad", defs.labelsToKeys(eLog, "{Nope} + 1"))), "no field called");
        fails("formula using a non-numeric field rejected", () -> defs.addField(calc(eLog, "Bad2", "{f_" + fDate + "} + 1")), "not a number");
        fails("formula with a syntax error rejected", () -> defs.addField(calc(eLog, "Bad3", "{f_" + fKm + "} +")), "formula problem");

        // ── records ──
        long tAmb = recs.create(eType, m("f_" + fTypeName, "Ambulance"), 0), tBus = recs.create(eType, m("f_" + fTypeName, "Staff Bus"), 0);
        fails("required field missing rejected", () -> recs.create(eType, m(), 0), "required");
        long v1 = recs.create(eVeh, m("f_" + fReg, "KDA 123A", "f_" + fVType, tAmb, "f_" + fCap, "4"), 0);
        long v2 = recs.create(eVeh, m("f_" + fReg, "KCB 555B", "f_" + fVType, new LookupOption(tBus, "Staff Bus"), "f_" + fCap, 33), 0);
        check("choice default applied on create", recs.get(v1).values().get("f_" + fStatus).equals("In service"));
        fails("link to a record that doesn't exist rejected", () -> recs.create(eVeh, m("f_" + fReg, "X", "f_" + fVType, 99999), 0), "doesn't exist");
        fails("link to a record of the WRONG entity rejected", () -> recs.create(eVeh, m("f_" + fReg, "X", "f_" + fVType, v1), 0), "doesn't exist");
        fails("whole-number field rejects 4.5", () -> recs.create(eVeh, m("f_" + fReg, "X", "f_" + fVType, tAmb, "f_" + fCap, "4.5"), 0), "whole number");
        fails("number field rejects text", () -> recs.create(eLog, m("f_" + fLV, v1, "f_" + fKm, "abc"), 0), "number");
        fails("choice outside the list rejected", () -> recs.create(eVeh, m("f_" + fReg, "X", "f_" + fVType, tAmb, "f_" + fStatus, "Flying"), 0), "options");
        fails("bad date rejected", () -> recs.create(eLog, m("f_" + fLV, v1, "f_" + fKm, 5, "f_" + fDate, "31/12/2026"), 0), "valid date");
        check("nothing was stored by any rejected attempt", recs.count(eVeh) == 2 && recs.count(eLog) == 0);

        long l1 = recs.create(eLog, m("f_" + fLV, v1, "f_" + fKm, "120.5", "f_" + fL, "14", "f_" + fCost, "1,850.555"), 7);
        long l2 = recs.create(eLog, m("f_" + fLV, v1, "f_" + fKm, 80, "f_" + fL, 0), 7);
        long l3 = recs.create(eLog, m("f_" + fLV, v2, "f_" + fKm, 300, "f_" + fL, 40), 7);
        long l4 = recs.create(eLog, m("f_" + fLV, v2, "f_" + fKm, 10), 7);
        check("date default 'today' applied", recs.get(l1).values().get("f_" + fDate).equals(java.time.LocalDate.now().toString()));
        check("money stored to 2 decimals", ((Double) recs.get(l1).values().get("f_" + fCost)) == 1850.56);
        check("calc: km per litre = 120.5/14 = 8.61", ((Double) recs.get(l1).values().get("f_" + fKpl)) == 8.61);
        check("calc: divide by zero litres -> empty, not an error", recs.get(l2).values().get("f_" + fKpl) == null);
        check("calc: missing litres -> empty", recs.get(l4).values().get("f_" + fKpl) == null);
        check("aggregate: vehicle 1 total km = 120.5+80 = 200.5", ((Double) recs.get(v1).values().get("f_" + fTotKm)) == 200.5);
        check("aggregate: vehicle 2 total km = 310, 2 trips", ((Double) recs.get(v2).values().get("f_" + fTotKm)) == 310.0 && ((Double) recs.get(v2).values().get("f_" + fTrips)) == 2.0);

        // ── integrity ──
        fails("can't delete a vehicle that has mileage logs", () -> recs.delete(v1), "still used");
        recs.delete(l1); recs.delete(l2);
        recs.delete(v1);
        check("soft delete: gone from get() and list()", recs.get(v1) == null && recs.list(eVeh, null, 100).size() == 1);
        check("soft delete keeps the row (nothing is destroyed)", sql1("SELECT COUNT(*) FROM custom_records WHERE id=" + v1 + " AND deleted_at IS NOT NULL").equals("1"));
        check("lookup picker shows record titles", recs.lookupOptions(eType).stream().map(LookupOption::title).toList().equals(List.of("Ambulance", "Staff Bus")));

        // ── search (incl. LIKE wildcards treated literally) ──
        recs.create(eVeh, m("f_" + fReg, "100%_SPECIAL", "f_" + fVType, tAmb), 0);
        check("search finds by substring, case-insensitive", recs.list(eVeh, "kcb", 50).size() == 1);
        check("search treats % and _ literally (no wildcard injection)", recs.list(eVeh, "%", 50).size() == 1 && recs.list(eVeh, "_", 50).size() == 1);
        check("search looks at VALUES only, never internal field keys (searching \"f_\" or \"f\" finds nothing spurious)", recs.list(eVeh, "f_", 50).isEmpty() && recs.list(eVeh, "type", 50).isEmpty());
        check("search with SQL-ish text is just text", recs.list(eVeh, "'; DROP TABLE custom_records;--", 50).isEmpty() && recs.count(eVeh) == 2);

        // ── evolution without migration ──
        defs.updateField(fKm, "Distance (km)", true, null, List.of(), null, "km", true, false);
        check("renaming a label doesn't touch stored data or formulas", ((Double) recs.get(l3).values().get("f_" + fKm)) == 300.0 && defs.keysToLabels(eLog, defs.getField(fKpl).formula()).contains("{Distance (km)}"));
        int fNote = defs.addField(f(eLog, "Driver note", FieldType.TEXT, false));
        check("new field on existing records needs no migration (old rows simply empty)", recs.get(l3).values().get("f_" + fNote) == null);
        recs.update(l3, m("f_" + fNote, "Night run"));
        check("update changes only the given fields", recs.get(l3).values().get("f_" + fNote).equals("Night run") && ((Double) recs.get(l3).values().get("f_" + fKm)) == 300.0);
        fails("making a required field blank on update is rejected", () -> recs.update(l3, m("f_" + fKm, "")), "required");
        fails("archiving a field a formula depends on is blocked", () -> defs.archiveField(fL, true), "uses this field");
        defs.archiveField(fNote, true);
        check("archived field disappears from forms but its value is kept in the record", defs.listFields(eLog, false).stream().noneMatch(x -> x.id() == fNote)
              && sql1("SELECT data FROM custom_records WHERE id=" + l3).contains("Night run"));
        defs.updateField(fReg, "Registration", true, null, List.of(), null, null, true, false);
        check("un-marking searchable drops the index", sql1("SELECT name FROM sqlite_master WHERE name='ix_cx_f" + fReg + "'") == null);

        // ── structural guards ──
        fails("entity with records can't be deleted", () -> defs.deleteEntity(eType), "holds");
        fails("module with records can't be deleted (deactivate instead)", () -> defs.deleteModule(mod), "deactivate");
        defs.updateModule(mod, "Vehicles & Mileage", "V", "x", false);
        check("module can be deactivated", !defs.getModule(mod).active() && defs.listModules(true).isEmpty());
        int empty = defs.createModule("scratch", "Scratch", null, null);
        defs.deleteModule(empty);
        check("an empty module can be deleted", defs.getModule(empty) == null);
        check("the database rejects malformed JSON even if code were bypassed", sqlFails("INSERT INTO custom_records (entity_id,data) VALUES (" + eLog + ",'not json')"));

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
    static boolean sqlFails(String q) { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement()) { s.execute(q); return false; } catch (Exception e) { return true; } }
}
