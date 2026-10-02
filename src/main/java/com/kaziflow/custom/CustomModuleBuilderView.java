package com.kaziflow.custom;

import com.kaziflow.custom.CustomModuleService.CustomModuleException;
import com.kaziflow.custom.CustomModuleService.EntityDef;
import com.kaziflow.custom.CustomModuleService.FieldDef;
import com.kaziflow.custom.CustomModuleService.ModuleDef;
import com.kaziflow.services.PermissionService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.util.*;

/**
 * CustomModuleBuilderView — where an administrator creates a department the product doesn't ship with
 * (an Ambulance or Staff Transport department, say): name it, add its record types ("Vehicle",
 * "Mileage Log"), and give each record type fields (text, numbers, dates, choices, links to other
 * record types, calculated values). Saving registers the module with {@code ModuleRegistry} so it appears
 * in the sidebar and in the Positions editor; Positions then decide who can open it.
 *
 * <p>All rules (valid names, unique labels, formula checks, "can't delete what holds data") live in
 * {@link CustomModuleService}; this class only collects input and shows its error messages.
 */
public class CustomModuleBuilderView {

    private static final String BTN_PRIMARY = "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 34px; -fx-padding: 0 16; -fx-cursor: hand; -fx-font-size: 13px;";
    private static final String BTN_PLAIN = "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-pref-height: 32px; -fx-padding: 0 12; -fx-cursor: hand; -fx-font-size: 12px;";
    private static final String BTN_DANGER = "-fx-background-color: white; -fx-border-color: #fecaca; -fx-text-fill: #dc2626; -fx-border-radius: 8; -fx-background-radius: 8; -fx-pref-height: 32px; -fx-padding: 0 12; -fx-cursor: hand; -fx-font-size: 12px;";
    private static final String FIELD = "-fx-pref-height: 34px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;";

    private final CustomModuleService defs = CustomModuleService.getInstance();
    private final HBox root = new HBox(0);
    private final ListView<ModuleDef> list = new ListView<>();
    private final VBox editor = new VBox(16);
    private Integer currentModuleId = null;   // null = a new, unsaved module
    private boolean changed = false;          // anything saved that affects the sidebar

    public CustomModuleBuilderView() {
        build();
        reloadList(null);
    }

    public HBox getRoot() { return root; }
    public boolean hasChanges() { return changed; }

    /** Opens the builder in a resizable dialog. Administrators / Settings-writers only. */
    public static void showDialog(Window owner) {
        PermissionService ps = PermissionService.getInstance();
        if (!(ps.isAdministrator() || ps.canWrite("settings"))) {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Building modules needs permission to manage Settings.", ButtonType.OK);
            a.setHeaderText(null);
            a.showAndWait();
            return;
        }
        CustomModuleBuilderView view = new CustomModuleBuilderView();
        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        if (owner != null) d.initOwner(owner);
        d.setTitle("Custom Modules");
        d.setHeaderText(null);
        d.getDialogPane().setStyle("-fx-background-color: #f8fafc;");
        d.getDialogPane().setContent(view.getRoot());
        d.getDialogPane().setPrefSize(1060, 700);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.showAndWait();
        if (view.hasChanges()) {
            // The sidebar is built when the main screen is built; rebuild it so a new/renamed module appears now.
            try { com.kaziflow.utils.SceneManager.getInstance().showMainApp(); } catch (Exception ignored) { }
        }
    }

    // ── layout ────────────────────────────────────────────────────────────

    private void build() {
        root.setStyle("-fx-background-color: #f8fafc;");
        VBox left = new VBox(10);
        left.setPadding(new Insets(16));
        left.setPrefWidth(250);
        Label title = new Label("Your modules");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label hint = new Label("Departments you've added yourself, such as Ambulance Services or Staff Transport.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        Button newBtn = new Button("+ New module");
        newBtn.setId("cmNew");
        newBtn.setStyle(BTN_PRIMARY);
        newBtn.setMaxWidth(Double.MAX_VALUE);
        newBtn.setOnAction(e -> { loadingSelection = true; list.getSelectionModel().clearSelection(); loadingSelection = false; showModule(null); });
        list.setId("cmList");
        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(ModuleDef m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) { setGraphic(null); setText(null); return; }
                Label name = new Label(m.icon() + "  " + m.label());
                name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + (m.active() ? "#1e293b" : "#94a3b8") + ";");
                Label sub = new Label(m.active() ? m.moduleId() : m.moduleId() + "  ·  inactive");
                sub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
                setGraphic(new VBox(1, name, sub)); setText(null);
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((o, a, sel) -> { if (!loadingSelection && sel != null) showModule(sel.id()); });
        VBox.setVgrow(list, Priority.ALWAYS);
        left.getChildren().addAll(title, hint, newBtn, list);

        editor.setPadding(new Insets(20));
        ScrollPane scroll = new ScrollPane(editor);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #f8fafc;");
        HBox.setHgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(left, new Separator(javafx.geometry.Orientation.VERTICAL), scroll);
    }

