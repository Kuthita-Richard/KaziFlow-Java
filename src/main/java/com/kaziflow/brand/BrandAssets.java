package com.kaziflow.brand;

import com.kaziflow.utils.ThemeManager;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BrandAssets — the shared logo and icon as JavaFX images. One set serves Tihems, Tihems Software and the
 * product. Images are loaded once and cached; asking for a missing file returns {@code null} rather than
 * throwing, so a broken asset can never stop the app starting.
 *
 * <ul>
 *   <li>{@code mark-*}  — the TH symbol alone (compact spots: sidebar header, login panel)</li>
 *   <li>{@code logo-*}  — symbol + wordmark (About, Help, splash, printed pages)</li>
 *   <li>{@code icon-*}  — the rounded-square app icon (window / taskbar icon, favicons)</li>
 *   <li>{@code -dark} variants are for dark surfaces, {@code -light} for light ones</li>
 * </ul>
 */
public final class BrandAssets {

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();
    private static final int[] WINDOW_ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};

    private BrandAssets() {}

    /** The image in /brand, or null if the file is missing. */
    public static Image image(String file) {
        return CACHE.computeIfAbsent(file, f -> {
            try (InputStream in = BrandAssets.class.getResourceAsStream("/brand/" + f)) {
                return in == null ? null : new Image(in);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** The image scaled to a height, smooth, aspect preserved; null if the asset is missing. */
    public static ImageView view(String file, double fitHeight) {
        Image img = image(file);
        if (img == null) return null;
        ImageView v = new ImageView(img);
        v.setPreserveRatio(true);
        v.setSmooth(true);
        v.setFitHeight(fitHeight);
        return v;
    }

    /** Symbol for a surface: dark variant on dark surfaces. */
    public static ImageView mark(boolean onDark, double fitHeight) { return view(onDark ? "mark-dark.png" : "mark-light.png", fitHeight); }

    /** Symbol + wordmark for a surface. */
    public static ImageView logo(boolean onDark, double fitHeight) { return view(onDark ? "logo-dark.png" : "logo-light.png", fitHeight); }

    /** Symbol for whichever theme the user is in right now (content areas follow the theme; the sidebar is always dark). */
    public static ImageView markForCurrentTheme(double fitHeight) { return mark(ThemeManager.getInstance().isDark(), fitHeight); }

    /** Symbol + wordmark for the current theme. */
    public static ImageView logoForCurrentTheme(double fitHeight) { return logo(ThemeManager.getInstance().isDark(), fitHeight); }

    /** All window-icon sizes, so the OS can pick the crispest one for the title bar, taskbar and Alt-Tab. */
    public static List<Image> windowIcons() {
        List<Image> out = new ArrayList<>();
        for (int s : WINDOW_ICON_SIZES) { Image i = image("icon-" + s + ".png"); if (i != null) out.add(i); }
        return out;
    }

    private static boolean hookInstalled = false;

    /**
     * Installs ONE listener so every window that appears — main window, dialogs, alerts, report windows — gets the
     * brand icon automatically, instead of each screen remembering to set it. Windows that already carry an icon
     * are left alone. Call once at startup.
     */
    public static synchronized void installGlobalWindowIcons() {
        if (hookInstalled) return;
        hookInstalled = true;
        javafx.stage.Window.getWindows().addListener((javafx.collections.ListChangeListener<javafx.stage.Window>) change -> {
            while (change.next()) {
                if (!change.wasAdded()) continue;
                for (javafx.stage.Window w : change.getAddedSubList()) {
                    if (w instanceof Stage s && s.getIcons().isEmpty()) applyTo(s);
                }
            }
        });
        for (javafx.stage.Window w : javafx.stage.Window.getWindows()) if (w instanceof Stage s && s.getIcons().isEmpty()) applyTo(s);
    }

    /** Gives a window the brand icon (keeps whatever it has if the assets are missing). */
    public static void applyTo(Stage stage) {
        if (stage == null) return;
        List<Image> icons = windowIcons();
        if (!icons.isEmpty()) stage.getIcons().setAll(icons);
    }
}
