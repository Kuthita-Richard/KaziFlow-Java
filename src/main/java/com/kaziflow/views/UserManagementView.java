package com.kaziflow.views;

import com.kaziflow.dao.UserDAO;
import com.kaziflow.models.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.List;

public class UserManagementView {

    private VBox root;
    private TableView<User> table;
    private ObservableList<User> data = FXCollections.observableArrayList();
    private final UserDAO userDAO = new UserDAO();

    public UserManagementView() { buildUI(); }

    public VBox getRoot() { return root; }

    private void buildUI() {
        root = new VBox(0);
        root.setStyle("-fx-background-color: #f8fafc;");

        HBox topbar = buildTopBar();
        VBox content = new VBox(20);
        content.setPadding(new Insets(24));

        HBox mainRow = buildMainRow();
        content.getChildren().add(mainRow);
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
        addBtn.setOnAction(e -> showAddEditDialog(null));

        bar.getChildren().addAll(titleBox, sp, addBtn);
        return bar;
    }

    private HBox buildMainRow() {
        HBox row = new HBox(20);

        // ── Left: user table ──
        VBox tableSection = new VBox(16);
        HBox.setHgrow(tableSection, Priority.ALWAYS);

        // Stat cards
        HBox stats = new HBox(16);
        int total = userDAO.getTotalCount();
        stats.getChildren().addAll(
            statCard("Total Users", String.valueOf(total), "+2 this month", "#1e293b"),
            statCard("Active Now", "—", "Online Now", "#16a34a"),
            statCard("Pending Requests", "0", "Needs Approval", "#d97706"),
            statCard("Licenses Used", total + "/30", "Standard Plan", "#7c3aed")
        );
        for (var c : stats.getChildren()) HBox.setHgrow((Region)c, Priority.ALWAYS);

        // Filter bar
        HBox filterBar = new HBox(10); filterBar.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField();
        search.setPromptText("Search by name, email, or role...");
        search.getStyleClass().add("search-box");
        search.setPrefWidth(280);
        search.textProperty().addListener((obs,o,v) -> data.setAll(v.isBlank() ? userDAO.findAll() : userDAO.findAll().stream().filter(u -> u.getName().toLowerCase().contains(v.toLowerCase()) || u.getEmail().toLowerCase().contains(v.toLowerCase())).toList()));

        ComboBox<String> roleFilter = new ComboBox<>();
        roleFilter.getItems().addAll("Filter by Role", "Administrator", "Store Manager", "Cashier", "Warehouse Staff", "Accountant");
        roleFilter.setValue("Filter by Role"); roleFilter.setPrefHeight(36);

        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("Status: All", "Active", "Inactive", "Suspended");
        statusFilter.setValue("Status: All"); statusFilter.setPrefHeight(36);

        filterBar.getChildren().addAll(search, roleFilter, statusFilter);

        // Table
        VBox tableCard = new VBox(0);
        tableCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1;");
        table = buildTable();
        tableCard.getChildren().add(table);

        tableSection.getChildren().addAll(stats, filterBar, tableCard);

        // ── Right: role distribution + activity ──
        VBox rightPanel = buildRightPanel();
        rightPanel.setPrefWidth(280);

        row.getChildren().addAll(tableSection, rightPanel);
        return row;
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

    @SuppressWarnings("unchecked")
    private TableView<User> buildTable() {
        TableView<User> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(420);
        tv.setStyle("-fx-background-color: white;");

        // User Details
        TableColumn<User,String> userCol = new TableColumn<>("USER DETAILS");
        userCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getName()));
        userCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String n, boolean empty) {
                super.updateItem(n, empty);
                if(empty||n==null){setGraphic(null);return;}
                User u = getTableView().getItems().get(getIndex());
                HBox row = new HBox(10); row.setAlignment(Pos.CENTER_LEFT);
                StackPane avatar = new StackPane();
                Circle circle = new Circle(18);
                circle.setFill(Color.web(roleColor(u.getRoleName())));
                Label init = new Label(u.getName().isEmpty()?"?":String.valueOf(u.getName().charAt(0)).toUpperCase());
                init.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
                avatar.getChildren().addAll(circle, init);
                VBox info = new VBox(1);
                Label name = new Label(u.getName()); name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
                Label email = new Label("@" + u.getEmail().split("@")[0]); email.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
                info.getChildren().addAll(name, email);
                row.getChildren().addAll(avatar, info);
                setGraphic(row); setText(null);
            }
        }); userCol.setPrefWidth(200);

        // Role
        TableColumn<User,String> roleCol = new TableColumn<>("ROLE");
        roleCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getRoleName()));
        roleCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                if(empty||role==null){setGraphic(null);return;}
                Label badge = new Label(role);
                badge.setStyle("-fx-background-color: " + roleBg(role) + "; -fx-text-fill: " + roleColor(role) + "; -fx-font-size: 11px; -fx-background-radius: 20; -fx-padding: 3 10; -fx-font-weight: bold;");
                setGraphic(badge); setText(null);
            }
        }); roleCol.setPrefWidth(150);

        // Email
        TableColumn<User,String> emailCol = new TableColumn<>("EMAIL ADDRESS");
        emailCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getEmail()));
        emailCol.setPrefWidth(200);

        // Status
        TableColumn<User,String> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getStatus()));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if(empty||s==null){setGraphic(null);return;}
                String color = switch(s) {
                    case "active"    -> "#16a34a";
                    case "suspended" -> "#dc2626";
                    default          -> "#94a3b8";
                };
                Label lbl = new Label("● " + s.substring(0,1).toUpperCase() + s.substring(1));
                lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; -fx-font-weight: bold;");
                setGraphic(lbl); setText(null);
            }
        }); statusCol.setPrefWidth(100);

        // Last Login
        TableColumn<User,String> lastLoginCol = new TableColumn<>("LAST LOGIN");
        lastLoginCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty("—"));
        lastLoginCol.setPrefWidth(130);

        // Actions
        TableColumn<User,Void> actCol = new TableColumn<>("ACTIONS"); actCol.setPrefWidth(80);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button editBtn = new Button("✎");
            private final Button pwBtn = new Button("🔑");
            private final HBox box = new HBox(6, editBtn, pwBtn);
            {
                for (Button b : new Button[]{editBtn, pwBtn})
                    b.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-cursor: hand; -fx-font-size: 13px; -fx-border-color: transparent; -fx-padding: 2;");
                editBtn.setOnAction(e -> showAddEditDialog(getTableView().getItems().get(getIndex())));
                pwBtn.setOnAction(e -> showChangePasswordDialog(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v,empty); setGraphic(empty?null:box); }
        });

        tv.getColumns().addAll(userCol, roleCol, emailCol, statusCol, lastLoginCol, actCol);
        tv.setItems(data);
        return tv;
    }

    private VBox buildRightPanel() {
        VBox panel = new VBox(16);

        // Role distribution card
        VBox roleCard = new VBox(12);
        roleCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        Label roleTitle = new Label("Role Distribution"); roleTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        roleCard.getChildren().add(roleTitle);

        String[][] roles = {{"Cashiers", "12", "#2563eb"}, {"Store Managers", "4", "#16a34a"}, {"Administrators", "2", "#7c3aed"}, {"Warehouse Staff", "6", "#d97706"}};
        for (String[] r : roles) {
            VBox rRow = new VBox(4);
            HBox top = new HBox();
            Label name = new Label(r[0]); name.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            Label count = new Label(r[1] + " users"); count.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");
            top.getChildren().addAll(name, sp, count);
            ProgressBar pb = new ProgressBar(Double.parseDouble(r[1]) / 24.0);
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.setStyle("-fx-accent: " + r[2] + ";");
            rRow.getChildren().addAll(top, pb);
            roleCard.getChildren().add(rRow);
        }

        Button manageRolesBtn = new Button("Manage Roles & Permissions");
        manageRolesBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 34px; -fx-cursor: hand; -fx-font-size: 12px;");
        manageRolesBtn.setMaxWidth(Double.MAX_VALUE);
        manageRolesBtn.setOnAction(e -> com.kaziflow.utils.SceneManager.getInstance().navigateTo("settings"));
        roleCard.getChildren().add(manageRolesBtn);

        // Activity card
        VBox actCard = new VBox(12);
        actCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        HBox actHeader = new HBox();
        Label actTitle = new Label("Recent Activity"); actTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Region sp2 = new Region(); HBox.setHgrow(sp2, Priority.ALWAYS);
        Hyperlink viewAll = new Hyperlink("View All"); viewAll.setStyle("-fx-text-fill: #2563eb; -fx-font-size: 12px;");
        actHeader.getChildren().addAll(actTitle, sp2, viewAll);
        actCard.getChildren().add(actHeader);

        String[][] activities = {
            {"👤", "New User Added", "Admin added John Doe", "7 min ago", "#eff6ff", "#2563eb"},
            {"🔒", "Role Updated", "Mercy promoted to Manager", "45 min ago", "#ede9fe", "#7c3aed"},
            {"⚠", "Failed Login", "Suspicious login for Sarah W.", "3 hrs ago", "#fee2e2", "#dc2626"},
            {"💾", "System Backup", "Automated daily backup", "5 hrs ago", "#dcfce7", "#16a34a"}
        };

        for (String[] a : activities) {
            HBox actRow = new HBox(10); actRow.setAlignment(Pos.TOP_LEFT);
            actRow.setPadding(new Insets(6, 0, 6, 0));
            actRow.setStyle("-fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");

            StackPane iconBox = new StackPane();
            iconBox.setStyle("-fx-background-color: " + a[4] + "; -fx-background-radius: 8; -fx-min-width: 32; -fx-min-height: 32;");
            Label icon = new Label(a[0]); icon.setStyle("-fx-font-size: 13px;");
            iconBox.getChildren().add(icon);

            VBox info = new VBox(1);
            Label evtTitle = new Label(a[1]); evtTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1e293b;");
            Label desc = new Label(a[2]); desc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-wrap-text: true;"); desc.setWrapText(true);
            Label time = new Label(a[3]); time.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px;");
            info.getChildren().addAll(evtTitle, desc, time);

            actRow.getChildren().addAll(iconBox, info);
            actCard.getChildren().add(actRow);
        }

        panel.getChildren().addAll(roleCard, actCard);
        return panel;
    }

    private void loadData() { data.setAll(userDAO.findAll()); }

    private void showAddEditDialog(User existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add New User" : "Edit User");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(440);

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));

        TextField nameField  = field("Full Name", existing != null ? existing.getName() : "");
        TextField emailField = field("Email Address", existing != null ? existing.getEmail() : "");
        PasswordField pwField = new PasswordField();
        pwField.setPromptText(existing != null ? "Leave blank to keep current" : "Password");
        pwField.setStyle("-fx-pref-height: 34px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;");

        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll("1:Administrator", "2:Store Manager", "3:Cashier", "4:Warehouse Staff", "5:Accountant");
        roleCombo.setPrefWidth(200);
        if (existing != null) roleCombo.getItems().forEach(item -> { if(item.startsWith(existing.getRoleId() + ":")) roleCombo.setValue(item); });
        if (roleCombo.getValue() == null && !roleCombo.getItems().isEmpty()) roleCombo.setValue(roleCombo.getItems().get(2));

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("active", "inactive", "suspended");
        statusCombo.setValue(existing != null ? existing.getStatus() : "active");

        form.addRow(0, lbl("Full Name"), nameField);
        form.addRow(1, lbl("Email Address"), emailField);
        form.addRow(2, lbl("Password"), pwField);
        form.addRow(3, lbl("Role"), roleCombo, lbl("Status"), statusCombo);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                User u = existing != null ? existing : new User();
                u.setName(nameField.getText().trim());
                u.setEmail(emailField.getText().trim());
                u.setStatus(statusCombo.getValue());
                if (!roleCombo.getValue().isEmpty()) {
                    String[] parts = roleCombo.getValue().split(":");
                    try { u.setRoleId(Integer.parseInt(parts[0])); } catch (Exception ignored) {}
                }
                boolean ok;
                if (existing == null) {
                    u.setPasswordHash(pwField.getText().isEmpty() ? "changeme123" : pwField.getText());
                    ok = userDAO.save(u);
                    if (ok) com.kaziflow.services.AuditLog.logUserCreated(u.getName());
                } else {
                    ok = userDAO.update(u);
                    if (!pwField.getText().isEmpty()) userDAO.updatePassword(u.getId(), pwField.getText());
                    if (ok) com.kaziflow.services.AuditLog.log("USER_UPDATED","User updated: "+u.getName(),"settings",u.getId());
                }
                if (ok) loadData();
            }
        });
    }

    private void showChangePasswordDialog(User user) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Password — " + user.getName());
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(360);

        VBox form = new VBox(12); form.setPadding(new Insets(20));
        PasswordField newPw = new PasswordField(); newPw.setPromptText("New password");
        newPw.setStyle("-fx-pref-height: 36px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;");
        PasswordField confirmPw = new PasswordField(); confirmPw.setPromptText("Confirm new password");
        confirmPw.setStyle(newPw.getStyle());
        form.getChildren().addAll(lbl("New Password"), newPw, lbl("Confirm Password"), confirmPw);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                if (!newPw.getText().equals(confirmPw.getText())) {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Passwords do not match.", ButtonType.OK);
                    err.setHeaderText(null); err.show(); return;
                }
                if (newPw.getText().length() < 6) {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Password must be at least 6 characters.", ButtonType.OK);
                    err.setHeaderText(null); err.show(); return;
                }
                if (userDAO.updatePassword(user.getId(), newPw.getText())) {
                    Alert ok = new Alert(Alert.AlertType.INFORMATION, "Password changed successfully.", ButtonType.OK);
                    ok.setHeaderText(null); ok.show();
                }
            }
        });
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String roleColor(String role) {
        if (role == null) return "#475569";
        return switch (role) {
            case "Administrator"  -> "#7c3aed";
            case "Store Manager"  -> "#2563eb";
            case "Cashier"        -> "#16a34a";
            case "Warehouse Staff"-> "#d97706";
            case "Accountant"     -> "#0891b2";
            default               -> "#475569";
        };
    }

    private String roleBg(String role) {
        if (role == null) return "#f1f5f9";
        return switch (role) {
            case "Administrator"  -> "#ede9fe";
            case "Store Manager"  -> "#eff6ff";
            case "Cashier"        -> "#dcfce7";
            case "Warehouse Staff"-> "#fef3c7";
            case "Accountant"     -> "#e0f2fe";
            default               -> "#f1f5f9";
        };
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
