package com.kaziflow.custom;

import com.kaziflow.database.DatabaseManager;
import com.kaziflow.services.AuditLog;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CustomModuleService — the DEFINITIONS side of admin-built departments: modules, their entities and
 * the fields on each entity. (The data side is {@link CustomRecordService}.) See
 * docs/design/custom-modules.md for the design and the SQLite experiments behind it.
 *
 * <ul>
 *   <li>A module's id in the app is {@code "x_" + module_key}; that string is what Positions grant access to.</li>
 *   <li><b>Field keys are immutable</b> ({@code f_<id>}), never derived from a label: renaming a label can't
 *       break stored data, formulas or indexes. Fields are archived, never dropped; data is never deleted
 *       by an edit.</li>
 *   <li>Field type and link target can't change once a field exists (that would invalidate stored data).</li>
 *   <li>No per-field schema change: the only DDL ever issued is an optional expression
 *       {@code CREATE INDEX} for fields marked searchable, with a name built from numeric ids only.</li>
 * </ul>
 * Pure JDBC (no JavaFX) so it is runtime-testable against a real SQLite file.
 */
public final class CustomModuleService {

    public static final String MODULE_ID_PREFIX = "x_";
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_]{1,30}");

    public record ModuleDef(int id, String key, String label, String icon, String description, boolean active, int sortOrder) {
        /** The id the rest of the app (registry, Positions, permissions) knows this module by. */
        public String moduleId() { return MODULE_ID_PREFIX + key; }
    }
    public record EntityDef(int id, int moduleId, String key, String label, String pluralLabel, Integer titleFieldId, int sortOrder) {}
    public record FieldDef(int id, int entityId, String label, FieldType type, boolean required, String defaultValue,
                           List<String> choices, Integer lookupEntityId, String formula, String unit,
                           boolean showInList, boolean searchable, int sortOrder, boolean archived) {
        /** Immutable key under which values are stored in a record's JSON. */
        public String key() { return "f_" + id; }
    }

    public static class CustomModuleException extends Exception {
        private static final long serialVersionUID = 1L;
        public CustomModuleException(String m) { super(m); }
        public CustomModuleException(String m, Throwable t) { super(m, t); }
    }

    private static CustomModuleService instance;
    public static synchronized CustomModuleService getInstance() {
        if (instance == null) instance = new CustomModuleService();
        return instance;
    }
    private CustomModuleService() {}

    // ── schema ────────────────────────────────────────────────────────────

    /** Idempotent; called at startup and defensively before first use. */
    public static void ensureSchema(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS custom_modules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    module_key TEXT NOT NULL UNIQUE,
                    label TEXT NOT NULL,
                    icon TEXT NOT NULL DEFAULT '◇',
                    description TEXT,
                    active INTEGER NOT NULL DEFAULT 1,
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS custom_entities (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    module_id INTEGER NOT NULL REFERENCES custom_modules(id) ON DELETE CASCADE,
                    entity_key TEXT NOT NULL,
                    label TEXT NOT NULL,
                    plural_label TEXT,
                    title_field_id INTEGER,
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(module_id, entity_key)
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS custom_fields (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entity_id INTEGER NOT NULL REFERENCES custom_entities(id) ON DELETE CASCADE,
                    label TEXT NOT NULL,
                    type TEXT NOT NULL,
                    required INTEGER NOT NULL DEFAULT 0,
                    default_value TEXT,
                    choices TEXT,
                    lookup_entity_id INTEGER REFERENCES custom_entities(id),
                    formula TEXT,
                    unit TEXT,
                    show_in_list INTEGER NOT NULL DEFAULT 1,
                    searchable INTEGER NOT NULL DEFAULT 0,
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    archived INTEGER NOT NULL DEFAULT 0
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS custom_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entity_id INTEGER NOT NULL REFERENCES custom_entities(id) ON DELETE CASCADE,
                    data TEXT NOT NULL CHECK (json_valid(data)),
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    created_by INTEGER,
                    deleted_at TEXT
                )""");
            s.execute("CREATE INDEX IF NOT EXISTS ix_custom_records_entity ON custom_records (entity_id, deleted_at)");
        }
    }

    private Connection conn() throws SQLException {
        Connection c = DatabaseManager.getInstance().getConnection();
        ensureSchema(c);
        return c;
    }

    // ── modules ───────────────────────────────────────────────────────────

    public List<ModuleDef> listModules(boolean onlyActive) {
        List<ModuleDef> out = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, module_key, label, icon, description, active, sort_order FROM custom_modules "
                     + (onlyActive ? "WHERE active = 1 " : "") + "ORDER BY sort_order, label COLLATE NOCASE")) {
            while (rs.next()) out.add(new ModuleDef(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), rs.getInt(6) == 1, rs.getInt(7)));
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    public ModuleDef getModule(int id) {
        return listModules(false).stream().filter(m -> m.id() == id).findFirst().orElse(null);
    }

    public ModuleDef getModuleByModuleId(String moduleId) {
        return listModules(false).stream().filter(m -> m.moduleId().equals(moduleId)).findFirst().orElse(null);
    }

    public int createModule(String key, String label, String icon, String description) throws CustomModuleException {
        String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (!KEY.matcher(k).matches())
            throw new CustomModuleException("The short name must be lowercase letters, digits and underscores, starting with a letter (e.g. vehicles).");
        requireLabel(label, "module");
        try (Connection c = conn()) {
            if (exists(c, "SELECT 1 FROM custom_modules WHERE module_key = ?", k))
                throw new CustomModuleException("A module with the short name \"" + k + "\" already exists.");
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO custom_modules (module_key, label, icon, description) VALUES (?,?,?,?)")) {
                ps.setString(1, k); ps.setString(2, label.trim());
                ps.setString(3, icon == null || icon.isBlank() ? "◇" : icon.trim());
                ps.setString(4, description == null ? "" : description.trim());
                ps.executeUpdate();
            }
            int id = scalar(c, "SELECT id FROM custom_modules WHERE module_key = ?", k);
            audit("CUSTOM_MODULE_CREATED", "Custom module created: " + label.trim(), id);
            return id;
        } catch (SQLException e) { throw db(e); }
    }

    public void updateModule(int id, String label, String icon, String description, boolean active) throws CustomModuleException {
        requireLabel(label, "module");
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE custom_modules SET label=?, icon=?, description=?, active=? WHERE id=?")) {
            ps.setString(1, label.trim());
            ps.setString(2, icon == null || icon.isBlank() ? "◇" : icon.trim());
            ps.setString(3, description == null ? "" : description.trim());
            ps.setInt(4, active ? 1 : 0); ps.setInt(5, id);
            if (ps.executeUpdate() == 0) throw new CustomModuleException("That module no longer exists.");
            audit("CUSTOM_MODULE_UPDATED", "Custom module updated: " + label.trim() + (active ? "" : " (deactivated)"), id);
        } catch (SQLException e) { throw db(e); }
    }

    /** Deleting is only allowed while the module holds no records; otherwise deactivate it instead. */
    public void deleteModule(int id) throws CustomModuleException {
        try (Connection c = conn()) {
            int records = scalar(c, "SELECT COUNT(*) FROM custom_records r JOIN custom_entities e ON e.id = r.entity_id "
                    + "WHERE e.module_id = ? AND r.deleted_at IS NULL", id);
            if (records > 0)
                throw new CustomModuleException("This module holds " + records + " record(s). Deactivate it instead of deleting, so nothing is lost.");
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM custom_modules WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            audit("CUSTOM_MODULE_DELETED", "Custom module deleted (id " + id + ")", id);
        } catch (SQLException e) {
            throw new CustomModuleException("This module is linked from another module's fields and can't be deleted yet.", e);
        }
    }

    // ── entities ──────────────────────────────────────────────────────────

    public List<EntityDef> listEntities(int moduleId) {
        return queryEntities("WHERE module_id = ?", moduleId);
    }

    public EntityDef getEntity(int id) {
        List<EntityDef> l = queryEntities("WHERE id = ?", id);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Every entity in every custom module — the targets a "link" field may point at. */
    public List<EntityDef> listAllEntities() { return queryEntities("WHERE 1 = ?", 1); }

    private List<EntityDef> queryEntities(String where, int arg) {
        List<EntityDef> out = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, module_id, entity_key, label, plural_label, title_field_id, sort_order FROM custom_entities "
                        + where + " ORDER BY sort_order, id")) {
            ps.setInt(1, arg);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int tf = rs.getInt(6);
                out.add(new EntityDef(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.wasNull() ? null : tf, rs.getInt(7)));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    public int addEntity(int moduleId, String label, String pluralLabel) throws CustomModuleException {
        requireLabel(label, "entity");
        String key = slug(label);
        try (Connection c = conn()) {
            String unique = key;
            for (int i = 2; exists(c, "SELECT 1 FROM custom_entities WHERE module_id = ? AND entity_key = ?", moduleId, unique); i++) unique = key + "_" + i;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO custom_entities (module_id, entity_key, label, plural_label, sort_order) VALUES (?,?,?,?, "
                            + "(SELECT COALESCE(MAX(sort_order),0)+10 FROM custom_entities WHERE module_id = ?))")) {
                ps.setInt(1, moduleId); ps.setString(2, unique); ps.setString(3, label.trim());
                ps.setString(4, pluralLabel == null || pluralLabel.isBlank() ? label.trim() + "s" : pluralLabel.trim());
                ps.setInt(5, moduleId);
                ps.executeUpdate();
            }
            int id = scalar(c, "SELECT id FROM custom_entities WHERE module_id = ? AND entity_key = ?", moduleId, unique);
            audit("CUSTOM_ENTITY_CREATED", "Entity added: " + label.trim(), id);
            return id;
        } catch (SQLException e) { throw db(e); }
    }

    public void updateEntity(int id, String label, String pluralLabel, Integer titleFieldId) throws CustomModuleException {
        requireLabel(label, "entity");
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE custom_entities SET label=?, plural_label=?, title_field_id=? WHERE id=?")) {
            ps.setString(1, label.trim());
            ps.setString(2, pluralLabel == null || pluralLabel.isBlank() ? label.trim() + "s" : pluralLabel.trim());
            if (titleFieldId == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, titleFieldId);
            ps.setInt(4, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw db(e); }
    }

    public void deleteEntity(int id) throws CustomModuleException {
        try (Connection c = conn()) {
            int records = scalar(c, "SELECT COUNT(*) FROM custom_records WHERE entity_id = ? AND deleted_at IS NULL", id);
            if (records > 0) throw new CustomModuleException("This entity holds " + records + " record(s) and can't be deleted.");
            int links = scalar(c, "SELECT COUNT(*) FROM custom_fields WHERE lookup_entity_id = ? AND entity_id <> ?", id, id);
            if (links > 0) throw new CustomModuleException("Other entities link to this one, so it can't be deleted.");
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM custom_entities WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) { throw db(e); }
    }

    // ── fields ────────────────────────────────────────────────────────────

    public List<FieldDef> listFields(int entityId, boolean includeArchived) {
        List<FieldDef> out = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, entity_id, label, type, required, default_value, choices, lookup_entity_id, formula, unit, "
                        + "show_in_list, searchable, sort_order, archived FROM custom_fields WHERE entity_id = ? "
                        + (includeArchived ? "" : "AND archived = 0 ") + "ORDER BY sort_order, id")) {
            ps.setInt(1, entityId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(mapField(rs));
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    public FieldDef getField(int id) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, entity_id, label, type, required, default_value, choices, lookup_entity_id, formula, unit, "
                        + "show_in_list, searchable, sort_order, archived FROM custom_fields WHERE id = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapField(rs) : null;
        } catch (SQLException e) { e.printStackTrace(); return null; }
    }

    private static FieldDef mapField(ResultSet rs) throws SQLException {
        int lookup = rs.getInt(8);
        Integer lookupId = rs.wasNull() ? null : lookup;
        String ch = rs.getString(7);
        List<String> choices = new ArrayList<>();
        if (ch != null && !ch.isBlank()) for (String x : ch.split("\n")) if (!x.isBlank()) choices.add(x.trim());
        return new FieldDef(rs.getInt(1), rs.getInt(2), rs.getString(3), FieldType.valueOf(rs.getString(4)),
                rs.getInt(5) == 1, rs.getString(6), choices, lookupId, rs.getString(9), rs.getString(10),
                rs.getInt(11) == 1, rs.getInt(12) == 1, rs.getInt(13), rs.getInt(14) == 1);
    }

    /** Adds a field. {@code draft.id()} is ignored. For CALC, {@code formula} is in KEY form ({f_12}). */
    public int addField(FieldDef draft) throws CustomModuleException {
        requireLabel(draft.label(), "field");
        if (draft.type() == FieldType.CHOICE && draft.choices().isEmpty())
            throw new CustomModuleException("A choice field needs at least one option.");
        if (draft.type() == FieldType.LOOKUP && (draft.lookupEntityId() == null || getEntity(draft.lookupEntityId()) == null))
            throw new CustomModuleException("Pick which kind of record this field links to.");
        if (draft.type() == FieldType.CALC) validateFormula(draft.entityId(), draft.formula(), -1);
        try (Connection c = conn()) {
            if (exists(c, "SELECT 1 FROM custom_fields WHERE entity_id = ? AND archived = 0 AND LOWER(label) = LOWER(?)",
                    draft.entityId(), draft.label().trim()))
                throw new CustomModuleException("This entity already has a field called \"" + draft.label().trim() + "\".");
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO custom_fields (entity_id, label, type, required, default_value, choices, lookup_entity_id, formula, "
                            + "unit, show_in_list, searchable, sort_order) VALUES (?,?,?,?,?,?,?,?,?,?,?, "
                            + "(SELECT COALESCE(MAX(sort_order),0)+10 FROM custom_fields WHERE entity_id = ?))")) {
                ps.setInt(1, draft.entityId()); ps.setString(2, draft.label().trim()); ps.setString(3, draft.type().name());
                ps.setInt(4, draft.required() && draft.type() != FieldType.CALC ? 1 : 0);
                ps.setString(5, draft.defaultValue());
                ps.setString(6, String.join("\n", draft.choices()));
                if (draft.lookupEntityId() == null) ps.setNull(7, Types.INTEGER); else ps.setInt(7, draft.lookupEntityId());
                ps.setString(8, draft.formula()); ps.setString(9, draft.unit());
                ps.setInt(10, draft.showInList() ? 1 : 0); ps.setInt(11, draft.searchable() ? 1 : 0);
                ps.setInt(12, draft.entityId());
                ps.executeUpdate();
            }
            int id = scalar(c, "SELECT MAX(id) FROM custom_fields WHERE entity_id = ?", draft.entityId());
            if (draft.searchable()) ensureIndex(c, draft.entityId(), id);
            // first text-like field becomes the record's display title automatically
            if (draft.type() == FieldType.TEXT && scalar(c, "SELECT COUNT(*) FROM custom_entities WHERE id = ? AND title_field_id IS NOT NULL", draft.entityId()) == 0) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE custom_entities SET title_field_id = ? WHERE id = ?")) {
                    ps.setInt(1, id); ps.setInt(2, draft.entityId()); ps.executeUpdate();
                }
            }
            audit("CUSTOM_FIELD_ADDED", "Field added: " + draft.label().trim(), id);
            return id;
        } catch (SQLException e) { throw db(e); }
    }

    /** Edits everything except the field's type and link target (those are fixed once data may exist). */
    public void updateField(int id, String label, boolean required, String defaultValue, List<String> choices,
                            String formula, String unit, boolean showInList, boolean searchable) throws CustomModuleException {
        FieldDef f = getField(id);
        if (f == null) throw new CustomModuleException("That field no longer exists.");
        requireLabel(label, "field");
        if (f.type() == FieldType.CHOICE && choices.isEmpty()) throw new CustomModuleException("A choice field needs at least one option.");
        if (f.type() == FieldType.CALC) validateFormula(f.entityId(), formula, id);
        try (Connection c = conn()) {
            if (exists(c, "SELECT 1 FROM custom_fields WHERE entity_id = ? AND archived = 0 AND id <> ? AND LOWER(label) = LOWER(?)",
                    f.entityId(), id, label.trim()))
                throw new CustomModuleException("This entity already has a field called \"" + label.trim() + "\".");
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE custom_fields SET label=?, required=?, default_value=?, choices=?, formula=?, unit=?, show_in_list=?, searchable=? WHERE id=?")) {
                ps.setString(1, label.trim());
                ps.setInt(2, required && f.type() != FieldType.CALC ? 1 : 0);
                ps.setString(3, defaultValue); ps.setString(4, String.join("\n", choices));
                ps.setString(5, formula); ps.setString(6, unit);
                ps.setInt(7, showInList ? 1 : 0); ps.setInt(8, searchable ? 1 : 0); ps.setInt(9, id);
                ps.executeUpdate();
            }
            if (searchable) ensureIndex(c, f.entityId(), id); else dropIndex(c, id);
        } catch (SQLException e) { throw db(e); }
    }

    /** Archived fields vanish from forms and lists but their stored values are kept. */
    public void archiveField(int id, boolean archived) throws CustomModuleException {
        FieldDef f = getField(id);
        if (f == null) throw new CustomModuleException("That field no longer exists.");
        try (Connection c = conn()) {
            if (archived) {
                // a live calculated field must not silently depend on a hidden one
                for (FieldDef other : listFields(f.entityId(), false)) {
                    if (other.type() == FieldType.CALC && other.id() != id && other.formula() != null
                            && other.formula().contains("{" + f.key() + "}"))
                        throw new CustomModuleException("The calculated field \"" + other.label() + "\" uses this field.");
                }
                dropIndex(c, id);
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE custom_fields SET archived = ? WHERE id = ?")) {
                ps.setInt(1, archived ? 1 : 0); ps.setInt(2, id); ps.executeUpdate();
            }
            audit("CUSTOM_FIELD_ARCHIVED", (archived ? "Field archived: " : "Field restored: ") + f.label(), id);
        } catch (SQLException e) { throw db(e); }
    }

    // ── formulas: label <-> key conversion and validation ─────────────────

    private static final Pattern BRACED = Pattern.compile("\\{([^}]*)}");
    private static final Pattern AGG = Pattern.compile("(?i)\\b(SUMOF|COUNTOF)\\s*\\(\\s*([^,)]+?)\\s*(?:,\\s*([^)]+?)\\s*)?\\)");

    /** Converts what an administrator types ({Distance}, SUMOF(Mileage Log, Distance)) to stored key form. */
    public String labelsToKeys(int entityId, String typed) throws CustomModuleException {
        if (typed == null) return null;
        Map<String, FieldDef> byLabel = new HashMap<>();
        for (FieldDef f : listFields(entityId, false)) byLabel.put(f.label().toLowerCase(Locale.ROOT), f);
        Matcher aggs = AGG.matcher(typed);
        StringBuilder sb = new StringBuilder();
        while (aggs.find()) {
            String fn = aggs.group(1).toUpperCase(Locale.ROOT);
            EntityDef child = findEntityByLabel(aggs.group(2).trim());
            if (child == null) throw new CustomModuleException("Unknown record type \"" + aggs.group(2).trim() + "\" in " + fn + ".");
            String repl;
            if (fn.equals("COUNTOF")) repl = "COUNTOF(e" + child.id() + ")";
            else {
                String fl = aggs.group(3) == null ? "" : aggs.group(3).trim().toLowerCase(Locale.ROOT);
                FieldDef cf = listFields(child.id(), false).stream().filter(x -> x.label().toLowerCase(Locale.ROOT).equals(fl)).findFirst().orElse(null);
                if (cf == null) throw new CustomModuleException("\"" + aggs.group(3) + "\" is not a field of " + child.label() + ".");
                repl = "SUMOF(e" + child.id() + ", " + cf.key() + ")";
            }
            aggs.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        aggs.appendTail(sb);
        Matcher m = BRACED.matcher(sb.toString());
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String inner = m.group(1).trim();
            String repl;
            if (inner.matches("f_\\d+")) repl = inner;
            else {
                FieldDef f = byLabel.get(inner.toLowerCase(Locale.ROOT));
                if (f == null) throw new CustomModuleException("There is no field called \"" + inner + "\".");
                repl = f.key();
            }
            m.appendReplacement(out, Matcher.quoteReplacement("{" + repl + "}"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Stored key form back to readable labels, for showing in the builder. */
    public String keysToLabels(int entityId, String stored) {
        if (stored == null) return "";
        Map<String, String> labels = new HashMap<>();
        for (FieldDef f : listFields(entityId, true)) labels.put(f.key(), f.label());
        Matcher m = BRACED.matcher(stored);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement("{" + labels.getOrDefault(m.group(1).trim(), m.group(1)) + "}"));
        m.appendTail(out);
        String s = out.toString();
        Matcher a = Pattern.compile("(SUMOF|COUNTOF)\\(e(\\d+)(?:,\\s*(f_\\d+))?\\)").matcher(s);
        StringBuilder out2 = new StringBuilder();
        while (a.find()) {
            EntityDef e = getEntity(Integer.parseInt(a.group(2)));
            String fieldLabel = a.group(3) == null ? null : Optional.ofNullable(getField(Integer.parseInt(a.group(3).substring(2)))).map(FieldDef::label).orElse(a.group(3));
            String r = a.group(1) + "(" + (e == null ? "e" + a.group(2) : e.label()) + (fieldLabel == null ? "" : ", " + fieldLabel) + ")";
            a.appendReplacement(out2, Matcher.quoteReplacement(r));
        }
        a.appendTail(out2);
        return out2.toString();
    }

    private EntityDef findEntityByLabel(String label) {
        for (EntityDef e : listAllEntities()) if (e.label().equalsIgnoreCase(label)) return e;
        return null;
    }

    /** Parses the formula and checks every reference points at a usable field of this entity. */
    void validateFormula(int entityId, String formula, int selfFieldId) throws CustomModuleException {
        try {
            FormulaEngine.Parsed p = FormulaEngine.parse(formula);
            Map<String, FieldDef> byKey = new HashMap<>();
            for (FieldDef f : listFields(entityId, false)) byKey.put(f.key(), f);
            for (String ref : p.fieldRefs()) {
                FieldDef f = byKey.get(ref);
                if (f == null) throw new CustomModuleException("The formula uses a field that doesn't exist or is archived.");
                if (f.id() == selfFieldId) throw new CustomModuleException("A calculated field can't use itself.");
                if (!f.type().usableInFormula()) throw new CustomModuleException("\"" + f.label() + "\" is not a number, so a formula can't use it.");
            }
        } catch (FormulaEngine.FormulaException e) {
            throw new CustomModuleException("Formula problem: " + e.getMessage());
        }
    }

    // ── optional expression index (the only DDL this feature ever issues) ──

    private void ensureIndex(Connection c, int entityId, int fieldId) throws SQLException {
        // Name and path are built from integer ids only — no user text reaches the DDL string.
        try (Statement s = c.createStatement()) {
            s.execute("CREATE INDEX IF NOT EXISTS ix_cx_f" + fieldId + " ON custom_records (json_extract(data, '$.f_" + fieldId
                    + "')) WHERE entity_id = " + entityId);
        }
    }

    private void dropIndex(Connection c, int fieldId) throws SQLException {
        try (Statement s = c.createStatement()) { s.execute("DROP INDEX IF EXISTS ix_cx_f" + fieldId); }
    }

    // ── small helpers ─────────────────────────────────────────────────────

    static String slug(String label) {
        String s = label.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.isEmpty() || !Character.isLetter(s.charAt(0))) s = "e_" + s;
        return s.length() > 30 ? s.substring(0, 30) : s;
    }

    private static void requireLabel(String label, String what) throws CustomModuleException {
        if (label == null || label.isBlank()) throw new CustomModuleException("Please give the " + what + " a name.");
        if (label.trim().length() > 60) throw new CustomModuleException("That name is too long (60 characters max).");
    }

    private static boolean exists(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps.executeQuery().next();
        }
    }

    private static int scalar(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private static CustomModuleException db(SQLException e) { return new CustomModuleException("Database error: " + e.getMessage(), e); }

    private static void audit(String action, String description, int id) {
        try { AuditLog.log(action, description, "settings", id); } catch (Throwable ignored) { }
    }
}
