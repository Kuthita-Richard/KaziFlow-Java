package com.kaziflow.custom;

import com.kaziflow.custom.CustomModuleService.CustomModuleException;
import com.kaziflow.custom.CustomModuleService.EntityDef;
import com.kaziflow.custom.CustomModuleService.FieldDef;
import com.kaziflow.custom.CustomModuleService.ModuleDef;
import com.kaziflow.custom.CustomQueryService.*;
import com.kaziflow.custom.CustomRecordService.RecordView;
import com.kaziflow.services.PermissionService;
import com.kaziflow.services.PermissionService.Action;
import com.kaziflow.utils.SessionManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;

/**
 * CustomModuleView — the ONE screen that renders ANY admin-defined module. Nothing in here knows about
 * vehicles, ambulances or transport: it reads the module's entities and fields and generates a tab per
 * entity with
 * <ul>
 *   <li>a searchable list (columns = the fields marked "show in list") with totals over ALL matching records;</li>
 *   <li>saved <b>views</b> and point-and-click <b>reports</b> (filter, sort, group, total) — see
 *       {@link CustomViewEditorDialog};</li>
 *   <li><b>CSV export</b>, a printable report page, and <b>CSV import</b> with a preview before anything is written;</li>
 *   <li>an add/edit form with <b>subforms</b> ({@link CustomRecordForm}).</li>
 * </ul>
 * Buttons follow the signed-in user's Create / Write / Delete access to THIS module via the same generic
 * {@link PermissionService#can(String, Action)} check every other module uses.
 */
public class CustomModuleView {

    private static final String BTN_PRIMARY = "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 34px; -fx-padding: 0 16; -fx-cursor: hand; -fx-font-size: 13px;";
    private static final String BTN_PLAIN = "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-pref-height: 34px; -fx-padding: 0 12; -fx-cursor: hand; -fx-font-size: 12px;";

    private final VBox root = new VBox(0);
    private final CustomModuleService defs = CustomModuleService.getInstance();
    private final CustomRecordService recs = CustomRecordService.getInstance();
    private final CustomQueryService query = CustomQueryService.getInstance();
    private final int moduleDbId;
    private String moduleId = "";
    private final List<EntityPane> panes = new ArrayList<>();
    private TabPane tabs;

    public CustomModuleView(int moduleDbId) {
        this.moduleDbId = moduleDbId;
        build();
    }

    public VBox getRoot() { return root; }

    /** Applies a view to a record type's tab — used by tests and available to callers that deep-link into a report. */
    public void applyView(int tabIndex, ViewDef def) { panes.get(tabIndex).apply(def, null); }

    public int tabCount() { return panes.size(); }

    /** Shows the tab for the record type at {@code index}. */
    public void selectTab(int index) { if (tabs != null) tabs.getSelectionModel().select(index); }

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
        root.getChildren().add(bar);

