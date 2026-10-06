package com.kaziflow.utils;

import com.kaziflow.dao.ProductImageDAO;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ProductImages — product thumbnails for the JavaFX screens, with a small LRU cache and BATCH PREFETCH.
 *
 * <p>Table cells and POS cards ask for a thumbnail every time they are redrawn (scrolling, filtering). Without a
 * cache that is a database hit per cell; with {@link #prefetch} the screen loads all the visible products' pictures
 * in one query and every later {@link #thumb} is a memory lookup. "No picture" is cached too, so products without a
 * photo don't trigger a query per redraw either. Call {@link #invalidate} after changing a product's picture.
 */
public final class ProductImages {

    private static final int MAX_ENTRIES = 600;
    private static final ProductImageDAO DAO = new ProductImageDAO();
    private static final Map<Integer, Optional<Image>> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        private static final long serialVersionUID = 1L;
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Optional<Image>> e) { return size() > MAX_ENTRIES; }
    };

    private ProductImages() {}

    /** Loads thumbnails for these products in one batched query (skipping ones already cached). */
    public static synchronized void prefetch(Collection<Integer> productIds) {
        java.util.List<Integer> missing = new java.util.ArrayList<>();
        for (Integer id : productIds) if (!CACHE.containsKey(id)) missing.add(id);
        if (missing.isEmpty()) return;
        Map<Integer, byte[]> found = DAO.thumbs(missing);
        for (Integer id : missing) {
            byte[] b = found.get(id);
            CACHE.put(id, b == null ? Optional.empty() : Optional.of(new Image(new ByteArrayInputStream(b))));
        }
    }

    /** The thumbnail, or null if the product has no picture. */
    public static synchronized Image thumb(int productId) {
        Optional<Image> cached = CACHE.get(productId);
        if (cached == null) { prefetch(java.util.List.of(productId)); cached = CACHE.get(productId); }
        return cached == null ? null : cached.orElse(null);
    }

    /** A square thumbnail view of the given size, or null if the product has no picture. */
    public static ImageView thumbView(int productId, double size) {
        Image img = thumb(productId);
        if (img == null) return null;
        ImageView v = new ImageView(img);
        v.setFitWidth(size);
        v.setFitHeight(size);
        v.setPreserveRatio(true);
        v.setSmooth(true);
        return v;
    }

    /** The larger picture (loaded on demand, not cached — it is only shown in detail views). */
    public static Image full(int productId) {
        byte[] b = DAO.getImage(productId);
        return b == null ? null : new Image(new ByteArrayInputStream(b));
    }

    public static synchronized void invalidate(int productId) { CACHE.remove(productId); }

    public static synchronized void clear() { CACHE.clear(); }
}
