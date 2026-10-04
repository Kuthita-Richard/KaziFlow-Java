// Branding check: names, override file, images, global window icon hook, and a render of login + sidebar. Needs a display.
import com.kaziflow.brand.Brand;
import com.kaziflow.brand.BrandAssets;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.User;
import com.kaziflow.modules.ModuleRegistry;
import com.kaziflow.utils.AppPaths;
import com.kaziflow.utils.SessionManager;
import com.kaziflow.views.LoginView;
import com.kaziflow.views.MainLayout;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class BrandingCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static <T> T onFx(Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(c.call()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.get(40, TimeUnit.SECONDS);
    }
    static void collect(Node n, Class<?> k, List<Node> out) {
        if (k.isInstance(n)) out.add(n);
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) collect(c, k, out);
    }
    static void shot(Scene sc, String file) throws Exception {
        WritableImage img = sc.snapshot(null);
        BufferedImage bi = new BufferedImage((int) img.getWidth(), (int) img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bi.getHeight(); y++) for (int x = 0; x < bi.getWidth(); x++) bi.setRGB(x, y, img.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(bi, "png", new File(file));
    }

    public static void main(String[] a) throws Exception {
        // ── names (no JavaFX needed) ──
        Brand.reload();
        check("hierarchy: Tihems → Tihems Software → KaziFlow ERP", Brand.groupName().equals("Tihems") && Brand.departmentName().equals("Tihems Software") && Brand.productFullName().equals("KaziFlow ERP"));
        check("composed lines", Brand.lineage().equals("Tihems Software · a Tihems company") && Brand.poweredBy().equals("Powered by KaziFlow ERP") && Brand.productByDepartment().equals("KaziFlow ERP by Tihems Software"));
        check("copyright carries the current year and the department", Brand.copyright().equals("© " + java.time.Year.now() + " Tihems Software. All rights reserved."));
        check("version comes from the update checker's single constant", Brand.version().equals(com.kaziflow.services.UpdateService.CURRENT_VERSION));
        check("data folder does NOT follow the brand (existing installs keep their data)", AppPaths.dataDir().endsWith("/KaziFlowERP") && AppPaths.dbFile().endsWith("/KaziFlowERP/kaziflow.db"));

        // ── per-install override (white-label) ──
        Path dir = Path.of(AppPaths.dataDir()); Files.createDirectories(dir);
        Files.writeString(dir.resolve("brand-override.properties"), "product.name=Duka\nproduct.fullName=Duka POS\ndepartment.name=Acme Reseller\n", java.nio.charset.StandardCharsets.UTF_8);
        Brand.reload();
        check("override file renames the product with no rebuild", Brand.productFullName().equals("Duka POS") && Brand.productName().equals("Duka") && Brand.lineage().startsWith("Acme Reseller"));
        check("keys not overridden keep their defaults", Brand.groupName().equals("Tihems") && !Brand.tagline().isBlank());
        Files.delete(dir.resolve("brand-override.properties")); Brand.reload();
        check("removing the override restores the defaults", Brand.productFullName().equals("KaziFlow ERP"));
        check("data URI helper returns an embeddable image (and '' for a missing file)", Brand.dataUri("icon-32.png").startsWith("data:image/png;base64,") && Brand.dataUri("nope.png").isEmpty());

        // ── JavaFX ──
        DatabaseManager.getInstance().initialize();
        ModuleRegistry.getInstance().init();
        User u = new User(); u.setId(1); u.setName("Admin"); SessionManager.getInstance().login(u);
        Platform.startup(() -> {});
        check("all window-icon sizes load (16–256)", onFx(() -> BrandAssets.windowIcons().size()) == 7);
        check("missing asset returns null instead of crashing", onFx(() -> BrandAssets.image("does-not-exist.png")) == null && onFx(() -> BrandAssets.view("does-not-exist.png", 20)) == null);
        check("mark is scaled to the requested height with aspect kept", onFx(() -> { ImageView v = BrandAssets.mark(true, 40); return v.getFitHeight() == 40 && v.isPreserveRatio(); }));

        onFx(() -> { BrandAssets.installGlobalWindowIcons(); return null; });
        final Stage[] st = new Stage[1];
        onFx(() -> { st[0] = new Stage(); st[0].setScene(new Scene(new Label("x"), 100, 60)); st[0].show(); return null; });
        check("global hook: a window created with NO code of its own gets the brand icon", onFx(() -> st[0].getIcons().size()) == 7);
        final Stage[] pre = new Stage[1];
        onFx(() -> { pre[0] = new Stage(); pre[0].getIcons().add(BrandAssets.image("favicon-16.png")); pre[0].setScene(new Scene(new Label("y"), 100, 60)); pre[0].show(); return null; });
        check("...but a window that already chose its own icon is left alone", onFx(() -> pre[0].getIcons().size()) == 1);

        // login screen (dark brand panel)
        final Scene[] ls = new Scene[1];
        onFx(() -> { LoginView lv = new LoginView(); Stage s = new Stage(); ls[0] = new Scene(lv.getRoot(), 1200, 720); ls[0].getStylesheets().add(BrandingCheck.class.getResource("/styles/main.css").toExternalForm()); com.kaziflow.utils.ThemeManager.getInstance().registerScene(ls[0]); s.setScene(ls[0]); s.show(); return null; });
        List<Node> imgs = new ArrayList<>(), labels = new ArrayList<>();
        onFx(() -> { collect(ls[0].getRoot(), ImageView.class, imgs); collect(ls[0].getRoot(), Label.class, labels); return null; });
        check("login shows the real mark (an image, not the old glyph)", !imgs.isEmpty());
        check("login shows the product name and the Tihems lineage + copyright", onFx(() -> labels.stream().map(l -> ((Label) l).getText()).anyMatch(t -> t.equals("KaziFlow ERP")))
              && onFx(() -> labels.stream().map(l -> ((Label) l).getText()).anyMatch(t -> t.contains("Tihems Software · a Tihems company") && t.contains("©"))));
        check("login no longer shows the invented 'KaziFlow Systems Ltd'", onFx(() -> labels.stream().noneMatch(l -> ((Label) l).getText().contains("Systems Ltd"))));
        Thread.sleep(300);
        onFx(() -> { shot(ls[0], "/tmp/pt/brand_login.png"); return null; });

        // main layout (sidebar)
        final Scene[] ms = new Scene[1];
        onFx(() -> { MainLayout ml = new MainLayout(); Stage s = new Stage(); ms[0] = new Scene(ml.getRoot(), 1280, 720); ms[0].getStylesheets().add(BrandingCheck.class.getResource("/styles/main.css").toExternalForm()); com.kaziflow.utils.ThemeManager.getInstance().registerScene(ms[0]); s.setScene(ms[0]); s.show(); return null; });
        List<Node> mimgs = new ArrayList<>(); onFx(() -> { collect(ms[0].getRoot(), ImageView.class, mimgs); return null; });
        check("sidebar header shows the real mark", !mimgs.isEmpty());
        Thread.sleep(300);
        onFx(() -> { shot(ms[0], "/tmp/pt/brand_sidebar.png"); return null; });

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
