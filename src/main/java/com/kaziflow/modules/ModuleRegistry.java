package com.kaziflow.modules;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.modules.core.*;
import javafx.scene.layout.Region;

import java.sql.*;
import java.util.*;

/**
 * ModuleRegistry — the central plug-in/plug-out engine for KaziFlow ERP.
 *
 * Responsibilities:
 *   1. Loads the active IndustryProfile from the database on startup
 *   2. Initialises enabled Module instances and calls onLoad()
 *   3. Provides the ordered list of nav items for MainLayout
 *   4. Builds module views on demand (used by SceneManager)
 *   5. Handles enable/disable of individual modules at runtime
 *
 * Usage:
 *   ModuleRegistry reg = ModuleRegistry.getInstance();
 *   reg.init();  // call once in App.start()
 *
 *   List<Module> navModules = reg.getNavModules();
 *   Region view = reg.buildView("inventory");
 *   IndustryProfile profile = reg.getActiveProfile();
 */
public class ModuleRegistry {

    private static ModuleRegistry instance;

    private IndustryProfile activeProfile = IndustryProfile.GENERAL_RETAIL;
    private final Map<String, Module> activeModules = new LinkedHashMap<>();
    private boolean initialized = false;

    // ── All available core modules (always present in codebase) ──────────
    private static final List<Module> CORE_MODULES = List.of(
        new DashboardModule(),
        new InventoryModule(),
        new BatchModule(),
        new SalesModule(),
        new PurchasesModule(),
        new AppointmentModule(),
        new PatientModule(),
        new RestaurantModule(),
        new WorkshopModule(),
        new GymModule(),
        new LaundryModule(),
        new HotelModule(),
        new FuelStationModule(),
        new SchoolCanteenModule(),
        new EmployeesModule(),
        new PayrollModule(),
        new FinanceModule(),
        new ReportsModule(),
        new DeniModule(),
        new QuotationModule(),
        new SaccoModule(),
        new DeliveryModule(),
        new SettingsModule(),
        new HelpModule()
    );

    /** CORE_MODULES plus anything added at run time via {@link #registerModule(Module)}. */
    private final List<Module> allModules = new ArrayList<>(CORE_MODULES);

    private ModuleRegistry() {}

    public static ModuleRegistry getInstance() {
        if (instance == null) instance = new ModuleRegistry();
        return instance;
    }

    // ── Initialisation ────────────────────────────────────────────────────

    /**
     * Load the active profile from DB and initialise all enabled modules.
     * Call once in App.start() after DatabaseManager.initialize().
     */
    public void init() {
        if (initialized) return;
        IndustryProfile fromDb = loadProfileFromDb();
        // On first run loadProfileFromDb() returns null — default to GENERAL_RETAIL
        activeProfile = (fromDb != null) ? fromDb : IndustryProfile.GENERAL_RETAIL;
        loadModules();
        initialized = true;
        System.out.println("[ModuleRegistry] Profile: " + activeProfile.displayName
            + " | Modules: " + activeModules.keySet());
    }

    private void loadModules() {
        activeModules.clear();
        Set<String> overrides = loadModuleOverrides();
        for (Module m : allModules) {
            // An override list, when present, takes precedence over the profile's default
            // module list entirely — this is what actually lets someone running (for example)
            // a CUSTOM healthcare business with a pharmacy + clinic + nursing home under one
            // roof turn on exactly the modules they need, rather than being stuck with
            // whichever single IndustryProfile's fixed list happens to be closest.
            boolean enabled = m.isUserDefined()
                || ((overrides != null) ? overrides.contains(m.getId()) : activeProfile.hasModule(m.getId()));
            if (enabled) {
                try {
                    m.onLoad();
                    activeModules.put(m.getId(), m);
                } catch (Exception e) {
                    System.err.println("[ModuleRegistry] Failed to load module: " + m.getId());
                    e.printStackTrace();
                }
            }
        }
    }

    // ── Profile management ────────────────────────────────────────────────

    public IndustryProfile getActiveProfile() { return activeProfile; }

    public boolean isFirstRun() {
        return loadProfileFromDb() == null;
    }

    public void setProfile(IndustryProfile profile) {
        this.activeProfile = profile;
        saveProfileToDb(profile);
        clearModuleOverrides(); // switching profiles should apply that profile's defaults, not a stale override from before
        loadModules();
    }

    public String term(String key, String defaultLabel) {
        return activeProfile.term(key, defaultLabel);
    }

    // ── Module access ─────────────────────────────────────────────────────

    public boolean isEnabled(String moduleId) {
        return activeModules.containsKey(moduleId);
    }

    /**
     * Returns modules for the main sidebar nav (top section),
     * in the order they were registered.
     */
    public List<Module> getNavModules() {
        return activeModules.values().stream()
            .filter(m -> !m.isBottomNav())
            .filter(this::isAccessible)
            .toList();
    }

