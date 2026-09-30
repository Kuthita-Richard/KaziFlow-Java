package com.kaziflow.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * PermissionResolver — the small, pure-JDBC building blocks shared by
 * {@link PermissionService} (run-time checks), {@link PermissionAdminService}
 * (editing + lockout safety) and {@link PermissionGroupSetup} (seeding).
 * One implementation of "expand a group set through the implies graph" and
 * "who counts as an administrator" so those rules can never drift apart
 * between the checker and the editor that protects it.
 *
 * No JavaFX, no singletons — takes a Connection, so it can be compiled and
 * runtime-tested against a real SQLite file in the sandbox.
 */
final class PermissionResolver {

    /** settings key holding a counter that is bumped on ANY permission change. */
    static final String EPOCH_KEY = "permissions_epoch";
    static final String ADMIN_GROUP_NAME = "Administrator";

    private PermissionResolver() {}

    /** group_id -> the set of groups it directly implies. */
    static Map<Integer, Set<Integer>> loadImplies(Connection c) throws SQLException {
        Map<Integer, Set<Integer>> graph = new HashMap<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT group_id, implied_id FROM permission_group_implies")) {
            while (rs.next()) graph.computeIfAbsent(rs.getInt(1), k -> new LinkedHashSet<>()).add(rs.getInt(2));
        }
        return graph;
    }

    /** Transitive closure of {@code direct} through the implies graph. Cycle-safe: each group is expanded once. */
    static Set<Integer> expand(Collection<Integer> direct, Map<Integer, Set<Integer>> implies) {
        Set<Integer> out = new LinkedHashSet<>(direct);
        Deque<Integer> frontier = new ArrayDeque<>(direct);
        while (!frontier.isEmpty()) {
            for (int implied : implies.getOrDefault(frontier.poll(), Collections.emptySet())) {
                if (out.add(implied)) frontier.add(implied);
            }
        }
        return out;
    }

    /** Id of the seeded system Administrator group, or null if it does not exist. */
    static Integer adminGroupId(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM permission_groups WHERE name = ? AND is_system = 1")) {
            ps.setString(1, ADMIN_GROUP_NAME);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : null;
        }
    }

    /** user_id -> directly assigned group ids, for every user matching {@code onlyActive}. */
    static Map<Integer, Set<Integer>> loadAssignments(Connection c, boolean onlyActive) throws SQLException {
        Map<Integer, Set<Integer>> m = new HashMap<>();
        String sql = onlyActive
            ? "SELECT u.id, upg.group_id FROM users u LEFT JOIN user_permission_groups upg ON upg.user_id = u.id WHERE u.status = 'active'"
            : "SELECT u.id, upg.group_id FROM users u LEFT JOIN user_permission_groups upg ON upg.user_id = u.id";
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Set<Integer> set = m.computeIfAbsent(rs.getInt(1), k -> new LinkedHashSet<>());
                int g = rs.getInt(2);
                if (!rs.wasNull()) set.add(g);
            }
        }
        return m;
    }

    /** Ids of active users who are administrator-equivalent (hold, directly or via "implies", the Administrator group). */
    static List<Integer> activeAdminUserIds(Connection c) throws SQLException {
        Integer adminId = adminGroupId(c);
        if (adminId == null) return Collections.emptyList();
        Map<Integer, Set<Integer>> implies = loadImplies(c);
        List<Integer> ids = new ArrayList<>();
        loadAssignments(c, true).forEach((userId, direct) -> {
            if (expand(direct, implies).contains(adminId)) ids.add(userId);
        });
        return ids;
    }

    static long readEpoch(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, EPOCH_KEY);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                try { return Long.parseLong(rs.getString(1)); } catch (Exception ignored) { }
            }
        }
        return 0L;
    }

    static void bumpEpoch(Connection c) throws SQLException {
        long next = readEpoch(c) + 1;
        try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)")) {
            ps.setString(1, EPOCH_KEY);
            ps.setString(2, String.valueOf(next));
            ps.executeUpdate();
        }
    }
}
