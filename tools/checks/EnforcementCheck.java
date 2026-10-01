// Checks enforcement wiring + the "admin adds a module later" flow against a real SQLite DB.
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.User;
import com.kaziflow.modules.Module;
import com.kaziflow.modules.ModuleRegistry;
import com.kaziflow.services.*;
import com.kaziflow.services.PermissionAdminService.ModuleGrant;
import com.kaziflow.utils.SessionManager;
import javafx.scene.layout.Region;

import java.sql.*;
import java.util.*;

public class EnforcementCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static Connection c() throws Exception { return DatabaseManager.getInstance().getConnection(); }
    static void sql(String q) throws Exception { try (Connection c = c(); Statement s = c.createStatement()) { s.execute(q); } }
    static int one(String q) throws Exception { try (Connection c = c(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) { return r.next() ? r.getInt(1) : -1; } }
    static void login(int id) { User u = new User(); u.setId(id); u.setName("u" + id); SessionManager.getInstance().login(u); }
    static Set<String> ids(List<Module> l) { Set<String> s = new HashSet<>(); l.forEach(m -> s.add(m.getId())); return s; }

    /** A module an administrator "built" after installation. */
    static class TransportModule implements Module {
        public String getId() { return "staff_transport"; }
        public String getLabel() { return "Staff Transport"; }
        public String getIcon() { return "T"; }
        public Region buildView() { return new Region(); }
        @Override public boolean isUserDefined() { return true; }
    }

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        ModuleRegistry reg = ModuleRegistry.getInstance();
        reg.init();
        List<String> mods = reg.getAllAvailableModules().stream().map(Module::getId).toList();
        try (Connection c = c()) { PermissionGroupSetup.seed(c, mods); }
        PermissionService ps = PermissionService.getInstance();
        ps.setRevalidateMillis(0);
        PermissionAdminService admin = PermissionAdminService.getInstance();

        int adminU = one("SELECT id FROM users WHERE email='admin@kaziflow.co.ke'");
        sql("INSERT INTO users (name,email,password_hash,role_id,status) VALUES ('Cash','cash@x','h',3,'active'),('Nobody','none@x','h',3,'active')");
        int cash = one("SELECT id FROM users WHERE email='cash@x'"), nobody = one("SELECT id FROM users WHERE email='none@x'");
        admin.saveUserAccess(cash, Set.of(one("SELECT id FROM permission_groups WHERE name='Cashier'")), null);
        admin.saveUserAccess(nobody, Set.of(), null);

        login(adminU);
        Set<String> adminSees = ids(reg.getNavModules()); adminSees.addAll(ids(reg.getBottomNavModules()));
        check("admin sees every enabled module, incl. settings (bottom nav)", adminSees.containsAll(Set.of("settings", "sales", "inventory", "dashboard")));
        check("admin lands on dashboard", "dashboard".equals(reg.getFirstAccessibleModuleId()));
        login(cash);
        Set<String> nav = ids(reg.getNavModules());
        check("cashier sidebar: has sales, no finance/settings", nav.contains("sales") && !nav.contains("finance") && !nav.contains("settings"));
        check("isAccessible() backs navigateTo: finance blocked, sales allowed", !reg.isAccessible("finance") && reg.isAccessible("sales"));
        check("Help is open to every signed-in user", ids(reg.getBottomNavModules()).contains("help") && reg.isAccessible("help"));
        login(nobody);
        check("user with no positions: empty sidebar, nothing to land on (-> 'no access' page)", ids(reg.getNavModules()).isEmpty() && reg.getFirstAccessibleModuleId() == null);
        check("...but still has Help", reg.isAccessible("help"));
        SessionManager.getInstance().logout();
        check("signed out: nothing accessible (not even Help)", !reg.isAccessible("help") && !reg.isAccessible("sales"));

        // ---- the admin-adds-a-module flow ----
        check("module not present before it is registered", reg.getAllAvailableModules().stream().noneMatch(m -> m.getId().equals("staff_transport")));
        check("registerModule adds it at run time", reg.registerModule(new TransportModule()));
        check("duplicate id refused", !reg.registerModule(new TransportModule()));
        check("it now appears in getAllAvailableModules() (what the Positions editor lists)",
              reg.getAllAvailableModules().stream().anyMatch(m -> m.getId().equals("staff_transport")));
        login(adminU);
        check("administrator can open the new module immediately (bypass)", reg.isEnabled("staff_transport") && reg.isAccessible("staff_transport"));
        login(cash);
        check("cashier cannot (default deny — new modules are never auto-granted)", !reg.isAccessible("staff_transport"));
        int driverPos = admin.savePosition(null, "Transport Officer", "", Map.of("staff_transport", ModuleGrant.ALL, "dashboard", new ModuleGrant(true,false,false,false)), Set.of(), Set.of());
        admin.saveUserAccess(cash, Set.of(one("SELECT id FROM permission_groups WHERE name='Cashier'"), driverPos), null);
        check("after granting via a Position, the holder sees it (live, no restart/re-login)", reg.isAccessible("staff_transport") && ids(reg.getNavModules()).contains("staff_transport"));
        login(nobody);
        check("someone without that position still can't", !reg.isAccessible("staff_transport"));

        // ---- special actions used at enforcement sites ----
        login(cash);
        check("cashier: PermissionGuard-style checks deny seeded special actions", !ps.canPerform(SpecialPermissions.VOID_SALE) && !ps.canPerform(SpecialPermissions.APPLY_DISCOUNT) && !ps.canPerform(SpecialPermissions.VIEW_COST_PRICE));
        admin.savePosition(driverPos, "Transport Officer", "", null, null, Set.of(SpecialPermissions.VIEW_COST_PRICE));
        check("granting view_cost_price to a position applies immediately", ps.canPerform(SpecialPermissions.VIEW_COST_PRICE) && !ps.canPerform(SpecialPermissions.VOID_SALE));

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
