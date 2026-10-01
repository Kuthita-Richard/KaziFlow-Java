package com.kaziflow.views;

import com.kaziflow.modules.Module;
import com.kaziflow.modules.ModuleRegistry;
import com.kaziflow.services.PermissionAdminService;
import com.kaziflow.services.PermissionAdminService.LockoutException;
import com.kaziflow.services.PermissionAdminService.ModuleGrant;
import com.kaziflow.services.PermissionAdminService.PermissionAdminException;
import com.kaziflow.services.PermissionAdminService.Position;
import com.kaziflow.services.PermissionAdminService.PositionDetail;
import com.kaziflow.services.PermissionService;
import com.kaziflow.services.SpecialPermissions;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.util.*;

/**
 * PositionsAccessView — the "Positions & Access" editor that replaces the old read-only
 * Roles & Perms screen. A Position is a named bundle of access ("Cashier", "Branch Manager");
 * a user can hold several, and a Position can include others (inheritance).
 *
 * <h3>Nothing here is hardcoded per module or per action</h3>
 * <ul>
 *   <li>Module rows come from {@link ModuleRegistry#getAllAvailableModules()} every time the
 *       editor renders — a module added later (including one registered at run time) simply
 *       appears, with Read/Create/Write/Delete boxes, and can be granted to Positions immediately.
 *       Grants that exist for a module id the registry no longer offers are still shown (marked
 *       unavailable) rather than silently hidden.</li>
 *   <li>Special-action checkboxes come from the {@code permission_actions} table
 *       ({@link SpecialPermissions#list()}); the "Add action" button registers a new row.</li>
 * </ul>
 * All reads/writes go through {@link PermissionAdminService}, which owns validation and the
 * lockout rule; this class only presents and collects.
 */
public class PositionsAccessView {

    private static final String[] ACTION_LABELS = {"Read", "Create", "Write", "Delete"};
    private static final String BTN_PRIMARY = "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 34px; -fx-padding: 0 16; -fx-cursor: hand; -fx-font-size: 13px;";
    private static final String BTN_PLAIN = "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-pref-height: 34px; -fx-padding: 0 14; -fx-cursor: hand; -fx-font-size: 13px;";
    private static final String BTN_DANGER = "-fx-background-color: white; -fx-border-color: #fecaca; -fx-text-fill: #dc2626; -fx-border-radius: 8; -fx-background-radius: 8; -fx-pref-height: 34px; -fx-padding: 0 14; -fx-cursor: hand; -fx-font-size: 13px;";
    private static final String FIELD = "-fx-pref-height: 34px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;";

    private final PermissionAdminService svc = PermissionAdminService.getInstance();
    private final HBox root = new HBox(0);

    private final ListView<Position> list = new ListView<>();
    private final VBox editorBody = new VBox(18);
    private final Label banner = new Label();
    private final TextField nameField = new TextField();
    private final TextArea descField = new TextArea();
    private final Button saveBtn = new Button("Save changes");
    private final Button revertBtn = new Button("Revert");
    private final Button deleteBtn = new Button("Delete position");

    // per-render state
    private Integer currentId = null;          // null = a brand-new, unsaved position
    private boolean isAdminPosition = false;
    private boolean loading = false;
    private boolean dirty = false;
    private boolean editable = true;
    private final Map<String, CheckBox[]> moduleBoxes = new LinkedHashMap<>();
    private final Map<Integer, CheckBox> impliesBoxes = new LinkedHashMap<>();
    private final Map<String, CheckBox> specialBoxes = new LinkedHashMap<>();

    public PositionsAccessView() {
        editable = PermissionService.getInstance().isAdministrator()
                || PermissionService.getInstance().canWrite("settings");
        build();
        reloadList(null);
    }

    public HBox getRoot() { return root; }