    private boolean loadingSelection = false;

    private void reloadList(Integer select) {
        loadingSelection = true;
        List<ModuleDef> mods = defs.listModules(false);
        list.getItems().setAll(mods);
        loadingSelection = false;
        Integer target = select != null ? select : (mods.isEmpty() ? null : mods.get(0).id());
        if (target != null) {
            for (ModuleDef m : mods) if (m.id() == target) { loadingSelection = true; list.getSelectionModel().select(m); loadingSelection = false; }
        }
        showModule(target);
    }

    // ── editor for one module ─────────────────────────────────────────────

    private void showModule(Integer moduleId) {
        currentModuleId = moduleId;
        editor.getChildren().clear();
        ModuleDef def = moduleId == null ? null : defs.getModule(moduleId);

        TextField label = new TextField(def == null ? "" : def.label()); label.setPromptText("e.g. Ambulance Services"); label.setStyle(FIELD); label.setId("cmLabel");
        TextField key = new TextField(def == null ? "" : def.key()); key.setPromptText("e.g. ambulance  (lowercase, no spaces)"); key.setStyle(FIELD); key.setId("cmKey");
        key.setDisable(def != null);
        TextField icon = new TextField(def == null ? "" : def.icon()); icon.setPromptText("◇"); icon.setPrefWidth(70); icon.setStyle(FIELD); icon.setId("cmIcon");
        TextArea desc = new TextArea(def == null ? "" : def.description()); desc.setPrefRowCount(2); desc.setWrapText(true); desc.setPromptText("What is this department for?"); desc.setId("cmDesc");
        CheckBox active = new CheckBox("Active (shown in the sidebar)"); active.setSelected(def == null || def.active()); active.setId("cmActive");
        if (def == null) {
            // suggest the short name from the label until the user edits it
            label.textProperty().addListener((o, a, b) -> { if (!key.isFocused()) key.setText(CustomModuleService.slug(b == null ? "" : b).replaceAll("^e_", "")); });
        }

        Button save = new Button(def == null ? "Create module" : "Save module");
        save.setStyle(BTN_PRIMARY); save.setId("cmSave");
        save.setOnAction(e -> {
            try {
                if (def == null) {
                    int id = defs.createModule(key.getText(), label.getText(), icon.getText(), desc.getText());
                    changed = true; CustomModuleLoader.sync();
                    reloadList(id);
                } else {
                    defs.updateModule(def.id(), label.getText(), icon.getText(), desc.getText(), active.isSelected());
                    changed = true; CustomModuleLoader.sync();
                    reloadList(def.id());
                }
            } catch (CustomModuleException ex) { error(ex.getMessage()); }
        });
        Button delete = new Button("Delete module"); delete.setStyle(BTN_DANGER); delete.setId("cmDelete");
        delete.setDisable(def == null);
        delete.setOnAction(e -> {
            if (!confirm("Delete the module \"" + def.label() + "\" and its (empty) record types?")) return;
            try { defs.deleteModule(def.id()); changed = true; CustomModuleLoader.sync(); reloadList(null); }
            catch (CustomModuleException ex) { error(ex.getMessage()); }
        });
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox buttons = new HBox(10, delete, sp, save);

        VBox card = card();
        card.getChildren().addAll(sectionTitle(def == null ? "New module" : "Module"), lbl("Name"), label,
                lbl("Short name (identifies the module; can't be changed later)"), key, lbl("Icon (one or two characters)"), icon,
                lbl("Description"), desc);
        if (def != null) card.getChildren().add(active);
        card.getChildren().add(buttons);
        editor.getChildren().add(card);

        if (def == null) {
            Label next = new Label("Create the module first, then add its record types and fields.");
            next.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            editor.getChildren().add(next);
            return;
        }
        Label access = new Label("New modules start with no access for anyone except Administrators. Grant access to the people who need it in Positions & Access.");
        access.setWrapText(true);
        access.setStyle("-fx-background-color: #ede9fe; -fx-text-fill: #6d28d9; -fx-background-radius: 8; -fx-padding: 10 12; -fx-font-size: 12px;");
        editor.getChildren().add(access);

        for (EntityDef e : defs.listEntities(def.id())) editor.getChildren().add(entityCard(def, e));
        Button addEntity = new Button("+ Add record type"); addEntity.setStyle(BTN_PRIMARY); addEntity.setId("cmAddEntity");
        addEntity.setOnAction(e -> entityDialog(def, null));
        editor.getChildren().add(addEntity);
    }

