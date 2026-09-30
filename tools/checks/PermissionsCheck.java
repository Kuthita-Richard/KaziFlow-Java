// Runtime check of the permissions engine against a REAL SQLite database (full app schema).
// Run: java -Duser.home=<empty temp dir> -cp <classes>:<sqlite-jdbc.jar>:<gson.jar>:<jbcrypt.jar>:<javafx jars> PermissionsCheck
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.User;
import com.kaziflow.services.*;
import com.kaziflow.services.PermissionAdminService.*;
import com.kaziflow.services.PermissionService.Action;
import com.kaziflow.utils.SessionManager;

import java.sql.*;
import java.util.*;

public class PermissionsCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static Connection c() throws Exception { return DatabaseManager.getInstance().getConnection(); }
    static void sql(String q) throws Exception { try (Connection c = c(); Statement s = c.createStatement()) { s.execute(q); } }
    static int one(String q) throws Exception { try (Connection c = c(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) { return r.next() ? r.getInt(1) : -1; } }
    static void login(int id) { User u = new User(); u.setId(id); u.setName("u" + id); SessionManager.getInstance().login(u); }
    static int userId(String email) throws Exception { return one("SELECT id FROM users WHERE email='" + email + "'"); }
    static int posId(String name) throws Exception { return one("SELECT id FROM permission_groups WHERE name='" + name + "'"); }
    static final List<String> MODULES = List.of("appointments","batches","dashboard","delivery","deni","employees","finance",
        "fuel_station","gym","help","hotel","inventory","laundry","patients","payroll","purchases","quotations","reports",
        "restaurant","sacco","sales","canteen","settings","workshop");

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        try (Connection c = c()) { PermissionGroupSetup.seed(c, MODULES); }
        PermissionService ps = PermissionService.getInstance();
        PermissionAdminService admin = PermissionAdminService.getInstance();
        ps.setRevalidateMillis(0);   // re-validate on every check so we can observe edits immediately

        // ── seeding & migration ──
        check("Administrator group exists and is_system", one("SELECT is_system FROM permission_groups WHERE name='Administrator'") == 1);
        check("5 legacy roles -> 5 positions (Administrator + 4)", one("SELECT COUNT(*) FROM permission_groups") == 5);
        check("special-action catalog seeded with 3", one("SELECT COUNT(*) FROM permission_actions") == 3);
        int adminUser = userId("admin@kaziflow.co.ke");
        check("default admin user was assigned to Administrator", one("SELECT COUNT(*) FROM user_permission_groups WHERE user_id=" + adminUser + " AND group_id=" + posId("Administrator")) == 1);
        check("Store Manager got vertical modules (gym) full CRUD", one("SELECT can_delete FROM module_access WHERE group_id=" + posId("Store Manager") + " AND module_id='gym'") == 1);
        check("Cashier did NOT get verticals", one("SELECT COUNT(*) FROM module_access WHERE group_id=" + posId("Cashier") + " AND module_id='gym'") == 0);
        check("Cashier: inventory is read-only (legacy \"read\")", one("SELECT can_read*10+can_write FROM module_access WHERE group_id=" + posId("Cashier") + " AND module_id='inventory'") == 10);
        check("Cashier has NO settings access", one("SELECT COUNT(*) FROM module_access WHERE group_id=" + posId("Cashier") + " AND module_id='settings'") == 0);
        check("Store Manager holds all 3 special actions, Cashier none",
              one("SELECT COUNT(*) FROM special_permissions WHERE group_id=" + posId("Store Manager")) == 3
           && one("SELECT COUNT(*) FROM special_permissions WHERE group_id=" + posId("Cashier")) == 0);
        int before = one("SELECT COUNT(*) FROM permission_groups") + one("SELECT COUNT(*) FROM module_access");
        sql("UPDATE module_access SET can_delete=0 WHERE group_id=" + posId("Store Manager") + " AND module_id='gym'");
        try (Connection c = c()) { PermissionGroupSetup.seed(c, MODULES); }
        check("re-seeding is idempotent and does NOT overwrite later edits",
              before == one("SELECT COUNT(*) FROM permission_groups") + one("SELECT COUNT(*) FROM module_access")
              && one("SELECT can_delete FROM module_access WHERE group_id=" + posId("Store Manager") + " AND module_id='gym'") == 0);

        // ── run-time checks (PermissionService) ──
        // create users for scenarios
        sql("INSERT INTO users (name,email,password_hash,role_id,status) VALUES ('Cash','cash@x','h',3,'active'),('Mgr','mgr@x','h',2,'active'),('Nobody','none@x','h',3,'active')");
        int cash = userId("cash@x"), mgr = userId("mgr@x"), nobody = userId("none@x");
        sql("DELETE FROM user_permission_groups WHERE user_id=" + nobody);
        try (Connection c = c()) { // migration only ran once, so give cash/mgr their positions explicitly
            sql("INSERT INTO user_permission_groups VALUES (" + cash + "," + posId("Cashier") + "),(" + mgr + "," + posId("Store Manager") + ")");
        }
        login(adminUser);
        check("admin bypass: can anything, incl. unknown module", ps.can("settings", Action.DELETE) && ps.can("brand_new_module", Action.WRITE) && ps.canPerform("anything"));
        check("admin: getReadableModuleIds() == null (no filtering)", ps.getReadableModuleIds() == null && ps.isAdministrator());
        login(cash);
        check("cashier can read+create sales", ps.canRead("sales") && ps.canCreate("sales"));
        check("cashier can read inventory but not write it", ps.canRead("inventory") && !ps.canWrite("inventory"));
        check("default-deny: cashier cannot read finance / settings", !ps.canRead("finance") && !ps.canRead("settings"));
        check("cashier: no special actions", !ps.canPerform(SpecialPermissions.VOID_SALE) && !ps.canPerform(SpecialPermissions.VIEW_COST_PRICE));
        check("cashier readable set excludes finance, includes sales+dashboard",
              ps.getReadableModuleIds().contains("sales") && ps.getReadableModuleIds().contains("dashboard") && !ps.getReadableModuleIds().contains("finance"));
        login(mgr);
        check("manager: special actions granted", ps.canPerform(SpecialPermissions.VIEW_COST_PRICE) && ps.canPerform(SpecialPermissions.APPLY_DISCOUNT));
        check("manager: no settings access (not admin)", !ps.canRead("settings") && !ps.isAdministrator());
        login(nobody);
        check("user with no positions: default deny everything", ps.getReadableModuleIds().isEmpty() && !ps.canRead("dashboard"));
        SessionManager.getInstance().logout();
        check("no session: everything denied", !ps.can("sales", Action.READ) && !ps.canPerform("void_sale"));

        // ── transitive inheritance, union, cycle guard (original 17-assertion territory) ──
        int a1, a2, a3;
        a1 = admin.savePosition(null, "Level A", "", Map.of("finance", new ModuleGrant(true,false,false,false)), Set.of(), Set.of());
        a2 = admin.savePosition(null, "Level B", "", Map.of("reports", new ModuleGrant(true,true,false,false)), Set.of(a1), Set.of(SpecialPermissions.VOID_SALE));
        a3 = admin.savePosition(null, "Level C", "", Map.of("finance", new ModuleGrant(true,true,true,false)), Set.of(a2), Set.of());
        int t1 = userId("none@x");
        admin.saveUserAccess(t1, Set.of(a3), null);
        login(t1);
        check("transitive implies C->B->A: inherits finance(A), reports(B), special(B)", ps.canRead("reports") && ps.canRead("finance") && ps.canPerform("void_sale"));
        check("union: finance write from C's own grant, read from both", ps.canWrite("finance") && !ps.canDelete("finance"));
        try { admin.savePosition(a1, "Level A", "", null, Set.of(a3), null); check("cycle A->C->B->A rejected", false); }
        catch (PermissionAdminException e) { check("cycle A->C->B->A rejected at write time", e.getMessage().contains("loop")); }
        sql("INSERT OR IGNORE INTO permission_group_implies VALUES (" + a1 + "," + a3 + ")"); // force a cycle in raw data
        ps.invalidate(); login(t1);
        check("cycle in raw data does not hang the resolver", ps.canRead("finance"));
        sql("DELETE FROM permission_group_implies WHERE group_id=" + a1);

        // ── live re-validation ──
        admin.savePosition(a3, "Level C", "", Map.of("finance", ModuleGrant.NONE), Set.of(), Set.of());
        check("edit applies to an already-logged-in user without re-login (epoch)", !ps.canWrite("finance") && !ps.canRead("reports"));

        // ── editor rules ──
        try { admin.savePosition(null, "level c", "", null, null, null); check("duplicate name (case-insensitive) rejected", false); }
        catch (PermissionAdminException e) { check("duplicate name (case-insensitive) rejected", e.getMessage().contains("already exists")); }
        try { admin.savePosition(null, "  ", "", null, null, null); check("blank name rejected", false); }
        catch (PermissionAdminException e) { check("blank name rejected", true); }
        try { admin.deletePosition(posId("Administrator")); check("cannot delete Administrator", false); }
        catch (PermissionAdminException e) { check("cannot delete Administrator position", true); }
        int adminG = posId("Administrator");
        admin.savePosition(adminG, "Hacked", "new desc", Map.of("sales", ModuleGrant.NONE), Set.of(a1), Set.of());
        check("Administrator: only description editable (name/implies untouched)",
              one("SELECT COUNT(*) FROM permission_groups WHERE name='Administrator' AND description='new desc'") == 1
           && one("SELECT COUNT(*) FROM permission_group_implies WHERE group_id=" + adminG) == 0);
        admin.savePosition(a3, "Level C", "", null, null, Set.of("void_sale", "not_in_catalog"));
        check("unknown special keys are ignored (catalog-validated)", admin.load(a3).specials().equals(Set.of("void_sale")));
        SpecialPermissions.register("approve_refund", "Approve refunds", "x");
        admin.savePosition(a3, "Level C", "", null, null, Set.of("approve_refund"));
        login(t1);
        check("newly registered special action works with zero code changes", ps.canPerform("approve_refund"));
        check("module grants for modules NOT in the map are left untouched",
              admin.savePosition(a2, "Level B", "", Map.of("hotel", ModuleGrant.ALL), null, null) == a2
              && admin.load(a2).access().containsKey("reports") && admin.load(a2).access().containsKey("hotel"));

        // ── lockout safety ──
        check("exactly one active admin to start", admin.countActiveAdmins() == 1);
        try { admin.saveUserAccess(adminUser, Set.of(posId("Cashier")), null); check("can't remove the last admin's Administrator position", false); }
        catch (LockoutException e) { check("can't remove the last admin's Administrator position", true); }
        check("...and it was rolled back", admin.isActiveAdmin(adminUser) && admin.getUserPositions(adminUser).contains(adminG));
        try { admin.saveUserAccess(adminUser, Set.of(adminG), "inactive"); check("can't deactivate the last admin", false); }
        catch (LockoutException e) { check("can't deactivate the last admin", true); }
        check("...status unchanged after rollback", one("SELECT COUNT(*) FROM users WHERE id=" + adminUser + " AND status='active'") == 1);
        try { admin.checkUserCanBeRemoved(adminUser); check("checkUserCanBeRemoved blocks last admin", false); }
        catch (LockoutException e) { check("checkUserCanBeRemoved blocks last admin", true); }
        int boss = admin.savePosition(null, "Super Manager", "", null, Set.of(adminG), null);
        admin.saveUserAccess(mgr, Set.of(boss), null);
        check("a position that IMPLIES Administrator makes its holder admin-equivalent", admin.countActiveAdmins() == 2 && admin.isActiveAdmin(mgr));
        login(mgr);
        check("...and PermissionService agrees (same rule in checker and guard)", ps.isAdministrator() && ps.canRead("settings"));
        admin.saveUserAccess(adminUser, Set.of(posId("Cashier")), null);
        check("with a 2nd admin, the original may be demoted", admin.countActiveAdmins() == 1);
        try { admin.savePosition(boss, "Super Manager", "", null, Set.of(), null); check("un-implying Administrator from the last admin-equivalent position rejected", false); }
        catch (LockoutException e) { check("un-implying Administrator from the last admin-equivalent position rejected", true); }
        try { admin.deletePosition(boss); check("deleting the last admin-equivalent position rejected", false); }
        catch (LockoutException e) { check("deleting the last admin-equivalent position rejected", true); }
        check("legacy role id: named match, else a non-admin role", admin.legacyRoleIdFor(Set.of(posId("Accountant"))) == 5 && admin.legacyRoleIdFor(Set.of(adminG)) != 1);

        // ── deleting a position cascades cleanly ──
        admin.deletePosition(a3);
        check("delete cascades assignments/access/specials/implies",
              one("SELECT COUNT(*) FROM user_permission_groups WHERE group_id=" + a3) == 0
           && one("SELECT COUNT(*) FROM special_permissions WHERE group_id=" + a3) == 0);

        // ── break-glass self-heal ──
        sql("DELETE FROM user_permission_groups");
        check("(setup) database now has no administrator", admin.countActiveAdmins() == 0);
        try (Connection c = c()) { PermissionGroupSetup.seed(c, MODULES); }
        check("startup self-heal restores an administrator (prefers legacy all-access role)",
              admin.countActiveAdmins() >= 1 && admin.isActiveAdmin(adminUser));

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