    /**
     * Returns modules for the bottom sidebar section (Settings, Help).
     */
    public List<Module> getBottomNavModules() {
        return activeModules.values().stream()
            .filter(Module::isBottomNav)
            .filter(this::isAccessible)
            .toList();
    }

    // ── Permission-aware access ───────────────────────────────────────────
    //
    // Filtering happens here, at READ time, per current user — NOT inside loadModules().
    // loadModules() runs once at startup before anyone has logged in (and activeModules is
    // shared by every user of this process), so baking permissions into it would freeze the
    // first user's view for everyone after a logout/login.

    /** True if the module is enabled on this installation AND the signed-in user may open it. */
    public boolean isAccessible(String moduleId) {
        Module m = activeModules.get(moduleId);
        return m != null && isAccessible(m);
    }

    private boolean isAccessible(Module m) {
        if (m.alwaysAccessible()) return com.kaziflow.utils.SessionManager.getInstance().isLoggedIn();
        return com.kaziflow.services.PermissionService.getInstance().canRead(m.getId());
    }

    /**
     * Where to land after login: the dashboard if allowed, else the first module in sidebar order
     * the user can open. Null means the user has no access to anything (show a "no access" page).
     */
    public String getFirstAccessibleModuleId() {
        if (isAccessible("dashboard")) return "dashboard";
        // Modules open to everyone (Help) don't count as somewhere to land: a user who has been given
        // nothing should see the "no access assigned" page, not be dropped into Help.
        for (Module m : getNavModules()) if (!m.alwaysAccessible()) return m.getId();
        for (Module m : getBottomNavModules()) if (!m.alwaysAccessible()) return m.getId();
        return null;
    }

    /**
     * Plug in a module at run time (e.g. one an administrator has built). It immediately shows up in
     * getAllAvailableModules(), so the Positions & Access editor offers it with no further work;
     * a user-defined module ({@link Module#isUserDefined()}) is also active straight away; a shipped one is
     * switched on via Settings → System Modules. Returns false if the id is taken.
     */
    public boolean registerModule(Module module) {
        for (Module m : allModules) if (m.getId().equals(module.getId())) return false;
        allModules.add(module);
        if (initialized) loadModules();
        return true;
    }

    /**
     * Build and return the view for a module.
     * Returns an error pane if the module is not enabled or not found.
     */
    public Region buildView(String moduleId) {
        Module m = activeModules.get(moduleId);
        if (m != null) return m.buildView();
        // Fallback to dashboard
        Module dash = activeModules.get("dashboard");
        return dash != null ? dash.buildView() : new javafx.scene.layout.StackPane();
    }

    /**
     * Returns all module IDs that should NOT be cached in SceneManager
     * (i.e. modules that declared noCache() = true).
     */
    public Set<String> getNoCacheModules() {
        Set<String> result = new HashSet<>();
        activeModules.forEach((id, m) -> { if (m.noCache()) result.add(id); });
        return result;
    }

    /**
     * Returns every module the app knows how to build, regardless of whether
     * it's currently active — the full checklist for a "which modules do you
     * want" settings screen.
     */
    public List<Module> getAllAvailableModules() {
        return Collections.unmodifiableList(allModules);
    }

    /** Currently-active module ids, as a plain set — used to pre-check the toggle UI. */
    public Set<String> getEnabledModuleIds() {
        return new LinkedHashSet<>(activeModules.keySet());
    }

    /** True if this installation is running a manual module override rather than the plain profile defaults. */
    public boolean hasModuleOverrides() {
        return loadModuleOverrides() != null;
    }

    /**
     * Sets exactly which modules are enabled, overriding the active profile's
     * default list. This is the real implementation of "enable/disable
     * individual modules at runtime" — previously documented on this class
     * but never actually built.
     */
    public void setModuleOverrides(Set<String> enabledModuleIds) {
        saveModuleOverrides(enabledModuleIds);
        loadModules();
    }

    /** Reverts to the active profile's plain default module list, discarding any override. */
    public void clearModuleOverrides() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM settings WHERE key='module_overrides'")) {
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private Set<String> loadModuleOverrides() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT value FROM settings WHERE key='module_overrides'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String csv = rs.getString("value");
                if (csv != null && !csv.isBlank()) {
                    return new LinkedHashSet<>(Arrays.asList(csv.split(",")));
                }
            }
        } catch (Exception e) { /* table may not exist yet on first run */ }
        return null; // null = no override, use the active profile's default list
    }

    private void saveModuleOverrides(Set<String> enabledModuleIds) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES ('module_overrides', ?)")) {
            ps.setString(1, String.join(",", enabledModuleIds));
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── DB persistence ────────────────────────────────────────────────────

    private IndustryProfile loadProfileFromDb() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT value FROM settings WHERE key='industry_profile'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return IndustryProfile.fromKey(rs.getString("value"));
        } catch (Exception e) { /* table may not exist yet on first run */ }
        return null; // null = first run, profile not set
    }

    private void saveProfileToDb(IndustryProfile profile) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES ('industry_profile', ?)")) {
            ps.setString(1, profile.name());
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }
}
