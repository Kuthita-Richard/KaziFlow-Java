package com.kaziflow.custom;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kaziflow.custom.CustomModuleService.CustomModuleException;
import com.kaziflow.custom.CustomModuleService.EntityDef;
import com.kaziflow.custom.CustomModuleService.FieldDef;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.services.AuditLog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * CustomRecordService — the DATA side of admin-built modules: validated create / update / soft-delete /
 * list of records, stored as JSON documents in the one generic {@code custom_records} table.
 *
 * <ul>
 *   <li>Every value is checked against its field definition (type, required, choice list, link target
 *       exists) before it is written; the database also enforces {@code json_valid}.</li>
 *   <li>Records are never hard-deleted: {@code deleted_at} is set, and a record that other records still
 *       link to can't be deleted.</li>
 *   <li>Calculated fields are never stored: they are evaluated on read with {@link FormulaEngine}.</li>
 *   <li>Values are keyed by the field's immutable key ({@code f_12}); a record keeps values for fields that
 *       were later archived.</li>
 * </ul>
 * Known limitation (design doc §8): an update is read-modify-write of one document, so two PCs editing the
 * same record at the same instant on a LAN could overwrite each other's change to different fields.
 */
public final class CustomRecordService {

    /** One record as the UI sees it: values keyed by field key (String, Long, Double, Boolean or null). */
    public record RecordView(long id, int entityId, Map<String, Object> values, String createdAt, String updatedAt) {}

    /** An entry for a "link" picker. */
    public record LookupOption(long id, String title) { @Override public String toString() { return title; } }

    private static CustomRecordService instance;
    public static synchronized CustomRecordService getInstance() {
        if (instance == null) instance = new CustomRecordService();
        return instance;
    }
    private CustomRecordService() {}

    private final CustomModuleService defs = CustomModuleService.getInstance();

    private Connection conn() throws SQLException {
        Connection c = DatabaseManager.getInstance().getConnection();
        CustomModuleService.ensureSchema(c);
        return c;
    }

    // ── create / update / delete ──────────────────────────────────────────

