package com.kaziflow.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * PermissionGroupSetup — the seeding + one-time migration the permissions
 * design (docs/design/dynamic-roles-and-permissions.md §3.4) called for.
 * Safe to run on EVERY startup; each step is idempotent.
 *
 * <ol>
 *   <li><b>System Administrator group</b> — always exists, flagged is_system.</li>
 *   <li><b>Special-action catalog</b> — seeds {@link SpecialPermissions#DEFAULTS} (INSERT OR IGNORE).</li>
 *   <li><b>One-time legacy migration</b> (guarded by a settings marker) — each old
 *       {@code roles} row becomes a Position, its JSON is translated to module_access
 *       via {@link #LEGACY_KEY_TO_MODULES}, and every user keeps the access their old
 *       role_id implied. After this, Positions are edited in the UI and never re-seeded.</li>
 *   <li><b>Break-glass self-heal</b> — if NO active user is administrator-equivalent
 *       (corrupted data, a failed migration, a hand-edited database) an administrator
 *       is restored, so an install can never be permanently locked out.</li>
 * </ol>
 *
 * Pure JDBC + Gson (no JavaFX, no singletons): takes the list of known module ids
 * as a parameter so it stays decoupled from ModuleRegistry.
 */
public final class PermissionGroupSetup {

    static final String MIGRATED_KEY = "permissions_migrated_v1";

    /**
     * Old role JSON used coarse keys ("sales", "finance"...) that were never real module ids.
     * This table is the ONE place that translates them; everything after migration is per-module.
     */
    static final Map<String, List<String>> LEGACY_KEY_TO_MODULES = Map.of(
        "inventory", List.of("inventory", "batches"),
        "sales",     List.of("sales", "quotations", "delivery", "deni"),
        "purchases", List.of("purchases"),
        "employees", List.of("employees", "payroll"),
        "finance",   List.of("finance"),
        "reports",   List.of("reports")
    );

    /** Modules never treated as an "industry vertical" during migration. */
    private static final Set<String> CORE_NON_VERTICAL = Set.of("dashboard", "settings", "help");

    /** A legacy role granting at least this many keys as full "true" is treated as manager-level. */
    private static final int BROAD_ROLE_MIN_KEYS = 4;

    private PermissionGroupSetup() {}

    public static void seed(Connection c, Collection<String> knownModuleIds) throws SQLException {
        boolean prevAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            int adminId = ensureAdminGroup(c);
            SpecialPermissions.seedDefaults(c);
            if (!isMigrated(c)) {
                migrateLegacyRoles(c, adminId, knownModuleIds);
                markMigrated(c);
            }
            ensureAnAdminExists(c, adminId);
            PermissionResolver.bumpEpoch(c);
            c.commit();
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(prevAuto);
        }
    }

    // ── 1. Administrator group ────────────────────────────────────────────

    private static int ensureAdminGroup(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO permission_groups (name, description, is_system) VALUES (?, ?, 1)")) {
            ps.setString(1, PermissionResolver.ADMIN_GROUP_NAME);
            ps.setString(2, "Full access to everything. Built in: cannot be edited or deleted.");
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("UPDATE permission_groups SET is_system = 1 WHERE name = ?")) {
            ps.setString(1, PermissionResolver.ADMIN_GROUP_NAME);
            ps.executeUpdate();
        }
        Integer id = PermissionResolver.adminGroupId(c);
        if (id == null) throw new SQLException("Administrator group could not be created");
        return id;
    }

    // ── 3. Legacy migration ───────────────────────────────────────────────

    private static boolean isMigrated(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM settings WHERE key = ?")) {
            ps.setString(1, MIGRATED_KEY);
            return ps.executeQuery().next();
        }
    }

    private static void markMigrated(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?, 'true')")) {
            ps.setString(1, MIGRATED_KEY);
            ps.executeUpdate();
        }
    }

    private static void migrateLegacyRoles(Connection c, int adminGroupId, Collection<String> knownModuleIds) throws SQLException {
        Set<String> mapped = new HashSet<>();
        LEGACY_KEY_TO_MODULES.values().forEach(mapped::addAll);
        List<String> verticals = new ArrayList<>();
        for (String id : knownModuleIds) if (!mapped.contains(id) && !CORE_NON_VERTICAL.contains(id)) verticals.add(id);

        Map<Integer, Integer> roleToGroup = new HashMap<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, name, permissions FROM roles ORDER BY id")) {
            while (rs.next()) {
                int roleId = rs.getInt("id");
                String name = rs.getString("name");
                JsonObject json = parse(rs.getString("permissions"));
                boolean all = json != null && json.has("all") && truthy(json.get("all"));
                if (all || PermissionResolver.ADMIN_GROUP_NAME.equalsIgnoreCase(name)) {
                    roleToGroup.put(roleId, adminGroupId);
                    continue;
                }
                int groupId = createGroupIfAbsent(c, name, "Migrated from the legacy role \"" + name + "\".");
                roleToGroup.put(roleId, groupId);
                grantFromLegacyJson(c, groupId, json, verticals);
            }
        }
        // Every user keeps the access their old role_id implied.
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT id, role_id FROM users");
             PreparedStatement ins = c.prepareStatement(
                 "INSERT OR IGNORE INTO user_permission_groups (user_id, group_id) VALUES (?, ?)")) {
            while (rs.next()) {
                Integer g = roleToGroup.get(rs.getInt("role_id"));
                if (g == null) continue;
                ins.setInt(1, rs.getInt("id"));
                ins.setInt(2, g);
                ins.executeUpdate();
            }
        }
    }

    private static void grantFromLegacyJson(Connection c, int groupId, JsonObject json, List<String> verticals) throws SQLException {
        Map<String, boolean[]> access = new LinkedHashMap<>();
        access.put("dashboard", new boolean[]{true, false, false, false}); // everyone lands somewhere
        int fullKeys = 0;
        if (json != null) {
            for (var e : json.entrySet()) {
                List<String> modules = LEGACY_KEY_TO_MODULES.get(e.getKey());
                if (modules == null) continue;
                boolean full = truthy(e.getValue());
                boolean readOnly = !full && e.getValue().isJsonPrimitive() && "read".equalsIgnoreCase(e.getValue().getAsString());
                if (!full && !readOnly) continue;
                if (full) fullKeys++;
                for (String m : modules) access.put(m, full ? new boolean[]{true, true, true, true} : new boolean[]{true, false, false, false});
            }
        }
        boolean broad = fullKeys >= BROAD_ROLE_MIN_KEYS;
        if (broad) for (String m : verticals) access.put(m, new boolean[]{true, true, true, true});

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO module_access (group_id, module_id, can_read, can_create, can_write, can_delete) VALUES (?,?,?,?,?,?)")) {
            for (var e : access.entrySet()) {
                ps.setInt(1, groupId);
                ps.setString(2, e.getKey());
                for (int i = 0; i < 4; i++) ps.setInt(3 + i, e.getValue()[i] ? 1 : 0);
                ps.executeUpdate();
            }
        }
        if (broad) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO special_permissions (group_id, action_key, granted) VALUES (?, ?, 1)")) {
                for (SpecialPermissions.Definition d : SpecialPermissions.DEFAULTS) {
                    ps.setInt(1, groupId);
                    ps.setString(2, d.key());
                    ps.executeUpdate();
                }
            }
        }
    }

    private static int createGroupIfAbsent(Connection c, String name, String description) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO permission_groups (name, description, is_system) VALUES (?, ?, 0)")) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM permission_groups WHERE name = ?")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }
        throw new SQLException("Could not create group " + name);
    }

    private static JsonObject parse(String json) {
        try {
            JsonElement el = JsonParser.parseString(json == null ? "{}" : json);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) { return null; }
    }

    private static boolean truthy(JsonElement el) {
        if (el == null || !el.isJsonPrimitive()) return false;
        return el.getAsJsonPrimitive().isBoolean() ? el.getAsBoolean() : "true".equalsIgnoreCase(el.getAsString());
    }

    // ── 4. Break-glass ────────────────────────────────────────────────────

    /**
     * If nobody active can administer the system, restore someone. Preference order:
     * active users whose legacy role was an all-access role, else the earliest-created active user.
     */
    private static void ensureAnAdminExists(Connection c, int adminGroupId) throws SQLException {
        if (!PermissionResolver.activeAdminUserIds(c).isEmpty()) return;
        List<Integer> candidates = new ArrayList<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("""
                 SELECT u.id FROM users u JOIN roles r ON r.id = u.role_id
                 WHERE u.status = 'active' AND (r.name = 'Administrator' OR r.permissions LIKE '%"all"%')
                 ORDER BY u.id""")) {
            while (rs.next()) candidates.add(rs.getInt(1));
        }
        if (candidates.isEmpty()) {
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT id FROM users WHERE status = 'active' ORDER BY id LIMIT 1")) {
                if (rs.next()) candidates.add(rs.getInt(1));
            }
        }
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT OR IGNORE INTO user_permission_groups (user_id, group_id) VALUES (?, ?)")) {
            for (int userId : candidates) {
                ins.setInt(1, userId);
                ins.setInt(2, adminGroupId);
                ins.executeUpdate();
            }
        }
        if (!candidates.isEmpty()) {
            System.err.println("[Permissions] No active administrator found — restored Administrator access for user id(s) " + candidates);
        }
    }
}