    /** Opens the editor in a resizable dialog. */
    public static void showDialog(Window owner) {
        PositionsAccessView view = new PositionsAccessView();
        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        if (owner != null) d.initOwner(owner);
        d.setTitle("Positions & Access");
        d.setHeaderText(null);
        d.getDialogPane().setStyle("-fx-background-color: #f8fafc;");
        d.getDialogPane().setContent(view.getRoot());
        d.getDialogPane().setPrefSize(1040, 680);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.showAndWait();
    }

    // ── layout ────────────────────────────────────────────────────────────

    private void build() {
        root.setStyle("-fx-background-color: #f8fafc;");

        // left: positions list
        VBox left = new VBox(10);
        left.setPadding(new Insets(16));
        left.setPrefWidth(250);
        left.setMinWidth(220);
        Label title = new Label("Positions");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label hint = new Label("A position is a set of access (e.g. Cashier). Staff can hold more than one.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        Button newBtn = new Button("+ New position");
        newBtn.setId("posNew");
        newBtn.setStyle(BTN_PRIMARY);
        newBtn.setMaxWidth(Double.MAX_VALUE);
        newBtn.setDisable(!editable);
        newBtn.setOnAction(e -> startNew());

        list.setId("posList");
        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Position p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setGraphic(null); setText(null); return; }
                Label name = new Label(p.name());
                name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
                Label sub = new Label(p.memberCount() + (p.memberCount() == 1 ? " person" : " people") + (p.system() ? "  ·  built-in" : ""));
                sub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
                setGraphic(new VBox(1, name, sub));
                setText(null);
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (loading || sel == null) return;
            if (currentId != null && sel.id() == currentId) return;
            if (!confirmDiscardIfDirty()) { reselectCurrent(); return; }
            showPosition(sel.id());
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        left.getChildren().addAll(title, hint, newBtn, list);

        // right: editor
        editorBody.setPadding(new Insets(20));
        ScrollPane scroll = new ScrollPane(editorBody);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #f8fafc;");
        HBox.setHgrow(scroll, Priority.ALWAYS);

        banner.setWrapText(true);
        banner.setVisible(false);
        banner.setManaged(false);

        root.getChildren().addAll(left, new Separator(javafx.geometry.Orientation.VERTICAL), scroll);
    }

    // ── list & selection ──────────────────────────────────────────────────

    private void reloadList(Integer selectId) {
        loading = true;
        List<Position> positions = svc.listPositions();
        list.getItems().setAll(positions);
        loading = false;
        Integer target = selectId != null ? selectId : (positions.isEmpty() ? null : positions.get(0).id());
        if (target != null) {
            for (Position p : positions) if (p.id() == target) {
                loading = true; list.getSelectionModel().select(p); loading = false;
            }
            showPosition(target);
        }
    }

    private void reselectCurrent() {
        loading = true;
        for (Position p : list.getItems()) if (currentId != null && p.id() == currentId) list.getSelectionModel().select(p);
        if (currentId == null) list.getSelectionModel().clearSelection();
        loading = false;
    }

    private void startNew() {
        if (!confirmDiscardIfDirty()) return;
        loading = true; list.getSelectionModel().clearSelection(); loading = false;
        render(null);
    }

    private void showPosition(int id) {
        PositionDetail detail = svc.load(id);
        if (detail == null) { reloadList(null); return; }
        render(detail);
    }

    private boolean confirmDiscardIfDirty() {
        if (!dirty) return true;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "You have unsaved changes to this position. Discard them?", ButtonType.OK, ButtonType.CANCEL);
        com.kaziflow.utils.DialogUtils.makeResizable(a);
        a.setHeaderText(null);
        return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    // ── render the editor for one position (or a blank new one) ───────────

