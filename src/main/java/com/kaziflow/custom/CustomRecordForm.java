package com.kaziflow.custom;

import com.kaziflow.custom.CustomModuleService.CustomModuleException;
import com.kaziflow.custom.CustomModuleService.EntityDef;
import com.kaziflow.custom.CustomModuleService.FieldDef;
import com.kaziflow.custom.CustomModuleService.ModuleDef;
import com.kaziflow.custom.CustomQueryService.Filter;
import com.kaziflow.custom.CustomQueryService.Op;
import com.kaziflow.custom.CustomQueryService.ViewDef;
import com.kaziflow.custom.CustomRecordService.LookupOption;
import com.kaziflow.custom.CustomRecordService.RecordView;
import com.kaziflow.services.PermissionService;
import com.kaziflow.services.PermissionService.Action;
import com.kaziflow.utils.SessionManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * CustomRecordForm — the generated add/edit form for any record type, plus SUBFORMS: when you open an existing
 * record, every record type that links to it (a Vehicle's Mileage Logs) is shown underneath, filtered to that
 * record, with add / edit / delete in place and the link to the parent pre-filled and locked.
 *
 * <p>Subforms follow the same rules as everything else: each child's buttons obey the user's Create / Write /
 * Delete access to the module the CHILD belongs to, and validation errors keep the dialog open.
 */
public final class CustomRecordForm {

    private static final String FIELD = "-fx-pref-height: 34px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;";
    private static final String BTN_PRIMARY = "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 30px; -fx-padding: 0 14; -fx-cursor: hand; -fx-font-size: 12px;";

    private CustomRecordForm() {}

    /**
     * @param presets    field key -> value to start with (e.g. the parent link when adding from a subform)
     * @param locked     field keys the user may not change (the parent link)
     * @param onSaved    called after a successful save
     */
    public static void open(EntityDef entity, RecordView existing, Map<String, Object> presets, Set<String> locked,
                            Runnable onSaved) {
        CustomModuleService defs = CustomModuleService.getInstance();
        CustomRecordService recs = CustomRecordService.getInstance();
        List<FieldDef> fields = defs.listFields(entity.id(), false);

        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle((existing == null ? "New " : "Edit ") + entity.label());
        d.setHeaderText(null);
        d.getDialogPane().setStyle("-fx-background-color: white;");
        d.getDialogPane().setPrefWidth(560);

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));
        Map<String, Supplier<Object>> readers = new LinkedHashMap<>();
        int row = 0;
        for (FieldDef f : fields) {
            Label lbl = new Label(f.label() + (f.required() ? " *" : "") + (f.unit() == null || f.unit().isBlank() ? "" : " (" + f.unit() + ")"));
            lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
            Object current = existing != null ? existing.values().get(f.key()) : presets.get(f.key());
            boolean isLocked = locked.contains(f.key());
            Node control;
            switch (f.type()) {
                case LONG_TEXT -> {
                    TextArea ta = new TextArea(current == null ? "" : current.toString());
                    ta.setPrefRowCount(3); ta.setWrapText(true); ta.setDisable(isLocked);
                    control = ta; readers.put(f.key(), ta::getText);
                }
                case DATE -> {
                    LocalDate start = current != null ? LocalDate.parse(current.toString())
                            : (existing == null && "today".equalsIgnoreCase(f.defaultValue()) ? LocalDate.now() : null);
                    DatePicker dp = new DatePicker(start);
                    dp.setPrefWidth(300); dp.setDisable(isLocked);
                    control = dp; readers.put(f.key(), () -> dp.getValue() == null ? null : dp.getValue().toString());
                }
                case BOOLEAN -> {
                    CheckBox cb = new CheckBox();
                    cb.setSelected(current instanceof Boolean b ? b : "true".equalsIgnoreCase(f.defaultValue()));
                    cb.setDisable(isLocked);
                    control = cb; readers.put(f.key(), cb::isSelected);
                }
                case CHOICE -> {
                    ComboBox<String> cb = new ComboBox<>();
                    if (!f.required()) cb.getItems().add("");
                    cb.getItems().addAll(f.choices());
                    cb.setValue(current != null ? current.toString()
                            : (existing == null && f.defaultValue() != null && f.choices().contains(f.defaultValue()) ? f.defaultValue() : (f.required() ? null : "")));
                    cb.setPrefWidth(300); cb.setDisable(isLocked);
                    control = cb; readers.put(f.key(), cb::getValue);
                }
                case LOOKUP -> {
                    ComboBox<Object> cb = new ComboBox<>();
                    List<LookupOption> opts = recs.lookupOptions(f.lookupEntityId());
                    if (!f.required()) cb.getItems().add("");
                    cb.getItems().addAll(opts);
                    if (current != null) for (LookupOption o : opts) if (o.id() == ((Number) current).longValue()) cb.setValue(o);
                    cb.setPrefWidth(300); cb.setDisable(isLocked);
                    cb.setPromptText(opts.isEmpty() ? "Nothing to choose from yet" : "Choose...");
                    control = cb; readers.put(f.key(), () -> cb.getValue() instanceof LookupOption o ? o : null);
                }
                case CALC -> {
                    Label val = new Label(existing == null ? "(worked out automatically)" : CustomFormat.display(f, current, Map.of()));
                    val.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
                    control = val;
                }
                default -> {
                    TextField tf = new TextField(current == null ? "" : (current instanceof Double dv ? stripZeros(dv) : current.toString()));
                    if (f.type() == FieldType.DATETIME) tf.setPromptText("yyyy-mm-ddThh:mm");
                    if (f.type().isNumeric()) tf.setPromptText(rangeHint(f));
                    tf.setStyle(FIELD); tf.setPrefWidth(300); tf.setDisable(isLocked);
                    control = tf; readers.put(f.key(), tf::getText);
                }
            }
            form.add(lbl, 0, row);
            form.add(control, 1, row++);
        }

        VBox content = new VBox(4, form);
        // Subforms need a saved parent; for a new record, say so instead of silently hiding them.
        if (existing != null) {
            Node subs = buildSubforms(entity, existing, onSaved);
            if (subs != null) content.getChildren().add(subs);
        } else if (!childRelations(entity).isEmpty()) {
            Label hint = new Label("Save this " + entity.label().toLowerCase() + " first — then you can add its "
                    + childRelations(entity).stream().map(r -> r.child.pluralLabel() == null ? r.child.label() : r.child.pluralLabel()).distinct().reduce((a, b) -> a + " and " + b).orElse("")
                    + " right here.");
            hint.setWrapText(true);
            hint.setPadding(new Insets(0, 20, 16, 20));
            hint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            content.getChildren().add(hint);
        }
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setPrefViewportHeight(Math.min(560, 100 + fields.size() * 46 + (existing != null ? 200 : 0)));
        sp.setStyle("-fx-background-color: white;");
        d.getDialogPane().setContent(sp);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Save inside the OK handler so a validation message keeps the dialog (and everything typed) on screen.
        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            Map<String, Object> input = new HashMap<>(presets);
            readers.forEach((k, r) -> input.put(k, r.get()));
            try {
                if (existing == null) {
                    var user = SessionManager.getInstance().getCurrentUser();
                    recs.create(entity.id(), input, user == null ? 0 : user.getId());
                } else recs.update(existing.id(), input);
            } catch (CustomModuleException ex) {
                error(ex.getMessage());
                ev.consume();
            }
        });
        if (d.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK && onSaved != null) onSaved.run();
    }

    // ── subforms ──────────────────────────────────────────────────────────

    /** A record type that links to the parent, and the link field that does it. */
    record Relation(EntityDef child, FieldDef link) {}

    static List<Relation> childRelations(EntityDef parent) {
        CustomModuleService defs = CustomModuleService.getInstance();
        List<Relation> out = new ArrayList<>();
        for (EntityDef e : defs.listAllEntities()) {
            for (FieldDef f : defs.listFields(e.id(), false)) {
                if (f.type() == FieldType.LOOKUP && f.lookupEntityId() != null && f.lookupEntityId() == parent.id() && e.id() != parent.id())
                    out.add(new Relation(e, f));
            }
        }
        return out;
    }

    /**
     * One section per record type that links to {@code parentRecord}'s type, listing only that record's children
     * with add / edit / delete. Returns null when nothing links to this type.
     */
    public static Node buildSubforms(EntityDef parent, RecordView parentRecord, Runnable onChanged) {
        List<Relation> relations = childRelations(parent);
        if (relations.isEmpty()) return null;
        VBox box = new VBox(14);
        box.setPadding(new Insets(0, 20, 20, 20));
        for (Relation rel : relations) box.getChildren().add(subform(rel, parentRecord, onChanged));
        return box;
    }

    private static Node subform(Relation rel, RecordView parentRecord, Runnable onChanged) {
        CustomModuleService defs = CustomModuleService.getInstance();
        CustomQueryService q = CustomQueryService.getInstance();
        CustomRecordService recs = CustomRecordService.getInstance();
        PermissionService ps = PermissionService.getInstance();
        ModuleDef childModule = defs.getModule(rel.child.moduleId());
        String moduleId = childModule == null ? "" : childModule.moduleId();
        List<FieldDef> fields = defs.listFields(rel.child.id(), false);

        Label title = new Label(rel.child.pluralLabel() == null || rel.child.pluralLabel().isBlank() ? rel.child.label() : rel.child.pluralLabel());
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label count = new Label();
        count.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        Button add = new Button("+ Add " + rel.child.label().toLowerCase());
        add.setId("sub-add:" + rel.child.id());
        add.setStyle(BTN_PRIMARY);
        add.setDisable(!ps.can(moduleId, Action.CREATE));
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox head = new HBox(10, title, count, sp, add);
        head.setAlignment(Pos.CENTER_LEFT);

        TableView<RecordView> table = new TableView<>();
        table.setId("sub-table:" + rel.child.id());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(170); table.setMinHeight(120);
        table.setPlaceholder(new Label("None yet."));
        Map<Integer, Map<Long, String>> titles = new HashMap<>();
        for (FieldDef f : fields) {
            if (!f.showInList() || f.id() == rel.link.id()) continue;   // the parent link is implied
            if (f.type() == FieldType.LOOKUP) titles.put(f.id(), CustomFormat.titles(f));
            TableColumn<RecordView, String> col = new TableColumn<>(f.label());
            col.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                    CustomFormat.display(f, c.getValue().values().get(f.key()), titles.getOrDefault(f.id(), Map.of()))));
            if (f.type().isNumeric() || f.type() == FieldType.CALC) col.setStyle("-fx-alignment: CENTER-RIGHT;");
            table.getColumns().add(col);
        }

        Runnable[] reload = new Runnable[1];
        reload[0] = () -> {
            try {
                var res = q.list(rel.child.id(), new ViewDef(List.of(new Filter(rel.link.key(), Op.EQ, String.valueOf(parentRecord.id()))), null, null, CustomQueryService.Bucket.NONE, List.of()), null, 500);
                table.getItems().setAll(res.rows());
                count.setText(res.totalMatching() + (res.totalMatching() == 1 ? " record" : " records"));
            } catch (CustomModuleException e) { count.setText(e.getMessage()); }
        };
        Runnable changed = () -> { reload[0].run(); if (onChanged != null) onChanged.run(); };

        TableColumn<RecordView, Void> actions = new TableColumn<>("");
        actions.setMaxWidth(90); actions.setMinWidth(90);
        actions.setCellFactory(c -> new TableCell<>() {
            private final Button edit = new Button("✎");
            private final Button del = new Button("🗑");
            private final HBox b = new HBox(6, edit, del);
            {
                for (Button x : new Button[]{edit, del}) x.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-cursor: hand; -fx-font-size: 13px; -fx-padding: 2;");
                edit.setDisable(!ps.can(moduleId, Action.WRITE));
                del.setDisable(!ps.can(moduleId, Action.DELETE));
                edit.setOnAction(e -> open(rel.child, getTableView().getItems().get(getIndex()), Map.of(), Set.of(rel.link.key()), changed));
                del.setOnAction(e -> {
                    RecordView r = getTableView().getItems().get(getIndex());
                    Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete this " + rel.child.label().toLowerCase() + " record?", ButtonType.OK, ButtonType.CANCEL);
                    com.kaziflow.utils.DialogUtils.makeResizable(a);
                    a.setHeaderText(null);
                    if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
                    try { recs.delete(r.id()); changed.run(); } catch (CustomModuleException ex) { error(ex.getMessage()); }
                });
            }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v, empty); setGraphic(empty ? null : b); }
        });
        table.getColumns().add(actions);

        add.setOnAction(e -> open(rel.child, null, Map.of(rel.link.key(), parentRecord.id()), Set.of(rel.link.key()), changed));
        reload[0].run();

        VBox section = new VBox(8, head, table);
        section.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-padding: 12;");
        return section;
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static String rangeHint(FieldDef f) {
        if (f.minValue() != null && f.maxValue() != null) return fmt(f.minValue()) + " to " + fmt(f.maxValue());
        if (f.minValue() != null) return "at least " + fmt(f.minValue());
        if (f.maxValue() != null) return "up to " + fmt(f.maxValue());
        return "0";
    }

    private static String fmt(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }

    private static String stripZeros(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    private static void error(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        com.kaziflow.utils.DialogUtils.makeResizable(a);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
