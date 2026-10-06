// Product pictures: processing (EXIF, bombs, transparency, sizes), the DAO (single, batched, cascade), cache, picker.
import com.kaziflow.dao.ProductImageDAO;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.utils.ImageProcessor;
import com.kaziflow.utils.ImageProcessor.ImageException;
import com.kaziflow.utils.ImageProcessor.Processed;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

public class ProductImageCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static byte[] encode(BufferedImage img, String fmt) throws Exception { ByteArrayOutputStream b = new ByteArrayOutputStream(); ImageIO.write(img, fmt, b); return b.toByteArray(); }
    static BufferedImage decode(byte[] b) throws Exception { return ImageIO.read(new ByteArrayInputStream(b)); }
    static String sql1(String q) throws Exception { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) { return r.next() ? r.getString(1) : null; } }
    static void sql(String q) throws Exception { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement()) { s.execute(q); } }
    static boolean refused(byte[] data, String contains) {
        try { ImageProcessor.process(data); return false; }
        catch (ImageException e) { boolean ok = e.getMessage().toLowerCase().contains(contains.toLowerCase()); if (!ok) System.out.println("   got: " + e.getMessage()); return ok; }
    }
    /** Which of the four quadrant colours (R,G,B,Y) is this pixel closest to? */
    static char quad(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int[][] ref = {{230, 30, 30}, {30, 200, 60}, {30, 60, 230}, {240, 220, 30}}; char[] name = {'R', 'G', 'B', 'Y'};
        int best = 0; double bd = 1e9;
        for (int i = 0; i < 4; i++) { double d = Math.pow(r - ref[i][0], 2) + Math.pow(g - ref[i][1], 2) + Math.pow(b - ref[i][2], 2); if (d < bd) { bd = d; best = i; } }
        return name[best];
    }

    public static void main(String[] a) throws Exception {
        // ── processing ──
        BufferedImage big = new BufferedImage(2000, 1500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = big.createGraphics(); g.setPaint(new GradientPaint(0, 0, Color.ORANGE, 2000, 1500, Color.BLUE)); g.fillRect(0, 0, 2000, 1500); g.setColor(Color.WHITE); g.fillOval(600, 400, 800, 700); g.dispose();
        Processed p = ImageProcessor.process(encode(big, "jpg"));
        check("a 2000×1500 photo becomes 640×480 (aspect kept, longest side 640)", p.width() == 640 && p.height() == 480 && decode(p.full()).getWidth() == 640 && decode(p.full()).getHeight() == 480);
        BufferedImage th = decode(p.thumb());
        check("thumbnail is a 160×160 square", th.getWidth() == 160 && th.getHeight() == 160);
        check("both outputs are real JPEGs and small (full < 100 KB, thumb < 15 KB)", p.full().length < 100_000 && p.thumb().length < 15_000 && (p.full()[0] & 255) == 0xFF && (p.full()[1] & 255) == 0xD8);
        long t0 = System.nanoTime();
        BufferedImage cam = new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB); Graphics2D g2 = cam.createGraphics(); g2.setPaint(new GradientPaint(0, 0, Color.RED, 4000, 3000, Color.GREEN)); g2.fillRect(0, 0, 4000, 3000); g2.dispose();
        byte[] camBytes = encode(cam, "jpg"); long t1 = System.nanoTime();
        Processed cp = ImageProcessor.process(camBytes); long t2 = System.nanoTime();
        System.out.printf("   perf: 12-megapixel photo (%d KB file) -> %d KB + %d KB in %d ms%n", camBytes.length / 1024, cp.full().length / 1024, cp.thumb().length / 1024, (t2 - t1) / 1_000_000);
        check("a 12-megapixel camera photo is processed in under 3 s", (t2 - t1) / 1_000_000 < 3000);

        BufferedImage small = new BufferedImage(50, 30, BufferedImage.TYPE_INT_RGB); Graphics2D sg = small.createGraphics(); sg.setColor(Color.MAGENTA); sg.fillRect(0, 0, 50, 30); sg.dispose();
        Processed sp = ImageProcessor.process(encode(small, "png"));
        check("a small picture is NOT enlarged (50×30 stays 50×30; thumb is 30×30)", sp.width() == 50 && sp.height() == 30 && decode(sp.thumb()).getWidth() == 30 && decode(sp.thumb()).getHeight() == 30);

        BufferedImage alpha = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB); Graphics2D ag = alpha.createGraphics(); ag.setColor(new Color(220, 20, 20)); ag.fillRect(80, 80, 40, 40); ag.dispose();
        BufferedImage flat = decode(ImageProcessor.process(encode(alpha, "png")).full());
        int corner = flat.getRGB(2, 2);
        check("transparent PNG background becomes WHITE, not black", ((corner >> 16) & 255) > 245 && ((corner >> 8) & 255) > 245 && (corner & 255) > 245);
        check("...and the opaque part keeps its colour", quad(flat.getRGB(100, 100)) == 'R');

        for (String fmt : new String[]{"png", "gif", "bmp", "jpg"}) {
            BufferedImage src = new BufferedImage(100, 100, fmt.equals("jpg") || fmt.equals("bmp") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
            Graphics2D x = src.createGraphics(); x.setColor(Color.CYAN); x.fillRect(0, 0, 100, 100); x.dispose();
            Processed r = ImageProcessor.process(encode(src, fmt));
            check("reads " + fmt.toUpperCase(), r.width() == 100 && r.full().length > 0);
        }

        // ── refusing what isn't safe or isn't a picture ──
        check("random text is refused with a clear message", refused("hello, I am definitely not a picture".getBytes(), "isn't a picture"));
        check("a .jpg that is really a PDF/zip header is refused (content decides, not the extension)", refused(new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A, 0x25}, "isn't a picture"));
        check("empty file refused", refused(new byte[0], "empty"));
        byte[] png = encode(small, "png");
        check("a truncated/damaged PNG is refused, not crashed on", refused(Arrays.copyOf(png, png.length / 2), "damaged") || refused(Arrays.copyOf(png, 20), "damaged") || refused(Arrays.copyOf(png, png.length / 2), "isn't a picture"));
        BufferedImage bomb = new BufferedImage(8000, 8000, BufferedImage.TYPE_BYTE_BINARY);          // 64 megapixels, compresses to almost nothing
        byte[] bombBytes = encode(bomb, "png"); long b0 = System.nanoTime();
        boolean bombRefused = refused(bombBytes, "far too large"); long b1 = System.nanoTime();
        System.out.printf("   perf: %d KB file claiming 8000×8000 pixels refused in %d ms (never decoded)%n", bombBytes.length / 1024, (b1 - b0) / 1_000_000);
        check("a decompression bomb (tiny file, 64 megapixels) is refused BEFORE decoding", bombRefused && (b1 - b0) / 1_000_000 < 1500);
        check("a file over 20 MB is refused", refused(new byte[ImageProcessor.MAX_FILE_BYTES + 1], "too large"));

        // ── phone photos: EXIF orientation 1..8 must all come out upright, identically ──
        Path fx = Path.of("tools/checks/fixtures");
        StringBuilder bad = new StringBuilder();
        for (int o = 1; o <= 8; o++) {
            Processed r = ImageProcessor.process(Files.readAllBytes(fx.resolve("exif_" + o + ".jpg")));
            BufferedImage im = decode(r.full());
            String got = "" + quad(im.getRGB(15, 10)) + quad(im.getRGB(105, 10)) + quad(im.getRGB(15, 70)) + quad(im.getRGB(105, 70));
            if (im.getWidth() != 120 || im.getHeight() != 80 || !got.equals("RGBY")) bad.append(" o").append(o).append("=").append(im.getWidth()).append("x").append(im.getHeight()).append(":").append(got);
        }
        check("EXIF orientations 1–8 all come out upright, 120×80, quadrants R G / B Y" + (bad.length() > 0 ? "  [wrong:" + bad + "]" : ""), bad.length() == 0);

        // ── DAO on the real schema ──
        DatabaseManager.getInstance().initialize();
        ProductImageDAO dao = new ProductImageDAO();
        sql("INSERT INTO products (name, sku, selling_price, stock_quantity) VALUES ('Maize flour 2kg','FLR-2',180,10)");
        int pid = Integer.parseInt(sql1("SELECT id FROM products WHERE sku='FLR-2'"));
        check("no picture yet", !dao.has(pid) && dao.getImage(pid) == null);
        check("save stores a picture", dao.save(pid, p) && dao.has(pid));
        check("full picture round-trips as a valid 640×480 JPEG", decode(dao.getImage(pid)).getWidth() == 640);
        check("thumbnail round-trips as a 160×160 JPEG", decode(dao.getThumb(pid)).getWidth() == 160);
        check("stored as TEXT (base64), so it works over the LAN protocol which cannot carry BLOBs",
              "text".equalsIgnoreCase(sql1("SELECT typeof(image) FROM product_images WHERE product_id=" + pid)) && "text".equalsIgnoreCase(sql1("SELECT typeof(thumb) FROM product_images WHERE product_id=" + pid)));
        dao.save(pid, sp);
        check("saving again REPLACES (one row per product)", sql1("SELECT COUNT(*) FROM product_images WHERE product_id=" + pid).equals("1") && decode(dao.getImage(pid)).getWidth() == 50);
        check("delete removes it", dao.delete(pid) && !dao.has(pid));

        // batched thumbnails, including more ids than one query chunk (400)
        List<Integer> ids = new ArrayList<>();
        try (Connection c = DatabaseManager.getInstance().getConnection(); PreparedStatement ps = c.prepareStatement("INSERT INTO products (name, sku, selling_price, stock_quantity) VALUES (?,?,1,1)")) {
            for (int i = 0; i < 450; i++) { ps.setString(1, "Item " + i); ps.setString(2, "BATCH-" + i); ps.executeUpdate(); }
        }
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT id FROM products WHERE sku LIKE 'BATCH-%' ORDER BY id")) { while (r.next()) ids.add(r.getInt(1)); }
        for (int i = 0; i < ids.size(); i += 2) dao.save(ids.get(i), sp);     // every other product has a picture
        long q0 = System.nanoTime(); Map<Integer, byte[]> thumbs = dao.thumbs(ids); long q1 = System.nanoTime();
        System.out.printf("   perf: thumbnails for 450 products fetched in %d ms (2 queries)%n", (q1 - q0) / 1_000_000);
        check("batched fetch returns exactly the products that have a picture (225 of 450, across 2 chunks)", thumbs.size() == 225 && thumbs.containsKey(ids.get(0)) && !thumbs.containsKey(ids.get(1)));
        check("batched fetch with no ids / unknown ids is safe", dao.thumbs(List.of()).isEmpty() && dao.thumbs(List.of(999999)).isEmpty());
        sql("DELETE FROM products WHERE id = " + ids.get(0));
        check("deleting a product deletes its picture (cascade)", sql1("SELECT COUNT(*) FROM product_images WHERE product_id=" + ids.get(0)).equals("0"));
        String daoSrc = Files.readString(Path.of("src/main/java/com/kaziflow/dao/ProductImageDAO.java"));
        check("DAO never uses setBytes/getBytes/Blob (the LAN protocol throws on them)", !daoSrc.contains("setBytes") && !daoSrc.contains("getBytes") && !daoSrc.contains("Blob"));

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