    private void render(PositionDetail detail) {
        loading = true;
        currentId = detail == null ? null : detail.position().id();
        isAdminPosition = detail != null && detail.position().system()
                && "Administrator".equalsIgnoreCase(detail.position().name());
        moduleBoxes.clear(); impliesBoxes.clear(); specialBoxes.clear();
        editorBody.getChildren().clear();

        Map<String, ModuleGrant> direct = detail == null ? Map.of() : detail.access();
        Map<String, ModuleGrant> inherited = currentId == null ? Map.of() : svc.inheritedAccess(currentId);
        Set<Integer> implied = detail == null ? Set.of() : detail.implied();
        Set<String> specials = detail == null ? Set.of() : detail.specials();
        Set<String> inheritedSpecials = currentId == null ? Set.of() : svc.inheritedSpecials(currentId);

        // header: name + description
        nameField.setText(detail == null ? "" : detail.position().name());
        nameField.setPromptText("Position name, e.g. Branch Manager");
        nameField.setId("posName");
        nameField.setStyle(FIELD);
        nameField.setDisable(!editable || isAdminPosition);
        descField.setText(detail == null || detail.position().description() == null ? "" : detail.position().description());
        descField.setPromptText("What is this position for? (optional)");
        descField.setPrefRowCount(2);
        descField.setWrapText(true);
        descField.setId("posDesc");
        descField.setDisable(!editable);
        nameField.textProperty().addListener((o, a, b) -> markDirty());
        descField.textProperty().addListener((o, a, b) -> markDirty());

        VBox header = card();
        header.getChildren().addAll(sectionTitle(detail == null ? "New position" : "Position"), lbl("Name"), nameField, lbl("Description"), descField);
        if (!editable) {
            setBanner("You can view positions, but changing them needs permission to manage Settings.", "#fef3c7", "#92400e");
            header.getChildren().add(0, banner);
        } else if (isAdminPosition) {
            setBanner("Administrator is built in: it always has full access to everything (including modules added in future) and can't be edited or deleted.", "#ede9fe", "#6d28d9");
            header.getChildren().add(0, banner);
        } else {
            banner.setVisible(false); banner.setManaged(false);
        }
        editorBody.getChildren().add(header);

        // inheritance
        VBox inheritCard = card();
        inheritCard.getChildren().add(sectionTitle("Also includes"));
        Label inheritHint = new Label("Everything in the positions ticked here is granted too. Access is always added together — a position can't take away what another gives.");
        inheritHint.setWrapText(true);
        inheritHint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        inheritCard.getChildren().add(inheritHint);
        FlowPane implyFlow = new FlowPane(16, 8);
        boolean anyOther = false;
        for (Position p : svc.listPositions()) {
            if (currentId != null && p.id() == currentId) continue;
            CheckBox cb = new CheckBox(p.system() ? p.name() + " (full access)" : p.name());
            cb.setId("imp:" + p.id());
            cb.setSelected(implied.contains(p.id()));
            cb.setDisable(!editable || isAdminPosition);
            cb.selectedProperty().addListener((o, a, b) -> markDirty());
            impliesBoxes.put(p.id(), cb);
            implyFlow.getChildren().add(cb);
            anyOther = true;
        }
        if (!anyOther) implyFlow.getChildren().add(grey("There are no other positions yet."));
        inheritCard.getChildren().add(implyFlow);
        editorBody.getChildren().add(inheritCard);

        // module matrix
        editorBody.getChildren().add(buildMatrixCard(direct, inherited));

        // special actions
        editorBody.getChildren().add(buildSpecialsCard(specials, inheritedSpecials));

        // footer buttons
        saveBtn.setId("posSave");
        saveBtn.setStyle(BTN_PRIMARY);
        saveBtn.setOnAction(e -> save());
        revertBtn.setId("posRevert");
        revertBtn.setStyle(BTN_PLAIN);
        revertBtn.setOnAction(e -> { if (currentId == null) { reloadList(null); } else showPosition(currentId); });
        deleteBtn.setId("posDelete");
        deleteBtn.setStyle(BTN_DANGER);
        deleteBtn.setOnAction(e -> delete());
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox footer = new HBox(10, deleteBtn, sp, revertBtn, saveBtn);
        footer.setAlignment(Pos.CENTER_LEFT);
        editorBody.getChildren().add(footer);

        boolean canEditThis = editable && !isAdminPosition;
        // a brand-new position can be saved straight away; an existing one only after a change
        saveBtn.setDisable(!(canEditThis && currentId == null));
        revertBtn.setDisable(true);
        deleteBtn.setDisable(!canEditThis || currentId == null);
        dirty = false;
        loading = false;
    }

