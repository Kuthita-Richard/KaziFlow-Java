package com.kaziflow.custom;

import com.kaziflow.custom.CustomModuleService.CustomModuleException;
import com.kaziflow.custom.CustomModuleService.EntityDef;
import com.kaziflow.custom.CustomModuleService.FieldDef;
import com.kaziflow.custom.CustomQueryService.*;
import com.kaziflow.custom.CustomRecordService.LookupOption;
import com.kaziflow.utils.SessionManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * CustomViewEditorDialog — build a view or report by pointing and clicking: which records to show (conditions),
 * how to order them, and optionally how to group them into totals. Everything offered is drawn from the record
 * type's real fields; nothing is typed as a query. Calculated fields are not offered for conditions, sorting or
 * grouping (they can still appear in lists).
 *
 * <p>The dialog only collects a {@link ViewDef}; {@link CustomQueryService} validates and runs it.
 */
final class CustomViewEditorDialog {

    interface Listener { void apply(ViewDef def, SavedView savedOrNull); }

    private static final String FIELD = "-fx-pref-height: 32px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;";
    private static final String BTN_PLAIN = "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-pref-height: 30px; -fx-padding: 0 12; -fx-cursor: hand; -fx-font-size: 12px;";

    /** Relative-date choices, so a saved "this month" view stays correct next month. */
    private static final LinkedHashMap<String, String> RELATIVE = new LinkedHashMap<>();
    static {
        RELATIVE.put("(pick a date)", null);
        RELATIVE.put("Today", "@today");
        RELATIVE.put("Start of this month", "@month_start");
        RELATIVE.put("End of this month", "@month_end");
        RELATIVE.put("Start of this year", "@year_start");
        RELATIVE.put("7 days ago", "@days_ago_7");
        RELATIVE.put("30 days ago", "@days_ago_30");
        RELATIVE.put("90 days ago", "@days_ago_90");
    }

    private final EntityDef entity;
    private final List<FieldDef> stored = new ArrayList<>();      // fields usable in conditions / sort / group
    private final List<FieldDef> numeric = new ArrayList<>();
    private final VBox filterBox = new VBox(6);
    private final List<FilterRow> filterRows = new ArrayList<>();
    private final VBox aggBox = new VBox(6);
    private final List<AggRow> aggRows = new ArrayList<>();
    private final ComboBox<FieldDef> sortField = new ComboBox<>();
    private final ComboBox<String> sortDir = new ComboBox<>();
    private final ComboBox<FieldDef> groupField = new ComboBox<>();
    private final ComboBox<Bucket> bucket = new ComboBox<>();
    private final VBox groupOptions = new VBox(8);

    private CustomViewEditorDialog(EntityDef entity) {
        this.entity = entity;
        for (FieldDef f : CustomModuleService.getInstance().listFields(entity.id(), false)) {
            if (f.type() == FieldType.CALC) continue;
            stored.add(f);
            if (f.type().isNumeric()) numeric.add(f);
        }
    }

    static void show(EntityDef entity, ViewDef start, SavedView current, boolean canSave, Listener listener) {
        new CustomViewEditorDialog(entity).run(start, current, canSave, listener);
    }

