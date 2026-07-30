package com.kaziflow.views;

import com.kaziflow.dao.EmployeeDAO;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.Employee;
import com.kaziflow.utils.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LeaveRequestView {

    private VBox root;
    private TableView<LeaveRecord> table;
    private ObservableList<LeaveRecord> data = FXCollections.observableArrayList();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public LeaveRequestView() { buildUI(); }
    public VBox getRoot() { return root; }

    public static class LeaveRecord {
        public int id;
        public int employeeId;
        public String employeeName;
        public String leaveType;
        public LocalDate startDate;
        public LocalDate endDate;
        public int days;
        public String reason;
        public String status;
        public String reviewerNotes;
        public String createdAt;

        public String getDateRange() {
            String s = startDate != null ? startDate.format(DATE_FMT) : "—";
            String e = endDate   != null ? endDate.format(DATE_FMT)   : "—";
            return s + " → " + e;
        }
    }

    private void buildUI() {
        root = new VBox(0);
        root.setStyle("-fx-background-color: #f8fafc;");
        root.getChildren().addAll(buildTopBar(), buildContent());
        loadData();
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(2);
        Label title = new Label("Leave Management"); title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label bc = new Label("Employees › Leave Requests"); bc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        titleBox.getChildren().addAll(title, bc);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button applyBtn = new Button("+ Apply for Leave");
        applyBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 38px; -fx-padding: 0 18; -fx-cursor: hand; -fx-font-size: 13px;");
        applyBtn.setOnAction(e -> showApplyDialog());

        bar.getChildren().addAll(titleBox, sp, applyBtn);
        return bar;
    }

    private VBox buildContent() {
        VBox content = new VBox(20); content.setPadding(new Insets(24));

        // Stats
        HBox statsRow = new HBox(16);
        int pending  = countByStatus("pending");
        int approved = countByStatus("approved");
        int onLeave  = countActiveLeaves();
        statsRow.getChildren().addAll(
            statCard("Pending Review",  String.valueOf(pending),  "Requires attention",  "#d97706"),
            statCard("Approved",        String.valueOf(approved), "This month",           "#16a34a"),
            statCard("Currently on Leave", String.valueOf(onLeave), "Active leave today", "#2563eb"),
            statCard("Leave Policies",  "Annual 21d", "Sick 10d • Casual 5d",            "#7c3aed")
        );
        for (var c : statsRow.getChildren()) HBox.setHgrow((Region)c, Priority.ALWAYS);

        // Filter bar
        HBox filterBar = new HBox(10); filterBar.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField();
        search.setPromptText("Search by employee name...");
        search.getStyleClass().add("search-box");
        search.setPrefWidth(260);
        search.textProperty().addListener((obs, o, v) -> {
            if (v.isBlank()) loadData();
            else data.setAll(getAllLeaves().stream()
                .filter(r -> r.employeeName.toLowerCase().contains(v.toLowerCase())).toList());
        });

        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All Statuses", "pending", "approved", "rejected", "cancelled");
        statusFilter.setValue("All Statuses"); statusFilter.setPrefHeight(36);
        statusFilter.setOnAction(e -> {
            String val = statusFilter.getValue();
            if ("All Statuses".equals(val)) loadData();
            else data.setAll(getAllLeaves().stream().filter(r -> val.equals(r.status)).toList());
        });

        ComboBox<String> typeFilter = new ComboBox<>();
        typeFilter.getItems().addAll("Leave Type", "Annual", "Sick", "Maternity", "Paternity", "Casual", "Unpaid");
        typeFilter.setValue("Leave Type"); typeFilter.setPrefHeight(36);

        filterBar.getChildren().addAll(search, statusFilter, typeFilter);

        // Table
        VBox tableCard = new VBox(0);
        tableCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1;");
        table = buildTable();
        tableCard.getChildren().add(table);

        content.getChildren().addAll(statsRow, filterBar, tableCard);
        return content;
    }

    @SuppressWarnings("unchecked")
    private TableView<LeaveRecord> buildTable() {
        TableView<LeaveRecord> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(480);
        tv.setStyle("-fx-background-color: white;");

        TableColumn<LeaveRecord,String> empCol = new TableColumn<>("EMPLOYEE");
        empCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().employeeName));
        empCol.setPrefWidth(160);

        TableColumn<LeaveRecord,String> typeCol = new TableColumn<>("LEAVE TYPE");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().leaveType));
        typeCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setGraphic(null); return; }
                String[] style = switch (t.toLowerCase()) {
                    case "sick"      -> new String[]{"#fee2e2", "#dc2626"};
                    case "maternity","paternity" -> new String[]{"#ede9fe", "#7c3aed"};
                    case "annual"    -> new String[]{"#dcfce7", "#16a34a"};
                    default          -> new String[]{"#f1f5f9", "#475569"};
                };
                Label badge = new Label(t);
                badge.setStyle("-fx-background-color:"+style[0]+";-fx-text-fill:"+style[1]+";-fx-font-size:11px;-fx-background-radius:20;-fx-padding:3 10;-fx-font-weight:bold;");
                setGraphic(badge); setText(null);
            }
        }); typeCol.setPrefWidth(110);

        TableColumn<LeaveRecord,String> dateCol = new TableColumn<>("DATE RANGE");
        dateCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDateRange()));
        dateCol.setPrefWidth(200);

        TableColumn<LeaveRecord,String> daysCol = new TableColumn<>("DAYS");
        daysCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().days + " days"));
        daysCol.setPrefWidth(80);

        TableColumn<LeaveRecord,String> reasonCol = new TableColumn<>("REASON");
        reasonCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().reason != null ? d.getValue().reason : "—"));
        reasonCol.setPrefWidth(200);

        TableColumn<LeaveRecord,String> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                String[] style = switch (s) {
                    case "approved"  -> new String[]{"#dcfce7", "#16a34a", "✓ Approved"};
                    case "rejected"  -> new String[]{"#fee2e2", "#dc2626", "✗ Rejected"};
                    case "cancelled" -> new String[]{"#f1f5f9", "#94a3b8", "Cancelled"};
                    default          -> new String[]{"#fef3c7", "#d97706", "⏳ Pending"};
                };
                Label badge = new Label(style[2]);
                badge.setStyle("-fx-background-color:"+style[0]+";-fx-text-fill:"+style[1]+";-fx-font-size:11px;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:3 10;");
                setGraphic(badge); setText(null);
            }
        }); statusCol.setPrefWidth(110);

        // Actions (approve/reject for managers)
        TableColumn<LeaveRecord,Void> actCol = new TableColumn<>("ACTIONS");
        actCol.setPrefWidth(140);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button approveBtn = new Button("✓ Approve");
            private final Button rejectBtn  = new Button("✗ Reject");
            private final HBox box = new HBox(6, approveBtn, rejectBtn);
            {
                approveBtn.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #16a34a; -fx-font-weight: bold; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 11px; -fx-cursor: hand; -fx-pref-height: 26px; -fx-padding: 0 8; -fx-border-color: transparent;");
                rejectBtn.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 11px; -fx-cursor: hand; -fx-pref-height: 26px; -fx-padding: 0 8; -fx-border-color: transparent;");
                approveBtn.setOnAction(e -> { updateLeaveStatus(getTableView().getItems().get(getIndex()).id, "approved");  loadData(); });
                rejectBtn.setOnAction(e  -> { updateLeaveStatus(getTableView().getItems().get(getIndex()).id, "rejected"); loadData(); });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                LeaveRecord r = getTableView().getItems().get(getIndex());
                boolean isPending = "pending".equals(r.status);
                approveBtn.setDisable(!isPending);
                rejectBtn.setDisable(!isPending);
                setGraphic(box);
            }
        });

        tv.getColumns().addAll(empCol, typeCol, dateCol, daysCol, reasonCol, statusCol, actCol);
        tv.setItems(data);
        return tv;
    }

    private VBox statCard(String label, String value, String note, String color) {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        Label lbl = new Label(label); lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        Label val = new Label(value); val.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label noteL = new Label(note); noteL.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        card.getChildren().addAll(lbl, val, noteL);
        return card;
    }

    // ─── Data ─────────────────────────────────────────────────────────────
    private List<LeaveRecord> getAllLeaves() {
        List<LeaveRecord> list = new ArrayList<>();
        String sql = """
            SELECT lr.*, e.name as emp_name
            FROM leave_requests lr
            JOIN employees e ON lr.employee_id = e.id
            ORDER BY lr.created_at DESC
        """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             ResultSet rs = conn.createStatement().executeQuery(sql)) {
            while (rs.next()) {
                LeaveRecord r = new LeaveRecord();
                r.id           = rs.getInt("id");
                r.employeeId   = rs.getInt("employee_id");
                r.employeeName = rs.getString("emp_name");
                r.leaveType    = rs.getString("leave_type");
                r.days         = rs.getInt("days");
                r.reason       = rs.getString("reason");
                r.status       = rs.getString("status");
                r.reviewerNotes = rs.getString("reviewer_notes");
                r.createdAt    = rs.getString("created_at");
                try { r.startDate = LocalDate.parse(rs.getString("start_date")); } catch (Exception ignored) {}
                try { r.endDate   = LocalDate.parse(rs.getString("end_date"));   } catch (Exception ignored) {}
                list.add(r);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    private void loadData() { data.setAll(getAllLeaves()); }

    private int countByStatus(String status) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM leave_requests WHERE status=?")) {
            ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    private int countActiveLeaves() {
        String today = LocalDate.now().toString();
        String sql = "SELECT COUNT(*) FROM leave_requests WHERE status='approved' AND start_date <= ? AND end_date >= ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, today); ps.setString(2, today);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    private void updateLeaveStatus(int id, String status) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE leave_requests SET status=? WHERE id=?")) {
            ps.setString(1, status); ps.setInt(2, id); ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showApplyDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Apply for Leave");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(460);

        VBox form = new VBox(14); form.setPadding(new Insets(20));

        form.getChildren().add(lbl("Employee *"));
        ComboBox<String> empCombo = new ComboBox<>();
        List<Employee> emps = employeeDAO.findAll();
        emps.forEach(e -> empCombo.getItems().add(e.getId() + ":" + e.getName()));
        if (!empCombo.getItems().isEmpty()) empCombo.setValue(empCombo.getItems().get(0));
        empCombo.setPrefHeight(36);
        form.getChildren().add(empCombo);

        form.getChildren().add(lbl("Leave Type *"));
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("Annual", "Sick", "Maternity", "Paternity", "Casual", "Unpaid");
        typeCombo.setValue("Annual"); typeCombo.setPrefHeight(36);
        form.getChildren().add(typeCombo);

        HBox dateRow = new HBox(16);
        VBox startBox = new VBox(6); startBox.getChildren().add(lbl("Start Date"));
        DatePicker startPicker = new DatePicker(LocalDate.now());
        startBox.getChildren().add(startPicker); HBox.setHgrow(startBox, Priority.ALWAYS);

        VBox endBox = new VBox(6); endBox.getChildren().add(lbl("End Date"));
        DatePicker endPicker = new DatePicker(LocalDate.now().plusDays(2));
        endBox.getChildren().add(endPicker); HBox.setHgrow(endBox, Priority.ALWAYS);
        dateRow.getChildren().addAll(startBox, endBox);
        form.getChildren().add(dateRow);

        form.getChildren().add(lbl("Reason"));
        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Brief reason for leave request...");
        reasonArea.setPrefRowCount(2);
        reasonArea.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 8;");
        form.getChildren().add(reasonArea);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                String[] parts = empCombo.getValue().split(":");
                int empId = Integer.parseInt(parts[0]);
                LocalDate start = startPicker.getValue();
                LocalDate end   = endPicker.getValue();
                long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;

                String sql = "INSERT INTO leave_requests (employee_id, leave_type, start_date, end_date, days, reason, status, requested_by) VALUES (?,?,?,?,?,?,?,?)";
                try (Connection conn = DatabaseManager.getInstance().getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, empId);
                    ps.setString(2, typeCombo.getValue());
                    ps.setString(3, start.toString());
                    ps.setString(4, end.toString());
                    ps.setLong(5, days);
                    ps.setString(6, reasonArea.getText().trim());
                    ps.setString(7, "pending");
                    try { ps.setInt(8, SessionManager.getInstance().getCurrentUser().getId()); }
                    catch (Exception ex) { ps.setInt(8, 1); }
                    ps.executeUpdate();
                    loadData();
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    private Label lbl(String text) {
        Label l = new Label(text); l.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;"); return l;
    }
}
