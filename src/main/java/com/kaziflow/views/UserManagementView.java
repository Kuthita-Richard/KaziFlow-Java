package com.kaziflow.views;

import com.kaziflow.dao.UserDAO;
import com.kaziflow.models.User;
import com.kaziflow.services.AuditLog;
import com.kaziflow.services.PermissionAdminService;
import com.kaziflow.services.PermissionAdminService.PermissionAdminException;
import com.kaziflow.services.PermissionAdminService.Position;
import com.kaziflow.services.PermissionService;
import com.kaziflow.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * User Management ("Users & Access"). Every number and list on this screen is computed from the
 * database — earlier versions showed invented figures (made-up role counts, a fake activity feed,
 * a fake licence cap). A user now holds one or more POSITIONS (see PositionsAccessView) instead of a
 * single hardcoded role.
 */
public class UserManagementView {

    /** Badge palette {text, background}; a position's colour is derived from its name, so new positions just work. */
    private static final String[][] PALETTE = {
        {"#7c3aed", "#ede9fe"}, {"#2563eb", "#eff6ff"}, {"#16a34a", "#dcfce7"},
        {"#d97706", "#fef3c7"}, {"#0891b2", "#e0f2fe"}, {"#db2777", "#fce7f3"}, {"#475569", "#f1f5f9"}
    };
    /** Audit actions that belong on an "access & security" feed. */
    private static final Set<String> ACTIVITY_ACTIONS = Set.of(
        "USER_LOGIN", "LOGIN_FAILED", "USER_CREATED", "USER_UPDATED", "USER_ACCESS_CHANGED", "PASSWORD_CHANGED",
        "POSITION_CREATED", "POSITION_UPDATED", "POSITION_DELETED", "ACCESS_DENIED");
    private static final DateTimeFormatter LOGIN_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private VBox root;
    private TableView<User> table;
    private final ObservableList<User> data = FXCollections.observableArrayList();
    private final UserDAO userDAO = new UserDAO();
    private final PermissionAdminService access = PermissionAdminService.getInstance();
    private final boolean editable =
        PermissionService.getInstance().isAdministrator() || PermissionService.getInstance().canWrite("settings");

    private List<User> allUsers = new ArrayList<>();
    private Map<Integer, List<String>> positionsByUser = new HashMap<>();
    private String searchText = "";
    private String positionFilter = null;   // null = all
    private String statusFilter = null;     // null = all

    private HBox statsRow;
    private VBox rightPanelHolder;

    public UserManagementView() { buildUI(); }

    public VBox getRoot() { return root; }

    private void buildUI() {
        root = new VBox(0);
        root.setStyle("-fx-background-color: #f8fafc;");

        HBox topbar = buildTopBar();
        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.getChildren().add(buildMainRow());
        root.getChildren().addAll(topbar, content);
        loadData();
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(2);
        Label title = new Label("User Management"); title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label bc = new Label("Dashboard › Settings › Users & Access"); bc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        titleBox.getChildren().addAll(title, bc);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button addBtn = new Button("+ Add New User");
        addBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 38px; -fx-padding: 0 18; -fx-cursor: hand; -fx-font-size: 13px;");
        addBtn.setDisable(!editable);
        addBtn.setOnAction(e -> showAddEditDialog(null));

        bar.getChildren().addAll(titleBox, sp, addBtn);
        return bar;
    }

