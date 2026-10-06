package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.utils.ImageProcessor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * ProductImageDAO — product pictures in table {@code product_images} (base64 TEXT: the LAN protocol can't carry
 * BLOBs). Two sizes per product: a 160 px square thumbnail for lists and the POS grid, and a 640 px picture.
 * Pure JDK. Thumbnails can be fetched in one batched query so a grid of 200 products costs one round trip.
 */
public class ProductImageDAO {

    private static final int BATCH = 400;   // SQLite's default parameter limit is 999 (32766 in newer builds)

    /** Stores (or replaces) a product's picture from the processed result. */
    public boolean save(int productId, ImageProcessor.Processed p) {
        String sql = "INSERT OR REPLACE INTO product_images (product_id, mime, image, thumb, updated_at) VALUES (?, 'image/jpeg', ?, ?, CURRENT_TIMESTAMP)";
        try (Connection c = DatabaseManager.getInstance().getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setString(2, Base64.getEncoder().encodeToString(p.full()));
            ps.setString(3, Base64.getEncoder().encodeToString(p.thumb()));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean delete(int productId) {
        try (Connection c = DatabaseManager.getInstance().getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM product_images WHERE product_id = ?")) {
            ps.setInt(1, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public byte[] getImage(int productId) { return getColumn(productId, "image"); }
    public byte[] getThumb(int productId) { return getColumn(productId, "thumb"); }

    private byte[] getColumn(int productId, String column) {   // column is one of two literals below, never user input
        if (!column.equals("image") && !column.equals("thumb")) throw new IllegalArgumentException(column);
        try (Connection c = DatabaseManager.getInstance().getConnection(); PreparedStatement ps = c.prepareStatement("SELECT " + column + " FROM product_images WHERE product_id = ?")) {
            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getString(1) != null ? Base64.getDecoder().decode(rs.getString(1)) : null;
        } catch (SQLException | IllegalArgumentException e) { return null; }
    }

    /** Thumbnails for many products in as few queries as possible. Products without a picture are simply absent from the map. */
    public Map<Integer, byte[]> thumbs(Collection<Integer> productIds) {
        Map<Integer, byte[]> out = new HashMap<>();
        List<Integer> ids = new ArrayList<>(new LinkedHashSet<>(productIds));
        for (int from = 0; from < ids.size(); from += BATCH) {
            List<Integer> chunk = ids.subList(from, Math.min(ids.size(), from + BATCH));
            StringBuilder q = new StringBuilder("SELECT product_id, thumb FROM product_images WHERE product_id IN (");
            for (int i = 0; i < chunk.size(); i++) q.append(i == 0 ? "?" : ",?");
            q.append(")");
            try (Connection c = DatabaseManager.getInstance().getConnection(); PreparedStatement ps = c.prepareStatement(q.toString())) {
                for (int i = 0; i < chunk.size(); i++) ps.setInt(i + 1, chunk.get(i));
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    try { out.put(rs.getInt(1), Base64.getDecoder().decode(rs.getString(2))); } catch (IllegalArgumentException ignored) { }
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }
        return out;
    }

    public boolean has(int productId) { return getThumb(productId) != null; }
}
