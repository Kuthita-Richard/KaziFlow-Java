// UI check for product pictures: inventory table cells, the picker component, and cache invalidation. Needs a display.
import com.kaziflow.dao.ProductImageDAO;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.User;
import com.kaziflow.modules.ModuleRegistry;
import com.kaziflow.services.PermissionGroupSetup;
import com.kaziflow.utils.AppPaths;
import com.kaziflow.utils.ImageProcessor;
import com.kaziflow.utils.ProductImages;
import com.kaziflow.utils.SessionManager;
import com.kaziflow.views.InventoryView;
import com.kaziflow.views.ProductImagePicker;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class ProductImageUiCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static <T> T onFx(Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(c.call()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.get(40, TimeUnit.SECONDS);
    }
    static Node byId(Node n, String id) {
        if (id.equals(n.getId())) return n;
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) { Node r = byId(c, id); if (r != null) return r; }
        return null;
    }
    static void collect(Node n, Class<?> k, List<Node> out) {
        if (k.isInstance(n)) out.add(n);
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) collect(c, k, out);
    }
    static int one(String q) throws Exception { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) { return r.next() ? r.getInt(1) : -1; } }
    static void sql(String q) throws Exception { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement()) { s.execute(q); } }
    static boolean waitFor(Callable<Boolean> cond, int ms) throws Exception { long end = System.currentTimeMillis() + ms; while (System.currentTimeMillis() < end) { if (onFx(cond)) return true; Thread.sleep(100); } return false; }
    static File png(int w, int h, Color c) throws Exception {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB); Graphics2D g = im.createGraphics(); g.setColor(c); g.fillRect(0, 0, w, h); g.setColor(Color.WHITE); g.fillOval(w / 4, h / 4, w / 2, h / 2); g.dispose();
        File f = File.createTempFile("pic", ".png"); f.deleteOnExit(); ImageIO.write(im, "png", f); return f;
    }
    static void shot(Scene sc, String file) throws Exception {
        WritableImage img = sc.snapshot(null);
        BufferedImage bi = new BufferedImage((int) img.getWidth(), (int) img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bi.getHeight(); y++) for (int x = 0; x < bi.getWidth(); x++) bi.setRGB(x, y, img.getPixelReader().getArgb(x, y));
        ImageIO.write(bi, "png", new File(file));
    }

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        ModuleRegistry reg = ModuleRegistry.getInstance(); reg.init();
        try (Connection c = DatabaseManager.getInstance().getConnection()) { PermissionGroupSetup.seed(c, reg.getAllAvailableModules().stream().map(com.kaziflow.modules.Module::getId).toList()); }
        User u = new User(); u.setId(one("SELECT id FROM users WHERE email='admin@kaziflow.co.ke'")); u.setName("Admin"); SessionManager.getInstance().login(u);
        for (String[] p : new String[][]{{"Maize flour 2kg", "FLR-2"}, {"Cooking oil 1L", "OIL-1"}, {"Zzz Sugar 1kg", "SUG-1"}})
            sql("INSERT INTO products (name, sku, selling_price, stock_quantity) VALUES ('" + p[0] + "','" + p[1] + "',150,20)");
        int flour = one("SELECT id FROM products WHERE sku='FLR-2'"), oil = one("SELECT id FROM products WHERE sku='OIL-1'"), sugar = one("SELECT id FROM products WHERE sku='SUG-1'");
        ProductImageDAO dao = new ProductImageDAO();
        dao.save(flour, ImageProcessor.process(Files.readAllBytes(png(500, 400, new Color(37, 99, 235)).toPath())));
        dao.save(oil, ImageProcessor.process(Files.readAllBytes(png(300, 300, new Color(217, 119, 6)).toPath())));
        Platform.startup(() -> {});

        // ── inventory table ──
        final Scene[] sc = new Scene[1];
        onFx(() -> { InventoryView iv = new InventoryView(); Stage st = new Stage(); sc[0] = new Scene(iv.getRoot(), 1180, 1700);
            sc[0].getStylesheets().add(ProductImageUiCheck.class.getResource("/styles/main.css").toExternalForm()); st.setScene(sc[0]); st.show(); return null; });
        List<Node> tables = new ArrayList<>();
        check("inventory table loads the products (asynchronously)", waitFor(() -> { tables.clear(); collect(sc[0].getRoot(), TableView.class, tables); return !tables.isEmpty() && ((TableView<?>) tables.get(0)).getItems().size() >= 3; }, 10000));
        Thread.sleep(600);
        List<Node> imgs = new ArrayList<>(), labels = new ArrayList<>();
        onFx(() -> { collect(sc[0].getRoot(), ImageView.class, imgs); return null; });
        check("top of the table: 'Cooking oil' (has a photo) shows it", onFx(() -> imgs.stream().filter(i -> ((ImageView) i).getFitWidth() == 38).count()) >= 1);
        onFx(() -> { shot(sc[0], "/tmp/pt/pi_inventory.png"); return null; });
        @SuppressWarnings("unchecked") TableView<Object> tv = (TableView<Object>) tables.get(0);
        onFx(() -> { tv.scrollTo(tv.getItems().size() - 1); return null; });
        Thread.sleep(600);
        imgs.clear(); labels.clear();
        onFx(() -> { collect(sc[0].getRoot(), ImageView.class, imgs); collect(sc[0].getRoot(), Label.class, labels); return null; });
        check("bottom of the table: 'Maize flour' (has a photo) shows it", onFx(() -> imgs.stream().filter(i -> ((ImageView) i).getFitWidth() == 38).count()) >= 1);
        check("bottom of the table: 'Zzz Sugar' (NO photo) shows a coloured letter avatar 'Z'", onFx(() -> labels.stream().anyMatch(l -> ((Label) l).getText().equals("Z"))));
        onFx(() -> { shot(sc[0], "/tmp/pt/pi_inventory_bottom.png"); return null; });

        // ── the picker: valid file ──
        final ProductImagePicker[] pk = new ProductImagePicker[1];
        final Scene[] ps = new Scene[1];
        onFx(() -> { pk[0] = new ProductImagePicker(sugar); Stage st = new Stage(); ps[0] = new Scene((Parent) pk[0].getNode(), 520, 140); st.setScene(ps[0]); st.show(); return null; });
        check("new product: Remove is disabled and nothing is staged", onFx(() -> byId(ps[0].getRoot(), "picker-remove").isDisabled() && !pk[0].hasChange()));
        File good = png(1600, 1200, new Color(22, 163, 74));
        onFx(() -> { pk[0].load(good); return null; });
        check("choosing a picture stages it (processed off the UI thread)", waitFor(pk[0]::hasChange, 8000));
        check("the status explains what happened", onFx(() -> ((Label) byId(ps[0].getRoot(), "picker-status")).getText().contains("New photo ready (640 × 480)")));
        check("a picture is shown in the preview and Remove is now enabled", onFx(() -> { List<Node> v = new ArrayList<>(); collect(byId(ps[0].getRoot(), "picker-preview"), ImageView.class, v); return v.size() == 1 && !byId(ps[0].getRoot(), "picker-remove").isDisabled(); }));
        onFx(() -> { shot(ps[0], "/tmp/pt/pi_picker.png"); return null; });
        check("nothing is written until applyTo (the dialog's Save)", !dao.has(sugar));
        check("applyTo commits it and reports success", onFx(() -> pk[0].applyTo(sugar)) == null && dao.has(sugar));
        check("the thumbnail cache is invalidated: the new picture is what the table would now show", onFx(() -> { var img = ProductImages.thumb(sugar); return img != null && img.getWidth() == 160; }));

        // ── the picker: bad files ──
        final ProductImagePicker[] bad = new ProductImagePicker[1];
        onFx(() -> { bad[0] = new ProductImagePicker(null); Stage st = new Stage(); st.setScene(new Scene((Parent) bad[0].getNode(), 520, 140)); st.show(); return null; });
        File junk = File.createTempFile("junk", ".jpg"); junk.deleteOnExit(); Files.writeString(junk.toPath(), "this is not a picture");
        onFx(() -> { bad[0].load(junk); return null; });
        check("a text file renamed .jpg is refused with a plain-language warning and nothing is staged",
              waitFor(() -> ((Label) byId(bad[0].getNode(), "picker-status")).getText().contains("⚠") , 8000) && !onFx(bad[0]::hasChange));

        // ── removal ──
        final ProductImagePicker[] rm = new ProductImagePicker[1];
        onFx(() -> { rm[0] = new ProductImagePicker(flour); Stage st = new Stage(); st.setScene(new Scene((Parent) rm[0].getNode(), 520, 140)); st.show(); return null; });
        check("editing a product that has a photo: preview shows it, Remove enabled", onFx(() -> { List<Node> v = new ArrayList<>(); collect(byId(rm[0].getNode(), "picker-preview"), ImageView.class, v); return v.size() == 1 && !byId(rm[0].getNode(), "picker-remove").isDisabled(); }));
        onFx(() -> { ((Button) byId(rm[0].getNode(), "picker-remove")).fire(); return null; });
        check("Remove stages the removal; nothing is deleted until Save", onFx(rm[0]::hasChange) && dao.has(flour));
        check("applyTo deletes the stored picture and clears the cache", onFx(() -> rm[0].applyTo(flour)) == null && !dao.has(flour) && onFx(() -> ProductImages.thumb(flour)) == null);

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