    private void run(ViewDef start, SavedView current, boolean canSave, Listener listener) {
        CustomQueryService q = CustomQueryService.getInstance();
        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle("Customize view — " + entity.label());
        d.setHeaderText(null);
        d.getDialogPane().setStyle("-fx-background-color: white;");
        d.getDialogPane().setPrefWidth(660);

        // 1. conditions
        Button addFilter = new Button("+ Add condition"); addFilter.setStyle(BTN_PLAIN); addFilter.setId("ve-add-filter");
        addFilter.setOnAction(e -> addFilterRow(null));
        VBox s1 = section("Show only records where…", filterBox, addFilter);

        // 2. sort
        sortField.getItems().add(null);
        sortField.getItems().addAll(stored);
        sortField.setConverter(fieldConverter("(newest first)"));
        sortField.setValue(null);
        sortDir.getItems().addAll("Ascending (smallest first)", "Descending (largest first)");
        sortDir.setValue(sortDir.getItems().get(0));
        HBox sortRow = new HBox(10, sortField, sortDir);
        sortRow.setAlignment(Pos.CENTER_LEFT);
        VBox s2 = section("Order by", sortRow);

        // 3. grouping / totals
        groupField.getItems().add(null);
        groupField.getItems().addAll(stored);
        groupField.setConverter(fieldConverter("(don't group — list the records)"));
        groupField.setValue(null);
        groupField.setId("ve-group");
        bucket.getItems().addAll(Bucket.values());
        bucket.setValue(Bucket.NONE);
        Button addAgg = new Button("+ Add a total"); addAgg.setStyle(BTN_PLAIN); addAgg.setId("ve-add-agg");
        addAgg.setOnAction(e -> addAggRow(null));
        Label countNote = new Label("The number of records in each group is always shown.");
        countNote.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        groupOptions.getChildren().addAll(new HBox(10, new Label("Split by"), bucket), aggBox, addAgg, countNote);
        Runnable syncGroup = () -> {
            FieldDef g = groupField.getValue();
            boolean grouped = g != null;
            groupOptions.setVisible(grouped); groupOptions.setManaged(grouped);
            boolean isDate = g != null && (g.type() == FieldType.DATE || g.type() == FieldType.DATETIME);
            bucket.getParent().setVisible(isDate); bucket.getParent().setManaged(isDate);
            if (!isDate) bucket.setValue(Bucket.NONE);
        };
        groupField.valueProperty().addListener((o, a, b) -> syncGroup.run());
        VBox s3 = section("Group into a report (optional)", groupField, groupOptions);

        TextField name = new TextField(current == null ? "" : current.name());
        name.setPromptText("Name this view to save it, e.g. This month by vehicle");
        name.setStyle(FIELD); name.setId("ve-name");
        VBox s4 = section(canSave ? "Save for everyone" : "Save", name);
        if (!canSave) { name.setDisable(true); name.setPromptText("Saving views needs permission to edit this module"); }

        VBox body = new VBox(14, s1, s2, s3, s4);
        body.setPadding(new Insets(16));
        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);
        sp.setPrefViewportHeight(520);
        sp.setStyle("-fx-background-color: white;");
        d.getDialogPane().setContent(sp);

        ButtonType applyBt = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        ButtonType saveBt = new ButtonType("Save view", ButtonBar.ButtonData.APPLY);
        ButtonType deleteBt = new ButtonType("Delete view", ButtonBar.ButtonData.LEFT);
        d.getDialogPane().getButtonTypes().add(applyBt);
        if (canSave) d.getDialogPane().getButtonTypes().add(saveBt);
        if (canSave && current != null) d.getDialogPane().getButtonTypes().add(deleteBt);
        d.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        load(start == null ? ViewDef.ALL : start);
        syncGroup.run();

