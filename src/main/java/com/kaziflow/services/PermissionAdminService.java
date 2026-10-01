package com.kaziflow.services;

import com.kaziflow.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * PermissionAdminService — everything the Positions & Access editor and the
 * user screen do to permission data. The read side is {@link PermissionService};
 * this is the write side, kept separate so run-time checks stay tiny and the
 * editing rules live in one place.
 *
 * <h3>Generic by design</h3>
 * One {@link #savePosition} takes a position's whole state (module grants keyed by
 * module id STRING, implied positions, special-action keys) — there is no method per
 * module or per action, so a module or action added tomorrow needs no change here.
 *
 * <h3>Lockout safety (design doc §3.4)</h3>
 * Every mutation runs through {@link #guarded}: one transaction, then a post-condition
 * check that at least one ACTIVE user is still administrator-equivalent (holds the
 * Administrator group directly or via "implies"). If not, the whole change is rolled
 * back and a {@link LockoutException} explains why. This single rule covers deleting
 * the group, un-assigning the last admin, deactivating the last admin, and editing
 * "implies" so an admin stops being one — with no per-case special-casing to forget.
 * (If the system is ALREADY admin-less, changes are allowed so it can be repaired.)
 *
 * Any successful change bumps a settings epoch; {@link PermissionService} re-validates
 * against it, so edits apply to already-logged-in users (and other PCs) within seconds.
 */
public final class PermissionAdminService {

    // ── value types ───────────────────────────────────────────────────────

    public record Position(int id, String name, String description, boolean system, int memberCount) {}

    public record ModuleGrant(boolean read, boolean create, boolean write, boolean delete) {
        public static final ModuleGrant NONE = new ModuleGrant(false, false, false, false);
        public static final ModuleGrant ALL  = new ModuleGrant(true, true, true, true);
        public boolean any() { return read || create || write || delete; }
        public ModuleGrant or(ModuleGrant o) {
            return new ModuleGrant(read || o.read, create || o.create, write || o.write, delete || o.delete);
        }
    }

    public record PositionDetail(Position position, Map<String, ModuleGrant> access,
                                 Set<Integer> implied, Set<String> specials) {}

    public static class PermissionAdminException extends Exception {
        private static final long serialVersionUID = 1L;
        public PermissionAdminException(String m) { super(m); }
        public PermissionAdminException(String m, Throwable t) { super(m, t); }
    }
    /** The requested change would leave no active administrator. */
    public static class LockoutException extends PermissionAdminException {
        private static final long serialVersionUID = 1L;
        public LockoutException(String m) { super(m); }
    }

    @FunctionalInterface private interface Work<T> { T run(Connection c) throws SQLException, PermissionAdminException; }

    private static PermissionAdminService instance;
    public static synchronized PermissionAdminService getInstance() {
        if (instance == null) instance = new PermissionAdminService();
        return instance;
    }
    private PermissionAdminService() {}

    // ── reads ─────────────────────────────────────────────────────────────

    public List<Position> listPositions() {
        List<Position> out = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("""
                SELECT g.id, g.name, g.description, g.is_system,
                       (SELECT COUNT(*) FROM user_permission_groups u WHERE u.group_id = g.id) AS members
                FROM permission_groups g
                ORDER BY g.is_system DESC, g.name COLLATE NOCASE""")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(new Position(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4) == 1, rs.getInt(5)));
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    public PositionDetail load(int groupId) {
        try (Connection c = conn()) {
            Position pos = null;
            for (Position p : listPositions()) if (p.id() == groupId) pos = p;
            if (pos == null) return null;
            Map<String, ModuleGrant> access = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT module_id, can_read, can_create, can_write, can_delete FROM module_access WHERE group_id = ?")) {
                ps.setInt(1, groupId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) access.put(rs.getString(1),
                    new ModuleGrant(rs.getInt(2) != 0, rs.getInt(3) != 0, rs.getInt(4) != 0, rs.getInt(5) != 0));
            }
            Set<Integer> implied = new LinkedHashSet<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT implied_id FROM permission_group_implies WHERE group_id = ?")) {
                ps.setInt(1, groupId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) implied.add(rs.getInt(1));
            }
            Set<String> specials = new LinkedHashSet<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT action_key FROM special_permissions WHERE group_id = ? AND granted = 1")) {
                ps.setInt(1, groupId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) specials.add(rs.getString(1));
            }
            return new PositionDetail(pos, access, implied, specials);
        } catch (SQLException e) { e.printStackTrace(); return null; }
    }

    /**
     * What a position grants once everything it implies is included. Used by the editor to show
     * "inherited" access next to what is granted directly.
     */
    public Map<String, ModuleGrant> inheritedAccess(int groupId) {
        Map<String, ModuleGrant> out = new LinkedHashMap<>();
        try (Connection c = conn()) {
            Set<Integer> all = PermissionResolver.expand(List.of(groupId), PermissionResolver.loadImplies(c));
            all.remove(groupId);
            for (int g : all) {
                PositionDetail d = load(g);
                if (d != null) d.access().forEach((m, grant) -> out.merge(m, grant, ModuleGrant::or));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    /** All special-action keys held by the position's inherited positions (excluding its own direct grants). */
    public Set<String> inheritedSpecials(int groupId) {
        Set<String> out = new LinkedHashSet<>();
        try (Connection c = conn()) {
            Set<Integer> all = PermissionResolver.expand(List.of(groupId), PermissionResolver.loadImplies(c));
            all.remove(groupId);
            for (int g : all) { PositionDetail d = load(g); if (d != null) out.addAll(d.specials()); }
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    public Set<Integer> getUserPositions(int userId) {
        Set<Integer> out = new LinkedHashSet<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT group_id FROM user_permission_groups WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(rs.getInt(1));
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    /** user id -> names of the positions assigned directly to them (for table badges). */
    public Map<Integer, List<String>> positionNamesByUser() {
        Map<Integer, List<String>> out = new HashMap<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("""
                SELECT upg.user_id, g.name FROM user_permission_groups upg
                JOIN permission_groups g ON g.id = upg.group_id
                ORDER BY g.is_system DESC, g.name COLLATE NOCASE""")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(rs.getString(2));
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    public int countActiveAdmins() {
        try (Connection c = conn()) { return PermissionResolver.activeAdminUserIds(c).size(); }
        catch (SQLException e) { e.printStackTrace(); return 0; }
    }

    /** True if this user currently counts as an active administrator-equivalent. */
    public boolean isActiveAdmin(int userId) {
        try (Connection c = conn()) { return PermissionResolver.activeAdminUserIds(c).contains(userId); }
        catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // ── writes ────────────────────────────────────────────────────────────

    /**
     * Creates (id == null) or updates a position in ONE transaction.
     * For module grants only the modules present in {@code access} are touched — grants for modules
     * not in the map (e.g. a custom module that is currently unavailable) are left alone.
     * The built-in Administrator position only accepts a new description.
     *
     * @return the position id
     */
    public int savePosition(Integer id, String name, String description,
                            Map<String, ModuleGrant> access, Set<Integer> implied, Set<String> specials)
            throws PermissionAdminException {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) throw new PermissionAdminException("A position needs a name.");
        if (cleanName.length() > 60) throw new PermissionAdminException("Position name is too long (60 characters max).");
        String desc = description == null ? "" : description.trim();

        int result = guarded(c -> {
            Integer adminId = PermissionResolver.adminGroupId(c);
            boolean isAdminGroup = id != null && adminId != null && id.equals(adminId);

            // Unique name, case-insensitively (the column's UNIQUE is case-sensitive).
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM permission_groups WHERE LOWER(name) = LOWER(?)")) {
                ps.setString(1, cleanName);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    if (id == null || rs.getInt(1) != id)
                        throw new PermissionAdminException("A position named \"" + cleanName + "\" already exists.");
                }
            }

            int gid;
            if (id == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO permission_groups (name, description, is_system) VALUES (?, ?, 0)")) {
                    ps.setString(1, cleanName);
                    ps.setString(2, desc);
                    ps.executeUpdate();
                }
                // Look the id up by the (unique) name rather than getGeneratedKeys(): that is not
                // supported by every JDBC path this app uses (sqlite-jdbc, and the LAN RemoteConnection).
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM permission_groups WHERE name = ?")) {
                    ps.setString(1, cleanName);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) throw new SQLException("New position was not found after insert");
                    gid = rs.getInt(1);
                }
            } else {
                gid = id;
                if (isAdminGroup) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE permission_groups SET description = ? WHERE id = ?")) {
                        ps.setString(1, desc); ps.setInt(2, gid); ps.executeUpdate();
                    }
                    return gid; // nothing else about the built-in Administrator is editable
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE permission_groups SET name = ?, description = ? WHERE id = ?")) {
                    ps.setString(1, cleanName); ps.setString(2, desc); ps.setInt(3, gid);
                    if (ps.executeUpdate() == 0) throw new PermissionAdminException("That position no longer exists.");
                }
            }

            // module access — only touch the modules we were given
            if (access != null) {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM module_access WHERE group_id = ? AND module_id = ?");
                     PreparedStatement ins = c.prepareStatement(
                         "INSERT INTO module_access (group_id, module_id, can_read, can_create, can_write, can_delete) VALUES (?,?,?,?,?,?)")) {
                    for (var e : access.entrySet()) {
                        del.setInt(1, gid); del.setString(2, e.getKey()); del.executeUpdate();
                        ModuleGrant g = e.getValue();
                        if (g != null && g.any()) {
                            ins.setInt(1, gid); ins.setString(2, e.getKey());
                            ins.setInt(3, g.read() ? 1 : 0); ins.setInt(4, g.create() ? 1 : 0);
                            ins.setInt(5, g.write() ? 1 : 0); ins.setInt(6, g.delete() ? 1 : 0);
                            ins.executeUpdate();
                        }
                    }
                }
            }

            // implies — validate, then replace
            Set<Integer> wanted = new LinkedHashSet<>(implied == null ? Set.of() : implied);
            wanted.remove(gid);
            Map<Integer, Set<Integer>> graph = PermissionResolver.loadImplies(c);
            for (int target : wanted) {
                if (!exists(c, target)) throw new PermissionAdminException("An inherited position no longer exists.");
                if (PermissionResolver.expand(List.of(target), graph).contains(gid))
                    throw new PermissionAdminException(
                        "That would create a loop: \"" + nameOf(c, target) + "\" already includes \"" + cleanName + "\".");
            }
            try (PreparedStatement del = c.prepareStatement("DELETE FROM permission_group_implies WHERE group_id = ?");
                 PreparedStatement ins = c.prepareStatement("INSERT INTO permission_group_implies (group_id, implied_id) VALUES (?, ?)")) {
                del.setInt(1, gid); del.executeUpdate();
                for (int target : wanted) { ins.setInt(1, gid); ins.setInt(2, target); ins.executeUpdate(); }
            }

            // special actions — replace, keeping only keys that exist in the catalog
            if (specials != null) {
                Set<String> catalog = new HashSet<>();
                for (SpecialPermissions.Definition d : SpecialPermissions.list(c)) catalog.add(d.key());
                try (PreparedStatement del = c.prepareStatement("DELETE FROM special_permissions WHERE group_id = ?");
                     PreparedStatement ins = c.prepareStatement(
                         "INSERT INTO special_permissions (group_id, action_key, granted) VALUES (?, ?, 1)")) {
                    del.setInt(1, gid); del.executeUpdate();
                    for (String key : specials) {
                        if (!catalog.contains(key)) continue;
                        ins.setInt(1, gid); ins.setString(2, key); ins.executeUpdate();
                    }
                }
            }
            return gid;
        });
        audit(id == null ? "POSITION_CREATED" : "POSITION_UPDATED",
              (id == null ? "Position created: " : "Position updated: ") + cleanName, result);
        return result;
    }

    public void deletePosition(int groupId) throws PermissionAdminException {
        String name = guarded(c -> {
            Integer adminId = PermissionResolver.adminGroupId(c);
            if (adminId != null && adminId == groupId)
                throw new PermissionAdminException("The built-in Administrator position cannot be deleted.");
            String n = nameOf(c, groupId);
            if (n == null) throw new PermissionAdminException("That position no longer exists.");
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM permission_groups WHERE id = ?")) {
                ps.setInt(1, groupId);
                ps.executeUpdate();
            }
            return n;
        });
        audit("POSITION_DELETED", "Position deleted: " + name, groupId);
    }

    /**
     * Replaces the positions a user holds, and (optionally) their account status, in one guarded step.
     * Pass {@code newStatus == null} to leave the status alone.
     */
    public void saveUserAccess(int userId, Set<Integer> positionIds, String newStatus) throws PermissionAdminException {
        guarded(c -> {
            try (PreparedStatement del = c.prepareStatement("DELETE FROM user_permission_groups WHERE user_id = ?");
                 PreparedStatement ins = c.prepareStatement("INSERT INTO user_permission_groups (user_id, group_id) VALUES (?, ?)")) {
                del.setInt(1, userId); del.executeUpdate();
                for (int gid : positionIds) {
                    if (!exists(c, gid)) throw new PermissionAdminException("A selected position no longer exists.");
                    ins.setInt(1, userId); ins.setInt(2, gid); ins.executeUpdate();
                }
            }
            if (newStatus != null) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE users SET status = ? WHERE id = ?")) {
                    ps.setString(1, newStatus); ps.setInt(2, userId); ps.executeUpdate();
                }
            }
            return null;
        });
        audit("USER_ACCESS_CHANGED", "Positions changed for user id " + userId, userId);
    }

    /** Guard for deleting/deactivating a user outside saveUserAccess: throws if it would leave no administrator. */
    public void checkUserCanBeRemoved(int userId) throws PermissionAdminException {
        try (Connection c = conn()) {
            List<Integer> admins = PermissionResolver.activeAdminUserIds(c);
            if (admins.size() == 1 && admins.contains(userId))
                throw new LockoutException("This is the only active administrator. Make someone else an administrator first.");
        } catch (SQLException e) { throw new PermissionAdminException("Database error: " + e.getMessage(), e); }
    }

    /**
     * Legacy compatibility only: users.role_id is still NOT NULL with a foreign key to the old roles table.
     * Picks a role whose name matches one of the user's positions, else a non-admin legacy role.
     */
    public int legacyRoleIdFor(Set<Integer> positionIds) {
        try (Connection c = conn()) {
            for (int gid : positionIds) {
                String n = nameOf(c, gid);
                if (n == null || PermissionResolver.ADMIN_GROUP_NAME.equalsIgnoreCase(n)) continue;
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM roles WHERE LOWER(name) = LOWER(?)")) {
                    ps.setString(1, n);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) return rs.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM roles WHERE permissions NOT LIKE '%\"all\"%' ORDER BY id DESC LIMIT 1")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt(1);
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM roles ORDER BY id LIMIT 1")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 1;
    }

    // ── internals ─────────────────────────────────────────────────────────

    private Connection conn() throws SQLException { return DatabaseManager.getInstance().getConnection(); }

    /**
     * One transaction + the "an active administrator must remain" post-condition.
     *
     * <p>Retries when SQLite reports the database busy. In WAL mode a transaction that READS first and
     * then writes fails immediately (SQLITE_BUSY_SNAPSHOT — the 5 s busy_timeout does not apply) if any
     * other connection committed in between, and the asynchronous audit writer commits all the time. The
     * work is idempotent (a failed attempt is fully rolled back), so trying again is safe.
     */
    private <T> T guarded(Work<T> work) throws PermissionAdminException {
        PermissionAdminException last = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                return guardedOnce(work);
            } catch (PermissionAdminException e) {
                if (!isBusy(e)) throw e;
                last = e;
                try { Thread.sleep(120L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        throw last;
    }

    private static boolean isBusy(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m != null && (m.contains("SQLITE_BUSY") || m.contains("database is locked"))) return true;
        }
        return false;
    }

    private <T> T guardedOnce(Work<T> work) throws PermissionAdminException {
        try (Connection c = conn()) {
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int before = PermissionResolver.activeAdminUserIds(c).size();
                T result = work.run(c);
                int after = PermissionResolver.activeAdminUserIds(c).size();
                if (before >= 1 && after < 1) {
                    c.rollback();
                    throw new LockoutException(
                        "That change would leave nobody able to administer the system. Keep at least one active Administrator.");
                }
                PermissionResolver.bumpEpoch(c);
                c.commit();
                return result;
            } catch (PermissionAdminException e) {
                safeRollback(c); throw e;
            } catch (SQLException e) {
                safeRollback(c); throw new PermissionAdminException("Database error: " + e.getMessage(), e);
            } finally {
                try { c.setAutoCommit(prev); } catch (SQLException ignored) { }
            }
        } catch (SQLException e) {
            throw new PermissionAdminException("Database error: " + e.getMessage(), e);
        }
    }

    private static void safeRollback(Connection c) { try { c.rollback(); } catch (SQLException ignored) { } }

    private static boolean exists(Connection c, int groupId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM permission_groups WHERE id = ?")) {
            ps.setInt(1, groupId);
            return ps.executeQuery().next();
        }
    }

    private static String nameOf(Connection c, int groupId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT name FROM permission_groups WHERE id = ?")) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static void audit(String action, String description, int recordId) {
        try { AuditLog.log(action, description, "settings", recordId); } catch (Throwable ignored) { }
    }
}