    private VBox buildMatrixCard(Map<String, ModuleGrant> direct, Map<String, ModuleGrant> inherited) {
        VBox card = card();
        card.getChildren().add(sectionTitle("What this position can do in each module"));
        Label hint = new Label("Read lets someone open the module. Create, Write and Delete need Read. The list always shows every module the app has — including ones added later.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        card.getChildren().add(hint);

        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(6);
        grid.setPadding(new Insets(6, 0, 0, 0));
        ColumnConstraints nameCol = new ColumnConstraints();
        nameCol.setHgrow(Priority.ALWAYS);
        nameCol.setMinWidth(180);
        grid.getColumnConstraints().add(nameCol);

        // header row with "select whole column" checkboxes
        Label modHeader = new Label("MODULE");
        modHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        grid.add(modHeader, 0, 0);
        CheckBox[] colAll = new CheckBox[4];
        for (int i = 0; i < 4; i++) {
            final int col = i;
            CheckBox all = new CheckBox(ACTION_LABELS[i]);
            all.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #475569;");
            all.setDisable(!editable || isAdminPosition);
            all.setOnAction(e -> {
                for (CheckBox[] row : moduleBoxes.values()) if (!row[col].isDisabled()) row[col].setSelected(all.isSelected());
            });
            colAll[i] = all;
            grid.add(all, 1 + i, 0);
        }
        Label inhHeader = new Label("INHERITED");
        inhHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        grid.add(inhHeader, 5, 0);

        // module rows: live from the registry, plus any orphaned grants
        ModuleRegistry reg = ModuleRegistry.getInstance();
        LinkedHashMap<String, String[]> rows = new LinkedHashMap<>(); // id -> [label, note]
        for (Module m : reg.getAllAvailableModules()) {
            if (m.alwaysAccessible()) continue; // open to everyone, nothing to grant
            rows.put(m.getId(), new String[]{m.getIcon() + "  " + m.getLabel(), reg.isEnabled(m.getId()) ? "" : "not enabled for this business"});
        }
        for (String id : direct.keySet()) rows.putIfAbsent(id, new String[]{id, "module not available in this version"});
        for (String id : inherited.keySet()) rows.putIfAbsent(id, new String[]{id, "module not available in this version"});

        int r = 1;
        for (var e : rows.entrySet()) {
            String id = e.getKey();
            ModuleGrant g = direct.getOrDefault(id, ModuleGrant.NONE);
            ModuleGrant inh = inherited.getOrDefault(id, ModuleGrant.NONE);

            VBox nameBox = new VBox(0);
            Label nm = new Label(e.getValue()[0]);
            nm.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
            nameBox.getChildren().add(nm);
            if (!e.getValue()[1].isEmpty()) nameBox.getChildren().add(grey(e.getValue()[1]));
            grid.add(nameBox, 0, r);

            boolean[] vals = isAdminPosition ? new boolean[]{true, true, true, true}
                    : new boolean[]{g.read(), g.create(), g.write(), g.delete()};
            CheckBox[] boxes = new CheckBox[4];
            for (int i = 0; i < 4; i++) {
                CheckBox cb = new CheckBox();
                cb.setId("cb:" + id + ":" + ACTION_LABELS[i].charAt(0));
                cb.setSelected(vals[i]);
                cb.setDisable(!editable || isAdminPosition);
                boxes[i] = cb;
                grid.add(cb, 1 + i, r);
            }
            // Create/Write/Delete need Read; clearing Read clears them.
            for (int i = 1; i < 4; i++) {
                boxes[i].selectedProperty().addListener((o, a, b) -> { if (b && !boxes[0].isSelected()) boxes[0].setSelected(true); });
            }
            boxes[0].selectedProperty().addListener((o, a, b) -> {
                if (!b) for (int i = 1; i < 4; i++) boxes[i].setSelected(false);
            });
            for (CheckBox cb : boxes) cb.selectedProperty().addListener((o, a, b) -> markDirty());
            moduleBoxes.put(id, boxes);

            String inhText = inheritedLetters(inh);
            Label inhLbl = grey(inhText.isEmpty() ? "—" : inhText);
            grid.add(inhLbl, 5, r);
            r++;
        }
        card.getChildren().add(grid);
        return card;
    }

    private VBox buildSpecialsCard(Set<String> granted, Set<String> inherited) {
        VBox card = card();
        HBox head = new HBox(10);
        head.setAlignment(Pos.CENTER_LEFT);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button addAction = new Button("+ Add action");
        addAction.setId("posAddAction");
        addAction.setStyle(BTN_PLAIN);
        addAction.setDisable(!editable);
        addAction.setOnAction(e -> addSpecialAction());
        head.getChildren().addAll(sectionTitle("Special permissions"), sp, addAction);
        card.getChildren().add(head);
        Label hint = new Label("Sensitive things beyond plain open/edit access. Each one is checked where it matters in the app.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        card.getChildren().add(hint);

        List<SpecialPermissions.Definition> defs = SpecialPermissions.list();
        if (defs.isEmpty()) card.getChildren().add(grey("No special actions are defined."));
        for (SpecialPermissions.Definition d : defs) {
            CheckBox cb = new CheckBox(d.label());
            cb.setId("sp:" + d.key());
            cb.setSelected(isAdminPosition || granted.contains(d.key()));
            cb.setDisable(!editable || isAdminPosition);
            cb.selectedProperty().addListener((o, a, b) -> markDirty());
            specialBoxes.put(d.key(), cb);
            VBox row = new VBox(0, cb);
            String desc = d.description() == null ? "" : d.description();
            if (!granted.contains(d.key()) && inherited.contains(d.key())) desc = (desc.isEmpty() ? "" : desc + "  ") + "(granted through an included position)";
            if (!desc.isEmpty()) {
                Label dl = grey(desc);
                dl.setPadding(new Insets(0, 0, 0, 24));
                row.getChildren().add(dl);
            }
            card.getChildren().add(row);
        }
        return card;
    }

    private void addSpecialAction() {
        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle("New special action");
        d.setHeaderText(null);
        d.getDialogPane().setStyle("-fx-background-color: white;");
        TextField key = new TextField(); key.setPromptText("approve_refund"); key.setStyle(FIELD);
        TextField label = new TextField(); label.setPromptText("Approve refunds"); label.setStyle(FIELD);
        TextField desc = new TextField(); desc.setPromptText("What does it allow? (optional)"); desc.setStyle(FIELD);
        Label note = new Label("The key is what code checks (lowercase letters, digits, underscores). A new action only has an effect once a screen checks it — for example a module you build calls PermissionService.canPerform(\"approve_refund\").");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        VBox form = new VBox(8, lbl("Key"), key, lbl("Label"), label, lbl("Description"), desc, note);
        form.setPadding(new Insets(16));
        form.setPrefWidth(420);
        d.getDialogPane().setContent(form);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait().ifPresent(res -> {
            if (res != ButtonType.OK) return;
            try {
                if (!SpecialPermissions.register(key.getText().trim(), label.getText(), desc.getText().trim()))
                    alert(Alert.AlertType.INFORMATION, "An action with that key already exists.");
                else {
                    Set<String> keep = collectSpecials();
                    Integer id = currentId;
                    if (id != null) { showPosition(id); for (var e : specialBoxes.entrySet()) if (keep.contains(e.getKey())) e.getValue().setSelected(true); }
                    else render(null);
                }
            } catch (IllegalArgumentException ex) {
                alert(Alert.AlertType.ERROR, ex.getMessage());
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Could not add the action: " + ex.getMessage());
            }
        });
    }

    // ── save / delete ─────────────────────────────────────────────────────

    private Set<String> collectSpecials() {
        Set<String> out = new LinkedHashSet<>();
        specialBoxes.forEach((k, cb) -> { if (cb.isSelected()) out.add(k); });
        return out;
    }

    private void save() {
        Map<String, ModuleGrant> access = new LinkedHashMap<>();
        moduleBoxes.forEach((id, b) -> access.put(id, new ModuleGrant(b[0].isSelected(), b[1].isSelected(), b[2].isSelected(), b[3].isSelected())));
        Set<Integer> implied = new LinkedHashSet<>();
        impliesBoxes.forEach((id, cb) -> { if (cb.isSelected()) implied.add(id); });
        try {
            int id = svc.savePosition(currentId, nameField.getText(), descField.getText(), access, implied, collectSpecials());
            javafx.stage.Stage toastOwner = com.kaziflow.utils.Toast.stage();
            if (toastOwner != null) com.kaziflow.utils.Toast.success(toastOwner, "Position saved");
            reloadList(id);
        } catch (LockoutException ex) {
            alert(Alert.AlertType.WARNING, ex.getMessage());
        } catch (PermissionAdminException ex) {
            alert(Alert.AlertType.ERROR, ex.getMessage());
        }
    }

    private void delete() {
        if (currentId == null) return;
        Position p = null;
        for (Position x : list.getItems()) if (x.id() == currentId) p = x;
        if (p == null) return;
        String warn = p.memberCount() > 0
                ? "Delete \"" + p.name() + "\"? " + p.memberCount() + (p.memberCount() == 1 ? " person holds" : " people hold") + " it and will lose the access it gives."
                : "Delete \"" + p.name() + "\"?";
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, warn, ButtonType.OK, ButtonType.CANCEL);
        com.kaziflow.utils.DialogUtils.makeResizable(a);
        a.setHeaderText(null);
        if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            svc.deletePosition(currentId);
            dirty = false;
            reloadList(null);
        } catch (LockoutException ex) {
            alert(Alert.AlertType.WARNING, ex.getMessage());
        } catch (PermissionAdminException ex) {
            alert(Alert.AlertType.ERROR, ex.getMessage());
        }
    }

