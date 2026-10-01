// UI check: build a department through the builder's real controls, then render its generated screen. Needs a display.
import com.kaziflow.custom.*;
import com.kaziflow.custom.CustomModuleService.*;
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
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class CustomModuleUiCheck {
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
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) collect(c, k, out);
    }
    static <T> T onFx(Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(c.call()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.get(30, TimeUnit.SECONDS);
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
        ModuleRegistry reg = ModuleRegistry.getInstance();
        reg.init();
        List<String> ids = reg.getAllAvailableModules().stream().map(Module::getId).toList();
        try (Connection c = DatabaseManager.getInstance().getConnection()) { PermissionGroupSetup.seed(c, ids); }
        User u = new User(); u.setId(one("SELECT id FROM users WHERE email='admin@kaziflow.co.ke'")); u.setName("Admin");
        SessionManager.getInstance().login(u);
        PermissionService.getInstance().setRevalidateMillis(0);
        Platform.startup(() -> {});

        // 1. builder: create the module through its own controls
        final Scene[] bs = new Scene[1]; final CustomModuleBuilderView[] bv = new CustomModuleBuilderView[1];
        onFx(() -> { bv[0] = new CustomModuleBuilderView(); Stage st = new Stage(); bs[0] = new Scene(bv[0].getRoot(), 1060, 700); st.setScene(bs[0]); st.show(); return null; });
        onFx(() -> { ((Button) byId(bs[0].getRoot(), "cmNew")).fire(); return null; });
        onFx(() -> { ((TextField) byId(bs[0].getRoot(), "cmLabel")).setText("Ambulance Services"); return null; });
        check("short name is suggested from the label", onFx(() -> ((TextField) byId(bs[0].getRoot(), "cmKey")).getText()).equals("ambulance_services"));
        onFx(() -> { ((TextField) byId(bs[0].getRoot(), "cmKey")).setText("ambulance"); ((TextField) byId(bs[0].getRoot(), "cmIcon")).setText("A"); return null; });
        onFx(() -> { ((Button) byId(bs[0].getRoot(), "cmSave")).fire(); return null; });
        int mod = one("SELECT id FROM custom_modules WHERE module_key='ambulance'");
        check("module saved through the UI", mod > 0);
        check("registered with the app registry immediately (id x_ambulance)", reg.getAllAvailableModules().stream().anyMatch(x -> x.getId().equals("x_ambulance") && x.isUserDefined()));
        check("administrator can open it right away", reg.isAccessible("x_ambulance"));
        check("builder has marked the sidebar as needing a refresh", bv[0].hasChanges());

        // 2. define its record types and fields (modal dialogs aren't driven here; the service is what they call)
        CustomModuleService defs = CustomModuleService.getInstance(); CustomRecordService recs = CustomRecordService.getInstance();
        int eVeh = defs.addEntity(mod, "Vehicle", "Vehicles"), eLog = defs.addEntity(mod, "Mileage Log", "Mileage Logs");
        int fReg = defs.addField(f(eVeh, "Registration", FieldType.TEXT, true)), fCap = defs.addField(f(eVeh, "Capacity", FieldType.INTEGER, false));
        int fLV = defs.addField(new FieldDef(0, eLog, "Vehicle", FieldType.LOOKUP, true, null, List.of(), eVeh, null, null, true, false, 0, false));
        int fDate = defs.addField(new FieldDef(0, eLog, "Date", FieldType.DATE, true, "today", List.of(), null, null, null, true, false, 0, false));
        int fKm = defs.addField(new FieldDef(0, eLog, "Km", FieldType.DECIMAL, true, null, List.of(), null, null, "km", true, false, 0, false));
        int fL = defs.addField(new FieldDef(0, eLog, "Litres", FieldType.DECIMAL, false, null, List.of(), null, null, "L", true, false, 0, false));
        int fCost = defs.addField(f(eLog, "Cost", FieldType.MONEY, false));
        defs.addField(new FieldDef(0, eLog, "Km per litre", FieldType.CALC, false, null, List.of(), null, defs.labelsToKeys(eLog, "ROUND({Km}/{Litres}, 2)"), null, true, false, 0, false));
        defs.addField(new FieldDef(0, eVeh, "Total km", FieldType.CALC, false, null, List.of(), null, defs.labelsToKeys(eVeh, "SUMOF(Mileage Log, Km)"), "km", true, false, 0, false));
        long v1 = recs.create(eVeh, m("f_" + fReg, "KDA 123A", "f_" + fCap, 4), 0), v2 = recs.create(eVeh, m("f_" + fReg, "KCB 555B", "f_" + fCap, 33), 0);
        recs.create(eLog, m("f_" + fLV, v1, "f_" + fKm, 120.5, "f_" + fL, 14, "f_" + fCost, 1850.5), 0);
        recs.create(eLog, m("f_" + fLV, v1, "f_" + fKm, 80, "f_" + fL, 9.5, "f_" + fCost, 1200), 0);
        recs.create(eLog, m("f_" + fLV, v2, "f_" + fKm, 300, "f_" + fL, 40, "f_" + fCost, 5400), 0);

        // 3. the builder shows what was defined
        onFx(() -> { @SuppressWarnings("unchecked") ListView<ModuleDef> lv = (ListView<ModuleDef>) byId(bs[0].getRoot(), "cmList"); lv.getSelectionModel().clearSelection(); lv.getSelectionModel().select(0); return null; });
        check("builder shows an 'Add field' button for each record type", onFx(() -> byId(bs[0].getRoot(), "cmAddField:" + eVeh) != null && byId(bs[0].getRoot(), "cmAddField:" + eLog) != null));
        onFx(() -> { shot(bs[0], "/tmp/pt/cm_builder.png"); return null; });

        // 4. the generated runtime screen
        final Scene[] rs = new Scene[1];
        onFx(() -> { CustomModuleView v = new CustomModuleView(mod); Stage st = new Stage(); rs[0] = new Scene(v.getRoot(), 1100, 600); st.setScene(rs[0]); st.show(); return null; });
        List<Node> tabsL = new ArrayList<>(); onFx(() -> { collect(rs[0].getRoot(), TabPane.class, tabsL); return null; });
        TabPane tabs = (TabPane) tabsL.get(0);
        check("one tab per record type", onFx(() -> tabs.getTabs().size()) == 2 && onFx(() -> tabs.getTabs().get(1).getText()).equals("Vehicles") == false);
        check("tab titles use the plural labels", onFx(() -> tabs.getTabs().get(0).getText()).equals("Vehicles") && onFx(() -> tabs.getTabs().get(1).getText()).equals("Mileage Logs"));
        List<Node> tables = new ArrayList<>(); onFx(() -> { for (Tab t : tabs.getTabs()) collect(t.getContent(), TableView.class, tables); return null; });
        @SuppressWarnings("unchecked") TableView<CustomRecordService.RecordView> tVeh = (TableView<CustomRecordService.RecordView>) tables.get(0), tLog = (TableView<CustomRecordService.RecordView>) tables.get(1);
        check("vehicle list shows both vehicles", onFx(() -> tVeh.getItems().size()) == 2);
        check("mileage list shows the 3 logs", onFx(() -> tLog.getItems().size()) == 3);
        check("columns = fields marked 'show in list' + actions (Vehicle: Registration, Capacity, Total km, actions)", onFx(() -> tVeh.getColumns().size()) == 4);
        check("calculated 'Total km' column holds the aggregate (KDA: 200.5)", onFx(() -> {
            for (var r : tVeh.getItems()) if ("KDA 123A".equals(r.values().get("f_" + fReg))) return ((Double) r.values().get("f_" + (fCap + 2 > 0 ? defs.listFields(eVeh, false).stream().filter(x -> x.type() == FieldType.CALC).findFirst().get().id() : 0))) == 200.5;
            return false; }));
        List<Node> labels = new ArrayList<>(); onFx(() -> { collect(tabs.getTabs().get(1).getContent(), Label.class, labels); return null; });
        check("totals line for numeric columns (Total Km: 500.5)", onFx(() -> labels.stream().anyMatch(l -> ((Label) l).getText().contains("Total Km: 500.5"))));
        check("totals line includes money (Total Cost: KES 8,450.50)", onFx(() -> labels.stream().anyMatch(l -> ((Label) l).getText().contains("KES 8,450.50"))));
        onFx(() -> { tabs.getSelectionModel().select(1); return null; });
        Thread.sleep(300);
        onFx(() -> { shot(rs[0], "/tmp/pt/cm_runtime.png"); return null; });

        // 5. permissions: another user has no access until a Position grants it
        PermissionAdminService adm = PermissionAdminService.getInstance();
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement()) { s.execute("INSERT INTO users (name,email,password_hash,role_id,status) VALUES ('Driver','drv@x','h',3,'active')"); }
        int drv = one("SELECT id FROM users WHERE email='drv@x'");
        int cashier = one("SELECT id FROM permission_groups WHERE name='Cashier'");
        adm.saveUserAccess(drv, Set.of(cashier), null);
        User du = new User(); du.setId(drv); du.setName("Driver"); SessionManager.getInstance().login(du);
        check("a Cashier cannot open the new module (default deny)", !reg.isAccessible("x_ambulance"));
        int pos = adm.savePosition(null, "Fleet Officer", "", Map.of("x_ambulance", PermissionAdminService.ModuleGrant.ALL, "dashboard", new PermissionAdminService.ModuleGrant(true,false,false,false)), Set.of(), Set.of());
        adm.saveUserAccess(drv, Set.of(cashier, pos), null);
        check("a Position granting x_ambulance opens it, live", reg.isAccessible("x_ambulance"));
        adm.savePosition(pos, "Fleet Officer", "", Map.of("x_ambulance", new PermissionAdminService.ModuleGrant(true,false,false,false)), null, null);
        List<Node> btns = new ArrayList<>();
        onFx(() -> { CustomModuleView v = new CustomModuleView(mod); collect(v.getRoot(), Button.class, btns); return null; });
        check("read-only position: '+ New' button is disabled in the generated screen", onFx(() -> btns.stream().filter(b -> ((Button) b).getText().startsWith("+ New")).allMatch(Node::isDisabled)));
        SessionManager.getInstance().login(u);
        btns.clear();
        onFx(() -> { CustomModuleView v = new CustomModuleView(mod); collect(v.getRoot(), Button.class, btns); return null; });
        check("administrator: '+ New' enabled", onFx(() -> btns.stream().filter(b -> ((Button) b).getText().startsWith("+ New")).noneMatch(Node::isDisabled)));
        // 6. deactivation removes it from the app
        defs.updateModule(mod, "Ambulance Services", "A", "", false); CustomModuleLoader.sync();
        check("deactivated module is removed from the registry (and its data is kept)", reg.getAllAvailableModules().stream().noneMatch(x -> x.getId().equals("x_ambulance")) && recs.count(eLog) == 3);

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
