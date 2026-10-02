package com.kaziflow.custom;

import com.kaziflow.custom.CustomModuleService.CustomModuleException;
import com.kaziflow.custom.CustomModuleService.EntityDef;
import com.kaziflow.custom.CustomModuleService.FieldDef;
import com.kaziflow.custom.CustomQueryService.GroupResult;
import com.kaziflow.custom.CustomRecordService.LookupOption;
import com.kaziflow.custom.CustomRecordService.RecordView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * CustomCsv — export a list or report to CSV, and import CSV into an entity.
 *
 * <p><b>Export</b> writes what Excel opens cleanly: header row of field labels, dates as yyyy-mm-dd, numbers
 * without thousands separators, yes/no for booleans, and links as the linked record's title. The caller writes
 * the text as UTF-8 with a BOM ({@link #BOM}) so Excel reads accents correctly.
 *
 * <p><b>Import</b> is two-step so nothing is written blind: {@link #plan} reads the CSV, matches columns to
 * fields by header label, converts every cell, and reports a per-row problem list; {@link #commit} then writes
 * the rows that were clean, in a single transaction. It is tolerant of how people actually keep data in Kenya:
 * {@code ;} or {@code ,} delimiters, {@code dd/mm/yyyy} dates, {@code KES 1,200} amounts, yes/no/y/n booleans, and
 * link and choice cells matched by their visible text ignoring case.
 *
 * <p>Import does not de-duplicate: importing the same file twice creates the records twice.
 */
public final class CustomCsv {

    public static final String BOM = "\uFEFF";
    public static final int MAX_IMPORT_ROWS = 5000;

    private CustomCsv() {}

    // ── export ────────────────────────────────────────────────────────────

    /** A list of records as CSV (header = field labels, only the fields shown in lists unless {@code allFields}). */
    public static String exportList(int entityId, List<RecordView> rows, boolean allFields) {
        CustomModuleService defs = CustomModuleService.getInstance();
        CustomRecordService recs = CustomRecordService.getInstance();
        List<FieldDef> fields = new ArrayList<>();
        for (FieldDef f : defs.listFields(entityId, false)) if (allFields || f.showInList()) fields.add(f);
        Map<Integer, Map<Long, String>> titles = new HashMap<>();
        for (FieldDef f : fields) {
            if (f.type() == FieldType.LOOKUP && f.lookupEntityId() != null) {
                Map<Long, String> m = new HashMap<>();
                for (LookupOption o : recs.lookupOptions(f.lookupEntityId())) m.put(o.id(), o.title());
                titles.put(f.id(), m);
            }
        }
        List<String> header = new ArrayList<>();
        for (FieldDef f : fields) header.add(f.label());
        List<List<String>> body = new ArrayList<>();
        for (RecordView r : rows) {
            List<String> line = new ArrayList<>();
            for (FieldDef f : fields) line.add(cell(f, r.values().get(f.key()), titles));
            body.add(line);
        }
        return toCsv(header, body);
    }

    /** A grouped report as CSV: the group, a record count, then one column per total/average. */
    public static String exportGroup(int entityId, String groupLabel, GroupResult result) {
        CustomModuleService defs = CustomModuleService.getInstance();
        Map<String, String> labels = new HashMap<>();
        for (FieldDef f : defs.listFields(entityId, false)) labels.put(f.key(), f.label());
        List<String> header = new ArrayList<>(List.of(groupLabel, "Count"));
        for (CustomQueryService.Aggregate a : result.aggregates())
            if (a.fn() != CustomQueryService.Agg.COUNT) header.add(a.fn().label() + " " + labels.getOrDefault(a.field(), a.field()));
        List<List<String>> body = new ArrayList<>();
        for (CustomQueryService.GroupRow g : result.rows()) {
            List<String> line = new ArrayList<>(List.of(g.label(), String.valueOf(g.count())));
            for (CustomQueryService.Aggregate a : result.aggregates())
                if (a.fn() != CustomQueryService.Agg.COUNT) line.add(num(g.values().get(a.fn().name() + ":" + a.field())));
            body.add(line);
        }
        List<String> total = new ArrayList<>(List.of("TOTAL", String.valueOf(result.totalCount())));
        for (CustomQueryService.Aggregate a : result.aggregates())
            if (a.fn() != CustomQueryService.Agg.COUNT) total.add(num(result.totals().get(a.fn().name() + ":" + a.field())));
        body.add(total);
        return toCsv(header, body);
    }

    private static String cell(FieldDef f, Object v, Map<Integer, Map<Long, String>> titles) {
        if (v == null) return "";
        return switch (f.type()) {
            case BOOLEAN -> ((Boolean) v) ? "Yes" : "No";
            case LOOKUP -> titles.getOrDefault(f.id(), Map.of()).getOrDefault(((Number) v).longValue(), "");
            case INTEGER -> String.valueOf(((Number) v).longValue());
            case DECIMAL, MONEY, CALC -> num(((Number) v).doubleValue());
            default -> v.toString();
        };
    }

    private static String num(Double d) {
        if (d == null) return "";
        return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf(d.longValue()) : java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    public static String toCsv(List<String> header, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        appendLine(sb, header);
        for (List<String> r : rows) appendLine(sb, r);
        return sb.toString();
    }

    private static void appendLine(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(quote(cells.get(i)));
        }
        sb.append("\r\n");
    }

    private static String quote(String s) {
        if (s == null) return "";
        // A cell starting with = + - @ would be run as a formula by Excel (CSV injection); a leading apostrophe defuses it.
        String safe = (!s.isEmpty() && "=+@".indexOf(s.charAt(0)) >= 0) ? "'" + s : s;
        boolean needs = safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0;
        return needs ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }

    // ── import ────────────────────────────────────────────────────────────

    public record RowPlan(int rowNumber, Map<String, Object> input, String error) {}
    /** {@code problems} are whole-file issues (a required column missing, too many rows); a plan with any is not importable. */
    public record ImportPlan(List<String> mappedColumns, List<String> ignoredColumns, List<RowPlan> rows, List<String> problems) {
        public long validCount() { return rows.stream().filter(r -> r.error() == null).count(); }
        public long errorCount() { return rows.stream().filter(r -> r.error() != null).count(); }
        public boolean importable() { return problems.isEmpty() && validCount() > 0; }
    }

    public static ImportPlan plan(int entityId, String csvText) throws CustomModuleException {
        CustomModuleService defs = CustomModuleService.getInstance();
        CustomRecordService recs = CustomRecordService.getInstance();
        EntityDef entity = defs.getEntity(entityId);
        if (entity == null) throw new CustomModuleException("That kind of record no longer exists.");
        List<List<String>> table = parse(csvText);
        if (table.isEmpty()) throw new CustomModuleException("The file is empty.");

        List<String> header = table.get(0);
        Map<String, FieldDef> byLabel = new HashMap<>();
        List<FieldDef> fields = defs.listFields(entityId, false);
        for (FieldDef f : fields) if (f.type() != FieldType.CALC) byLabel.put(f.label().trim().toLowerCase(Locale.ROOT), f);
        FieldDef[] colField = new FieldDef[header.size()];
        List<String> mapped = new ArrayList<>(), ignored = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i).trim();
            FieldDef f = byLabel.get(h.toLowerCase(Locale.ROOT));
            if (f != null && seen.add(f.key())) { colField[i] = f; mapped.add(f.label()); }
            else if (!h.isEmpty()) ignored.add(h);
        }
        List<String> problems = new ArrayList<>();
        for (FieldDef f : fields) {
            if (f.required() && f.type() != FieldType.CALC && !seen.contains(f.key()) && (f.defaultValue() == null || f.defaultValue().isBlank()))
                problems.add("The file has no \"" + f.label() + "\" column, and that field is required.");
        }
        if (table.size() - 1 > MAX_IMPORT_ROWS)
            problems.add("The file has " + (table.size() - 1) + " rows; import up to " + MAX_IMPORT_ROWS + " at a time.");

        // link-cell lookup tables, built once
        Map<Integer, Map<String, List<Long>>> byTitle = new HashMap<>();
        for (FieldDef f : fields) {
            if (f.type() == FieldType.LOOKUP && f.lookupEntityId() != null && seen.contains(f.key())) {
                Map<String, List<Long>> m = new HashMap<>();
                for (LookupOption o : recs.lookupOptions(f.lookupEntityId()))
                    m.computeIfAbsent(o.title().trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(o.id());
                byTitle.put(f.id(), m);
            }
        }

        List<RowPlan> rows = new ArrayList<>();
        Map<String, Boolean> linkCache = new HashMap<>();
        for (int r = 1; r < table.size() && r <= MAX_IMPORT_ROWS; r++) {
            List<String> cells = table.get(r);
            if (cells.stream().allMatch(c -> c == null || c.isBlank())) continue;     // skip blank lines
            Map<String, Object> input = new HashMap<>();
            String error = null;
            for (int i = 0; i < colField.length && error == null; i++) {
                FieldDef f = colField[i];
                if (f == null) continue;
                String raw = i < cells.size() ? cells.get(i).trim() : "";
                if (raw.isEmpty()) continue;
                try { input.put(f.key(), convert(f, raw, byTitle.get(f.id()))); }
                catch (CustomModuleException e) { error = e.getMessage(); }
            }
            if (error == null) error = recs.check(fields, input, linkCache);
            rows.add(new RowPlan(r + 1, input, error));                                   // r+1 = the line number in a spreadsheet
        }
        return new ImportPlan(mapped, ignored, rows, problems);
    }

    /** Writes the rows of the plan that had no error, in one transaction. Returns how many were created. */
    public static int commit(int entityId, ImportPlan plan, int userId) throws CustomModuleException {
        if (!plan.problems().isEmpty()) throw new CustomModuleException(plan.problems().get(0));
        List<Map<String, Object>> good = new ArrayList<>();
        for (RowPlan r : plan.rows()) if (r.error() == null) good.add(r.input());
        if (good.isEmpty()) throw new CustomModuleException("There are no valid rows to import.");
        return CustomRecordService.getInstance().createBatch(entityId, good, userId);
    }

    private static Object convert(FieldDef f, String raw, Map<String, List<Long>> titles) throws CustomModuleException {
        switch (f.type()) {
            case INTEGER, DECIMAL, MONEY:
                return raw.replaceAll("(?i)kes|ksh\\.?|[\\s]", "");
            case DATE:
                return normalizeDate(f, raw);
            case BOOLEAN: {
                String v = raw.toLowerCase(Locale.ROOT);
                if (Set.of("yes", "y", "true", "1").contains(v)) return Boolean.TRUE;
                if (Set.of("no", "n", "false", "0").contains(v)) return Boolean.FALSE;
                throw new CustomModuleException("\"" + f.label() + "\": \"" + raw + "\" isn't yes or no.");
            }
            case CHOICE:
                for (String c : f.choices()) if (c.equalsIgnoreCase(raw)) return c;
                throw new CustomModuleException("\"" + f.label() + "\": \"" + raw + "\" isn't one of: " + String.join(", ", f.choices()) + ".");
            case LOOKUP: {
                List<Long> hits = titles == null ? null : titles.get(raw.toLowerCase(Locale.ROOT));
                if (hits == null || hits.isEmpty()) throw new CustomModuleException("\"" + f.label() + "\": no existing record called \"" + raw + "\".");
                if (hits.size() > 1) throw new CustomModuleException("\"" + f.label() + "\": more than one record is called \"" + raw + "\", so it's ambiguous.");
                return hits.get(0);
            }
            default:
                return raw;
        }
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"));

    private static String normalizeDate(FieldDef f, String raw) throws CustomModuleException {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(raw, fmt).toString(); } catch (java.time.format.DateTimeParseException ignored) { }
        }
        throw new CustomModuleException("\"" + f.label() + "\": \"" + raw + "\" isn't a date (use dd/mm/yyyy or yyyy-mm-dd).");
    }

    // ── RFC 4180 parser (quotes, embedded commas/newlines, CRLF, BOM, ';' or ',') ──

    public static List<List<String>> parse(String text) {
        if (text == null) return List.of();
        if (text.startsWith(BOM)) text = text.substring(1);
        int firstLineEnd = text.indexOf('\n');
        String firstLine = firstLineEnd < 0 ? text : text.substring(0, firstLineEnd);
        char delim = count(firstLine, ';') > count(firstLine, ',') ? ';' : ',';
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else quoted = false;
                } else cell.append(c);
            } else if (c == '"' && cell.length() == 0) {
                quoted = true;
            } else if (c == delim) {
                row.add(cell.toString()); cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0);
                rows.add(row); row = new ArrayList<>();
            } else cell.append(c);
        }
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }

    private static int count(String s, char c) { int n = 0; for (char x : s.toCharArray()) if (x == c) n++; return n; }
}
