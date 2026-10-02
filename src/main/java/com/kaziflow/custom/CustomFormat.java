package com.kaziflow.custom;

import com.kaziflow.custom.CustomModuleService.FieldDef;
import com.kaziflow.custom.CustomRecordService.LookupOption;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

/** Display formatting shared by every custom-module table (list, report, subform) so they always agree. */
final class CustomFormat {
    // DecimalFormat isn't thread-safe; these are only used from the JavaFX thread.
    private static final DecimalFormat NUM = new DecimalFormat("#,##0.##");
    private static final DecimalFormat INT = new DecimalFormat("#,##0");
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private CustomFormat() {}

    static String number(FieldDef f, double d) {
        return switch (f.type()) {
            case INTEGER -> INT.format(d);
            case MONEY -> "KES " + MONEY.format(d);
            default -> NUM.format(d);
        };
    }

    /** id -> title for a link field's target records. */
    static Map<Long, String> titles(FieldDef f) {
        Map<Long, String> m = new HashMap<>();
        if (f.type() == FieldType.LOOKUP && f.lookupEntityId() != null)
            for (LookupOption o : CustomRecordService.getInstance().lookupOptions(f.lookupEntityId())) m.put(o.id(), o.title());
        return m;
    }

    static String display(FieldDef f, Object v, Map<Long, String> lookupTitles) {
        if (v == null) return "";
        return switch (f.type()) {
            case BOOLEAN -> ((Boolean) v) ? "Yes" : "No";
            case LOOKUP -> lookupTitles.getOrDefault(((Number) v).longValue(), "(deleted)");
            case INTEGER, DECIMAL, MONEY, CALC -> number(f, ((Number) v).doubleValue());
            default -> v.toString();
        };
    }
}
