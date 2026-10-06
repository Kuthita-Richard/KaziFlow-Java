package com.kaziflow.utils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * ImageProcessor — turns whatever file a user picks into two small, safe JPEGs: a picture (max 640 px) and a
 * square thumbnail (160 px). Pure JDK ({@code javax.imageio}), no JavaFX, so it is testable anywhere.
 *
 * <p>What it guards against, because users pick files from phones and the internet:
 * <ul>
 *   <li><b>Not really a picture</b> — the file is decoded, never trusted by its extension;</li>
 *   <li><b>Decompression bombs</b> — dimensions are read from the header first, and anything over 50 megapixels
 *       or 20 MB is refused BEFORE it is decoded (a tiny file can expand to gigabytes of pixels);</li>
 *   <li><b>Sideways phone photos</b> — JPEG EXIF orientation is applied (ImageIO ignores it);</li>
 *   <li><b>Transparency</b> — flattened onto white, because JPEG has no alpha and black boxes look broken;</li>
 *   <li><b>Bloat</b> — everything is re-encoded, so a 6 MB camera photo costs the database ~50 KB.</li>
 * </ul>
 * ImageIO reads JPEG, PNG, GIF and BMP. (WebP/HEIC are not supported by the JDK; the message says so.)
 */
public final class ImageProcessor {

    public static final int MAX_FILE_BYTES = 20 * 1024 * 1024;
    public static final long MAX_PIXELS = 50_000_000L;
    public static final int FULL_MAX = 640;
    public static final int THUMB = 160;
    private static final float QUALITY = 0.82f;

    /** The two encoded results and the dimensions of the full-size one. */
    public record Processed(byte[] full, byte[] thumb, int width, int height) {}

    /** A problem a user can understand and act on. */
    public static class ImageException extends Exception {
        private static final long serialVersionUID = 1L;
        public ImageException(String message) { super(message); }
    }

    private ImageProcessor() {}

    public static Processed process(byte[] file) throws ImageException {
        if (file == null || file.length == 0) throw new ImageException("That file is empty.");
        if (file.length > MAX_FILE_BYTES) throw new ImageException("That picture is too large (" + (file.length / 1024 / 1024) + " MB). Pick one under 20 MB.");
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(file))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext())
                throw new ImageException("That file isn't a picture I can read. Use a JPG, PNG, GIF or BMP (WebP and HEIC aren't supported).");
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, false);
                long w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0) throw new ImageException("That picture has no size.");
                if (w * h > MAX_PIXELS) throw new ImageException("That picture is far too large (" + w + " × " + h + " pixels). Resize it first.");
                int orientation = exifOrientation(reader);
                BufferedImage src = reader.read(0);
                if (src == null) throw new ImageException("That picture couldn't be read.");
                BufferedImage upright = flattenOnWhite(applyOrientation(src, orientation));

                BufferedImage full = fitWithin(upright, FULL_MAX);
                int side = Math.min(upright.getWidth(), upright.getHeight());
                BufferedImage square = upright.getSubimage((upright.getWidth() - side) / 2, (upright.getHeight() - side) / 2, side, side);
                BufferedImage thumb = resize(square, Math.min(THUMB, side), Math.min(THUMB, side));
                return new Processed(jpeg(full), jpeg(thumb), full.getWidth(), full.getHeight());
            } finally {
                reader.dispose();
            }
        } catch (ImageException e) {
            throw e;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            throw new ImageException("That picture couldn't be read — it may be damaged.");
        }
    }

    // ── EXIF orientation (phones store "rotate me" here instead of rotating the pixels) ──

    static int exifOrientation(ImageReader reader) {
        try {
            IIOMetadata meta = reader.getImageMetadata(0);
            if (meta == null || !meta.isStandardMetadataFormatSupported()) return 1;
            String fmt = "javax_imageio_jpeg_image_1.0";
            if (java.util.Arrays.stream(meta.getMetadataFormatNames()).noneMatch(fmt::equals)) return 1;
            return findOrientation(meta.getAsTree(fmt));
        } catch (Exception e) {
            return 1;
        }
    }

    private static int findOrientation(org.w3c.dom.Node node) {
        if (node instanceof IIOMetadataNode n && n.getUserObject() instanceof byte[] data) {
            String tag = n.getAttribute("MarkerTag");
            if ("225".equals(tag) && data.length > 14 && data[0] == 'E' && data[1] == 'x' && data[2] == 'i' && data[3] == 'f') {
                int o = parseTiffOrientation(data, 6);
                if (o >= 1 && o <= 8) return o;
            }
        }
        for (org.w3c.dom.Node c = node.getFirstChild(); c != null; c = c.getNextSibling()) {
            int o = findOrientation(c);
            if (o != 1) return o;
        }
        return 1;
    }

    private static int parseTiffOrientation(byte[] d, int tiff) {
        try {
            boolean le = d[tiff] == 'I';
            int ifd = tiff + u32(d, tiff + 4, le);
            int count = u16(d, ifd, le);
            for (int i = 0; i < count; i++) {
                int entry = ifd + 2 + i * 12;
                if (u16(d, entry, le) == 0x0112) return u16(d, entry + 8, le);
            }
        } catch (RuntimeException ignored) { }
        return 1;
    }

    private static int u16(byte[] d, int p, boolean le) { return le ? (d[p] & 0xFF) | (d[p + 1] & 0xFF) << 8 : (d[p] & 0xFF) << 8 | (d[p + 1] & 0xFF); }
    private static int u32(byte[] d, int p, boolean le) { return le ? u16(d, p, true) | u16(d, p + 2, true) << 16 : u16(d, p, false) << 16 | u16(d, p + 2, false); }

    static BufferedImage applyOrientation(BufferedImage src, int o) {
        if (o <= 1 || o > 8) return src;
        int w = src.getWidth(), h = src.getHeight();
        boolean swap = o >= 5;
        AffineTransform t = switch (o) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, t, null);
        g.dispose();
        return out;
    }

    // ── pixels ────────────────────────────────────────────────────────────

    private static BufferedImage flattenOnWhite(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage fitWithin(BufferedImage src, int max) {
        int w = src.getWidth(), h = src.getHeight();
        if (Math.max(w, h) <= max) return src;
        double scale = (double) max / Math.max(w, h);
        return resize(src, Math.max(1, (int) Math.round(w * scale)), Math.max(1, (int) Math.round(h * scale)));
    }

    /** High-quality downscale: halve repeatedly (bilinear), then a final bicubic step — avoids the jaggies of one big jump. */
    private static BufferedImage resize(BufferedImage src, int tw, int th) {
        BufferedImage cur = src;
        int w = cur.getWidth(), h = cur.getHeight();
        while (w / 2 >= tw && h / 2 >= th) {
            w /= 2; h /= 2;
            cur = scaleOnce(cur, w, h, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return (w == tw && h == th) ? cur : scaleOnce(cur, tw, th, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage scaleOnce(BufferedImage src, int w, int h, Object interpolation) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static byte[] jpeg(BufferedImage img) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(QUALITY);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(32 * 1024);
            try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bos)) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(img, null, null), p);
            }
            return bos.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
