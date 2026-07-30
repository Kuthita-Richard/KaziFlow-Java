package com.kaziflow.modules;

import javafx.scene.layout.Region;

/**
 * Module — the contract every KaziFlow module must implement.
 *
 * Modules are self-contained units that can be plugged in or out
 * without touching core code. The ModuleRegistry loads enabled
 * modules at startup based on the active IndustryProfile.
 *
 * To add a new module:
 *   1. Create a class implementing Module
 *   2. Register it in ModuleRegistry.ALL_MODULES
 *   3. Add it to the relevant IndustryProfile(s)
 *   4. Add a view builder case in SceneManager.buildView()
 */
public interface Module {

    /** Unique key used for navigation and settings storage. E.g. "inventory", "batch" */
    String getId();

    /** Display label shown in the sidebar nav. E.g. "Inventory" */
    String getLabel();

    /** Icon character shown in the sidebar. E.g. "◈" */
    String getIcon();

    /** Build and return the module's main UI pane. Called once then cached. */
    Region buildView();

    /**
     * Whether this module should always rebuild fresh on navigation
     * (i.e. it's stateless/lightweight, like Settings or Help).
     * Default: false (views are cached).
     */
    default boolean noCache() { return false; }

    /**
     * Whether this module appears in the bottom section of the sidebar
     * (below the spacer). Default: false (appears in main nav).
     */
    default boolean isBottomNav() { return false; }

    /**
     * Called once when the module is first loaded by ModuleRegistry.
     * Use for one-time setup: ensuring DB tables exist, seeding data, etc.
     * Default: no-op.
     */
    default void onLoad() {}
}