        List<EntityDef> entities = defs.listEntities(moduleDbId);
        if (entities.isEmpty()) {
            Label none = new Label("Nothing has been set up in this module yet.\nAn administrator can add record types and fields in Settings → Custom Modules.");
            none.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
            none.setPadding(new Insets(40));
            root.getChildren().add(none);
            return;
        }
        tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (EntityDef e : entities) {
            EntityPane p = new EntityPane(e);
            panes.add(p);
            Tab t = new Tab(e.pluralLabel() == null || e.pluralLabel().isBlank() ? e.label() : e.pluralLabel());
            t.setContent(p.root);
            tabs.getTabs().add(t);
        }
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.getChildren().add(tabs);
    }

    // ── one tab: toolbar + list/report + totals ───────────────────────────

    private final class EntityPane {
        final VBox root = new VBox(12);
        final EntityDef entity;
        final TableView<RecordView> table = new TableView<>();
        final TableView<GroupRow> groupTable = new TableView<>();
        final StackPane tableHolder = new StackPane();
        final TextField search = new TextField();
        final Label totals = new Label();
        final Label count = new Label();
        final ComboBox<Object> viewBox = new ComboBox<>();
        final PermissionService ps = PermissionService.getInstance();
        List<FieldDef> fields;
        ViewDef currentDef = ViewDef.ALL;
        SavedView currentSaved = null;
        boolean updatingViewBox = false;
        // what's on screen, so export / report page reuse the exact same result
        List<RecordView> shownRows = List.of();
        GroupResult shownGroup = null;

        EntityPane(EntityDef entity) {
            this.entity = entity;
            root.setPadding(new Insets(20, 24, 20, 24));

            search.setPromptText("Search " + entity.label().toLowerCase() + "...");
            search.setPrefWidth(240);
            search.textProperty().addListener((o, a, b) -> refresh());

            viewBox.setId("view-box:" + entity.id());
            viewBox.setPrefWidth(210);
            viewBox.valueProperty().addListener((o, a, b) -> {
                if (updatingViewBox) return;
                if (b instanceof SavedView sv) apply(sv.def(), sv); else apply(ViewDef.ALL, null);
            });
            Button customize = button("Customize view…", BTN_PLAIN, "customize:" + entity.id());
            customize.setOnAction(e -> CustomViewEditorDialog.show(entity, currentDef, currentSaved, ps.can(moduleId, Action.WRITE),
                    (def, saved) -> apply(def, saved)));
            Button export = button("Export CSV", BTN_PLAIN, "export:" + entity.id());
            export.setOnAction(e -> exportCsv());
            Button report = button("Report page", BTN_PLAIN, "report:" + entity.id());
            report.setOnAction(e -> openReportPage());
            Button imp = button("Import CSV", BTN_PLAIN, "import:" + entity.id());
            imp.setDisable(!ps.can(moduleId, Action.CREATE));
            imp.setOnAction(e -> importCsv());
            Button add = button("+ New " + entity.label(), BTN_PRIMARY, "new:" + entity.id());
            add.setDisable(!ps.can(moduleId, Action.CREATE));
            add.setOnAction(e -> CustomRecordForm.open(entity, null, Map.of(), Set.of(), this::refresh));

            count.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            FlowPane toolbar = new FlowPane(10, 8, search, viewBox, customize, export, report, imp, add);
            toolbar.setAlignment(Pos.CENTER_LEFT);

            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            table.setPlaceholder(new Label("No " + entity.label().toLowerCase() + " records yet."));
            groupTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            groupTable.setPlaceholder(new Label("Nothing matches."));
            groupTable.setVisible(false);
            tableHolder.getChildren().addAll(table, groupTable);
            VBox.setVgrow(tableHolder, Priority.ALWAYS);
            totals.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
            totals.setWrapText(true);

            root.getChildren().addAll(toolbar, count, tableHolder, totals);
            rebuildColumns();
            reloadViews();
            refresh();
        }

        Button button(String text, String style, String id) {
            Button b = new Button(text);
            b.setStyle(style);
            b.setId(id);
            return b;
        }

        // ── views ─────────────────────────────────────────────────────────

        void reloadViews() {
            updatingViewBox = true;
            viewBox.getItems().setAll("All records");
            viewBox.getItems().addAll(query.listViews(entity.id()));
            Object shown = "All records";
            if (currentSaved != null) {
                for (Object o : viewBox.getItems()) if (o instanceof SavedView sv && sv.id() == currentSaved.id()) shown = sv;
            } else if (isCustom()) shown = "(customized)";
            viewBox.setValue(shown);
            updatingViewBox = false;
        }

        boolean isCustom() { return currentSaved == null && !currentDef.equals(ViewDef.ALL); }

        void apply(ViewDef def, SavedView saved) {
            currentDef = def;
            currentSaved = saved;
            reloadViews();
            refresh();
        }

        // ── columns for the plain list ────────────────────────────────────

        void rebuildColumns() {
            fields = defs.listFields(entity.id(), false);
            table.getColumns().clear();
            Map<Integer, Map<Long, String>> titles = new HashMap<>();
            for (FieldDef f : fields) {
                if (!f.showInList()) continue;
                if (f.type() == FieldType.LOOKUP) titles.put(f.id(), CustomFormat.titles(f));
                TableColumn<RecordView, String> col = new TableColumn<>(f.label() + (f.unit() == null || f.unit().isBlank() ? "" : " (" + f.unit() + ")"));
                col.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                        CustomFormat.display(f, d.getValue().values().get(f.key()), titlesFor(f, titles))));
                if (f.type().isNumeric() || f.type() == FieldType.CALC) col.setStyle("-fx-alignment: CENTER-RIGHT;");
                table.getColumns().add(col);
            }
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
                    edit.setOnAction(e -> CustomRecordForm.open(entity, getTableView().getItems().get(getIndex()), Map.of(), Set.of(), EntityPane.this::refresh));
                    del.setOnAction(e -> confirmDelete(getTableView().getItems().get(getIndex())));
                }
                @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v, empty); setGraphic(empty ? null : box); }
            });
            table.getColumns().add(actions);
        }

        Map<Long, String> titlesFor(FieldDef f, Map<Integer, Map<Long, String>> cache) {
            return cache.getOrDefault(f.id(), Map.of());
        }

        // ── refreshing what's shown ───────────────────────────────────────

        void refresh() {
            try {
                if (currentDef.isGrouped()) showGroup(query.group(entity.id(), currentDef));
                else showList(query.list(entity.id(), currentDef, search.getText(), 1000));
            } catch (CustomModuleException ex) {
                // e.g. a saved view that refers to a field archived since — fall back rather than leave a blank screen
                error(ex.getMessage());
                currentDef = ViewDef.ALL; currentSaved = null;
                reloadViews();
                try { showList(query.list(entity.id(), ViewDef.ALL, search.getText(), 1000)); } catch (CustomModuleException ignored) { }
            }
        }

        void showList(ListResult lr) {
            rebuildColumns();                    // fields may have been added or archived since the tab was built
            shownRows = lr.rows();
            shownGroup = null;
            groupTable.setVisible(false); table.setVisible(true);
            search.setDisable(false);
            table.getItems().setAll(lr.rows());
            count.setText(lr.rows().size() < lr.totalMatching()
                    ? "Showing the newest " + lr.rows().size() + " of " + lr.totalMatching() + " records (export to get them all)"
                    : lr.totalMatching() + (lr.totalMatching() == 1 ? " record" : " records"));
            StringBuilder sb = new StringBuilder();
            for (FieldDef f : fields) {
                if (!f.showInList() || !f.type().isNumeric()) continue;
                Double sum = lr.totals().get(f.key());
                if (sum == null) continue;
                if (sb.length() > 0) sb.append("   ·   ");
                sb.append("Total ").append(f.label()).append(": ").append(CustomFormat.number(f, sum));
            }
            totals.setText(sb.toString());
            totals.setVisible(sb.length() > 0); totals.setManaged(sb.length() > 0);
        }

        void showGroup(GroupResult gr) {
            shownGroup = gr;
            shownRows = List.of();
            Map<String, FieldDef> byKey = new HashMap<>();
            for (FieldDef f : fields) byKey.put(f.key(), f);
            FieldDef g = byKey.get(currentDef.groupBy());
            String groupTitle = (g == null ? "Group" : g.label())
                    + (currentDef.bucket() == Bucket.MONTH ? " (by month)" : currentDef.bucket() == Bucket.YEAR ? " (by year)" : "");
            groupTable.getColumns().clear();
            TableColumn<GroupRow, String> c0 = new TableColumn<>(groupTitle);
            c0.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().label()));
            TableColumn<GroupRow, String> c1 = new TableColumn<>("Records");
            c1.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.valueOf(d.getValue().count())));
            c1.setStyle("-fx-alignment: CENTER-RIGHT;");
            groupTable.getColumns().addAll(c0, c1);
            for (Aggregate a : gr.aggregates()) {
                if (a.fn() == Agg.COUNT) continue;
                FieldDef f = byKey.get(a.field());
                String key = a.fn().name() + ":" + a.field();
                TableColumn<GroupRow, String> c = new TableColumn<>(a.fn().label() + " " + (f == null ? a.field() : f.label()));
                c.setCellValueFactory(d -> {
                    Double v = d.getValue().values().get(key);
                    return new javafx.beans.property.SimpleStringProperty(v == null || f == null ? "" : CustomFormat.number(f, v));
                });
                c.setStyle("-fx-alignment: CENTER-RIGHT;");
                groupTable.getColumns().add(c);
            }
            List<GroupRow> items = new ArrayList<>(gr.rows());
            items.add(new GroupRow(null, "TOTAL", gr.totalCount(), gr.totals()));
            groupTable.getItems().setAll(items);
            table.setVisible(false); groupTable.setVisible(true);
            search.setDisable(true);
            count.setText(gr.rows().size() + (gr.rows().size() == 1 ? " group" : " groups") + " · " + gr.totalCount() + " records");
            totals.setVisible(false); totals.setManaged(false);
        }

        void confirmDelete(RecordView r) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete this " + entity.label().toLowerCase() + " record?", ButtonType.OK, ButtonType.CANCEL);
            com.kaziflow.utils.DialogUtils.makeResizable(a);
            a.setHeaderText(null);
            if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            try { recs.delete(r.id()); refresh(); }
            catch (CustomModuleException ex) { error(ex.getMessage()); }
        }

        // ── export / report page ──────────────────────────────────────────

        void exportCsv() {
            try {
                String csv;
                if (currentDef.isGrouped()) {
                    FieldDef g = fields.stream().filter(f -> f.key().equals(currentDef.groupBy())).findFirst().orElse(null);
                    csv = CustomCsv.exportGroup(entity.id(), g == null ? "Group" : g.label(), query.group(entity.id(), currentDef));
                } else {
                    // every matching record, not just the 1,000 shown
                    csv = CustomCsv.exportList(entity.id(), query.list(entity.id(), currentDef, search.getText(), 20000).rows(), false);
                }
                FileChooser fc = new FileChooser();
                fc.setTitle("Export to CSV");
                fc.setInitialFileName(CustomModuleService.slug(entity.label()) + "-" + LocalDate.now() + ".csv");
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (opens in Excel)", "*.csv"));
                File f = fc.showSaveDialog(root.getScene() == null ? null : root.getScene().getWindow());
                if (f == null) return;
                // UTF-8 with a BOM, so Excel reads accents and symbols correctly
                Files.write(f.toPath(), (CustomCsv.BOM + csv).getBytes(StandardCharsets.UTF_8));
                info("Saved " + f.getName());
            } catch (Exception ex) { error("Could not export: " + ex.getMessage()); }
        }

        void openReportPage() {
            try {
                String html;
                String subtitle = describe(currentDef);
                if (currentDef.isGrouped()) {
                    GroupResult gr = query.group(entity.id(), currentDef);
                    Map<String, FieldDef> byKey = new HashMap<>();
                    for (FieldDef f : fields) byKey.put(f.key(), f);
                    List<String> headers = new ArrayList<>(List.of(byKey.get(currentDef.groupBy()).label(), "Records"));
                    List<Boolean> right = new ArrayList<>(List.of(false, true));
                    for (Aggregate a : gr.aggregates()) if (a.fn() != Agg.COUNT) { headers.add(a.fn().label() + " " + byKey.get(a.field()).label()); right.add(true); }
                    List<List<String>> rows = new ArrayList<>();
                    for (GroupRow g : gr.rows()) {
                        List<String> line = new ArrayList<>(List.of(g.label(), String.valueOf(g.count())));
                        for (Aggregate a : gr.aggregates()) if (a.fn() != Agg.COUNT) line.add(CustomFormat.number(byKey.get(a.field()), g.values().getOrDefault(a.fn().name() + ":" + a.field(), 0.0)));
                        rows.add(line);
                    }
                    List<String> total = new ArrayList<>(List.of("TOTAL", String.valueOf(gr.totalCount())));
                    for (Aggregate a : gr.aggregates()) if (a.fn() != Agg.COUNT) total.add(CustomFormat.number(byKey.get(a.field()), gr.totals().getOrDefault(a.fn().name() + ":" + a.field(), 0.0)));
                    html = CustomReportHtml.render(defs.businessName(), entity.pluralLabel() + " — report", subtitle, headers, rows, total, right);
                } else {
                    ListResult lr = query.list(entity.id(), currentDef, search.getText(), 2000);
                    List<FieldDef> shown = fields.stream().filter(FieldDef::showInList).toList();
                    List<String> headers = shown.stream().map(FieldDef::label).toList();
                    List<Boolean> right = shown.stream().map(f -> f.type().isNumeric() || f.type() == FieldType.CALC).toList();
                    Map<Integer, Map<Long, String>> titles = new HashMap<>();
                    for (FieldDef f : shown) if (f.type() == FieldType.LOOKUP) titles.put(f.id(), CustomFormat.titles(f));
                    List<List<String>> rows = new ArrayList<>();
                    for (RecordView r : lr.rows()) {
                        List<String> line = new ArrayList<>();
                        for (FieldDef f : shown) line.add(CustomFormat.display(f, r.values().get(f.key()), titles.getOrDefault(f.id(), Map.of())));
                        rows.add(line);
                    }
                    List<String> total = null;
                    if (shown.stream().anyMatch(f -> f.type().isNumeric())) {
                        total = new ArrayList<>();
                        for (int i = 0; i < shown.size(); i++) {
                            FieldDef f = shown.get(i);
                            Double sum = f.type().isNumeric() ? lr.totals().get(f.key()) : null;
                            total.add(i == 0 ? "TOTAL" : (sum == null ? "" : CustomFormat.number(f, sum)));
                        }
                    }
                    String sub = subtitle + (lr.rows().size() < lr.totalMatching() ? " — first " + lr.rows().size() + " of " + lr.totalMatching() + " records" : "");
                    html = CustomReportHtml.render(defs.businessName(), entity.pluralLabel(), sub, headers, rows, total, right);
                }
                File out = File.createTempFile("kaziflow-report-", ".html");
                out.deleteOnExit();
                Files.writeString(out.toPath(), html, StandardCharsets.UTF_8);
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(out);
                else info("Report saved to " + out.getAbsolutePath());
            } catch (Exception ex) { error("Could not open the report: " + ex.getMessage()); }
        }

        /** A plain-English line under the report title: which view, search and date. */
        String describe(ViewDef def) {
            StringBuilder sb = new StringBuilder(currentSaved != null ? currentSaved.name() : (def.equals(ViewDef.ALL) ? "All records" : "Custom view"));
            if (!search.getText().isBlank() && !def.isGrouped()) sb.append(" · search: \"").append(search.getText().trim()).append("\"");
            return sb.toString();
        }

        // ── import ────────────────────────────────────────────────────────

        void importCsv() {
            FileChooser fc = new FileChooser();
            fc.setTitle("Import " + entity.pluralLabel() + " from CSV");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV / Excel-saved-as-CSV", "*.csv", "*.txt"));
            File f = fc.showOpenDialog(root.getScene() == null ? null : root.getScene().getWindow());
            if (f == null) return;
            try {
                String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                CustomCsv.ImportPlan plan = CustomCsv.plan(entity.id(), text);
                showImportPreview(f.getName(), plan);
            } catch (CustomModuleException ex) {
                error(ex.getMessage());
            } catch (java.nio.charset.MalformedInputException ex) {
                error("That file isn't UTF-8 text. In Excel use Save As → \"CSV UTF-8 (Comma delimited)\".");
            } catch (Exception ex) { error("Could not read the file: " + ex.getMessage()); }
        }

        void showImportPreview(String fileName, CustomCsv.ImportPlan plan) {
            Dialog<ButtonType> d = new Dialog<>();
            com.kaziflow.utils.DialogUtils.makeResizable(d);
            d.setTitle("Import preview — " + fileName);
            d.setHeaderText(null);
            d.getDialogPane().setStyle("-fx-background-color: white;");
            d.getDialogPane().setPrefWidth(620);

            VBox body = new VBox(10);
            body.setPadding(new Insets(16));
            Label summary = new Label(plan.validCount() + " row(s) are ready to import" + (plan.errorCount() > 0 ? ", " + plan.errorCount() + " have problems and will be skipped." : "."));
            summary.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + (plan.errorCount() > 0 ? "#b45309" : "#16a34a") + ";");
            body.getChildren().add(summary);
            body.getChildren().add(grey("Columns used: " + (plan.mappedColumns().isEmpty() ? "none" : String.join(", ", plan.mappedColumns()))));
            if (!plan.ignoredColumns().isEmpty()) body.getChildren().add(grey("Columns ignored (no matching field): " + String.join(", ", plan.ignoredColumns())));
            for (String p : plan.problems()) {
                Label l = new Label("⚠ " + p);
                l.setWrapText(true);
                l.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
                body.getChildren().add(l);
            }
            if (plan.errorCount() > 0) {
                TextArea errs = new TextArea();
                errs.setEditable(false); errs.setPrefRowCount(8);
                StringBuilder sb = new StringBuilder();
                for (CustomCsv.RowPlan r : plan.rows()) if (r.error() != null) sb.append("Row ").append(r.rowNumber()).append(": ").append(r.error()).append('\n');
                errs.setText(sb.toString());
                body.getChildren().addAll(new Label("Problems (row numbers match your spreadsheet):"), errs);
            }
            body.getChildren().add(grey("Importing the same file twice creates the records twice."));
            d.getDialogPane().setContent(body);
            ButtonType go = new ButtonType("Import " + plan.validCount() + " row(s)", ButtonBar.ButtonData.OK_DONE);
            d.getDialogPane().getButtonTypes().addAll(go, ButtonType.CANCEL);
            d.getDialogPane().lookupButton(go).setDisable(!plan.importable());
            if (d.showAndWait().orElse(ButtonType.CANCEL) != go) return;
            try {
                var user = SessionManager.getInstance().getCurrentUser();
                int n = CustomCsv.commit(entity.id(), plan, user == null ? 0 : user.getId());
                refresh();
                info("Imported " + n + " record(s).");
            } catch (CustomModuleException ex) { error(ex.getMessage()); }
        }
    }

    // ── small helpers ─────────────────────────────────────────────────────

    private static Label grey(String t) {
        Label l = new Label(t);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        return l;
    }

    private void info(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        com.kaziflow.utils.DialogUtils.makeResizable(a);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private void error(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        com.kaziflow.utils.DialogUtils.makeResizable(a);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
