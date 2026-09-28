package com.kaziflow.services;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.utils.SessionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * PermissionService — the enforcement layer that never existed before this.
 * (SessionManager.hasPermission() was the previous attempt: stored data,
 * zero callers anywhere in the codebase — see docs/design/dynamic-roles-and-
 * permissions.md §1 for the full audit.)
 *
 * GENERIC BY DESIGN, on purpose — this is the part of the "generic modular
 * monolith" principle most at risk of erosion in a permissions feature. It
 * would be easy to grow canVoidSale(), canApplyDiscount(), canEditPatients(),
 * one bespoke method per feature, the same way processVisa()/processMastercard()
 * would be easy in a payments module. Instead:
 *
 *   - can(moduleId, Action) is ONE method for all CRUD-style checks on any
 *     module, current or future. moduleId is the exact same string
 *     Module.getId() already returns — no separate permission registry to
 *     keep in sync when a new module is added.
 *   - canPerform(actionKey) is ONE method for named, non-CRUD actions
 *     ("void_sale", "apply_discount", "view_cost_price", ...). New action
 *     keys need zero changes here — see SpecialPermissions for the (also
 *     data-driven) list of what exists.
 *
 * Resolution mirrors Odoo: a user's groups are expanded transitively via
 * permission_group_implies (cycle-guarded), and grants are the UNION across
 * every group — no group can revoke what another grants. Default deny.
 */
public class PermissionService {

    public enum Action { READ, CREATE, WRITE, DELETE }

    private static PermissionService instance;
    public static synchronized PermissionService getInstance() {
        if (instance == null) instance = new PermissionService();
        return instance;
    }

    // Cache: resolved per logged-in user, invalidated on login/logout/group edit.
    private Integer cachedUserId = null;
    private Set<Integer> resolvedGroupIds = Collections.emptySet();
    private Map<String, boolean[]> moduleAccessCache = new HashMap<>(); // moduleId -> [read,create,write,delete]
    private Set<String> specialGrantsCache = new HashSet<>();
    private Boolean superAdminCache = null;

    private PermissionService() {}

    /** Call after login, logout, or any edit to the current user's group assignments. */
    public void invalidate() {
        cachedUserId = null;
        resolvedGroupIds = Collections.emptySet();
        moduleAccessCache = new HashMap<>();
        specialGrantsCache = new HashSet<>();
        superAdminCache = null;
    }

    private void ensureResolved() {
        Integer userId;
        try {
            var user = SessionManager.getInstance().getCurrentUser();
            userId = user != null ? user.getId() : null;
        } catch (Exception e) { userId = null; }

        if (userId == null) { invalidate(); return; }
        if (userId.equals(cachedUserId)) return; // already resolved for this user

        cachedUserId = userId;
        resolvedGroupIds = resolveGroupsForUser(userId);
        moduleAccessCache = loadModuleAccess(resolvedGroupIds);
        specialGrantsCache = loadSpecialGrants(resolvedGroupIds);
        superAdminCache = null; // recomputed lazily by isSuperAdmin()
    }

