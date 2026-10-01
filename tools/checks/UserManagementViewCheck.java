// Smoke test: builds the Users & Access screen on a real DB and renders it (needs a display).
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.User;
import com.kaziflow.modules.ModuleRegistry;
import com.kaziflow.services.*;
import com.kaziflow.utils.SessionManager;
import com.kaziflow.views.UserManagementView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class UserManagementViewCheck {
    static int one(String q) throws Exception { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) { return r.next() ? r.getInt(1) : -1; } }
    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        ModuleRegistry.getInstance().init();
        List<String> ids = ModuleRegistry.getInstance().getAllAvailableModules().stream().map(com.kaziflow.modules.Module::getId).toList();
        try (Connection c = DatabaseManager.getInstance().getConnection()) { PermissionGroupSetup.seed(c, ids); }
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO users (name,email,password_hash,role_id,status,last_login) VALUES ('Wanjiku Kamau','wanjiku@x.co.ke','h',3,'active','2026-09-30T08:15:00'),('Otieno Odhiambo','otieno@x.co.ke','h',2,'suspended',NULL)");
        }
        int w = one("SELECT id FROM users WHERE email='wanjiku@x.co.ke'"), o = one("SELECT id FROM users WHERE email='otieno@x.co.ke'");
        PermissionAdminService adm = PermissionAdminService.getInstance();
        adm.saveUserAccess(w, Set.of(one("SELECT id FROM permission_groups WHERE name='Cashier'"), one("SELECT id FROM permission_groups WHERE name='Accountant'")), null);
        adm.saveUserAccess(o, Set.of(one("SELECT id FROM permission_groups WHERE name='Store Manager'")), null);
        User u = new User(); u.setId(one("SELECT id FROM users WHERE email='admin@kaziflow.co.ke'")); u.setName("Admin");
        SessionManager.getInstance().login(u);
        AuditLog.ensureHashColumns(); // App.start() does this at startup
        SessionManager.getInstance().logout();
        AuditLog.log("LOGIN_FAILED", "Failed sign-in attempt for nobody@x.co.ke", "auth", null);   // nobody signed in: user_id would be 0
        SessionManager.getInstance().login(u);
        AuditLog.log("LOGIN_FAILED", "Failed sign-in attempt for otieno@x.co.ke", "auth", null);
        AuditLog.logLogin("admin@kaziflow.co.ke");
        Thread.sleep(1500); // audit writes are asynchronous
        Platform.startup(() -> {});
        CompletableFuture<String> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                UserManagementView v = new UserManagementView();
                Stage st = new Stage(); Scene sc = new Scene(v.getRoot(), 1200, 720); st.setScene(sc); st.show();
                Platform.runLater(() -> { try {
                    WritableImage img = sc.snapshot(null);
                    BufferedImage bi = new BufferedImage((int) img.getWidth(), (int) img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < bi.getHeight(); y++) for (int x = 0; x < bi.getWidth(); x++) bi.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                    javax.imageio.ImageIO.write(bi, "png", new File("/tmp/pt/users.png")); done.complete("rendered");
                } catch (Throwable t) { done.completeExceptionally(t); } });
            } catch (Throwable t) { done.completeExceptionally(t); }
        });
        System.out.println(done.get(60, TimeUnit.SECONDS));
        System.exit(0);
    }
}