    private HBox buildMainRow() {
        HBox row = new HBox(20);

        VBox tableSection = new VBox(16);
        HBox.setHgrow(tableSection, Priority.ALWAYS);

        statsRow = new HBox(16);

        HBox filterBar = new HBox(10); filterBar.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField();
        search.setPromptText("Search by name or email...");
        search.getStyleClass().add("search-box");
        search.setPrefWidth(280);
        search.textProperty().addListener((obs, o, v) -> { searchText = v == null ? "" : v.trim().toLowerCase(); applyFilters(); });

        // Filter lists come from the database, so new positions appear here with no code change.
        ComboBox<String> positionBox = new ComboBox<>();
        positionBox.getItems().add("All positions");
        for (Position p : access.listPositions()) positionBox.getItems().add(p.name());
        positionBox.setValue("All positions"); positionBox.setPrefHeight(36);
        positionBox.setOnAction(e -> { positionFilter = "All positions".equals(positionBox.getValue()) ? null : positionBox.getValue(); applyFilters(); });

        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("Status: All", "Active", "Inactive", "Suspended");
        statusBox.setValue("Status: All"); statusBox.setPrefHeight(36);
        statusBox.setOnAction(e -> { String v = statusBox.getValue(); statusFilter = "Status: All".equals(v) ? null : v.toLowerCase(); applyFilters(); });

        filterBar.getChildren().addAll(search, positionBox, statusBox);

        VBox tableCard = new VBox(0);
        tableCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1;");
        table = buildTable();
        tableCard.getChildren().add(table);

        tableSection.getChildren().addAll(statsRow, filterBar, tableCard);

        rightPanelHolder = new VBox();
        rightPanelHolder.setPrefWidth(290);

        row.getChildren().addAll(tableSection, rightPanelHolder);
        return row;
    }

    // ── data ──────────────────────────────────────────────────────────────

    private void loadData() {
        allUsers = userDAO.findAll();
        positionsByUser = access.positionNamesByUser();
        applyFilters();
        rebuildStats();
        rightPanelHolder.getChildren().setAll(buildRightPanel());
    }

    private void applyFilters() {
        data.setAll(allUsers.stream().filter(u -> {
            if (!searchText.isEmpty()
                && !u.getName().toLowerCase().contains(searchText)
                && !u.getEmail().toLowerCase().contains(searchText)) return false;
            if (statusFilter != null && !statusFilter.equalsIgnoreCase(u.getStatus())) return false;
            if (positionFilter != null && !positionsByUser.getOrDefault(u.getId(), List.of()).contains(positionFilter)) return false;
            return true;
        }).toList());
    }

    private void rebuildStats() {
        long active = allUsers.stream().filter(u -> "active".equalsIgnoreCase(u.getStatus())).count();
        long blocked = allUsers.size() - active;
        int admins = access.countActiveAdmins();
        int positions = access.listPositions().size();
        statsRow.getChildren().setAll(
            statCard("Total Users", String.valueOf(allUsers.size()), active + " active", "#1e293b"),
            statCard("Administrators", String.valueOf(admins), "active admin accounts", "#7c3aed"),
            statCard("Inactive / Suspended", String.valueOf(blocked), "can't sign in", blocked > 0 ? "#d97706" : "#16a34a"),
            statCard("Positions", String.valueOf(positions), "access bundles defined", "#2563eb")
        );
        for (var c : statsRow.getChildren()) HBox.setHgrow((Region) c, Priority.ALWAYS);
    }

    private VBox statCard(String label, String value, String note, String color) {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 16;");
        Label lbl = new Label(label); lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        Label val = new Label(value); val.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label noteL = new Label(note); noteL.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        card.getChildren().addAll(lbl, val, noteL);
        return card;
    }

    // ── table ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private TableView<User> buildTable() {
        TableView<User> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(420);
        tv.setStyle("-fx-background-color: white;");
        tv.setPlaceholder(new Label("No users match."));

