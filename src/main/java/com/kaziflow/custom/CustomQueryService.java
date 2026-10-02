package com.kaziflow.custom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kaziflow.custom.CustomModuleService.CustomModuleException;
import com.kaziflow.custom.CustomModuleService.FieldDef;
import com.kaziflow.custom.CustomRecordService.LookupOption;
import com.kaziflow.custom.CustomRecordService.RecordView;
import com.kaziflow.database.DatabaseManager;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CustomQueryService — saved VIEWS and REPORTS over an admin-built entity: filter, sort, group and total,
 * assembled by pointing and clicking (no query language is ever typed).
 *
 * <p><b>Safety:</b> a {@link ViewDef} refers to fields only by their immutable key ({@code f_12}); every key is
 * validated against the entity's real fields, JSON paths are built from validated keys and passed as bound
 * parameters, and every filter VALUE is a bound parameter. Nothing a user types is ever concatenated into SQL.
 *
 * <p><b>Scope (v1):</b> filters, sorting, grouping and aggregates work on STORED fields. Calculated fields can
 * be shown in a list but not filtered, sorted or grouped (they are computed per record in Java). Dates accept
 * relative tokens — {@code @today}, {@code @month_start}, {@code @month_end}, {@code @year_start},
 * {@code @days_ago_30} — so a saved view such as "this month" stays correct next month.
 */
public final class CustomQueryService {

    public enum Op {
        EQ("is"), NE("is not"), GT("is greater than"), GE("is at least"), LT("is less than"), LE("is at most"),
        CONTAINS("contains"), EMPTY("is empty"), NOT_EMPTY("is not empty");
        private final String label;
        Op(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }
        public boolean needsValue() { return this != EMPTY && this != NOT_EMPTY; }
    }
    public enum Agg {
        SUM("Total"), AVG("Average"), MIN("Smallest"), MAX("Largest"), COUNT("Count");
        private final String label;
        Agg(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }
    public enum Bucket { NONE("Exact value"), MONTH("By month"), YEAR("By year");
        private final String label;
        Bucket(String label) { this.label = label; }
        @Override public String toString() { return label; } }

    public record Filter(String field, Op op, String value) {}
    public record Aggregate(String field, Agg fn) {}      // field is null for a plain COUNT
    public record Sort(String field, boolean desc) {}
    public record ViewDef(List<Filter> filters, Sort sort, String groupBy, Bucket bucket, List<Aggregate> aggregates) {
        public static final ViewDef ALL = new ViewDef(List.of(), null, null, Bucket.NONE, List.of());
        public boolean isGrouped() { return groupBy != null && !groupBy.isBlank(); }
    }
    public record SavedView(int id, int entityId, String name, ViewDef def) {}

    /** A filtered list: the rows (capped), how many match in all, and sums over ALL matches of each numeric stored field. */
    public record ListResult(List<RecordView> rows, long totalMatching, Map<String, Double> totals) {}
    public record GroupRow(Object key, String label, long count, Map<String, Double> values) {}
    /** {@code values} keys look like "SUM:f_12". */
    public record GroupResult(List<GroupRow> rows, long totalCount, Map<String, Double> totals, List<Aggregate> aggregates) {}

    private static final Pattern KEY = Pattern.compile("f_\\d+");
    private static final Pattern DAYS_AGO = Pattern.compile("@days_ago_(\\d{1,4})");

    private static CustomQueryService instance;
    public static synchronized CustomQueryService getInstance() {
        if (instance == null) instance = new CustomQueryService();
        return instance;
    }
    private CustomQueryService() {}

    private final CustomModuleService defs = CustomModuleService.getInstance();
    private final CustomRecordService recs = CustomRecordService.getInstance();

    private Connection conn() throws SQLException { return CustomModuleService.openConnection(); }

    // ── running a view ────────────────────────────────────────────────────