    private VBox entityCard(ModuleDef module, EntityDef entity) {
        VBox card = card();
        Label name = sectionTitle(entity.label());
        Button rename = new Button("Rename"); rename.setStyle(BTN_PLAIN);
        rename.setOnAction(e -> entityDialog(module, entity));
        Button del = new Button("Delete"); del.setStyle(BTN_DANGER);
        del.setOnAction(e -> {
            if (!confirm("Delete the record type \"" + entity.label() + "\" and all its fields?")) return;
            try { defs.deleteEntity(entity.id()); changed = true; showModule(module.id()); }
            catch (CustomModuleException ex) { error(ex.getMessage()); }
        });
        Button addField = new Button("+ Add field"); addField.setStyle(BTN_PRIMARY);
        addField.setId("cmAddField:" + entity.id());
        addField.setOnAction(e -> fieldDialog(module, entity, null));
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox head = new HBox(8, name, sp, rename, del, addField);
        head.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(head);

        List<FieldDef> fields = defs.listFields(entity.id(), true);
        if (fields.isEmpty()) {
            Label none = new Label("No fields yet. Add the details you want to record, e.g. Registration, Capacity, Date.");
            none.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            card.getChildren().add(none);
        }
        for (FieldDef f : fields) card.getChildren().add(fieldRow(module, entity, f));
        return card;
    }

