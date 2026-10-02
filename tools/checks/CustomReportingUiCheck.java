// UI check for views / reports / subforms / validation-rule fields. Needs a display (xvfb-run).
import com.kaziflow.custom.*;
import com.kaziflow.custom.CustomModuleService.*;
import com.kaziflow.custom.CustomQueryService.*;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.User;
import com.kaziflow.modules.Module;
import com.kaziflow.modules.ModuleRegistry;
import com.kaziflow.services.*;
import com.kaziflow.utils.SessionManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class CustomReportingUiCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static int one(String q) throws Exception { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) { return r.next() ? r.getInt(1) : -1; } }
    static Node byId(Node n, String id) {
        if (id.equals(n.getId())) return n;
        if (n instanceof ScrollPane sp && sp.getContent() != null) { Node r = byId(sp.getContent(), id); if (r != null) return r; }
        if (n instanceof TabPane tp) for (Tab t : tp.getTabs()) if (t.getContent() != null) { Node r = byId(t.getContent(), id); if (r != null) return r; }
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) { Node r = byId(c, id); if (r != null) return r; }
        return null;
    }
    static void collect(Node n, Class<?> k, List<Node> out) {
        if (k.isInstance(n)) out.add(n);
        if (n instanceof ScrollPane sp && sp.getContent() != null) collect(sp.getContent(), k, out);
        if (n instanceof TabPane tp) for (Tab t : tp.getTabs()) if (t.getContent() != null) collect(t.getContent(), k, out);
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) collect(c, k, out);
    }
    static <T> T onFx(Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(c.call()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.get(40, TimeUnit.SECONDS);
    }
    static void shot(Scene sc, String file) throws Exception {
        WritableImage img = sc.snapshot(null);
        BufferedImage bi = new BufferedImage((int) img.getWidth(), (int) img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bi.getHeight(); y++) for (int x = 0; x < bi.getWidth(); x++) bi.setRGB(x, y, img.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(bi, "png", new File(file));
    }
    static FieldDef f(int e, String l, FieldType t, boolean req) { return new FieldDef(0, e, l, t, req, null, List.of(), null, null, null, true, false, 0, false); }
    static Map<String,Object> m(Object... kv) { Map<String,Object> x = new HashMap<>(); for (int i = 0; i < kv.length; i += 2) x.put((String) kv[i], kv[i+1]); return x; }

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        AuditLog.ensureHashColumns();
        ModuleRegistry reg = ModuleRegistry.getInstance(); reg.init();
        try (Connection c = DatabaseManager.getInstance().getConnection()) { PermissionGroupSetup.seed(c, reg.getAllAvailableModules().stream().map(Module::getId).toList()); }
        User u = new User(); u.setId(one("SELECT id FROM users WHERE email='admin@kaziflow.co.ke'")); u.setName("Admin");
        SessionManager.getInstance().login(u);
        PermissionService.getInstance().setRevalidateMillis(0);
        Platform.startup(() -> {});

        CustomModuleService defs = CustomModuleService.getInstance(); CustomRecordService recs = CustomRecordService.getInstance(); CustomQueryService q = CustomQueryService.getInstance();
        int mod = defs.createModule("fleet", "Fleet & Mileage", "F", "Ambulances and staff transport");
        int eVeh = defs.addEntity(mod, "Vehicle", "Vehicles"), eLog = defs.addEntity(mod, "Mileage Log", "Mileage Logs");
        int fReg = defs.addField(f(eVeh, "Registration", FieldType.TEXT, true));
        int fLV = defs.addField(new FieldDef(0, eLog, "Vehicle", FieldType.LOOKUP, true, null, List.of(), eVeh, null, null, true, false, 0, false));
        int fDate = defs.addField(f(eLog, "Date", FieldType.DATE, true));
        int fKm = defs.addField(new FieldDef(0, eLog, "Km", FieldType.DECIMAL, true, null, List.of(), null, null, "km", true, false, 0, false, 0.0, 5000.0, null));
        int fCost = defs.addField(f(eLog, "Cost", FieldType.MONEY, false));
        defs.addField(new FieldDef(0, eVeh, "Total km", FieldType.CALC, false, null, List.of(), null, defs.labelsToKeys(eVeh, "SUMOF(Mileage Log, Km)"), "km", true, false, 0, false));
        long v1 = recs.create(eVeh, m("f_" + fReg, "KDA 123A"), 0), v2 = recs.create(eVeh, m("f_" + fReg, "KCB 555B"), 0);
        recs.createBatch(eLog, List.of(
            m("f_" + fLV, v1, "f_" + fDate, "2026-08-10", "f_" + fKm, 100, "f_" + fCost, 1000), m("f_" + fLV, v1, "f_" + fDate, "2026-09-05", "f_" + fKm, 200, "f_" + fCost, 2000),
            m("f_" + fLV, v1, "f_" + fDate, "2026-09-20", "f_" + fKm, 50, "f_" + fCost, 500), m("f_" + fLV, v2, "f_" + fDate, "2026-09-01", "f_" + fKm, 300, "f_" + fCost, 4000),
            m("f_" + fLV, v2, "f_" + fDate, "2026-10-01", "f_" + fKm, 10)), 0);
        q.saveView(eLog, "Km by vehicle", new ViewDef(List.of(), null, "f_" + fLV, Bucket.NONE, List.of(new Aggregate(null, Agg.COUNT), new Aggregate("f_" + fKm, Agg.SUM), new Aggregate("f_" + fCost, Agg.SUM))), 1);
        q.saveView(eLog, "Long trips", new ViewDef(List.of(new Filter("f_" + fKm, Op.GE, "100")), new Sort("f_" + fKm, true), null, Bucket.NONE, List.of()), 1);

        // ── the module screen: toolbar, views, grouped report ──
        final Scene[] sc = new Scene[1]; final CustomModuleView[] mv = new CustomModuleView[1];
        onFx(() -> { mv[0] = new CustomModuleView(mod); Stage st = new Stage(); sc[0] = new Scene(mv[0].getRoot(), 1180, 620); st.setScene(sc[0]); st.show(); return null; });
        check("toolbar has Customize / Export / Report page / Import / New for each record type", onFx(() -> {
            for (int id : new int[]{eVeh, eLog}) for (String p : new String[]{"customize:", "export:", "report:", "import:", "new:"}) if (byId(sc[0].getRoot(), p + id) == null) return false;
            return true; }));
        @SuppressWarnings("unchecked") ComboBox<Object> viewBox = (ComboBox<Object>) byId(sc[0].getRoot(), "view-box:" + eLog);
        check("the views list offers 'All records' plus the saved views", onFx(() -> viewBox.getItems().size()) == 3 && onFx(() -> viewBox.getItems().get(0)).equals("All records"));
        onFx(() -> { mv[0].applyView(1, q.listViews(eLog).stream().filter(v -> v.name().equals("Km by vehicle")).findFirst().get().def()); return null; });
        List<Node> tables = new ArrayList<>(); onFx(() -> { collect(sc[0].getRoot(), TableView.class, tables); return null; });
        // order of tables: [vehicle list, vehicle group, log list, log group]
        @SuppressWarnings("unchecked") TableView<GroupRow> logGroup = (TableView<GroupRow>) tables.get(3);
        @SuppressWarnings("unchecked") TableView<CustomRecordService.RecordView> logList = (TableView<CustomRecordService.RecordView>) tables.get(2);
        check("grouped report replaces the list: group table visible, list hidden", onFx(() -> logGroup.isVisible() && !logList.isVisible()));
        check("the views dropdown reflects an applied unsaved report as '(customized)'", onFx(() -> viewBox.getValue()).equals("(customized)"));
        check("report rows: one per vehicle + a TOTAL row", onFx(() -> logGroup.getItems().size()) == 3 && onFx(() -> logGroup.getItems().get(2).label()).equals("TOTAL"));
        check("report columns: group, Records, Total Km, Total Cost", onFx(() -> logGroup.getColumns().stream().map(TableColumn::getText).toList()).equals(List.of("Vehicle", "Records", "Total Km", "Total Cost")));
        check("report shows readable vehicle names and totals", onFx(() -> logGroup.getItems().get(0).label().equals("KDA 123A") && logGroup.getItems().get(0).count() == 3 && logGroup.getItems().get(2).count() == 5));
        onFx(() -> { mv[0].selectTab(1); return null; });
        Thread.sleep(300);
        onFx(() -> { shot(sc[0], "/tmp/pt/rep_grouped.png"); return null; });
        onFx(() -> { mv[0].applyView(1, q.listViews(eLog).stream().filter(v -> v.name().equals("Long trips")).findFirst().get().def()); return null; });
        check("switching back to a filtered list: 3 'long trips', biggest first", onFx(() -> logList.isVisible() && !logGroup.isVisible()) && onFx(() -> logList.getItems().size()) == 3
              && ((Number) onFx(() -> logList.getItems().get(0).values().get("f_" + fKm))).doubleValue() == 300.0);
        List<Node> labels = new ArrayList<>(); onFx(() -> { collect(sc[0].getRoot(), Label.class, labels); return null; });
        check("totals line follows the filter (Total Km: 600)", onFx(() -> labels.stream().anyMatch(l -> ((Label) l).getText().contains("Total Km: 600"))));
        onFx(() -> { mv[0].applyView(1, ViewDef.ALL); return null; });
        check("back to all records: 5 rows", onFx(() -> logList.getItems().size()) == 5);
        onFx(() -> { mv[0].applyView(1, new ViewDef(List.of(new Filter("f_" + fKm, Op.GT, "50")), null, null, Bucket.NONE, List.of())); return null; });
        check("an ad-hoc (unsaved) view shows as '(customized)'", onFx(() -> viewBox.getValue()).equals("(customized)"));

        // ── subforms ──
        CustomRecordService.RecordView kda = recs.get(v1);
        final Node[] sub = new Node[1];
        onFx(() -> { sub[0] = CustomRecordForm.buildSubforms(defs.getEntity(eVeh), kda, null); return null; });
        check("a Vehicle has a subform for its Mileage Logs", sub[0] != null);
        check("the child record type has no subform (nothing links to it)", onFx(() -> CustomRecordForm.buildSubforms(defs.getEntity(eLog), recs.list(eLog, null, 1).get(0), null)) == null);
        @SuppressWarnings("unchecked") TableView<CustomRecordService.RecordView> subTable = (TableView<CustomRecordService.RecordView>) onFx(() -> byId(sub[0], "sub-table:" + eLog));
        check("the subform lists ONLY this vehicle's logs (3 of the 5)", onFx(() -> subTable.getItems().size()) == 3);
        check("...and omits the parent-link column (Date, Km, Cost + actions)", onFx(() -> subTable.getColumns().stream().map(TableColumn::getText).filter(t -> !t.isEmpty()).toList()).equals(List.of("Date", "Km", "Cost")));
        Button subAdd = (Button) onFx(() -> byId(sub[0], "sub-add:" + eLog));
        check("administrator may add from the subform", !onFx(subAdd::isDisabled));
        Stage subStage = onFx(() -> { Stage st = new Stage(); st.setScene(new Scene((Parent) sub[0], 700, 330)); st.show(); return st; });
        onFx(() -> { shot(subStage.getScene(), "/tmp/pt/rep_subform.png"); return null; });
        // a read-only position sees the subform with its buttons disabled
        PermissionAdminService adm = PermissionAdminService.getInstance();
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement()) { s.execute("INSERT INTO users (name,email,password_hash,role_id,status) VALUES ('Viewer','vw@x','h',3,'active')"); }
        int vw = one("SELECT id FROM users WHERE email='vw@x'");
        int pos = adm.savePosition(null, "Fleet Viewer", "", Map.of("x_fleet", new PermissionAdminService.ModuleGrant(true, false, false, false)), Set.of(), Set.of());
        adm.saveUserAccess(vw, Set.of(pos), null);
        User vu = new User(); vu.setId(vw); vu.setName("Viewer"); SessionManager.getInstance().login(vu);
        final Node[] sub2 = new Node[1];
        onFx(() -> { sub2[0] = CustomRecordForm.buildSubforms(defs.getEntity(eVeh), kda, null); return null; });
        check("read-only position: subform add button disabled", onFx(() -> byId(sub2[0], "sub-add:" + eLog).isDisabled()));
        final CustomModuleView[] mv2 = new CustomModuleView[1];
        onFx(() -> { mv2[0] = new CustomModuleView(mod); return null; });
        check("read-only position: Import and New are disabled, Export and Customize stay available", onFx(() -> byId(mv2[0].getRoot(), "import:" + eLog).isDisabled() && byId(mv2[0].getRoot(), "new:" + eLog).isDisabled()
              && !byId(mv2[0].getRoot(), "export:" + eLog).isDisabled() && !byId(mv2[0].getRoot(), "customize:" + eLog).isDisabled()));
        SessionManager.getInstance().login(u);

        // ── the view editor dialog (modal): snapshot it from inside its own event loop, then close it ──
        final boolean[] shown = {false};
        // Poll from a helper thread until the modal dialog actually exists (a one-shot task can run before it appears).
        Thread closer = new Thread(() -> {
            for (int i = 0; i < 300 && !shown[0]; i++) {
                try { Thread.sleep(100); } catch (InterruptedException e) { return; }
                Platform.runLater(() -> {
                    for (Window w : new ArrayList<>(Window.getWindows())) {
                        if (!shown[0] && w instanceof Stage st && st.isShowing() && st.getTitle() != null && st.getTitle().startsWith("Customize view")) {
                            try { shot(st.getScene(), "/tmp/pt/rep_editor.png"); shown[0] = true; } catch (Exception ignored) { }
                            st.hide();
                        }
                    }
                });
            }
        });
        closer.setDaemon(true);
        closer.start();
        ViewDef edit = new ViewDef(List.of(new Filter("f_" + fDate, Op.GE, "@month_start"), new Filter("f_" + fKm, Op.GT, "50")), new Sort("f_" + fKm, true), "f_" + fDate, Bucket.MONTH,
            List.of(new Aggregate(null, Agg.COUNT), new Aggregate("f_" + fKm, Agg.SUM), new Aggregate("f_" + fCost, Agg.AVG)));
        onFx(() -> { CustomViewEditorDialog_show(defs.getEntity(eLog), edit); return null; });
        check("the view editor dialog opens with an existing view loaded (screenshot taken)", shown[0]);

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    /** CustomViewEditorDialog is package-private; reach it through the module screen's own entry point via reflection. */
    static void CustomViewEditorDialog_show(EntityDef entity, ViewDef def) throws Exception {
        Class<?> c = Class.forName("com.kaziflow.custom.CustomViewEditorDialog");
        Class<?> listener = Class.forName("com.kaziflow.custom.CustomViewEditorDialog$Listener");
        java.lang.reflect.Method show = c.getDeclaredMethod("show", EntityDef.class, ViewDef.class, SavedView.class, boolean.class, listener);
        show.setAccessible(true);
        Object l = java.lang.reflect.Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (p, m, args) -> null);
        show.invoke(null, entity, def, null, true, l);
    }
}