        TableColumn<User, String> userCol = new TableColumn<>("USER DETAILS");
        userCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getName()));
        userCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) { setGraphic(null); return; }
                User u = getTableView().getItems().get(getIndex());
                List<String> pos = positionsByUser.getOrDefault(u.getId(), List.of());
                HBox row = new HBox(10); row.setAlignment(Pos.CENTER_LEFT);
                StackPane avatar = new StackPane();
                Circle circle = new Circle(18);
                circle.setFill(Color.web(colorsFor(pos.isEmpty() ? "" : pos.get(0))[0]));
                Label init = new Label(u.getName().isEmpty() ? "?" : String.valueOf(u.getName().charAt(0)).toUpperCase());
                init.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
                avatar.getChildren().addAll(circle, init);
                VBox info = new VBox(1);
                Label name = new Label(u.getName()); name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
                Label email = new Label("@" + u.getEmail().split("@")[0]); email.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
                info.getChildren().addAll(name, email);
                row.getChildren().addAll(avatar, info);
                setGraphic(row); setText(null);
            }
        });
        userCol.setPrefWidth(200);
        userCol.setMinWidth(170);

        TableColumn<User, String> posCol = new TableColumn<>("POSITIONS");
        posCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.join(", ", positionsByUser.getOrDefault(d.getValue().getId(), List.of()))));
        posCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty) { setGraphic(null); setText(null); return; }
                User u = getTableView().getItems().get(getIndex());
                List<String> pos = positionsByUser.getOrDefault(u.getId(), List.of());
                FlowPane badges = new FlowPane(4, 4);
                if (pos.isEmpty()) {
                    Label none = new Label("No access");
                    none.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 11px; -fx-font-weight: bold;");
                    badges.getChildren().add(none);
                }
                for (String p : pos) {
                    String[] col = colorsFor(p);
                    Label badge = new Label(p);
                    badge.setStyle("-fx-background-color: " + col[1] + "; -fx-text-fill: " + col[0] + "; -fx-font-size: 11px; -fx-background-radius: 20; -fx-padding: 3 10; -fx-font-weight: bold;");
                    badges.getChildren().add(badge);
                }
                setGraphic(badges); setText(null);
            }
        });
        posCol.setPrefWidth(190);

        TableColumn<User, String> emailCol = new TableColumn<>("EMAIL ADDRESS");
        emailCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getEmail()));
        emailCol.setPrefWidth(190);

        TableColumn<User, String> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getStatus()));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                String color = switch (s) {
                    case "active"    -> "#16a34a";
                    case "suspended" -> "#dc2626";
                    default          -> "#94a3b8";
                };
                Label lbl = new Label("● " + s.substring(0, 1).toUpperCase() + s.substring(1));
                lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; -fx-font-weight: bold;");
                setGraphic(lbl); setText(null);
            }
        });
        statusCol.setPrefWidth(100);

        TableColumn<User, String> lastLoginCol = new TableColumn<>("LAST LOGIN");
        lastLoginCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getLastLogin() == null ? "Never" : d.getValue().getLastLogin().format(LOGIN_FMT)));
        lastLoginCol.setPrefWidth(140);

        TableColumn<User, Void> actCol = new TableColumn<>("ACTIONS"); actCol.setPrefWidth(80);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button editBtn = new Button("✎");
            private final Button pwBtn = new Button("🔑");
            private final HBox box = new HBox(6, editBtn, pwBtn);
            {
                for (Button b : new Button[]{editBtn, pwBtn}) {
                    b.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-cursor: hand; -fx-font-size: 13px; -fx-border-color: transparent; -fx-padding: 2;");
                    b.setDisable(!editable);
                }
                editBtn.setOnAction(e -> showAddEditDialog(getTableView().getItems().get(getIndex())));
                pwBtn.setOnAction(e -> showChangePasswordDialog(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v, empty); setGraphic(empty ? null : box); }
        });

        tv.getColumns().addAll(userCol, posCol, emailCol, statusCol, lastLoginCol, actCol);
        tv.setItems(data);
        return tv;
    }

    // ── right panel: real distribution + real activity ────────────────────

    private VBox buildRightPanel() {
        VBox panel = new VBox(16);

        VBox posCard = new VBox(12);
        posCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        Label posTitle = new Label("Position Distribution"); posTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        posCard.getChildren().add(posTitle);

        List<Position> positions = access.listPositions();
        int totalUsers = Math.max(1, allUsers.size());
        if (positions.isEmpty()) {
            Label none = new Label("No positions defined yet.");
            none.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            posCard.getChildren().add(none);
        }
        for (Position p : positions) {
            VBox rRow = new VBox(4);
            HBox top = new HBox();
            Label name = new Label(p.name()); name.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            Label count = new Label(p.memberCount() + (p.memberCount() == 1 ? " user" : " users")); count.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");
            top.getChildren().addAll(name, sp, count);
            ProgressBar pb = new ProgressBar((double) p.memberCount() / totalUsers);
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.setStyle("-fx-accent: " + colorsFor(p.name())[0] + ";");
            rRow.getChildren().addAll(top, pb);
            posCard.getChildren().add(rRow);
        }

        Button manageBtn = new Button("Manage Positions & Access");
        manageBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 34px; -fx-cursor: hand; -fx-font-size: 12px;");
        manageBtn.setMaxWidth(Double.MAX_VALUE);
        manageBtn.setOnAction(e -> {
            PositionsAccessView.showDialog(root.getScene() != null ? root.getScene().getWindow() : null);
            loadData(); // positions may have been added, renamed or deleted
        });
        posCard.getChildren().add(manageBtn);

        VBox actCard = new VBox(12);
        actCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        Label actTitle = new Label("Recent Access Activity"); actTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        actCard.getChildren().add(actTitle);

        List<String[]> events = new ArrayList<>();
        for (String[] log : AuditLog.getRecentLogs(300)) {           // {user, action, description, module, created_at}
            if (ACTIVITY_ACTIONS.contains(log[1])) events.add(log);
            if (events.size() == 6) break;
        }
        if (events.isEmpty()) {
            Label none = new Label("Nothing recorded yet. Sign-ins, failed sign-ins and access changes appear here.");
            none.setWrapText(true);
            none.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            actCard.getChildren().add(none);
        }
        for (String[] ev : events) {
            boolean bad = ev[1].equals("LOGIN_FAILED") || ev[1].equals("ACCESS_DENIED");
            boolean accessEv = ev[1].startsWith("POSITION_") || ev[1].equals("USER_ACCESS_CHANGED");
            String bg = bad ? "#fee2e2" : accessEv ? "#ede9fe" : "#eff6ff";
            String icon = bad ? "⚠" : accessEv ? "🔒" : "👤";

            HBox actRow = new HBox(10); actRow.setAlignment(Pos.TOP_LEFT);
            actRow.setPadding(new Insets(6, 0, 6, 0));
            actRow.setStyle("-fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");
            StackPane iconBox = new StackPane();
            iconBox.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 8; -fx-min-width: 32; -fx-min-height: 32;");
            iconBox.getChildren().add(new Label(icon));
            VBox info = new VBox(1);
            Label evtTitle = new Label(humanize(ev[1])); evtTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1e293b;");
            Label desc = new Label(ev[2]); desc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;"); desc.setWrapText(true);
            desc.setMaxWidth(200); desc.setMinHeight(Region.USE_PREF_SIZE); // wrap, don't clip with "..."
            Label time = new Label(timeAgo(ev[4]) + (ev[0] == null || ev[0].equals("system") ? "" : "  ·  " + ev[0])); time.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px;");
            info.getChildren().addAll(evtTitle, desc, time);
            actRow.getChildren().addAll(iconBox, info);
            actCard.getChildren().add(actRow);
        }

        panel.getChildren().addAll(posCard, actCard);
        return panel;
    }

    private static String humanize(String action) {
        String s = action.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String timeAgo(String iso) {
        try {
            LocalDateTime t = LocalDateTime.parse(iso.trim().replace(' ', 'T'));
            long mins = Duration.between(t, LocalDateTime.now()).toMinutes();
            if (mins < 1) return "just now";
            if (mins < 60) return mins + " min ago";
            if (mins < 60 * 24) return (mins / 60) + " hr ago";
            return (mins / (60 * 24)) + " d ago";
        } catch (Exception e) { return iso == null ? "" : iso; }
    }

    // ── add / edit ────────────────────────────────────────────────────────

    private void showAddEditDialog(User existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle(existing == null ? "Add New User" : "Edit User");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(480);

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));

        TextField nameField = field("Full Name", existing != null ? existing.getName() : "");
        TextField emailField = field("Email Address", existing != null ? existing.getEmail() : "");
        PasswordField pwField = new PasswordField();
        pwField.setPromptText(existing != null ? "Leave blank to keep current" : "At least 6 characters");
        pwField.setStyle("-fx-pref-height: 34px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;");

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("active", "inactive", "suspended");
        statusCombo.setValue(existing != null ? existing.getStatus() : "active");

        // Positions: one checkbox per position in the database (a person can hold several).
        Set<Integer> current = existing != null ? access.getUserPositions(existing.getId()) : Set.of();
        Map<Integer, CheckBox> posBoxes = new LinkedHashMap<>();
        VBox posList = new VBox(6);
        for (Position p : access.listPositions()) {
            CheckBox cb = new CheckBox(p.name() + (p.system() ? "  (full access)" : ""));
            cb.setSelected(current.contains(p.id()));
            posBoxes.put(p.id(), cb);
            posList.getChildren().add(cb);
        }
        ScrollPane posScroll = new ScrollPane(posList);
        posScroll.setFitToWidth(true);
        posScroll.setPrefHeight(150);
        posScroll.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6;");
        Label posNote = new Label();
        posNote.setWrapText(true);
        Runnable updateNote = () -> {
            boolean any = posBoxes.values().stream().anyMatch(CheckBox::isSelected);
            posNote.setText(any ? "Access is the combination of everything these positions grant."
                                : "No position selected: this person can sign in but won't be able to open anything.");
            posNote.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (any ? "#94a3b8" : "#d97706") + ";");
        };
        posBoxes.values().forEach(cb -> cb.selectedProperty().addListener((o, a, b) -> updateNote.run()));
        updateNote.run();

        form.addRow(0, lbl("Full Name"), nameField);
        form.addRow(1, lbl("Email Address"), emailField);
        form.addRow(2, lbl("Password"), com.kaziflow.utils.PasswordFieldUtils.withToggle(pwField));
        form.addRow(3, lbl("Status"), statusCombo);
        form.addRow(4, lbl("Positions"), posScroll);
        form.add(posNote, 1, 5);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Validate BEFORE closing, so a mistake keeps the dialog (and what was typed) on screen.
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            String err = validate(existing, nameField.getText(), emailField.getText(), pwField.getText(), statusCombo.getValue());
            if (err != null) { error(err); ev.consume(); }
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
            Set<Integer> selected = new LinkedHashSet<>();
            posBoxes.forEach((id, cb) -> { if (cb.isSelected()) selected.add(id); });
            saveUser(existing, nameField.getText().trim(), emailField.getText().trim(), pwField.getText(), statusCombo.getValue(), selected);
        });
    }

    private String validate(User existing, String name, String email, String password, String status) {
        if (name == null || name.isBlank()) return "Please enter the person's name.";
        if (email == null || !email.trim().matches("[^@\\s]+@[^@\\s]+")) return "Please enter a valid email address.";
        if (existing == null && (password == null || password.length() < 6)) return "Please set a password of at least 6 characters.";
        if (existing != null && !password.isEmpty() && password.length() < 6) return "A new password must be at least 6 characters.";
        boolean selfDeactivate = existing != null && SessionManager.getInstance().getCurrentUser() != null
            && SessionManager.getInstance().getCurrentUser().getId() == existing.getId() && !"active".equals(status);
        if (selfDeactivate) return "You can't deactivate the account you're signed in with.";
        for (User u : allUsers) {
            if (u.getEmail().equalsIgnoreCase(email.trim()) && (existing == null || u.getId() != existing.getId()))
                return "Another user already has that email address.";
        }
        return null;
    }

    private void saveUser(User existing, String name, String email, String password, String status, Set<Integer> positions) {
        try {
            if (existing == null) {
                User u = new User();
                u.setName(name); u.setEmail(email); u.setStatus(status);
                u.setPasswordHash(password);                       // hashed by UserDAO.save
                u.setRoleId(access.legacyRoleIdFor(positions));    // legacy NOT NULL column only; Positions decide access
                if (!userDAO.save(u)) { error("The user could not be saved (is that email already in use?)."); return; }
                int id = userDAO.findIdByEmail(email);
                if (id > 0) access.saveUserAccess(id, positions, null);
                AuditLog.logUserCreated(name);
            } else {
                // Positions/status first: if it would leave no administrator it throws and nothing else is changed.
                access.saveUserAccess(existing.getId(), positions, status);
                existing.setName(name); existing.setEmail(email); existing.setStatus(status);
                if (!userDAO.update(existing)) { error("The user could not be updated."); return; }
                if (!password.isEmpty()) {
                    userDAO.updatePassword(existing.getId(), password);
                    AuditLog.log("PASSWORD_CHANGED", "Password changed for " + name, "settings", existing.getId());
                }
                AuditLog.log("USER_UPDATED", "User updated: " + name, "settings", existing.getId());
            }
            loadData();
        } catch (PermissionAdminException ex) {
            error(ex.getMessage());
        }
    }

    private void showChangePasswordDialog(User user) {
        Dialog<ButtonType> dialog = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle("Change Password — " + user.getName());
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(360);

        VBox form = new VBox(12); form.setPadding(new Insets(20));
        PasswordField newPw = new PasswordField(); newPw.setPromptText("New password");
        newPw.setStyle("-fx-pref-height: 36px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;");
        PasswordField confirmPw = new PasswordField(); confirmPw.setPromptText("Confirm new password");
        confirmPw.setStyle(newPw.getStyle());
        form.getChildren().addAll(lbl("New Password"), com.kaziflow.utils.PasswordFieldUtils.withToggle(newPw), lbl("Confirm Password"), com.kaziflow.utils.PasswordFieldUtils.withToggle(confirmPw));
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                if (!newPw.getText().equals(confirmPw.getText())) { error("Passwords do not match."); return; }
                if (newPw.getText().length() < 6) { error("Password must be at least 6 characters."); return; }
                if (userDAO.updatePassword(user.getId(), newPw.getText())) {
                    AuditLog.log("PASSWORD_CHANGED", "Password changed for " + user.getName(), "settings", user.getId());
                    Alert ok = new Alert(Alert.AlertType.INFORMATION, "Password changed successfully.", ButtonType.OK);
                    com.kaziflow.utils.DialogUtils.makeResizable(ok);
                    ok.setHeaderText(null); ok.show();
                    loadData();
                }
            }
        });
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static String[] colorsFor(String positionName) {
        if (positionName == null || positionName.isEmpty()) return PALETTE[PALETTE.length - 1];
        return PALETTE[Math.floorMod(positionName.hashCode(), PALETTE.length - 1)];
    }

    private void error(String msg) {
        Alert err = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        com.kaziflow.utils.DialogUtils.makeResizable(err);
        err.setHeaderText(null);
        err.showAndWait();
    }

    private TextField field(String prompt, String val) {
        TextField tf = new TextField(val); tf.setPromptText(prompt); tf.setPrefWidth(280);
        tf.setStyle("-fx-pref-height: 34px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;");
        return tf;
    }

    private Label lbl(String text) {
        Label l = new Label(text); l.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;"); return l;
    }
}
