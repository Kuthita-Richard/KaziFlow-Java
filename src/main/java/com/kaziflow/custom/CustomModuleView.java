package com.kaziflow.custom;

import com.kaziflow.custom.CustomModuleService.CustomModuleException;
import com.kaziflow.custom.CustomModuleService.EntityDef;
import com.kaziflow.custom.CustomModuleService.FieldDef;
import com.kaziflow.custom.CustomModuleService.ModuleDef;
import com.kaziflow.custom.CustomRecordService.LookupOption;
import com.kaziflow.custom.CustomRecordService.RecordView;
import com.kaziflow.services.PermissionService;
import com.kaziflow.services.PermissionService.Action;
import com.kaziflow.utils.SessionManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * CustomModuleView — the ONE screen that renders ANY admin-defined module. Nothing in here knows about
 * vehicles, ambulances or transport: it reads the module's entities and fields and generates a tab per
 * entity with a searchable list (columns = the fields marked "show in list", plus totals for numeric
 * ones) and an add/edit form (one control per field, chosen by its type).
 *
 * <p>Buttons follow the signed-in user's access to THIS module (Create / Write / Delete), via the same
 * generic {@link PermissionService#can(String, Action)} check every other module uses.
 */
public class CustomModuleView {

    private static final String BTN_PRIMARY = "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 34px; -fx-padding: 0 16; -fx-cursor: hand; -fx-font-size: 13px;";
    private static final String FIELD = "-fx-pref-height: 34px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;";
    private static final DecimalFormat NUM = new DecimalFormat("#,##0.##");
    private static final DecimalFormat INT = new DecimalFormat("#,##0");
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private final VBox root = new VBox(0);
    private final CustomModuleService defs = CustomModuleService.getInstance();
    private final CustomRecordService recs = CustomRecordService.getInstance();
    private final int moduleDbId;
    private String moduleId = "";

    public CustomModuleView(int moduleDbId) {
        this.moduleDbId = moduleDbId;
        build();
    }

    public VBox getRoot() { return root; }

    private void build() {
        root.setStyle("-fx-background-color: #f8fafc;");
        ModuleDef def = defs.getModule(moduleDbId);
        if (def == null) {
            root.getChildren().add(new Label("This module no longer exists."));
            return;
        }
        moduleId = def.moduleId();

        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");
        VBox titles = new VBox(2);
        Label title = new Label(def.icon() + "  " + def.label());
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        titles.getChildren().add(title);
        if (def.description() != null && !def.description().isBlank()) {
            Label sub = new Label(def.description());
            sub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            titles.getChildren().add(sub);
        }
        bar.getChildren().add(titles);

        List<EntityDef> entities = defs.listEntities(moduleDbId);
        root.getChildren().add(bar);
        if (entities.isEmpty()) {
            Label none = new Label("Nothing has been set up in this module yet.\nAn administrator can add record types and fields in Settings → Custom Modules.");
            none.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
            none.setPadding(new Insets(40));
            root.getChildren().add(none);
            return;
        }
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (EntityDef e : entities) {
            Tab t = new Tab(e.pluralLabel() == null || e.pluralLabel().isBlank() ? e.label() : e.pluralLabel());
            t.setContent(new EntityPane(e).root);
            tabs.getTabs().add(t);
        }
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.getChildren().add(tabs);
    }

    // ── one tab: list + toolbar + totals ──────────────────────────────────

    private final class EntityPane {
        final VBox root = new VBox(12);
        final EntityDef entity;
        final TableView<RecordView> table = new TableView<>();
        final TextField search = new TextField();
        final Label totals = new Label();
        final Label count = new Label();
        List<FieldDef> fields;
        Map<Integer, Map<Long, String>> lookupTitles = new HashMap<>();

        EntityPane(EntityDef entity) {
            this.entity = entity;
            root.setPadding(new Insets(20, 24, 20, 24));
            PermissionService ps = PermissionService.getInstance();

            search.setPromptText("Search " + entity.label().toLowerCase() + "...");
            search.setPrefWidth(280);
            search.textProperty().addListener((o, a, b) -> refresh());
            Button add = new Button("+ New " + entity.label());
            add.setStyle(BTN_PRIMARY);
            add.setDisable(!ps.can(moduleId, Action.CREATE));
            add.setOnAction(e -> openForm(null));
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            count.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            HBox toolbar = new HBox(12, search, count, sp, add);
            toolbar.setAlignment(Pos.CENTER_LEFT);

            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            table.setPlaceholder(new Label("No " + entity.label().toLowerCase() + " records yet."));
            VBox.setVgrow(table, Priority.ALWAYS);
            totals.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
            totals.setWrapText(true);

            root.getChildren().addAll(toolbar, table, totals);
            rebuildColumns();
            refresh();
        }

        void rebuildColumns() {
            fields = defs.listFields(entity.id(), false);
            table.getColumns().clear();
            for (FieldDef f : fields) {
                if (!f.showInList()) continue;
                TableColumn<RecordView, String> col = new TableColumn<>(f.label() + (f.unit() == null || f.unit().isBlank() ? "" : " (" + f.unit() + ")"));
                col.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(display(f, d.getValue().values().get(f.key()))));
                if (f.type().isNumeric() || f.type() == FieldType.CALC) col.setStyle("-fx-alignment: CENTER-RIGHT;");
                table.getColumns().add(col);
            }
            PermissionService ps = PermissionService.getInstance();
            TableColumn<RecordView, Void> actions = new TableColumn<>("");
            actions.setMaxWidth(90); actions.setMinWidth(90);
            actions.setCellFactory(c -> new TableCell<>() {
                private final Button edit = new Button("✎");
                private final Button del = new Button("🗑");
                private final HBox box = new HBox(6, edit, del);
                {
                    for (Button b : new Button[]{edit, del})
                        b.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-cursor: hand; -fx-font-size: 13px; -fx-padding: 2;");
                    edit.setDisable(!ps.can(moduleId, Action.WRITE));
                    del.setDisable(!ps.can(moduleId, Action.DELETE));
                    edit.setOnAction(e -> openForm(getTableView().getItems().get(getIndex())));
                    del.setOnAction(e -> confirmDelete(getTableView().getItems().get(getIndex())));
                }
                @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v, empty); setGraphic(empty ? null : box); }
            });
            table.getColumns().add(actions);
        }

        void refresh() {
            lookupTitles = new HashMap<>();
            for (FieldDef f : fields) {
                if (f.type() == FieldType.LOOKUP && f.lookupEntityId() != null) {
                    Map<Long, String> titles = new HashMap<>();
                    for (LookupOption o : recs.lookupOptions(f.lookupEntityId())) titles.put(o.id(), o.title());
                    lookupTitles.put(f.id(), titles);
                }
            }
            List<RecordView> rows = recs.list(entity.id(), search.getText(), 1000);
            table.getItems().setAll(rows);
            count.setText(rows.size() + (rows.size() == 1 ? " record" : " records"));
            StringBuilder sb = new StringBuilder();
            for (FieldDef f : fields) {
                if (!f.showInList() || !f.type().isNumeric()) continue;
                double sum = 0;
                for (RecordView r : rows) if (r.values().get(f.key()) instanceof Number n) sum += n.doubleValue();
                if (sb.length() > 0) sb.append("   ·   ");
                sb.append("Total ").append(f.label()).append(": ").append(format(f, sum));
            }
            totals.setText(sb.toString());
            totals.setVisible(sb.length() > 0);
            totals.setManaged(sb.length() > 0);
        }

        String display(FieldDef f, Object v) {
            if (v == null) return "";
            return switch (f.type()) {
                case BOOLEAN -> ((Boolean) v) ? "Yes" : "No";
                case LOOKUP -> lookupTitles.getOrDefault(f.id(), Map.of()).getOrDefault(((Number) v).longValue(), "(deleted)");
                case INTEGER, DECIMAL, MONEY, CALC -> format(f, ((Number) v).doubleValue());
                default -> v.toString();
            };
        }

        String format(FieldDef f, double d) {
            return switch (f.type()) {
                case INTEGER -> INT.format(d);
                case MONEY -> "KES " + MONEY.format(d);
                default -> NUM.format(d);
            };
        }

        void confirmDelete(RecordView r) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete this " + entity.label().toLowerCase() + " record?", ButtonType.OK, ButtonType.CANCEL);
            com.kaziflow.utils.DialogUtils.makeResizable(a);
            a.setHeaderText(null);
            if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            try { recs.delete(r.id()); refresh(); }
            catch (CustomModuleException ex) { error(ex.getMessage()); }
        }

        // ── generated form ────────────────────────────────────────────────

        void openForm(RecordView existing) {
            Dialog<ButtonType> d = new Dialog<>();
            com.kaziflow.utils.DialogUtils.makeResizable(d);
            d.setTitle((existing == null ? "New " : "Edit ") + entity.label());
            d.setHeaderText(null);
            d.getDialogPane().setStyle("-fx-background-color: white;");
            d.getDialogPane().setPrefWidth(480);

            GridPane form = new GridPane();
            form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));
            Map<String, Supplier<Object>> readers = new LinkedHashMap<>();
            int row = 0;
            for (FieldDef f : fields) {
                Label lbl = new Label(f.label() + (f.required() ? " *" : "") + (f.unit() == null || f.unit().isBlank() ? "" : " (" + f.unit() + ")"));
                lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
                Object current = existing == null ? null : existing.values().get(f.key());
                javafx.scene.Node control;
                switch (f.type()) {
                    case LONG_TEXT -> {
                        TextArea ta = new TextArea(current == null ? "" : current.toString());
                        ta.setPrefRowCount(3); ta.setWrapText(true);
                        control = ta; readers.put(f.key(), ta::getText);
                    }
                    case DATE -> {
                        DatePicker dp = new DatePicker(current == null ? (existing == null && "today".equalsIgnoreCase(f.defaultValue()) ? LocalDate.now() : null) : LocalDate.parse(current.toString()));
                        dp.setPrefWidth(280);
                        control = dp; readers.put(f.key(), () -> dp.getValue() == null ? null : dp.getValue().toString());
                    }
                    case BOOLEAN -> {
                        CheckBox cb = new CheckBox();
                        cb.setSelected(current instanceof Boolean b ? b : "true".equalsIgnoreCase(f.defaultValue()));
                        control = cb; readers.put(f.key(), cb::isSelected);
                    }
                    case CHOICE -> {
                        ComboBox<String> cb = new ComboBox<>();
                        if (!f.required()) cb.getItems().add("");
                        cb.getItems().addAll(f.choices());
                        cb.setValue(current != null ? current.toString() : (existing == null && f.defaultValue() != null && f.choices().contains(f.defaultValue()) ? f.defaultValue() : (f.required() ? null : "")));
                        cb.setPrefWidth(280);
                        control = cb; readers.put(f.key(), cb::getValue);
                    }
                    case LOOKUP -> {
                        ComboBox<Object> cb = new ComboBox<>();
                        List<LookupOption> opts = recs.lookupOptions(f.lookupEntityId());
                        if (!f.required()) cb.getItems().add("");
                        cb.getItems().addAll(opts);
                        if (current != null) for (LookupOption o : opts) if (o.id() == ((Number) current).longValue()) cb.setValue(o);
                        cb.setPrefWidth(280);
                        cb.setPromptText(opts.isEmpty() ? "Nothing to choose from yet" : "Choose...");
                        control = cb; readers.put(f.key(), () -> cb.getValue() instanceof LookupOption o ? o : null);
                    }
                    case CALC -> {
                        Label val = new Label(existing == null ? "(worked out automatically)" : display(f, current));
                        val.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
                        control = val;
                    }
                    default -> {
                        TextField tf = new TextField(current == null ? "" : (current instanceof Double dv ? stripZeros(dv) : current.toString()));
                        if (f.type() == FieldType.DATETIME) tf.setPromptText("yyyy-mm-ddThh:mm");
                        if (f.type().isNumeric()) tf.setPromptText("0");
                        tf.setStyle(FIELD); tf.setPrefWidth(280);
                        control = tf; readers.put(f.key(), tf::getText);
                    }
                }
                form.add(lbl, 0, row);
                form.add(control, 1, row++);
            }
            ScrollPane sp = new ScrollPane(form);
            sp.setFitToWidth(true);
            sp.setPrefViewportHeight(Math.min(440, 90 + fields.size() * 46));
            sp.setStyle("-fx-background-color: white;");
            d.getDialogPane().setContent(sp);
            d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            // Save inside the OK handler so a validation message leaves the dialog (and the typed data) on screen.
            Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
            ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                Map<String, Object> input = new HashMap<>();
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
            if (d.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) refresh();
        }
    }

    private static String stripZeros(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    private void error(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        com.kaziflow.utils.DialogUtils.makeResizable(a);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