    public ListResult list(int entityId, ViewDef def, String search, int limit) throws CustomModuleException {
        Map<String, FieldDef> byKey = fieldsByKey(entityId);
        Compiled w = compileWhere(def, byKey);
        String order = compileOrder(def.sort(), byKey, w.params);
        List<RecordView> rows = recs.query(entityId, w.sql, w.params, order, search, limit);

        Map<String, Double> totals = new LinkedHashMap<>();
        long matching = 0;
        List<FieldDef> numeric = new ArrayList<>();
        for (FieldDef f : byKey.values()) if (f.type().isNumeric()) numeric.add(f);
        StringBuilder sql = new StringBuilder("SELECT COUNT(*)");
        List<Object> params = new ArrayList<>();
        for (FieldDef f : numeric) { sql.append(", COALESCE(SUM(CAST(json_extract(data, ?) AS REAL)), 0)"); params.add("$." + f.key()); }
        sql.append(" FROM custom_records WHERE entity_id = ? AND deleted_at IS NULL");
        params.add(entityId);
        if (!w.sql.isBlank()) { sql.append(" AND (").append(w.sql).append(")"); params.addAll(w.params); }
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, params);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                matching = rs.getLong(1);
                for (int i = 0; i < numeric.size(); i++) totals.put(numeric.get(i).key(), rs.getDouble(i + 2));
            }
        } catch (SQLException e) { throw new CustomModuleException("Could not run the view: " + e.getMessage(), e); }
        return new ListResult(rows, matching, totals);
    }

    public GroupResult group(int entityId, ViewDef def) throws CustomModuleException {
        Map<String, FieldDef> byKey = fieldsByKey(entityId);
        if (!def.isGrouped()) throw new CustomModuleException("Choose what to group by.");
        FieldDef g = requireStored(byKey, def.groupBy(), "group by");
        List<Aggregate> aggs = def.aggregates() == null || def.aggregates().isEmpty()
                ? List.of(new Aggregate(null, Agg.COUNT)) : def.aggregates();
        for (Aggregate a : aggs) {
            if (a.fn() == Agg.COUNT) continue;
            FieldDef f = requireStored(byKey, a.field(), a.fn().label().toLowerCase());
            if (!f.type().isNumeric()) throw new CustomModuleException("\"" + f.label() + "\" is not a number, so it can't be totalled or averaged.");
        }
        Compiled w = compileWhere(def, byKey);

        // group expression: exact value, or the month / year of a date
        String groupExpr;
        Bucket bucket = def.bucket() == null ? Bucket.NONE : def.bucket();
        if (bucket != Bucket.NONE && g.type() != FieldType.DATE && g.type() != FieldType.DATETIME)
            throw new CustomModuleException("Grouping by month or year needs a date field.");
        groupExpr = switch (bucket) {
            case MONTH -> "substr(json_extract(data, ?), 1, 7)";
            case YEAR -> "substr(json_extract(data, ?), 1, 4)";
            default -> "json_extract(data, ?)";
        };

        StringBuilder select = new StringBuilder("SELECT " + groupExpr + ", COUNT(*)");
        List<Object> params = new ArrayList<>();
        params.add("$." + g.key());
        for (Aggregate a : aggs) {
            if (a.fn() == Agg.COUNT) continue;
            select.append(", ").append(a.fn() == Agg.AVG ? "AVG" : a.fn().name()).append("(CAST(json_extract(data, ?) AS REAL))");
            params.add("$." + a.field());
        }
        select.append(" FROM custom_records WHERE entity_id = ? AND deleted_at IS NULL");
        params.add(entityId);
        if (!w.sql.isBlank()) { select.append(" AND (").append(w.sql).append(")"); params.addAll(w.params); }
        // GROUP BY 1 (the first select column) so the grouped expression is guaranteed identical.
        select.append(" GROUP BY 1 ORDER BY ").append(groupOrder(def, aggs));

        Map<Object, String> lookupTitles = new HashMap<>();
        if (g.type() == FieldType.LOOKUP && g.lookupEntityId() != null)
            for (LookupOption o : recs.lookupOptions(g.lookupEntityId())) lookupTitles.put(o.id(), o.title());

        List<GroupRow> rows = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(select.toString())) {
            bind(ps, params);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Object key = rs.getObject(1);
                Map<String, Double> values = new LinkedHashMap<>();
                int col = 3;
                for (Aggregate a : aggs) {
                    if (a.fn() == Agg.COUNT) continue;
                    double v = rs.getDouble(col++);
                    values.put(a.fn().name() + ":" + a.field(), rs.wasNull() ? 0 : v);
                }
                rows.add(new GroupRow(key, groupLabel(g, key, lookupTitles), rs.getLong(2), values));
            }
        } catch (SQLException e) { throw new CustomModuleException("Could not run the report: " + e.getMessage(), e); }

        // grand totals across the same filter
        long total = 0;
        Map<String, Double> totals = new LinkedHashMap<>();
        StringBuilder ts = new StringBuilder("SELECT COUNT(*)");
        List<Object> tp = new ArrayList<>();
        for (Aggregate a : aggs) {
            if (a.fn() == Agg.COUNT) continue;
            ts.append(", ").append(a.fn() == Agg.AVG ? "AVG" : a.fn().name()).append("(CAST(json_extract(data, ?) AS REAL))");
            tp.add("$." + a.field());
        }
        ts.append(" FROM custom_records WHERE entity_id = ? AND deleted_at IS NULL");
        tp.add(entityId);
        if (!w.sql.isBlank()) { ts.append(" AND (").append(w.sql).append(")"); tp.addAll(w.params); }
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(ts.toString())) {
            bind(ps, tp);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                total = rs.getLong(1);
                int col = 2;
                for (Aggregate a : aggs) {
                    if (a.fn() == Agg.COUNT) continue;
                    double v = rs.getDouble(col++);
                    totals.put(a.fn().name() + ":" + a.field(), rs.wasNull() ? 0 : v);
                }
            }
        } catch (SQLException e) { throw new CustomModuleException("Could not total the report: " + e.getMessage(), e); }
        return new GroupResult(rows, total, totals, aggs);
    }

    private String groupOrder(ViewDef def, List<Aggregate> aggs) {
        Sort s = def.sort();
        if (s != null && s.field() != null && !s.field().equals(def.groupBy())) {
            int col = 3;
            for (Aggregate a : aggs) {
                if (a.fn() == Agg.COUNT) continue;
                if (s.field().equals(a.field())) return col + (s.desc() ? " DESC" : " ASC");
                col++;
            }
        }
        return "1 " + (s != null && def.groupBy().equals(s.field()) && s.desc() ? "DESC" : "ASC");
    }

    private static String groupLabel(FieldDef g, Object key, Map<Object, String> lookupTitles) {
        if (key == null || key.toString().isEmpty()) return "(none)";
        if (g.type() == FieldType.LOOKUP) return lookupTitles.getOrDefault(((Number) key).longValue(), "(deleted)");
        if (g.type() == FieldType.BOOLEAN) return ((Number) key).intValue() != 0 ? "Yes" : "No";
        if (key instanceof Double d) return d == Math.rint(d) ? String.valueOf(d.longValue()) : d.toString();
        return key.toString();
    }

    // ── compiling filters / sort ──────────────────────────────────────────

    private record Compiled(String sql, List<Object> params) {}

    private Compiled compileWhere(ViewDef def, Map<String, FieldDef> byKey) throws CustomModuleException {
        StringBuilder sb = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (Filter f : def.filters() == null ? List.<Filter>of() : def.filters()) {
            FieldDef field = requireStored(byKey, f.field(), "filter");
            String expr = expression(field);
            String path = "$." + field.key();
            if (sb.length() > 0) sb.append(" AND ");
            switch (f.op()) {
                case EMPTY -> { sb.append("(").append(expr).append(" IS NULL OR CAST(").append(expr).append(" AS TEXT) = '')"); params.add(path); params.add(path); }
                case NOT_EMPTY -> { sb.append("(").append(expr).append(" IS NOT NULL AND CAST(").append(expr).append(" AS TEXT) <> '')"); params.add(path); params.add(path); }
                case CONTAINS -> {
                    sb.append("LOWER(CAST(").append(expr).append(" AS TEXT)) LIKE ? ESCAPE '\\'");
                    params.add(path);
                    String v = f.value() == null ? "" : f.value().trim().toLowerCase(Locale.ROOT);
                    params.add("%" + v.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
                }
                case NE -> {
                    sb.append("(").append(expr).append(" IS NULL OR ").append(expr).append(" <> ?)");
                    params.add(path); params.add(path); params.add(bindValue(field, f.value()));
                }
                default -> {
                    String sym = switch (f.op()) { case EQ -> "="; case GT -> ">"; case GE -> ">="; case LT -> "<"; default -> "<="; };
                    sb.append(expr).append(" ").append(sym).append(" ?");
                    params.add(path); params.add(bindValue(field, f.value()));
                }
            }
        }
        return new Compiled(sb.toString(), params);
    }

    private String compileOrder(Sort sort, Map<String, FieldDef> byKey, List<Object> whereParams) throws CustomModuleException {
        if (sort == null || sort.field() == null) return null;
        FieldDef f = requireStored(byKey, sort.field(), "sort by");
        // ORDER BY goes through recs.query(), which binds the WHERE values first and appends its own search/limit
        // parameters, so the sort path is embedded as a literal instead of a parameter. That is safe: the key is
        // checked against the entity's real fields above and re-checked here to be exactly f_<digits>.
        if (!KEY.matcher(f.key()).matches()) throw new CustomModuleException("Bad field.");
        return expression(f).replace("?", "'$." + f.key() + "'") + (sort.desc() ? " DESC" : " ASC") + ", id DESC";
    }

    private static String expression(FieldDef f) {
        return f.type().isNumeric() ? "CAST(json_extract(data, ?) AS REAL)" : "json_extract(data, ?)";
    }

    /** Converts a filter value to the type the stored field compares as. */
    private static Object bindValue(FieldDef f, String raw) throws CustomModuleException {
        if (raw == null || raw.isBlank()) throw new CustomModuleException("Enter a value to compare \"" + f.label() + "\" with.");
        String v = raw.trim();
        try {
            switch (f.type()) {
                case INTEGER, DECIMAL, MONEY: return Double.parseDouble(v.replace(",", ""));
                case LOOKUP: return Long.parseLong(v);
                case BOOLEAN: return ("true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v) || "1".equals(v)) ? 1 : 0;
                case DATE: return expandDate(v).toString();
                case DATETIME: return v.startsWith("@") ? expandDate(v).toString() : v.replace(' ', 'T');
                default: return v;
            }
        } catch (NumberFormatException e) {
            throw new CustomModuleException("\"" + v + "\" isn't a valid value for \"" + f.label() + "\".");
        } catch (java.time.format.DateTimeParseException e) {
            throw new CustomModuleException("\"" + v + "\" isn't a valid date for \"" + f.label() + "\" (use yyyy-mm-dd or a token such as @today).");
        }
    }

    /** Relative date tokens, so a saved "this month" view never goes stale. */
    static LocalDate expandDate(String v) {
        LocalDate today = LocalDate.now();
        if (v.startsWith("@")) {
            if (v.equals("@today")) return today;
            if (v.equals("@month_start")) return today.withDayOfMonth(1);
            if (v.equals("@month_end")) return YearMonth.from(today).atEndOfMonth();
            if (v.equals("@year_start")) return today.withDayOfYear(1);
            Matcher m = DAYS_AGO.matcher(v);
            if (m.matches()) return today.minusDays(Long.parseLong(m.group(1)));
            throw new java.time.format.DateTimeParseException("Unknown date token", v, 0);
        }
        return LocalDate.parse(v);
    }

    private Map<String, FieldDef> fieldsByKey(int entityId) throws CustomModuleException {
        if (defs.getEntity(entityId) == null) throw new CustomModuleException("That kind of record no longer exists.");
        Map<String, FieldDef> m = new LinkedHashMap<>();
        for (FieldDef f : defs.listFields(entityId, false)) m.put(f.key(), f);
        return m;
    }

    private static FieldDef requireStored(Map<String, FieldDef> byKey, String key, String purpose) throws CustomModuleException {
        FieldDef f = key == null ? null : byKey.get(key);
        if (f == null) throw new CustomModuleException("A field used to " + purpose + " no longer exists.");
        if (f.type() == FieldType.CALC)
            throw new CustomModuleException("\"" + f.label() + "\" is calculated, so it can't be used to " + purpose + " (it can still be shown in the list).");
        return f;
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        int i = 1;
        for (Object p : params) ps.setObject(i++, p);
    }

    // ── saved views ───────────────────────────────────────────────────────

    public List<SavedView> listViews(int entityId) {
        List<SavedView> out = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, entity_id, name, definition FROM custom_views WHERE entity_id = ? ORDER BY name COLLATE NOCASE")) {
            ps.setInt(1, entityId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(new SavedView(rs.getInt(1), rs.getInt(2), rs.getString(3), fromJson(rs.getString(4))));
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    /** Saves (or replaces, by name) a view. Everyone with access to the module can use it. */
    public int saveView(int entityId, String name, ViewDef def, int userId) throws CustomModuleException {
        if (name == null || name.isBlank()) throw new CustomModuleException("Give the view a name.");
        if (name.trim().length() > 60) throw new CustomModuleException("That name is too long (60 characters max).");
        validate(entityId, def);
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM custom_views WHERE entity_id = ? AND name = ?")) {
                ps.setInt(1, entityId); ps.setString(2, name.trim()); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO custom_views (entity_id, name, definition, created_by) VALUES (?,?,?,?)")) {
                ps.setInt(1, entityId); ps.setString(2, name.trim()); ps.setString(3, toJson(def).toString());
                if (userId > 0) ps.setInt(4, userId); else ps.setNull(4, Types.INTEGER);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM custom_views WHERE entity_id = ? AND name = ?")) {
                ps.setInt(1, entityId); ps.setString(2, name.trim());
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) { throw new CustomModuleException("Database error: " + e.getMessage(), e); }
    }

    public void deleteView(int viewId) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("DELETE FROM custom_views WHERE id = ?")) {
            ps.setInt(1, viewId); ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    /** Checks a definition against the entity's real fields without running it. */
    public void validate(int entityId, ViewDef def) throws CustomModuleException {
        Map<String, FieldDef> byKey = fieldsByKey(entityId);
        compileWhere(def, byKey);
        if (def.sort() != null && def.sort().field() != null) requireStored(byKey, def.sort().field(), "sort by");
        if (def.isGrouped()) {
            FieldDef g = requireStored(byKey, def.groupBy(), "group by");
            if (def.bucket() != null && def.bucket() != Bucket.NONE && g.type() != FieldType.DATE && g.type() != FieldType.DATETIME)
                throw new CustomModuleException("Grouping by month or year needs a date field.");
            for (Aggregate a : def.aggregates() == null ? List.<Aggregate>of() : def.aggregates()) {
                if (a.fn() == Agg.COUNT) continue;
                FieldDef f = requireStored(byKey, a.field(), a.fn().label().toLowerCase());
                if (!f.type().isNumeric()) throw new CustomModuleException("\"" + f.label() + "\" is not a number, so it can't be totalled or averaged.");
            }
        }
    }

    // ── JSON (explicit, so the stored format is stable and doesn't depend on a serializer) ──

    static JsonObject toJson(ViewDef d) {
        JsonObject o = new JsonObject();
        JsonArray filters = new JsonArray();
        for (Filter f : d.filters() == null ? List.<Filter>of() : d.filters()) {
            JsonObject fo = new JsonObject();
            fo.addProperty("field", f.field()); fo.addProperty("op", f.op().name());
            if (f.value() != null) fo.addProperty("value", f.value());
            filters.add(fo);
        }
        o.add("filters", filters);
        if (d.sort() != null && d.sort().field() != null) {
            JsonObject so = new JsonObject();
            so.addProperty("field", d.sort().field()); so.addProperty("desc", d.sort().desc());
            o.add("sort", so);
        }
        if (d.isGrouped()) {
            o.addProperty("groupBy", d.groupBy());
            o.addProperty("bucket", (d.bucket() == null ? Bucket.NONE : d.bucket()).name());
        }
        JsonArray aggs = new JsonArray();
        for (Aggregate a : d.aggregates() == null ? List.<Aggregate>of() : d.aggregates()) {
            JsonObject ao = new JsonObject();
            if (a.field() != null) ao.addProperty("field", a.field());
            ao.addProperty("fn", a.fn().name());
            aggs.add(ao);
        }
        o.add("aggregates", aggs);
        return o;
    }

    static ViewDef fromJson(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        List<Filter> filters = new ArrayList<>();
        if (o.has("filters")) for (JsonElement e : o.getAsJsonArray("filters")) {
            JsonObject f = e.getAsJsonObject();
            filters.add(new Filter(f.get("field").getAsString(), Op.valueOf(f.get("op").getAsString()), f.has("value") ? f.get("value").getAsString() : null));
        }
        Sort sort = null;
        if (o.has("sort")) { JsonObject s = o.getAsJsonObject("sort"); sort = new Sort(s.get("field").getAsString(), s.has("desc") && s.get("desc").getAsBoolean()); }
        String groupBy = o.has("groupBy") ? o.get("groupBy").getAsString() : null;
        Bucket bucket = o.has("bucket") ? Bucket.valueOf(o.get("bucket").getAsString()) : Bucket.NONE;
        List<Aggregate> aggs = new ArrayList<>();
        if (o.has("aggregates")) for (JsonElement e : o.getAsJsonArray("aggregates")) {
            JsonObject a = e.getAsJsonObject();
            aggs.add(new Aggregate(a.has("field") ? a.get("field").getAsString() : null, Agg.valueOf(a.get("fn").getAsString())));
        }
        return new ViewDef(filters, sort, groupBy, bucket, aggs);
    }
}