        // Validate before the dialog closes, so a mistake keeps everything the user set up on screen.
        for (ButtonType bt : List.of(applyBt, saveBt)) {
            Node btn = d.getDialogPane().lookupButton(bt);
            if (btn == null) continue;
            btn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                try {
                    ViewDef def = read();
                    q.validate(entity.id(), def);
                    if (bt == saveBt && name.getText().isBlank()) throw new CustomModuleException("Give the view a name to save it.");
                } catch (CustomModuleException ex) { error(ex.getMessage()); ev.consume(); }
            });
        }

        Optional<ButtonType> res = d.showAndWait();
        if (res.isEmpty() || res.get() == ButtonType.CANCEL) return;
        try {
            if (res.get() == deleteBt && current != null) { q.deleteView(current.id()); listener.apply(ViewDef.ALL, null); return; }
            ViewDef def = read();
            if (res.get() == saveBt) {
                var user = SessionManager.getInstance().getCurrentUser();
                int id = q.saveView(entity.id(), name.getText(), def, user == null ? 0 : user.getId());
                SavedView saved = q.listViews(entity.id()).stream().filter(v -> v.id() == id).findFirst().orElse(null);
                listener.apply(def, saved);
            } else listener.apply(def, null);
        } catch (CustomModuleException ex) { error(ex.getMessage()); }
    }

    // ── reading / loading the controls ────────────────────────────────────

    private ViewDef read() throws CustomModuleException {
        List<Filter> filters = new ArrayList<>();
        for (FilterRow r : filterRows) filters.add(r.read());
        Sort sort = sortField.getValue() == null ? null : new Sort(sortField.getValue().key(), sortDir.getValue().startsWith("Desc"));
        FieldDef g = groupField.getValue();
        List<Aggregate> aggs = new ArrayList<>();
        if (g != null) {
            aggs.add(new Aggregate(null, Agg.COUNT));
            for (AggRow a : aggRows) { Aggregate x = a.read(); if (x != null) aggs.add(x); }
        }
        return new ViewDef(filters, sort, g == null ? null : g.key(), g == null ? Bucket.NONE : bucket.getValue(), aggs);
    }

    private void load(ViewDef def) {
        for (Filter f : def.filters()) addFilterRow(f);
        if (def.sort() != null) {
            for (FieldDef f : stored) if (f.key().equals(def.sort().field())) sortField.setValue(f);
            sortDir.setValue(def.sort().desc() ? sortDir.getItems().get(1) : sortDir.getItems().get(0));
        }
        if (def.isGrouped()) {
            for (FieldDef f : stored) if (f.key().equals(def.groupBy())) groupField.setValue(f);
            bucket.setValue(def.bucket() == null ? Bucket.NONE : def.bucket());
            for (Aggregate a : def.aggregates()) if (a.fn() != Agg.COUNT) addAggRow(a);
        }
    }

    // ── a condition row ───────────────────────────────────────────────────

    private void addFilterRow(Filter preset) {
        if (stored.isEmpty()) return;
        FilterRow r = new FilterRow(preset);
        filterRows.add(r);
        filterBox.getChildren().add(r.node);
    }

    private final class FilterRow {
        final HBox node = new HBox(8);
        final ComboBox<FieldDef> field = new ComboBox<>();
        final ComboBox<Op> op = new ComboBox<>();
        Supplier<String> value = () -> null;
        final StackPane valueHolder = new StackPane();

        FilterRow(Filter preset) {
            field.getItems().addAll(stored);
            field.setConverter(fieldConverter("Choose a field"));
            field.setPrefWidth(190);
            op.setPrefWidth(200);
            Button remove = new Button("✕");
            remove.setStyle(BTN_PLAIN);
            remove.setOnAction(e -> { filterRows.remove(this); filterBox.getChildren().remove(node); });
            node.setAlignment(Pos.CENTER_LEFT);
            node.getChildren().addAll(field, op, valueHolder, remove);
            field.valueProperty().addListener((o, a, b) -> rebuild(b, null, null));
            op.valueProperty().addListener((o, a, b) -> valueHolder.setVisible(b == null || b.needsValue()));
            if (preset != null) {
                for (FieldDef f : stored) if (f.key().equals(preset.field())) field.setValue(f);
                rebuild(field.getValue(), preset.op(), preset.value());
            } else field.setValue(stored.get(0));
        }

        void rebuild(FieldDef f, Op presetOp, String presetValue) {
            op.getItems().clear();
            valueHolder.getChildren().clear();
            if (f == null) return;
            switch (f.type()) {
                case TEXT, LONG_TEXT -> op.getItems().addAll(Op.EQ, Op.NE, Op.CONTAINS, Op.EMPTY, Op.NOT_EMPTY);
                case CHOICE, LOOKUP, BOOLEAN -> op.getItems().addAll(Op.EQ, Op.NE, Op.EMPTY, Op.NOT_EMPTY);
                default -> op.getItems().addAll(Op.EQ, Op.NE, Op.GT, Op.GE, Op.LT, Op.LE, Op.EMPTY, Op.NOT_EMPTY);
            }
            op.setValue(presetOp != null ? presetOp : op.getItems().get(0));
            Node control;
            switch (f.type()) {
                case LOOKUP -> {
                    ComboBox<LookupOption> cb = new ComboBox<>();
                    cb.getItems().addAll(CustomRecordService.getInstance().lookupOptions(f.lookupEntityId()));
                    cb.setPrefWidth(230);
                    if (presetValue != null) for (LookupOption o : cb.getItems()) if (String.valueOf(o.id()).equals(presetValue)) cb.setValue(o);
                    value = () -> cb.getValue() == null ? null : String.valueOf(cb.getValue().id());
                    control = cb;
                }
                case CHOICE -> {
                    ComboBox<String> cb = new ComboBox<>();
                    cb.getItems().addAll(f.choices()); cb.setPrefWidth(230);
                    if (presetValue != null) cb.setValue(presetValue);
                    value = cb::getValue;
                    control = cb;
                }
                case BOOLEAN -> {
                    ComboBox<String> cb = new ComboBox<>();
                    cb.getItems().addAll("Yes", "No"); cb.setPrefWidth(230);
                    if (presetValue != null) cb.setValue("1".equals(presetValue) || "yes".equalsIgnoreCase(presetValue) || "true".equalsIgnoreCase(presetValue) ? "Yes" : "No");
                    value = cb::getValue;
                    control = cb;
                }
                case DATE, DATETIME -> {
                    DatePicker dp = new DatePicker();
                    dp.setPrefWidth(140);
                    ComboBox<String> rel = new ComboBox<>();
                    rel.getItems().addAll(RELATIVE.keySet());
                    rel.setValue("(pick a date)");
                    rel.setPrefWidth(160);
                    if (presetValue != null) {
                        if (presetValue.startsWith("@")) {
                            for (var e : RELATIVE.entrySet()) if (presetValue.equals(e.getValue())) rel.setValue(e.getKey());
                        } else { try { dp.setValue(LocalDate.parse(presetValue)); } catch (Exception ignored) { } }
                    }
                    value = () -> {
                        String token = RELATIVE.get(rel.getValue());
                        if (token != null) return token;
                        return dp.getValue() == null ? null : dp.getValue().toString();
                    };
                    control = new HBox(6, dp, rel);
                }
                default -> {
                    TextField tf = new TextField(presetValue == null ? "" : presetValue);
                    tf.setStyle(FIELD); tf.setPrefWidth(230);
                    value = tf::getText;
                    control = tf;
                }
            }
            valueHolder.getChildren().add(control);
            valueHolder.setVisible(op.getValue() == null || op.getValue().needsValue());
        }

        Filter read() throws CustomModuleException {
            if (field.getValue() == null) throw new CustomModuleException("Choose a field for every condition (or remove the empty one).");
            Op o = op.getValue();
            String v = o.needsValue() ? value.get() : null;
            if (o.needsValue() && (v == null || v.isBlank()))
                throw new CustomModuleException("Enter a value for the condition on \"" + field.getValue().label() + "\".");
            return new Filter(field.getValue().key(), o, v);
        }
    }

    // ── a "total" row ─────────────────────────────────────────────────────

    private void addAggRow(Aggregate preset) {
        if (numeric.isEmpty()) {
            error("This record type has no number fields to total.");
            return;
        }
        AggRow r = new AggRow(preset);
        aggRows.add(r);
        aggBox.getChildren().add(r.node);
    }

    private final class AggRow {
        final HBox node = new HBox(8);
        final ComboBox<Agg> fn = new ComboBox<>();
        final ComboBox<FieldDef> field = new ComboBox<>();

        AggRow(Aggregate preset) {
            fn.getItems().addAll(Agg.SUM, Agg.AVG, Agg.MIN, Agg.MAX);
            fn.setValue(preset == null ? Agg.SUM : preset.fn());
            fn.setPrefWidth(120);
            field.getItems().addAll(numeric);
            field.setConverter(fieldConverter("Choose a number field"));
            field.setPrefWidth(220);
            if (preset != null) for (FieldDef f : numeric) if (f.key().equals(preset.field())) field.setValue(f);
            if (field.getValue() == null) field.setValue(numeric.get(0));
            Button remove = new Button("✕");
            remove.setStyle(BTN_PLAIN);
            remove.setOnAction(e -> { aggRows.remove(this); aggBox.getChildren().remove(node); });
            node.setAlignment(Pos.CENTER_LEFT);
            node.getChildren().addAll(fn, new Label("of"), field, remove);
        }

        Aggregate read() { return field.getValue() == null ? null : new Aggregate(field.getValue().key(), fn.getValue()); }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static StringConverter<FieldDef> fieldConverter(String whenNull) {
        return new StringConverter<>() {
            @Override public String toString(FieldDef f) { return f == null ? whenNull : f.label(); }
            @Override public FieldDef fromString(String s) { return null; }
        };
    }

    private static VBox section(String title, Node... content) {
        Label l = new Label(title);
        l.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        VBox v = new VBox(8);
        v.getChildren().add(l);
        v.getChildren().addAll(content);
        v.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-padding: 12;");
        return v;
    }

    private static void error(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        com.kaziflow.utils.DialogUtils.makeResizable(a);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