    /** @param input field key -> raw value (String/Number/Boolean/LookupOption...); calculated fields are ignored. */
    public long create(int entityId, Map<String, Object> input, int userId) throws CustomModuleException {
        EntityDef entity = requireEntity(entityId);
        List<FieldDef> fields = defs.listFields(entityId, false);
        JsonObject doc = new JsonObject();
        for (FieldDef f : fields) {
            if (f.type() == FieldType.CALC) continue;
            Object raw = input.containsKey(f.key()) ? input.get(f.key()) : defaultFor(f);
            put(doc, f, raw);
        }
        try (Connection c = conn()) {
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO custom_records (entity_id, data, created_by) VALUES (?,?,?)")) {
                    ps.setInt(1, entityId); ps.setString(2, doc.toString());
                    if (userId > 0) ps.setInt(3, userId); else ps.setNull(3, Types.INTEGER);
                    ps.executeUpdate();
                }
                long id;
                try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT MAX(id) FROM custom_records")) {
                    id = rs.next() ? rs.getLong(1) : -1;
                }
                c.commit();
                audit("CUSTOM_RECORD_CREATED", entity.label() + " record created", id);
                return id;
            } catch (SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(prev); }
        } catch (SQLException e) { throw new CustomModuleException("Database error: " + e.getMessage(), e); }
    }

    /** Changes only the fields present in {@code changes}; others keep their stored values. */
    public void update(long recordId, Map<String, Object> changes) throws CustomModuleException {
        try (Connection c = conn()) {
            Object[] row = loadRaw(c, recordId);
            if (row == null) throw new CustomModuleException("That record no longer exists.");
            int entityId = (int) row[0];
            JsonObject doc = JsonParser.parseString((String) row[1]).getAsJsonObject();
            for (FieldDef f : defs.listFields(entityId, false)) {
                if (f.type() == FieldType.CALC || !changes.containsKey(f.key())) continue;
                doc.remove(f.key());
                put(doc, f, changes.get(f.key()));
            }
            // fields that are required but were never filled (e.g. added after the record was created)
            for (FieldDef f : defs.listFields(entityId, false)) {
                if (f.required() && f.type() != FieldType.CALC && !doc.has(f.key()))
                    throw new CustomModuleException("\"" + f.label() + "\" is required.");
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE custom_records SET data = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted_at IS NULL")) {
                ps.setString(1, doc.toString()); ps.setLong(2, recordId);
                ps.executeUpdate();
            }
            audit("CUSTOM_RECORD_UPDATED", "Custom record updated", recordId);
        } catch (SQLException e) { throw new CustomModuleException("Database error: " + e.getMessage(), e); }
    }

    /** Soft delete. Refused while other live records still link to this one. */
    public void delete(long recordId) throws CustomModuleException {
        try (Connection c = conn()) {
            Object[] row = loadRaw(c, recordId);
            if (row == null) throw new CustomModuleException("That record no longer exists.");
            int entityId = (int) row[0];
            for (EntityDef e : defs.listAllEntities()) {
                for (FieldDef f : defs.listFields(e.id(), false)) {
                    if (f.type() != FieldType.LOOKUP || f.lookupEntityId() == null || f.lookupEntityId() != entityId) continue;
                    try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM custom_records WHERE entity_id = ? AND deleted_at IS NULL AND json_extract(data, ?) = ?")) {
                        ps.setInt(1, e.id()); ps.setString(2, "$." + f.key()); ps.setLong(3, recordId);
                        ResultSet rs = ps.executeQuery();
                        int n = rs.next() ? rs.getInt(1) : 0;
                        if (n > 0) throw new CustomModuleException("Still used by " + n + " " + e.label() + " record(s) (field \"" + f.label() + "\"). Remove those first.");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE custom_records SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                ps.setLong(1, recordId); ps.executeUpdate();
            }
            audit("CUSTOM_RECORD_DELETED", "Custom record deleted", recordId);
        } catch (SQLException e) { throw new CustomModuleException("Database error: " + e.getMessage(), e); }
    }

    // ── reads ─────────────────────────────────────────────────────────────

    public RecordView get(long recordId) {
        try (Connection c = conn()) {
            Object[] row = loadRaw(c, recordId);
            return row == null ? null : view(c, recordId, (int) row[0], (String) row[1], (String) row[2], (String) row[3]);
        } catch (SQLException e) { e.printStackTrace(); return null; }
    }

    /** Newest first. {@code search} matches anywhere in a record's VALUES (case-insensitive) — never its internal field keys. */
    public List<RecordView> list(int entityId, String search, int limit) {
        List<RecordView> out = new ArrayList<>();
        boolean hasSearch = search != null && !search.isBlank();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, data, created_at, updated_at FROM custom_records WHERE entity_id = ? AND deleted_at IS NULL "
                        + (hasSearch ? "AND EXISTS (SELECT 1 FROM json_each(custom_records.data) je WHERE LOWER(CAST(je.value AS TEXT)) LIKE ? ESCAPE '\\') " : "")
                        + "ORDER BY id DESC LIMIT ?")) {
            int i = 1;
            ps.setInt(i++, entityId);
            if (hasSearch) ps.setString(i++, "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            ps.setInt(i, Math.max(1, Math.min(limit, 5000)));
            ResultSet rs = ps.executeQuery();
            List<Object[]> rows = new ArrayList<>();
            while (rs.next()) rows.add(new Object[]{rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)});
            for (Object[] r : rows) out.add(view(c, (long) r[0], entityId, (String) r[1], (String) r[2], (String) r[3]));
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    public int count(int entityId) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM custom_records WHERE entity_id = ? AND deleted_at IS NULL")) {
            ps.setInt(1, entityId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    /** Choices for a link picker: every live record of the entity, with its display title. */
    public List<LookupOption> lookupOptions(int entityId) {
        List<LookupOption> out = new ArrayList<>();
        EntityDef entity = defs.getEntity(entityId);
        if (entity == null) return out;
        List<FieldDef> fields = defs.listFields(entityId, false);
        for (RecordView r : list(entityId, null, 5000)) out.add(new LookupOption(r.id(), titleOf(entity, fields, r)));
        Collections.reverse(out);
        return out;
    }

    /** Display title: the entity's title field, else the first text field with a value, else "Label #id". */
    public String titleOf(EntityDef entity, List<FieldDef> fields, RecordView r) {
        if (entity.titleFieldId() != null) {
            Object v = r.values().get("f_" + entity.titleFieldId());
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        for (FieldDef f : fields) {
            if (f.type() == FieldType.TEXT) { Object v = r.values().get(f.key()); if (v != null && !v.toString().isBlank()) return v.toString(); }
        }
        return entity.label() + " #" + r.id();
    }

    // ── validation / normalisation ────────────────────────────────────────

    private void put(JsonObject doc, FieldDef f, Object raw) throws CustomModuleException {
        boolean empty = raw == null || (raw instanceof String s && s.isBlank());
        if (empty) {
            if (f.required()) throw new CustomModuleException("\"" + f.label() + "\" is required.");
            return;
        }
        try {
            switch (f.type()) {
                case TEXT, LONG_TEXT -> doc.addProperty(f.key(), raw.toString().trim());
                case INTEGER -> {
                    BigDecimal bd = new BigDecimal(raw.toString().trim().replace(",", ""));
                    if (bd.stripTrailingZeros().scale() > 0) throw new NumberFormatException();
                    doc.addProperty(f.key(), bd.longValueExact());
                }
                case DECIMAL -> doc.addProperty(f.key(), new BigDecimal(raw.toString().trim().replace(",", "")).doubleValue());
                case MONEY -> doc.addProperty(f.key(), new BigDecimal(raw.toString().trim().replace(",", "")).setScale(2, RoundingMode.HALF_UP).doubleValue());
                case DATE -> doc.addProperty(f.key(), LocalDate.parse(raw.toString().trim()).toString());
                case DATETIME -> doc.addProperty(f.key(), LocalDateTime.parse(raw.toString().trim().replace(' ', 'T')).toString());
                case BOOLEAN -> doc.addProperty(f.key(), raw instanceof Boolean b ? b : "true".equalsIgnoreCase(raw.toString()) || "yes".equalsIgnoreCase(raw.toString()));
                case CHOICE -> {
                    String v = raw.toString().trim();
                    if (!f.choices().contains(v)) throw new CustomModuleException("\"" + v + "\" is not one of the options for \"" + f.label() + "\".");
                    doc.addProperty(f.key(), v);
                }
                case LOOKUP -> {
                    long id = raw instanceof LookupOption o ? o.id() : (raw instanceof Number n ? n.longValue() : Long.parseLong(raw.toString().trim()));
                    if (f.lookupEntityId() == null || !liveRecordExists(id, f.lookupEntityId()))
                        throw new CustomModuleException("The record chosen for \"" + f.label() + "\" doesn't exist.");
                    doc.addProperty(f.key(), id);
                }
                case CALC -> { /* never stored */ }
            }
        } catch (NumberFormatException | ArithmeticException e) {
            throw new CustomModuleException("\"" + f.label() + "\" needs " + (f.type() == FieldType.INTEGER ? "a whole number." : "a number."));
        } catch (java.time.format.DateTimeParseException e) {
            throw new CustomModuleException("\"" + f.label() + "\" needs a valid " + (f.type() == FieldType.DATE ? "date (yyyy-mm-dd)." : "date and time."));
        }
    }

    private static Object defaultFor(FieldDef f) {
        String d = f.defaultValue();
        if (d == null || d.isBlank()) return null;
        if (f.type() == FieldType.DATE && d.equalsIgnoreCase("today")) return LocalDate.now().toString();
        return d;
    }

    private boolean liveRecordExists(long id, int entityId) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT 1 FROM custom_records WHERE id = ? AND entity_id = ? AND deleted_at IS NULL")) {
            ps.setLong(1, id); ps.setInt(2, entityId);
            return ps.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    // ── building a RecordView (parse JSON, evaluate calculated fields) ─────

    private Object[] loadRaw(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT entity_id, data, created_at, updated_at FROM custom_records WHERE id = ? AND deleted_at IS NULL")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)} : null;
        }
    }

    private RecordView view(Connection c, long id, int entityId, String json, String created, String updated) {
        List<FieldDef> fields = defs.listFields(entityId, false);
        JsonObject doc = JsonParser.parseString(json).getAsJsonObject();
        Map<String, Object> values = new LinkedHashMap<>();
        for (FieldDef f : fields) {
            JsonElement el = doc.get(f.key());
            if (f.type() == FieldType.CALC || el == null || el.isJsonNull()) { values.put(f.key(), null); continue; }
            values.put(f.key(), switch (f.type()) {
                case INTEGER, LOOKUP -> el.getAsLong();
                case DECIMAL, MONEY -> el.getAsDouble();
                case BOOLEAN -> el.getAsBoolean();
                default -> el.getAsString();
            });
        }
        Map<String, FieldDef> byKey = new HashMap<>();
        for (FieldDef f : fields) byKey.put(f.key(), f);
        for (FieldDef f : fields) {
            if (f.type() == FieldType.CALC) values.put(f.key(), evaluateCalc(c, f, byKey, values, entityId, id, 0));
        }
        return new RecordView(id, entityId, values, created, updated);
    }

    private Double evaluateCalc(Connection c, FieldDef calc, Map<String, FieldDef> byKey, Map<String, Object> values,
                                int entityId, long recordId, int depth) {
        if (depth > 8 || calc.formula() == null) return null;   // depth guard: calc fields that reference each other in a loop
        try {
            FormulaEngine.Parsed p = FormulaEngine.parse(calc.formula());
            return p.evaluate(new FormulaEngine.Context() {
                @Override public Double value(String key) {
                    FieldDef f = byKey.get(key);
                    if (f == null) return null;
                    if (f.type() == FieldType.CALC) return evaluateCalc(c, f, byKey, values, entityId, recordId, depth + 1);
                    Object v = values.get(key);
                    return v instanceof Number n ? n.doubleValue() : null;
                }
                @Override public double sumOf(long childEntityId, String fieldKey) { return aggregate(c, childEntityId, fieldKey, entityId, recordId); }
                @Override public double countOf(long childEntityId) { return aggregate(c, childEntityId, null, entityId, recordId); }
            });
        } catch (FormulaEngine.FormulaException e) { return null; }
    }

    /** SUM of a child field (or COUNT when fieldKey is null) over live child records that link to this record. */
    private double aggregate(Connection c, long childEntityId, String fieldKey, int parentEntityId, long parentRecordId) {
        FieldDef link = null;
        for (FieldDef f : defs.listFields((int) childEntityId, false)) {
            if (f.type() == FieldType.LOOKUP && f.lookupEntityId() != null && f.lookupEntityId() == parentEntityId) { link = f; break; }
        }
        if (link == null) return 0;
        String sql = fieldKey == null
                ? "SELECT COUNT(*) FROM custom_records WHERE entity_id = ? AND deleted_at IS NULL AND json_extract(data, ?) = ?"
                : "SELECT COALESCE(SUM(CAST(json_extract(data, ?) AS REAL)), 0) FROM custom_records WHERE entity_id = ? AND deleted_at IS NULL AND json_extract(data, ?) = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (fieldKey != null) ps.setString(i++, "$." + fieldKey);   // fieldKey was validated as f_<digits> by the parser
            ps.setLong(i++, childEntityId);
            ps.setString(i++, "$." + link.key());
            ps.setLong(i, parentRecordId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private EntityDef requireEntity(int entityId) throws CustomModuleException {
        EntityDef e = defs.getEntity(entityId);
        if (e == null) throw new CustomModuleException("That kind of record no longer exists.");
        return e;
    }

    private static void audit(String action, String description, long id) {
        try { AuditLog.log(action, description, "custom", (int) id); } catch (Throwable ignored) { }
    }
}
