package com.kaziflow.views;

import com.kaziflow.dao.EmployeeDAO;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.Employee;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AttendanceView {

    private VBox root;
    private TableView<AttendanceRecord> table;
    private ObservableList<AttendanceRecord> data = FXCollections.observableArrayList();
    private DatePicker datePicker;
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public AttendanceView() { buildUI(); }
    public VBox getRoot() { return root; }

    // ─── Inner model ────────────────────────────────────────────────────────
    public static class AttendanceRecord {
        public int id;
        public int employeeId;
        public String employeeName;
        public String department;
        public LocalDate date;
        public LocalTime checkIn;
        public LocalTime checkOut;
        public String status;
        public String notes;

        public String getCheckInStr()  { return checkIn  != null ? checkIn.format(TIME_FMT)  : "—"; }
        public String getCheckOutStr() { return checkOut != null ? checkOut.format(TIME_FMT) : "—"; }
        public String getHoursWorked() {
            if (checkIn == null || checkOut == null) return "—";
            long mins = java.time.Duration.between(checkIn, checkOut).toMinutes();
            return String.format("%dh %02dm", mins / 60, mins % 60);
        }
    }

    // ─── UI Build ─────────────────────────────────────────────────────────
    private void buildUI() {
        root = new VBox(0);
        root.setStyle("-fx-background-color: #f8fafc;");
        root.getChildren().addAll(buildTopBar(), buildContent());
        loadData(LocalDate.now());
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(2);
        Label title = new Label("Attendance Tracking"); title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label bc = new Label("Employees › Attendance"); bc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        titleBox.getChildren().addAll(title, bc);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button markAllBtn = new Button("✓ Mark All Present");
        markAllBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-pref-height: 36px; -fx-padding: 0 14; -fx-cursor: hand; -fx-font-size: 13px;");
        markAllBtn.setOnAction(e -> markAllPresent());

        Button addBtn = new Button("+ Record Attendance");
        addBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 36px; -fx-padding: 0 16; -fx-cursor: hand; -fx-font-size: 13px;");
        addBtn.setOnAction(e -> showRecordDialog(null));

        bar.getChildren().addAll(titleBox, sp, markAllBtn, addBtn);
        bar.setSpacing(10);
        return bar;
    }

    private VBox buildContent() {
        VBox content = new VBox(20); content.setPadding(new Insets(24));

        // Stats
        HBox statsRow = buildStatsRow();

        // Controls
        HBox controls = new HBox(10); controls.setAlignment(Pos.CENTER_LEFT);
        Label dateLabel = new Label("Date:");
        dateLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #475569;");

        datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefHeight(36);
        datePicker.setOnAction(e -> loadData(datePicker.getValue()));

        Button todayBtn = new Button("Today");
        todayBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 36px; -fx-padding: 0 14; -fx-cursor: hand;");
        todayBtn.setOnAction(e -> { datePicker.setValue(LocalDate.now()); loadData(LocalDate.now()); });

        Button prevBtn = new Button("◀");
        prevBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 36px; -fx-padding: 0 10; -fx-cursor: hand;");
        prevBtn.setOnAction(e -> { datePicker.setValue(datePicker.getValue().minusDays(1)); loadData(datePicker.getValue()); });

        Button nextBtn = new Button("▶");
        nextBtn.setStyle(prevBtn.getStyle());
        nextBtn.setOnAction(e -> { datePicker.setValue(datePicker.getValue().plusDays(1)); loadData(datePicker.getValue()); });

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        ComboBox<String> deptFilter = new ComboBox<>();
        deptFilter.getItems().addAll("All Departments", "Management", "Sales", "Workshop", "Operations", "Finance");
        deptFilter.setValue("All Departments"); deptFilter.setPrefHeight(36);

        controls.getChildren().addAll(dateLabel, prevBtn, datePicker, nextBtn, todayBtn, sp, deptFilter);

        // Table
        VBox tableCard = new VBox(0);
        tableCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1;");
        table = buildTable();
        tableCard.getChildren().add(table);

        content.getChildren().addAll(statsRow, controls, tableCard);
        return content;
    }

    private HBox buildStatsRow() {
        HBox row = new HBox(16);
        int total = employeeDAO.getTotalCount();
        row.getChildren().addAll(
            statCard("Present Today",  String.valueOf(countByStatus(LocalDate.now(), "present")),  "Checked in",   "#16a34a"),
            statCard("Absent",         String.valueOf(countByStatus(LocalDate.now(), "absent")),   "Not checked in","#dc2626"),
            statCard("Late Arrivals",  String.valueOf(countByStatus(LocalDate.now(), "late")),     "After 8:30 AM","#d97706"),
            statCard("On Leave",       String.valueOf(countByStatus(LocalDate.now(), "on-leave")), "Approved leave","#7c3aed")
        );
        for (var c : row.getChildren()) HBox.setHgrow((Region)c, Priority.ALWAYS);
        return row;
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

    @SuppressWarnings("unchecked")
    private TableView<AttendanceRecord> buildTable() {
        TableView<AttendanceRecord> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(460);
        tv.setStyle("-fx-background-color: white;");

        // Employee
        TableColumn<AttendanceRecord,String> empCol = new TableColumn<>("EMPLOYEE");
        empCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().employeeName));
        empCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) { setGraphic(null); return; }
                AttendanceRecord r = getTableView().getItems().get(getIndex());
                HBox row = new HBox(10); row.setAlignment(Pos.CENTER_LEFT);
                StackPane av = new StackPane();
                javafx.scene.shape.Circle circle = new javafx.scene.shape.Circle(16);
                circle.setFill(javafx.scene.paint.Color.web("#2563eb"));
                Label init = new Label(n.isEmpty()?"?":String.valueOf(n.charAt(0)).toUpperCase());
                init.setStyle("-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;");
                av.getChildren().addAll(circle, init);
                VBox info = new VBox(1);
                Label name = new Label(n); name.setStyle("-fx-font-weight:bold;-fx-font-size:13px;-fx-text-fill:#1e293b;");
                Label dept = new Label(r.department != null ? r.department : "—"); dept.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;");
                info.getChildren().addAll(name, dept);
                row.getChildren().addAll(av, info); setGraphic(row); setText(null);
            }
        }); empCol.setPrefWidth(200);

        // Date
        TableColumn<AttendanceRecord,String> dateCol = new TableColumn<>("DATE");
        dateCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().date != null ? d.getValue().date.format(DATE_FMT) : "—"));
        dateCol.setPrefWidth(120);

        // Check-in
        TableColumn<AttendanceRecord,String> inCol = new TableColumn<>("CHECK IN");
        inCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCheckInStr()));
        inCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); return; }
                Label l = new Label(v);
                l.setStyle("-fx-font-weight: bold; -fx-text-fill: " + ("—".equals(v) ? "#94a3b8" : "#16a34a") + ";");
                setGraphic(l); setText(null);
            }
        }); inCol.setPrefWidth(100);

        // Check-out
        TableColumn<AttendanceRecord,String> outCol = new TableColumn<>("CHECK OUT");
        outCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCheckOutStr()));
        outCol.setPrefWidth(100);

        // Hours
        TableColumn<AttendanceRecord,String> hoursCol = new TableColumn<>("HOURS");
        hoursCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getHoursWorked()));
        hoursCol.setPrefWidth(90);

        // Status
        TableColumn<AttendanceRecord,String> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                String[] style = switch (s) {
                    case "present"  -> new String[]{"#dcfce7", "#16a34a", "Present"};
                    case "absent"   -> new String[]{"#fee2e2", "#dc2626", "Absent"};
                    case "late"     -> new String[]{"#fef3c7", "#d97706", "Late"};
                    case "on-leave" -> new String[]{"#ede9fe", "#7c3aed", "On Leave"};
                    default         -> new String[]{"#f1f5f9", "#94a3b8", s};
                };
                Label badge = new Label(style[2]);
                badge.setStyle("-fx-background-color:"+style[0]+";-fx-text-fill:"+style[1]+";-fx-font-size:11px;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:3 10;");
                setGraphic(badge); setText(null);
            }
        }); statusCol.setPrefWidth(100);

        // Actions
        TableColumn<AttendanceRecord,Void> actCol = new TableColumn<>(""); actCol.setPrefWidth(100);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button checkoutBtn = new Button("Check Out");
            private final HBox box = new HBox(6, editBtn, checkoutBtn);
            {
                String s = "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 11px; -fx-cursor: hand; -fx-pref-height: 26px; -fx-padding: 0 8;";
                editBtn.setStyle(s); checkoutBtn.setStyle(s);
                editBtn.setOnAction(e -> showRecordDialog(getTableView().getItems().get(getIndex())));
                checkoutBtn.setOnAction(e -> {
                    AttendanceRecord r = getTableView().getItems().get(getIndex());
                    recordCheckOut(r.id);
                    loadData(datePicker.getValue());
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                AttendanceRecord r = empty ? null : getTableView().getItems().get(getIndex());
                if (r != null) checkoutBtn.setDisable(r.checkOut != null);
                setGraphic(empty ? null : box);
            }
        });

        tv.getColumns().addAll(empCol, dateCol, inCol, outCol, hoursCol, statusCol, actCol);
        tv.setItems(data);
        return tv;
    }

    // ─── Data Loading ─────────────────────────────────────────────────────
    private void loadData(LocalDate date) {
        data.clear();
        List<Employee> employees = employeeDAO.findAll();
        List<AttendanceRecord> records = getAttendanceForDate(date);

        for (Employee emp : employees) {
            AttendanceRecord existing = records.stream()
                .filter(r -> r.employeeId == emp.getId()).findFirst().orElse(null);

            if (existing != null) {
                data.add(existing);
            } else {
                // Not yet recorded today
                AttendanceRecord r = new AttendanceRecord();
                r.employeeId   = emp.getId();
                r.employeeName = emp.getName();
                r.department   = emp.getDepartmentName();
                r.date         = date;
                r.status       = "absent";
                data.add(r);
            }
        }
    }

    private List<AttendanceRecord> getAttendanceForDate(LocalDate date) {
        List<AttendanceRecord> list = new ArrayList<>();
        String sql = """
            SELECT a.*, e.name as emp_name, d.name as dept_name
            FROM attendance a
            JOIN employees e ON a.employee_id = e.id
            LEFT JOIN departments d ON e.department_id = d.id
            WHERE a.date = ?
        """;
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                AttendanceRecord r = new AttendanceRecord();
                r.id           = rs.getInt("id");
                r.employeeId   = rs.getInt("employee_id");
                r.employeeName = rs.getString("emp_name");
                r.department   = rs.getString("dept_name");
                r.date         = date;
                r.status       = rs.getString("status");
                r.notes        = rs.getString("notes");
                try { String ci = rs.getString("check_in");  if (ci != null) r.checkIn  = LocalTime.parse(ci); } catch (Exception ignored) {}
                try { String co = rs.getString("check_out"); if (co != null) r.checkOut = LocalTime.parse(co); } catch (Exception ignored) {}
                list.add(r);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    private void saveAttendance(AttendanceRecord r) {
        String sql = r.id > 0
            ? "UPDATE attendance SET status=?, check_in=?, check_out=?, notes=? WHERE id=?"
            : "INSERT OR REPLACE INTO attendance (employee_id, date, status, check_in, check_out, notes) VALUES (?,?,?,?,?,?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (r.id > 0) {
                ps.setString(1, r.status);
                ps.setString(2, r.checkIn  != null ? r.checkIn.format(TIME_FMT)  : null);
                ps.setString(3, r.checkOut != null ? r.checkOut.format(TIME_FMT) : null);
                ps.setString(4, r.notes);
                ps.setInt(5, r.id);
            } else {
                ps.setInt(1, r.employeeId);
                ps.setString(2, r.date.toString());
                ps.setString(3, r.status);
                ps.setString(4, r.checkIn  != null ? r.checkIn.format(TIME_FMT)  : null);
                ps.setString(5, r.checkOut != null ? r.checkOut.format(TIME_FMT) : null);
                ps.setString(6, r.notes);
            }
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void recordCheckOut(int attendanceId) {
        if (attendanceId <= 0) return;
        String sql = "UPDATE attendance SET check_out=? WHERE id=?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, LocalTime.now().format(TIME_FMT));
            ps.setInt(2, attendanceId);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void markAllPresent() {
        LocalDate date = datePicker.getValue();
        LocalTime now  = LocalTime.now();
        List<Employee> employees = employeeDAO.findAll();
        for (Employee emp : employees) {
            AttendanceRecord r = new AttendanceRecord();
            r.employeeId   = emp.getId();
            r.employeeName = emp.getName();
            r.date         = date;
            r.checkIn      = now;
            r.status       = now.isAfter(LocalTime.of(8, 30)) ? "late" : "present";
            saveAttendance(r);
        }
        loadData(date);
    }

    private int countByStatus(LocalDate date, String status) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM attendance WHERE date=? AND status=?")) {
            ps.setString(1, date.toString());
            ps.setString(2, status);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    // ─── Record Dialog ────────────────────────────────────────────────────
    private void showRecordDialog(AttendanceRecord existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null || existing.id <= 0 ? "Record Attendance" : "Edit Attendance");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(440);

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));

        // Employee picker (only if new)
        ComboBox<String> empCombo = new ComboBox<>();
        List<Employee> emps = employeeDAO.findAll();
        emps.forEach(e -> empCombo.getItems().add(e.getId() + ":" + e.getName()));
        if (!empCombo.getItems().isEmpty()) empCombo.setValue(empCombo.getItems().get(0));
        if (existing != null) empCombo.setValue(existing.employeeId + ":" + existing.employeeName);
        empCombo.setDisable(existing != null && existing.id > 0);

        DatePicker dp = new DatePicker(existing != null && existing.date != null ? existing.date : LocalDate.now());

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("present", "absent", "late", "on-leave");
        statusCombo.setValue(existing != null ? existing.status : "present");

        TextField checkInField = new TextField(existing != null && existing.checkIn != null ? existing.checkIn.format(TIME_FMT) : LocalTime.now().format(TIME_FMT));
        checkInField.setPromptText("HH:mm");
        TextField checkOutField = new TextField(existing != null && existing.checkOut != null ? existing.checkOut.format(TIME_FMT) : "");
        checkOutField.setPromptText("HH:mm (optional)");
        TextField notesField = new TextField(existing != null && existing.notes != null ? existing.notes : "");

        String fs = "-fx-pref-height: 34px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;";
        checkInField.setStyle(fs); checkOutField.setStyle(fs); notesField.setStyle(fs);

        form.addRow(0, lbl("Employee"), empCombo, lbl("Date"), dp);
        form.addRow(1, lbl("Status"), statusCombo, lbl("Check In"), checkInField);
        form.addRow(2, lbl("Check Out"), checkOutField, lbl("Notes"), notesField);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                AttendanceRecord r = existing != null ? existing : new AttendanceRecord();
                if (existing == null || existing.id <= 0) {
                    String[] parts = empCombo.getValue().split(":");
                    r.employeeId = Integer.parseInt(parts[0]);
                }
                r.date   = dp.getValue();
                r.status = statusCombo.getValue();
                try { r.checkIn  = LocalTime.parse(checkInField.getText());  } catch (Exception ignored) {}
                try { r.checkOut = LocalTime.parse(checkOutField.getText()); } catch (Exception ignored) {}
                r.notes = notesField.getText();
                saveAttendance(r);
                loadData(datePicker.getValue());
            }
        });
    }

    private Label lbl(String text) {
        Label l = new Label(text); l.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;"); return l;
    }
}