    // ── small helpers ─────────────────────────────────────────────────────

    private void markDirty() {
        if (loading || !editable || isAdminPosition) return;
        dirty = true;
        saveBtn.setDisable(false);
        revertBtn.setDisable(false);
    }

    private static String inheritedLetters(ModuleGrant g) {
        StringBuilder sb = new StringBuilder();
        if (g.read()) sb.append("Read ");
        if (g.create()) sb.append("Create ");
        if (g.write()) sb.append("Write ");
        if (g.delete()) sb.append("Delete");
        return sb.toString().trim();
    }

    private void setBanner(String text, String bg, String fg) {
        banner.setText(text);
        banner.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; -fx-background-radius: 8; -fx-padding: 10 12; -fx-font-size: 12px;");
        banner.setVisible(true);
        banner.setManaged(true);
    }

    private VBox card() {
        VBox c = new VBox(10);
        c.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 18;");
        return c;
    }

    private Label sectionTitle(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        return l;
    }

    private Label lbl(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        return l;
    }

    private Label grey(String t) {
        Label l = new Label(t);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        return l;
    }

    private void alert(Alert.AlertType type, String msg) {
        Alert a = new Alert(type, msg, ButtonType.OK);
        com.kaziflow.utils.DialogUtils.makeResizable(a);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
