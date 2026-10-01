// UI check for the Positions & Access editor: drives the real controls under Xvfb, verifies the DB, writes screenshots.
// Needs a display: xvfb-run -a java -Duser.home=<empty temp dir> ... PositionsAccessViewCheck
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.User;
import com.kaziflow.modules.ModuleRegistry;
import com.kaziflow.services.*;
import com.kaziflow.utils.SessionManager;
import com.kaziflow.views.PositionsAccessView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.*;
import java.util.List;
import java.util.concurrent.*;

public class PositionsAccessViewCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static int one(String q) throws Exception { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) { return r.next() ? r.getInt(1) : -1; } }

    static void shot(Scene sc, String file) throws Exception {
        WritableImage img = sc.snapshot(null);
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, img.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(bi, "png", new File(file));
    }
    /** CSS lookup("#a:b") treats ':' as a pseudo-class, so search by getId() directly. */
    static javafx.scene.Node byId(Scene sc, String id) { return find(sc.getRoot(), id); }
    static javafx.scene.Node find(javafx.scene.Node n, String id) {
        if (id.equals(n.getId())) return n;
        if (n instanceof javafx.scene.Parent p)
            for (javafx.scene.Node c : p.getChildrenUnmodifiable()) { javafx.scene.Node r = find(c, id); if (r != null) return r; }
        if (n instanceof ScrollPane sp && sp.getContent() != null) return find(sp.getContent(), id);
        return null;
    }
    static <T> T onFx(Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(c.call()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.get(30, TimeUnit.SECONDS);
    }
    @SuppressWarnings("unchecked")
    static void select(ListView<PermissionAdminService.Position> lv, String name) {
        for (var p : lv.getItems()) if (p.name().equals(name)) lv.getSelectionModel().select(p);
    }

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        ModuleRegistry.getInstance().init();
        List<String> ids = ModuleRegistry.getInstance().getAllAvailableModules().stream().map(com.kaziflow.modules.Module::getId).toList();
        try (Connection c = DatabaseManager.getInstance().getConnection()) { PermissionGroupSetup.seed(c, ids); }
        User u = new User(); u.setId(one("SELECT id FROM users WHERE email='admin@kaziflow.co.ke'")); u.setName("Admin");
        SessionManager.getInstance().login(u);
        Platform.startup(() -> {});
        final Scene[] scene = new Scene[1];
        final PositionsAccessView[] view = new PositionsAccessView[1];
        onFx(() -> {
            view[0] = new PositionsAccessView();
            Stage st = new Stage();
            scene[0] = new Scene(view[0].getRoot(), 1040, 900);
            st.setScene(scene[0]); st.show();
            return null;
        });
        @SuppressWarnings("unchecked") ListView<PermissionAdminService.Position> lv = (ListView<PermissionAdminService.Position>) byId(scene[0], "posList");
        check("list shows the 5 migrated positions", onFx(() -> lv.getItems().size()) == 5);
        check("Administrator is first (system positions sort first)", onFx(() -> lv.getItems().get(0).name()).equals("Administrator"));
        onFx(() -> { shot(scene[0], "/tmp/pt/pos_admin.png"); return null; });
        check("Administrator: matrix checkboxes are disabled + all ticked", onFx(() -> {
            CheckBox cb = (CheckBox) byId(scene[0], "cb:finance:D"); return cb.isSelected() && cb.isDisable(); }));
        check("Administrator: Save and Delete disabled", onFx(() -> byId(scene[0], "posSave").isDisabled() && byId(scene[0], "posDelete").isDisabled()));

        onFx(() -> { select(lv, "Cashier"); return null; });
        check("Cashier: sales Read+Create ticked, finance not", onFx(() -> {
            return ((CheckBox) byId(scene[0], "cb:sales:R")).isSelected() && ((CheckBox) byId(scene[0], "cb:sales:C")).isSelected()
                && !((CheckBox) byId(scene[0], "cb:finance:R")).isSelected(); }));
        check("every registry module appears as a row (dynamic, not hardcoded)", onFx(() -> {
            int missing = 0;
            for (var m : ModuleRegistry.getInstance().getAllAvailableModules())
                if (!m.alwaysAccessible() && byId(scene[0], "cb:" + m.getId() + ":R") == null) missing++;
            return missing == 0; }));
        check("Save is disabled until something changes", onFx(() -> byId(scene[0], "posSave").isDisabled()));

        // tick finance:Write -> Read must auto-tick; then save
        onFx(() -> { ((CheckBox) byId(scene[0], "cb:finance:W")).setSelected(true); return null; });
        check("ticking Write auto-ticks Read", onFx(() -> ((CheckBox) byId(scene[0], "cb:finance:R")).isSelected()));
        check("Save enabled after a change", onFx(() -> !byId(scene[0], "posSave").isDisabled()));
        onFx(() -> { ((CheckBox) byId(scene[0], "sp:void_sale")).setSelected(true); return null; });
        onFx(() -> { ((Button) byId(scene[0], "posSave")).fire(); return null; });
        Thread.sleep(300);
        int cashier = one("SELECT id FROM permission_groups WHERE name='Cashier'");
        check("saved to DB: finance read+write", one("SELECT can_read*10+can_write FROM module_access WHERE group_id=" + cashier + " AND module_id='finance'") == 11);
        check("saved to DB: void_sale granted", one("SELECT COUNT(*) FROM special_permissions WHERE group_id=" + cashier + " AND action_key='void_sale'") == 1);
        check("unchanged modules preserved (sales still R+C)", one("SELECT can_read+can_create FROM module_access WHERE group_id=" + cashier + " AND module_id='sales'") == 2);

        // un-tick Read -> clears C/W/D
        onFx(() -> { ((CheckBox) byId(scene[0], "cb:finance:R")).setSelected(false); return null; });
        check("clearing Read clears Write", onFx(() -> !((CheckBox) byId(scene[0], "cb:finance:W")).isSelected()));

        // revert clears the dirty state (otherwise switching would -- correctly -- show a modal discard prompt)
        onFx(() -> { ((Button) byId(scene[0], "posRevert")).fire(); return null; });
        check("Revert reloads the saved values (finance Read back on)", onFx(() -> ((CheckBox) byId(scene[0], "cb:finance:R")).isSelected()));

        // new position that inherits Cashier and adds finance read
        onFx(() -> { ((Button) byId(scene[0], "posNew")).fire(); return null; });
        check("New position: Save enabled, Delete disabled", onFx(() -> !byId(scene[0], "posSave").isDisabled() && byId(scene[0], "posDelete").isDisabled()));
        onFx(() -> {
            ((TextField) byId(scene[0], "posName")).setText("Shift Lead");
            ((CheckBox) byId(scene[0], "imp:" + cashier)).setSelected(true);
            ((CheckBox) byId(scene[0], "cb:reports:R")).setSelected(true);
            ((CheckBox) byId(scene[0], "sp:apply_discount")).setSelected(true);
            return null; });
        onFx(() -> { shot(scene[0], "/tmp/pt/pos_new.png"); return null; });
        onFx(() -> { ((Button) byId(scene[0], "posSave")).fire(); return null; });
        Thread.sleep(300);
        int lead = one("SELECT id FROM permission_groups WHERE name='Shift Lead'");
        check("new position saved", lead > 0);
        check("inherits Cashier (implies row)", one("SELECT COUNT(*) FROM permission_group_implies WHERE group_id=" + lead + " AND implied_id=" + cashier) == 1);
        check("own grants saved: reports read, apply_discount", one("SELECT can_read FROM module_access WHERE group_id=" + lead + " AND module_id='reports'") == 1
            && one("SELECT COUNT(*) FROM special_permissions WHERE group_id=" + lead + " AND action_key='apply_discount'") == 1);
        check("list now has 6 positions and the new one is selected", onFx(() -> lv.getItems().size() == 6 && lv.getSelectionModel().getSelectedItem().name().equals("Shift Lead")));
        onFx(() -> { shot(scene[0], "/tmp/pt/pos_saved.png"); return null; });

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