    private HBox fieldRow(ModuleDef module, EntityDef entity, FieldDef f) {
        String col = f.archived() ? "#cbd5e1" : "#1e293b";
        Label name = new Label(f.label() + (f.required() ? " *" : ""));
        name.setPrefWidth(190);
        name.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + col + ";");
        StringBuilder detail = new StringBuilder(f.type().label());
        if (f.type() == FieldType.LOOKUP && f.lookupEntityId() != null) {
            EntityDef target = defs.getEntity(f.lookupEntityId());
            detail.append(" → ").append(target == null ? "?" : target.label());
        }
        if (f.type() == FieldType.CHOICE) detail.append(": ").append(String.join(", ", f.choices()));
        if (f.type() == FieldType.CALC) detail.append(" = ").append(defs.keysToLabels(entity.id(), f.formula()));
        if (f.unit() != null && !f.unit().isBlank()) detail.append("  (").append(f.unit()).append(")");
        if (f.minValue() != null || f.maxValue() != null) {
            detail.append("  · ").append(f.type().isNumeric() ? "allowed " : "length ")
                  .append(f.minValue() == null ? "up to " + plain(f.maxValue()) : f.maxValue() == null ? "at least " + plain(f.minValue())
                          : plain(f.minValue()) + " to " + plain(f.maxValue()));
        }
        if (f.searchable()) detail.append("  · indexed");
        if (f.archived()) detail.append("  · archived");
        Label info = new Label(detail.toString());
        info.setWrapText(true);
        info.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (f.archived() ? "#cbd5e1" : "#64748b") + ";");
        HBox.setHgrow(info, Priority.ALWAYS);
        Button edit = new Button("Edit"); edit.setStyle(BTN_PLAIN);
        edit.setOnAction(e -> fieldDialog(module, entity, f));
        Button arch = new Button(f.archived() ? "Restore" : "Archive"); arch.setStyle(BTN_PLAIN);
        arch.setOnAction(e -> {
            try { defs.archiveField(f.id(), !f.archived()); changed = true; showModule(module.id()); }
            catch (CustomModuleException ex) { error(ex.getMessage()); }
        });
        HBox row = new HBox(10, name, info, edit, arch);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 0, 6, 0));
        row.setStyle("-fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");
        return row;
    }

    // ── dialogs ───────────────────────────────────────────────────────────

    private void entityDialog(ModuleDef module, EntityDef existing) {
        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle(existing == null ? "Add record type" : "Rename record type");
        d.setHeaderText(null);
        d.getDialogPane().setStyle("-fx-background-color: white;");
        TextField label = new TextField(existing == null ? "" : existing.label()); label.setPromptText("e.g. Vehicle"); label.setStyle(FIELD); label.setId("cmEntityLabel");
        TextField plural = new TextField(existing == null || existing.pluralLabel() == null ? "" : existing.pluralLabel()); plural.setPromptText("e.g. Vehicles (optional)"); plural.setStyle(FIELD); plural.setId("cmEntityPlural");
        VBox form = new VBox(8, lbl("Name (singular)"), label, lbl("Plural name (shown on the tab)"), plural);
        form.setPadding(new Insets(16)); form.setPrefWidth(380);
        d.getDialogPane().setContent(form);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                if (existing == null) defs.addEntity(module.id(), label.getText(), plural.getText());
                else defs.updateEntity(existing.id(), label.getText(), plural.getText(), existing.titleFieldId());
                changed = true;
            } catch (CustomModuleException ex) { error(ex.getMessage()); ev.consume(); }
        });
        if (d.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) showModule(module.id());
    }

    private void fieldDialog(ModuleDef module, EntityDef entity, FieldDef existing) {
        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle(existing == null ? "Add field to " + entity.label() : "Edit field");
        d.setHeaderText(null);
        d.getDialogPane().setStyle("-fx-background-color: white;");
        d.getDialogPane().setPrefWidth(500);

        TextField label = new TextField(existing == null ? "" : existing.label()); label.setStyle(FIELD); label.setPromptText("e.g. Registration"); label.setId("cmFieldLabel");
        ComboBox<FieldType> type = new ComboBox<>(); type.getItems().addAll(FieldType.values());
        type.setValue(existing == null ? FieldType.TEXT : existing.type()); type.setDisable(existing != null); type.setId("cmFieldType");
        type.setPrefWidth(300);
        CheckBox required = new CheckBox("Required"); required.setSelected(existing != null && existing.required()); required.setId("cmFieldRequired");
        CheckBox showInList = new CheckBox("Show as a column in the list"); showInList.setSelected(existing == null || existing.showInList());
        CheckBox searchable = new CheckBox("Fast to search (adds an index)"); searchable.setSelected(existing != null && existing.searchable());
        TextField unit = new TextField(existing == null || existing.unit() == null ? "" : existing.unit()); unit.setStyle(FIELD); unit.setPromptText("e.g. km, litres (optional)");
        TextField def = new TextField(existing == null || existing.defaultValue() == null ? "" : existing.defaultValue()); def.setStyle(FIELD); def.setPromptText("Starting value (optional; for dates, type: today)");
        TextArea choices = new TextArea(existing == null ? "" : String.join("\n", existing.choices())); choices.setPrefRowCount(4); choices.setPromptText("One option per line"); choices.setId("cmFieldChoices");
        ComboBox<EntityDef> target = new ComboBox<>(); target.setPrefWidth(300); target.setId("cmFieldTarget");
        Map<Integer, String> entityNames = new HashMap<>();
        Map<Integer, String> moduleLabels = new HashMap<>();
        for (ModuleDef m : defs.listModules(false)) moduleLabels.put(m.id(), m.label());
        for (EntityDef e : defs.listAllEntities()) {
            target.getItems().add(e);
            entityNames.put(e.id(), moduleLabels.getOrDefault(e.moduleId(), "?") + " › " + e.label());
        }
        target.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(EntityDef e) { return e == null ? "" : entityNames.get(e.id()); }
            @Override public EntityDef fromString(String s) { return null; }
        });
        if (existing != null && existing.lookupEntityId() != null) for (EntityDef e : target.getItems()) if (e.id() == existing.lookupEntityId()) target.setValue(e);
        target.setDisable(existing != null);
        TextField formula = new TextField(existing == null || existing.formula() == null ? "" : defs.keysToLabels(entity.id(), existing.formula()));
        formula.setStyle(FIELD); formula.setPromptText("e.g. {Km} / {Litres}"); formula.setId("cmFieldFormula");
        List<String> numericNames = new ArrayList<>();
        for (FieldDef f : defs.listFields(entity.id(), false)) if (f.type().usableInFormula() && (existing == null || f.id() != existing.id())) numericNames.add("{" + f.label() + "}");
        Label formulaHint = new Label("Use field names in curly brackets, + - * / ( ), ROUND(x, 2), MIN, MAX, ABS, IF(a > b, x, y)."
                + (numericNames.isEmpty() ? "" : "\nNumeric fields you can use: " + String.join("  ", numericNames))
                + "\nTo add up records that link to this one: SUMOF(Record type, Field) or COUNTOF(Record type).");
        formulaHint.setWrapText(true);
        formulaHint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        // validation rule: numbers are bounded by value, text by length
        TextField minF = new TextField(existing == null || existing.minValue() == null ? "" : plain(existing.minValue())); minF.setStyle(FIELD); minF.setPrefWidth(110); minF.setId("cmFieldMin");
        TextField maxF = new TextField(existing == null || existing.maxValue() == null ? "" : plain(existing.maxValue())); maxF.setStyle(FIELD); maxF.setPrefWidth(110); maxF.setId("cmFieldMax");
        TextField msgF = new TextField(existing == null || existing.validationMessage() == null ? "" : existing.validationMessage()); msgF.setStyle(FIELD);
        msgF.setPromptText("Message shown when it's outside the range (optional)"); msgF.setId("cmFieldMsg");
        Label rulesTitle = lbl("Allowed values");
        VBox rulesBox = new VBox(6, rulesTitle, new HBox(8, new Label("from"), minF, new Label("to"), maxF), msgF);

        VBox choicesBox = new VBox(4, lbl("Options"), choices);
        VBox targetBox = new VBox(4, lbl("Links to"), target);
        VBox formulaBox = new VBox(4, lbl("Formula"), formula, formulaHint);
        VBox plainBox = new VBox(8, lbl("Unit"), unit, lbl("Default value"), def);
        Runnable toggle = () -> {
            FieldType t = type.getValue();
            choicesBox.setVisible(t == FieldType.CHOICE); choicesBox.setManaged(t == FieldType.CHOICE);
            targetBox.setVisible(t == FieldType.LOOKUP); targetBox.setManaged(t == FieldType.LOOKUP);
            formulaBox.setVisible(t == FieldType.CALC); formulaBox.setManaged(t == FieldType.CALC);
            boolean plain = t != FieldType.CALC && t != FieldType.LOOKUP && t != FieldType.BOOLEAN;
            plainBox.setVisible(plain); plainBox.setManaged(plain);
            required.setDisable(t == FieldType.CALC);
            boolean range = t.isNumeric() || t == FieldType.TEXT || t == FieldType.LONG_TEXT;
            rulesBox.setVisible(range); rulesBox.setManaged(range);
            rulesTitle.setText(t.isNumeric() ? "Allowed values (leave blank for no limit)" : "Allowed length in characters (leave blank for no limit)");
        };
        type.valueProperty().addListener((o, a, b) -> toggle.run());
        toggle.run();

        VBox form = new VBox(8, lbl("Field name"), label, lbl("Type"), type, choicesBox, targetBox, formulaBox, plainBox, rulesBox, required, showInList, searchable);
        form.setPadding(new Insets(16));
        ScrollPane sp = new ScrollPane(form);
        sp.setFitToWidth(true);
        sp.setPrefViewportHeight(480);
        sp.setStyle("-fx-background-color: white;");
        d.getDialogPane().setContent(sp);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                FieldType t = type.getValue();
                List<String> opts = new ArrayList<>();
                for (String line : choices.getText().split("\n")) if (!line.isBlank()) opts.add(line.trim());
                String storedFormula = t == FieldType.CALC ? defs.labelsToKeys(entity.id(), formula.getText().trim()) : null;
                String defVal = def.getText().isBlank() ? null : def.getText().trim();
                String unitVal = unit.getText().isBlank() ? null : unit.getText().trim();
                Double minV = parseLimit(minF.getText()), maxV = parseLimit(maxF.getText());
                String msgV = msgF.getText().isBlank() ? null : msgF.getText().trim();
                if (existing == null) {
                    defs.addField(new FieldDef(0, entity.id(), label.getText(), t, required.isSelected(), defVal, opts,
                            t == FieldType.LOOKUP && target.getValue() != null ? target.getValue().id() : null,
                            storedFormula, unitVal, showInList.isSelected(), searchable.isSelected(), 0, false, minV, maxV, msgV));
                } else {
                    defs.updateField(existing.id(), label.getText(), required.isSelected(), defVal, opts, storedFormula, unitVal,
                            showInList.isSelected(), searchable.isSelected(), minV, maxV, msgV);
                }
                changed = true;
            } catch (CustomModuleException ex) { error(ex.getMessage()); ev.consume(); }
        });
        if (d.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) showModule(module.id());
    }

    // ── small helpers ─────────────────────────────────────────────────────

    private static String plain(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }

    private static Double parseLimit(String s) throws CustomModuleException {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim().replace(",", "")); }
        catch (NumberFormatException e) { throw new CustomModuleException("The allowed range needs numbers (or leave it blank)."); }
    }

    private VBox card() {
        VBox c = new VBox(10);
        c.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 18;");
        return c;
    }
    private Label sectionTitle(String t) { Label l = new Label(t); l.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;"); return l; }
    private Label lbl(String t) { Label l = new Label(t); l.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;"); return l; }

    private boolean confirm(String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
        com.kaziflow.utils.DialogUtils.makeResizable(a);
        a.setHeaderText(null);
        return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void error(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        com.kaziflow.utils.DialogUtils.makeResizable(a);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