    /** Directly-assigned groups, expanded transitively through permission_group_implies. Cycle-guarded. */
    private Set<Integer> resolveGroupsForUser(int userId) {
        Set<Integer> direct = new LinkedHashSet<>();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT group_id FROM user_permission_groups WHERE user_id=?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) direct.add(rs.getInt("group_id"));
        } catch (Exception e) { e.printStackTrace(); }

        Set<Integer> expanded = new LinkedHashSet<>(direct);
        Deque<Integer> frontier = new ArrayDeque<>(direct);
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            while (!frontier.isEmpty()) {
                int groupId = frontier.poll();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT implied_id FROM permission_group_implies WHERE group_id=?")) {
                    ps.setInt(1, groupId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        int impliedId = rs.getInt("implied_id");
                        if (expanded.add(impliedId)) frontier.add(impliedId); // only recurse into genuinely new groups — cycle guard
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return expanded;
    }

    private Map<String, boolean[]> loadModuleAccess(Set<Integer> groupIds) {
        Map<String, boolean[]> result = new HashMap<>();
        if (groupIds.isEmpty()) return result;
        String placeholders = String.join(",", Collections.nCopies(groupIds.size(), "?"));
        String sql = "SELECT module_id, can_read, can_create, can_write, can_delete FROM module_access WHERE group_id IN (" + placeholders + ")";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (int gid : groupIds) ps.setInt(i++, gid);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String moduleId = rs.getString("module_id");
                boolean[] flags = result.computeIfAbsent(moduleId, k -> new boolean[4]);
                // Union across groups: OR into whatever's already there, never overwrite with a lesser grant.
                flags[0] |= rs.getInt("can_read") != 0;
                flags[1] |= rs.getInt("can_create") != 0;
                flags[2] |= rs.getInt("can_write") != 0;
                flags[3] |= rs.getInt("can_delete") != 0;
            }
        } catch (Exception e) { e.printStackTrace(); }
        return result;
    }

    private Set<String> loadSpecialGrants(Set<Integer> groupIds) {
        Set<String> result = new HashSet<>();
        if (groupIds.isEmpty()) return result;
        String placeholders = String.join(",", Collections.nCopies(groupIds.size(), "?"));
        String sql = "SELECT action_key FROM special_permissions WHERE granted=1 AND group_id IN (" + placeholders + ")";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (int gid : groupIds) ps.setInt(i++, gid);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(rs.getString("action_key"));
        } catch (Exception e) { e.printStackTrace(); }
        return result;
    }

    // ── The two generic checks — everything in the app should call these, nothing else ──

    /** Generic CRUD check for any module, by its Module.getId() string. */
    public boolean can(String moduleId, Action action) {
        ensureResolved();
        if (isSuperAdmin()) return true;
        boolean[] flags = moduleAccessCache.get(moduleId);
        if (flags == null) return false; // no grant from any group = default deny
        return flags[action.ordinal()];
    }

    public boolean canRead(String moduleId)   { return can(moduleId, Action.READ); }
    public boolean canCreate(String moduleId) { return can(moduleId, Action.CREATE); }
    public boolean canWrite(String moduleId)  { return can(moduleId, Action.WRITE); }
    public boolean canDelete(String moduleId) { return can(moduleId, Action.DELETE); }

    /** Generic named-action check — see SpecialPermissions for the data-driven list of valid keys. */
    public boolean canPerform(String actionKey) {
        ensureResolved();
        if (isSuperAdmin()) return true;
        return specialGrantsCache.contains(actionKey);
    }

    /** Every module id the current user can at least read — used to filter the sidebar. Null = no filtering (super admin). */
    public Set<String> getReadableModuleIds() {
        ensureResolved();
        if (isSuperAdmin()) return null;
        Set<String> ids = new HashSet<>();
        moduleAccessCache.forEach((moduleId, flags) -> { if (flags[0]) ids.add(moduleId); });
        return ids;
    }

    /**
     * A user in the seeded "Administrator" system group bypasses checks entirely. This is
     * NOT a hardcoded role concept re-appearing — it's a single, explicit escape hatch tied
     * to the one group PermissionGroupSetup guarantees always exists and always grants
     * everything, specifically so the lockout-safety rule (design doc §3.4) has a group
     * to protect. Every other group remains fully dynamic, same as before.
     */
    private boolean isSuperAdmin() {
        if (superAdminCache != null) return superAdminCache;
        if (cachedUserId == null) return false;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT COUNT(*) as cnt FROM user_permission_groups upg
                 JOIN permission_groups pg ON pg.id = upg.group_id
                 WHERE upg.user_id = ? AND pg.name = 'Administrator' AND pg.is_system = 1
             """)) {
            ps.setInt(1, cachedUserId);
            ResultSet rs = ps.executeQuery();
            superAdminCache = rs.next() && rs.getInt("cnt") > 0;
        } catch (Exception e) {
            e.printStackTrace();
            superAdminCache = false;
        }
        return superAdminCache;
    }
}
