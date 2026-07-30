package com.kaziflow.utils;

import javafx.scene.Scene;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * ThemeManager — singleton responsible for switching between light and dark mode.
 *
 * DESIGN:
 *  - The dark stylesheet (dark.css) overrides CSS variables and class styles
 *    defined in main.css. Applying dark.css as a second stylesheet on a scene
 *    causes the cascade to prefer its definitions, achieving theme switching
 *    without touching any view code.
 *  - ThemeManager tracks all open Scenes (registered by SceneManager on
 *    creation) so toggling the theme applies to every window instantly.
 *  - Preference (light/dark) is persisted via java.util.prefs.Preferences
 *    (stored in the OS user preferences store: registry on Windows,
 *    ~/Library/Preferences on macOS, ~/.java/.userPrefs on Linux).
 *  - ThemeColors provides string constants for the two most common inline
 *    style colors, allowing views to use ThemeColors.SURFACE instead of
 *    hardcoded "#f8fafc" / "#1e2535". Not all 600+ inline styles are
 *    converted — only the most structural ones; the CSS overrides handle
 *    everything that uses a CSS class.
 */
public class ThemeManager {

    public enum Theme { LIGHT, DARK }

    private static final String PREF_KEY = "kaziflow.theme";
    private static final String DARK_CSS = "/styles/dark.css";

    private static ThemeManager instance;
    private Theme currentTheme;
    private final List<Scene> scenes = new ArrayList<>();
    private final Preferences prefs;

    private ThemeManager() {
        prefs = Preferences.userNodeForPackage(ThemeManager.class);
        String saved = prefs.get(PREF_KEY, "LIGHT");
        currentTheme = "DARK".equals(saved) ? Theme.DARK : Theme.LIGHT;
    }

    public static synchronized ThemeManager getInstance() {
        if (instance == null) instance = new ThemeManager();
        return instance;
    }

    // ── Scene registration ────────────────────────────────────────────────

    /** Called by SceneManager whenever it creates a new Scene. */
    public void registerScene(Scene scene) {
        scenes.add(scene);
        applyTheme(scene, currentTheme);
    }

    public void unregisterScene(Scene scene) {
        scenes.remove(scene);
    }

    // ── Theme switching ───────────────────────────────────────────────────

    public Theme getTheme() { return currentTheme; }
    public boolean isDark()  { return currentTheme == Theme.DARK; }

    /** Toggle between light and dark, applying to all registered scenes. */
    public void toggleTheme() {
        setTheme(currentTheme == Theme.LIGHT ? Theme.DARK : Theme.LIGHT);
    }

    public void setTheme(Theme theme) {
        currentTheme = theme;
        prefs.put(PREF_KEY, theme.name());
        scenes.forEach(s -> applyTheme(s, theme));
    }

    private void applyTheme(Scene scene, Theme theme) {
        if (scene == null) return;
        String darkUrl = ThemeManager.class.getResource(DARK_CSS).toExternalForm();
        if (theme == Theme.DARK) {
            if (!scene.getStylesheets().contains(darkUrl))
                scene.getStylesheets().add(darkUrl);
        } else {
            scene.getStylesheets().remove(darkUrl);
        }
    }

    // ── Inline style colors ───────────────────────────────────────────────

    /**
     * Returns the appropriate color string for common inline-style properties,
     * based on the current theme.  Views can call these instead of hardcoding
     * "#f8fafc" etc., getting theme-correct colors at component-build time.
     *
     * NOTE: these are evaluated at the time a view is constructed.  Views that
     * are built once and cached will not auto-update when the theme changes —
     * only CSS-class-based styles update instantly.  For a fully reactive
     * implementation, views would need to listen to a ThemeChangeEvent; that
     * is left as a Phase 7 enhancement.
     */
    public String surface()      { return isDark() ? "#1e2535" : "white";    }
    public String background()   { return isDark() ? "#161b27" : "#f8fafc";  }
    public String border()       { return isDark() ? "#2d3748" : "#e2e8f0";  }
    public String textPrimary()  { return isDark() ? "#e2e8f0" : "#1e293b";  }
    public String textMuted()    { return isDark() ? "#8892a4" : "#64748b";  }
    public String inputBg()      { return isDark() ? "#2d3748" : "white";    }
    public String inputBorder()  { return isDark() ? "#3d4f6b" : "#e2e8f0";  }
    public String hoverBg()      { return isDark() ? "#252f42" : "#f8fafc";  }
    public String cardBg()       { return isDark() ? "#1e2535" : "white";    }
    public String headerBg()     { return isDark() ? "#1e2535" : "white";    }
    public String sidebarBg()    { return isDark() ? "#0f1420" : "#0d1b2a";  }
    public String navActive()    { return "#2563eb";                         } // same in both themes
}
