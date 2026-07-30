package com.kaziflow.views;

import com.kaziflow.dao.EmployeeDAO;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.Employee;
import com.kaziflow.utils.AsyncTask;
import com.kaziflow.utils.KESFormatter;
import com.kaziflow.utils.SceneManager;
import com.kaziflow.utils.SessionManager;
import com.kaziflow.utils.Toast;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class EmployeesView {

    private VBox root;
    private ObservableList<Employee> employeeData = FXCollections.observableArrayList();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private static final String CARD = "-fx-background-color:white;-fx-background-radius:12;-fx-border-color:#e2e8f0;-fx-border-radius:12;-fx-border-width:1;";

    public EmployeesView() { buildUI(); }
    public VBox getRoot() { return root; }

    private void buildUI() {
        root = new VBox(0); root.setStyle("-fx-background-color:#f8fafc;");

        HBox tabBar = new HBox(0);
        tabBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;-fx-padding:0 24;");

        Button empTab   = tabBtn("Employee List",  true);
        Button attnTab  = tabBtn("Attendance",     false);
        Button leaveTab = tabBtn("Leave Requests", false);

        StackPane area = new StackPane(); area.setStyle("-fx-background-color:#f8fafc;"); VBox.setVgrow(area, Priority.ALWAYS);

        VBox empView   = buildEmployeeList();
        VBox attnView  = buildAttendance();
        VBox leaveView = buildLeaveRequests();

        area.getChildren().setAll(empView);
        AsyncTask.run(employeeDAO::findAll, employeeData::setAll, err -> {});

        empTab  .setOnAction(e -> { area.getChildren().setAll(empView);   setActive(empTab, attnTab, leaveTab); AsyncTask.run(employeeDAO::findAll, employeeData::setAll, err -> {}); });
        attnTab .setOnAction(e -> { area.getChildren().setAll(attnView);  setActive(attnTab, empTab, leaveTab); });
        leaveTab.setOnAction(e -> { area.getChildren().setAll(leaveView); setActive(leaveTab, empTab, attnTab); });

        tabBar.getChildren().addAll(empTab, attnTab, leaveTab);
        root.getChildren().addAll(tabBar, area);
    }

    private void setActive(Button a, Button... rest) { applyTab(a,true); for(Button b:rest) applyTab(b,false); }
    private Button tabBtn(String label, boolean active) { Button b=new Button(label); applyTab(b,active); return b; }
    private void applyTab(Button b, boolean active) {
        if(active) b.setStyle("-fx-background-color:transparent;-fx-text-fill:#2563eb;-fx-font-size:13px;-fx-font-weight:bold;-fx-padding:14 16;-fx-border-color:transparent transparent #2563eb transparent;-fx-border-width:0 0 2 0;-fx-cursor:hand;");
        else       b.setStyle("-fx-background-color:transparent;-fx-text-fill:#94a3b8;-fx-font-size:13px;-fx-padding:14 16;-fx-border-color:transparent;-fx-cursor:hand;");
    }

    // ═══ TAB 1 · EMPLOYEE LIST ════════════════════════════════════════════════

    private VBox buildEmployeeList() {
        VBox view = new VBox(0); view.setStyle("-fx-background-color:#f8fafc;");
        Button addBtn = new Button("+ Add Employee");
        addBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        addBtn.setOnAction(e -> showAddEditDialog(null));
        view.getChildren().addAll(pageHeader("Employee List","Employees › List",addBtn), buildEmployeeBody());
        return view;
    }

    private VBox buildEmployeeBody() {
        VBox content = new VBox(20); content.setPadding(new Insets(24));
        int total = employeeDAO.getTotalCount();
        double payroll = employeeDAO.getTotalMonthlySalary();

        HBox stats = statsRow(
            statCard("Total Employees",  String.valueOf(total),                    "4 departments",   "#1e293b"),
            statCard("Active Today",     String.valueOf(total),                    "81% rate",        "#16a34a"),
            statCard("Pending Actions",  "3",                                      "Leave requests",  "#d97706"),
            statCard("Monthly Payroll",  KESFormatter.formatShort(payroll),        "October 2024",    "#7c3aed")
        );

        HBox filterBar = new HBox(10); filterBar.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField(); search.setPromptText("Search employees..."); search.getStyleClass().add("search-box"); search.setPrefWidth(280);
        search.textProperty().addListener((obs,o,v)->employeeData.setAll(v.isBlank()?employeeDAO.findAll():employeeDAO.search(v)));
        ComboBox<String> deptCombo = new ComboBox<>();
        deptCombo.getItems().addAll("All Departments","Management","Sales","Workshop","Operations","Finance"); deptCombo.setValue("All Departments"); deptCombo.setPrefHeight(36);
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("All Status","Active","On Leave","Inactive"); statusCombo.setValue("All Status"); statusCombo.setPrefHeight(36);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button exportBtn = new Button("⬇ Export Report");
        exportBtn.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-pref-height:36px;-fx-padding:0 14;-fx-cursor:hand;-fx-font-size:13px;");
        exportBtn.setOnAction(e -> new com.kaziflow.services.ReportExportService().exportCSV(com.kaziflow.services.ReportExportService.ReportType.EMPLOYEES));
        filterBar.getChildren().addAll(search, deptCombo, statusCombo, sp, exportBtn);

        VBox tableCard = new VBox(0); tableCard.setStyle(CARD);
        TableView<Employee> tv = buildEmployeeTable();
        tableCard.getChildren().add(tv);

        content.getChildren().addAll(stats, filterBar, tableCard);
        return content;
    }

    @SuppressWarnings("unchecked")
    private TableView<Employee> buildEmployeeTable() {
        TableView<Employee> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(440); tv.setStyle("-fx-background-color:white;"); tv.setItems(employeeData);

        TableColumn<Employee,String> nameCol = new TableColumn<>("EMPLOYEE");
        nameCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getName()));
        nameCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String n, boolean empty){
                super.updateItem(n,empty); if(empty||n==null){setGraphic(null);return;}
                Employee e=getTableView().getItems().get(getIndex()); HBox row=new HBox(10); row.setAlignment(Pos.CENTER_LEFT);
                StackPane avatar=new StackPane(); Circle circle=new Circle(18); circle.setFill(Color.web("#2563eb"));
                Label init=new Label(e.getName().isEmpty()?"?":String.valueOf(e.getName().charAt(0)).toUpperCase()); init.setStyle("-fx-text-fill:white;-fx-font-size:12px;-fx-font-weight:bold;");
                avatar.getChildren().addAll(circle,init);
                VBox info=new VBox(1); Label name=new Label(e.getName()); name.setStyle("-fx-font-weight:bold;-fx-font-size:13px;-fx-text-fill:#1e293b;");
                Label num=new Label(e.getEmployeeNumber()!=null?e.getEmployeeNumber():"—"); num.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;");
                info.getChildren().addAll(name,num); row.getChildren().addAll(avatar,info); setGraphic(row); setText(null);
            }
        }); nameCol.setPrefWidth(200);

        TableColumn<Employee,String> deptCol = new TableColumn<>("DEPARTMENT");
        deptCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getDepartmentName()!=null?d.getValue().getDepartmentName():"—"));
        deptCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String dept, boolean empty){
                super.updateItem(dept,empty); if(empty||dept==null){setGraphic(null);return;}
                String[] clrs={"Management:#7c3aed:#ede9fe","Sales:#2563eb:#eff6ff","Workshop:#d97706:#fef3c7","Operations:#16a34a:#dcfce7","Finance:#0891b2:#e0f2fe"};
                String fg="#475569", bg="#f1f5f9";
                for(String cc:clrs){String[]p=cc.split(":");if(dept.equals(p[0])){fg=p[1];bg=p[2];break;}}
                Label badge=new Label(dept); badge.setStyle("-fx-background-color:"+bg+";-fx-text-fill:"+fg+";-fx-font-size:11px;-fx-background-radius:20;-fx-padding:3 10;-fx-font-weight:bold;");
                setGraphic(badge); setText(null);
            }
        }); deptCol.setPrefWidth(130);

        TableColumn<Employee,String> posCol = new TableColumn<>("POSITION");
        posCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getPosition()!=null?d.getValue().getPosition():"—")); posCol.setPrefWidth(160);

        TableColumn<Employee,String> contactCol = new TableColumn<>("CONTACT");
        contactCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(""));
        contactCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String s, boolean empty){
                super.updateItem(s,empty); if(empty){setGraphic(null);return;}
                Employee e=getTableView().getItems().get(getIndex()); VBox box=new VBox(1);
                Label phone=new Label(e.getPhone()!=null?e.getPhone():"—"); phone.setStyle("-fx-font-size:12px;-fx-text-fill:#1e293b;");
                Label email=new Label(e.getEmail()!=null?e.getEmail():"—"); email.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");
                box.getChildren().addAll(phone,email); setGraphic(box); setText(null);
            }
        }); contactCol.setPrefWidth(190);

        TableColumn<Employee,String> salaryCol = new TableColumn<>("SALARY");
        salaryCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(KESFormatter.format(d.getValue().getSalary())));
        salaryCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String s, boolean empty){
                super.updateItem(s,empty); if(empty||s==null){setText(null);return;}
                Label l=new Label(s); l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;-fx-text-fill:#1e293b;"); setGraphic(l); setText(null);
            }
        }); salaryCol.setPrefWidth(120);

        TableColumn<Employee,String> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getStatus()!=null?d.getValue().getStatus():"active"));
        statusCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String s, boolean empty){
                super.updateItem(s,empty); if(empty||s==null){setGraphic(null);return;}
                String color=s.equals("active")?"#16a34a":"#94a3b8";
                Label l=new Label("● "+s.substring(0,1).toUpperCase()+s.substring(1)); l.setStyle("-fx-text-fill:"+color+";-fx-font-size:12px;-fx-font-weight:bold;");
                setGraphic(l); setText(null);
            }
        }); statusCol.setPrefWidth(80);

        TableColumn<Employee,Void> actCol = new TableColumn<>(""); actCol.setPrefWidth(70);
        actCol.setCellFactory(c->new TableCell<>(){
            private final Button editBtn=new Button("✎ Edit");
            {editBtn.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:5;-fx-background-radius:5;-fx-text-fill:#475569;-fx-cursor:hand;-fx-font-size:11px;-fx-padding:4 8;");
             editBtn.setOnAction(e->showAddEditDialog(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);setGraphic(empty?null:editBtn);}
        });

        tv.getColumns().addAll(nameCol,deptCol,posCol,contactCol,salaryCol,statusCol,actCol);
        return tv;
    }

    // ═══ TAB 2 · ATTENDANCE ══════════════════════════════════════════════════

    private VBox buildAttendance() {
        VBox view = new VBox(0); view.setStyle("-fx-background-color:#f8fafc;");

        HBox topbar = new HBox(); topbar.setAlignment(Pos.CENTER_LEFT); topbar.setSpacing(10);
        topbar.setPadding(new Insets(16,24,16,24));
        topbar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        VBox tb = new VBox(2);
        Label h = new Label("Attendance Tracking"); h.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label bc = new Label("Employees › Attendance"); bc.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12px;");
        tb.getChildren().addAll(h,bc);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button recordBtn = new Button("+ Record Attendance");
        recordBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        recordBtn.setOnAction(e -> showRecordAttendanceDialog());
        topbar.getChildren().addAll(tb, sp, recordBtn);

        VBox content = new VBox(20); content.setPadding(new Insets(24));

        // Stats
        HBox stats = statsRow(
            statCard("Present Today",   String.valueOf(employeeDAO.getTotalCount()), "All staff",         "#16a34a"),
            statCard("Absent Today",    "0",                                         "—",                 "#dc2626"),
            statCard("Late Arrivals",   "2",                                         "After 8:00 AM",     "#d97706"),
            statCard("On Leave",        "1",                                         "Approved leave",    "#7c3aed")
        );

        // Date selector + filter bar
        HBox filterBar = new HBox(10); filterBar.setAlignment(Pos.CENTER_LEFT);
        DatePicker datePicker = new DatePicker(LocalDate.now()); datePicker.setPrefWidth(160);
        ComboBox<String> deptFilter = new ComboBox<>();
        deptFilter.getItems().addAll("All Departments","Management","Sales","Workshop","Operations","Finance"); deptFilter.setValue("All Departments"); deptFilter.setPrefHeight(36);
        filterBar.getChildren().addAll(lbl("Date:"), datePicker, deptFilter);

        // Today's attendance table
        VBox tableCard = new VBox(0); tableCard.setStyle(CARD);
        TableView<String[]> tv = buildAttendanceTable();
        tableCard.getChildren().add(tv);

        // Weekly summary chart
        VBox weekCard = new VBox(12); weekCard.setStyle(CARD + "-fx-padding:20;");
        Label weekTitle = new Label("Weekly Attendance Summary"); weekTitle.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        javafx.scene.chart.CategoryAxis xAxis = new javafx.scene.chart.CategoryAxis();
        xAxis.setCategories(FXCollections.observableArrayList("Mon","Tue","Wed","Thu","Fri","Sat"));
        javafx.scene.chart.NumberAxis yAxis = new javafx.scene.chart.NumberAxis(0,30,5);
        javafx.scene.chart.BarChart<String,Number> barChart = new javafx.scene.chart.BarChart<>(xAxis,yAxis);
        barChart.setAnimated(false); barChart.setPrefHeight(200); barChart.setStyle("-fx-background-color:transparent;");
        barChart.setLegendVisible(true);
        javafx.scene.chart.XYChart.Series<String,Number> presentSeries = new javafx.scene.chart.XYChart.Series<>();
        presentSeries.setName("Present");
        int empCount = employeeDAO.getTotalCount();
        String[] days = {"Mon","Tue","Wed","Thu","Fri","Sat"};
        int[] pres = {empCount, empCount-1, empCount-2, empCount, empCount, empCount/2};
        for(int i=0;i<days.length;i++) presentSeries.getData().add(new javafx.scene.chart.XYChart.Data<>(days[i],(Number)pres[i]));
        barChart.getData().add(presentSeries);
        weekCard.getChildren().addAll(weekTitle, barChart);

        content.getChildren().addAll(stats, filterBar, tableCard, weekCard);
        view.getChildren().addAll(topbar, content);
        return view;
    }

    @SuppressWarnings("unchecked")
    private TableView<String[]> buildAttendanceTable() {
        TableView<String[]> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(300); tv.setStyle("-fx-background-color:white;");

        // Populate with employee list
        ObservableList<String[]> data = FXCollections.observableArrayList();
        List<Employee> emps = employeeDAO.findAll();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        for (int i=0; i<emps.size(); i++) {
            Employee e = emps.get(i);
            String status = i==emps.size()-1?"on leave":i%7==3?"late":"present";
            String checkIn = "present".equals(status)||"late".equals(status) ? (i%7==3?"08:32":"07:55") : "—";
            String checkOut = "present".equals(status)||"late".equals(status) ? "17:10" : "—";
            data.add(new String[]{e.getName(), e.getEmployeeNumber()!=null?e.getEmployeeNumber():"—", e.getDepartmentName()!=null?e.getDepartmentName():"—", today, checkIn, checkOut, status});
        }
        tv.setItems(data);

        String[] cols = {"EMPLOYEE","EMP #","DEPARTMENT","DATE","CHECK-IN","CHECK-OUT","STATUS"};
        int[] widths  = {180,       90,      130,         110,  90,         90,          100};
        for (int i=0; i<cols.length; i++) {
            final int idx = i;
            TableColumn<String[],String> col = new TableColumn<>(cols[i]);
            col.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue()[idx]));
            col.setPrefWidth(widths[i]);
            if (i==6) col.setCellFactory(c->new TableCell<>(){
                @Override protected void updateItem(String s, boolean empty){
                    super.updateItem(s,empty); if(empty||s==null){setGraphic(null);return;}
                    String bg,fg;
                    switch(s){case"present"->{bg="#dcfce7";fg="#16a34a";}case"late"->{bg="#fef3c7";fg="#d97706";}case"absent"->{bg="#fee2e2";fg="#dc2626";}default->{bg="#ede9fe";fg="#7c3aed";}}
                    Label badge=new Label(s.substring(0,1).toUpperCase()+s.substring(1)); badge.setStyle("-fx-background-color:"+bg+";-fx-text-fill:"+fg+";-fx-font-size:11px;-fx-background-radius:20;-fx-padding:3 10;-fx-font-weight:bold;");
                    setGraphic(badge); setText(null);
                }
            });
            tv.getColumns().add(col);
        }
        return tv;
    }

    private void showRecordAttendanceDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Record Attendance"); dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color:white;"); dialog.getDialogPane().setPrefWidth(460);

        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));

        ComboBox<String> empCombo = new ComboBox<>();
        employeeDAO.findAll().forEach(e->empCombo.getItems().add(e.getId()+":"+e.getName()));
        if (!empCombo.getItems().isEmpty()) empCombo.setValue(empCombo.getItems().get(0));
        empCombo.setPrefWidth(200);

        DatePicker datePicker = new DatePicker(LocalDate.now()); datePicker.setPrefWidth(200);
        TextField checkInField  = fld("e.g. 08:00", "08:00");
        TextField checkOutField = fld("e.g. 17:00", "17:00");

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("present","late","absent","half-day"); statusCombo.setValue("present"); statusCombo.setPrefWidth(200);

        TextArea notes = new TextArea(); notes.setPromptText("Notes (optional)"); notes.setPrefRowCount(2); notes.setPrefWidth(200);
        notes.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;");

        form.addRow(0, lbl("Employee"),   empCombo,    lbl("Date"),     datePicker);
        form.addRow(1, lbl("Status"),     statusCombo, lbl("Check-In"), checkInField);
        form.addRow(2, lbl("Check-Out"),  checkOutField, lbl("Notes"),  notes);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result==ButtonType.OK && empCombo.getValue()!=null) {
                try {
                    int empId = Integer.parseInt(empCombo.getValue().split(":")[0]);
                    try (Connection conn = DatabaseManager.getInstance().getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                             "INSERT OR REPLACE INTO attendance (employee_id, date, status, check_in, check_out, notes) VALUES (?,?,?,?,?,?)")) {
                        ps.setInt(1, empId);
                        ps.setString(2, datePicker.getValue().toString());
                        ps.setString(3, statusCombo.getValue());
                        ps.setString(4, checkInField.getText().trim());
                        ps.setString(5, checkOutField.getText().trim());
                        ps.setString(6, notes.getText().trim());
                        ps.executeUpdate();
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    // ═══ TAB 3 · LEAVE REQUESTS ═══════════════════════════════════════════════

    private ObservableList<String[]> leaveData = FXCollections.observableArrayList();

    private VBox buildLeaveRequests() {
        VBox view = new VBox(0); view.setStyle("-fx-background-color:#f8fafc;");

        HBox topbar = new HBox(); topbar.setAlignment(Pos.CENTER_LEFT); topbar.setSpacing(10);
        topbar.setPadding(new Insets(16,24,16,24));
        topbar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        VBox tb = new VBox(2);
        Label h = new Label("Leave Requests"); h.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label bc = new Label("Employees › Leave"); bc.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12px;");
        tb.getChildren().addAll(h,bc);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button applyBtn = new Button("+ Apply for Leave");
        applyBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        applyBtn.setOnAction(e -> showApplyLeaveDialog());
        topbar.getChildren().addAll(tb, sp, applyBtn);

        VBox content = new VBox(20); content.setPadding(new Insets(24));

        HBox stats = statsRow(
            statCard("Pending Requests",  "3",  "Awaiting approval", "#d97706"),
            statCard("Approved",          "12", "This year",         "#16a34a"),
            statCard("Rejected",          "2",  "This year",         "#dc2626"),
            statCard("Currently on Leave","1",  "Active now",        "#7c3aed")
        );

        // Filter
        HBox filterBar = new HBox(10); filterBar.setAlignment(Pos.CENTER_LEFT);
        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All Status","pending","approved","rejected"); statusFilter.setValue("All Status"); statusFilter.setPrefHeight(36);
        ComboBox<String> typeFilter = new ComboBox<>();
        typeFilter.getItems().addAll("All Types","Annual Leave","Sick Leave","Maternity","Paternity","Emergency"); typeFilter.setValue("All Types"); typeFilter.setPrefHeight(36);
        filterBar.getChildren().addAll(statusFilter, typeFilter);

        VBox tableCard = new VBox(0); tableCard.setStyle(CARD);
        TableView<String[]> tv = buildLeaveTable();
        tableCard.getChildren().add(tv);
        loadLeaveRequests();

        content.getChildren().addAll(stats, filterBar, tableCard);
        view.getChildren().addAll(topbar, content);
        return view;
    }

    @SuppressWarnings("unchecked")
    private TableView<String[]> buildLeaveTable() {
        TableView<String[]> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(440); tv.setStyle("-fx-background-color:white;"); tv.setItems(leaveData);

        String[] cols = {"EMPLOYEE","LEAVE TYPE","FROM","TO","DAYS","REASON","STATUS"};
        int[] widths  = {180,       130,          100,  100, 60,    200,      100};
        for (int i=0; i<cols.length-1; i++) {
            final int idx = i;
            TableColumn<String[],String> col = new TableColumn<>(cols[i]);
            col.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue()[idx]));
            col.setPrefWidth(widths[i]);
            tv.getColumns().add(col);
        }

        // Status column with badge + action buttons
        TableColumn<String[],Void> statusActCol = new TableColumn<>(cols[6]);
        statusActCol.setPrefWidth(160);
        statusActCol.setCellFactory(c->new TableCell<>(){
            private final Button approveBtn = new Button("✓");
            private final Button rejectBtn  = new Button("✕");
            private final HBox box = new HBox(6);
            {
                approveBtn.setStyle("-fx-background-color:#dcfce7;-fx-text-fill:#16a34a;-fx-background-radius:5;-fx-cursor:hand;-fx-font-size:12px;-fx-padding:3 8;-fx-border-color:transparent;");
                rejectBtn .setStyle("-fx-background-color:#fee2e2;-fx-text-fill:#dc2626;-fx-background-radius:5;-fx-cursor:hand;-fx-font-size:12px;-fx-padding:3 8;-fx-border-color:transparent;");
                approveBtn.setOnAction(e->{
                    String[] row=getTableView().getItems().get(getIndex());
                    updateLeaveStatus(row, "approved"); loadLeaveRequests();
                });
                rejectBtn.setOnAction(e->{
                    String[] row=getTableView().getItems().get(getIndex());
                    updateLeaveStatus(row, "rejected"); loadLeaveRequests();
                });
            }
            @Override protected void updateItem(Void v, boolean empty){
                super.updateItem(v,empty); if(empty){setGraphic(null);return;}
                String[] row = getTableView().getItems().get(getIndex());
                String status = row.length>6?row[6]:"pending";
                box.getChildren().clear();
                if ("pending".equals(status)) {
                    box.getChildren().addAll(approveBtn, rejectBtn);
                } else {
                    String bg, fg;
                    switch(status){case"approved"->{bg="#dcfce7";fg="#16a34a";}case"rejected"->{bg="#fee2e2";fg="#dc2626";}default->{bg="#fef3c7";fg="#d97706";}}
                    Label badge=new Label(status.substring(0,1).toUpperCase()+status.substring(1)); badge.setStyle("-fx-background-color:"+bg+";-fx-text-fill:"+fg+";-fx-font-size:11px;-fx-background-radius:20;-fx-padding:3 10;-fx-font-weight:bold;");
                    box.getChildren().add(badge);
                }
                setGraphic(box); setText(null);
            }
        });
        tv.getColumns().add(statusActCol);
        return tv;
    }

    private void updateLeaveStatus(String[] row, String newStatus) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE leave_requests SET status=? WHERE employee_id=(SELECT id FROM employees WHERE name=?) AND leave_type=? AND start_date=?")) {
            ps.setString(1, newStatus);
            ps.setString(2, row[0]);
            ps.setString(3, row[1]);
            ps.setString(4, row[2]);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
        row[6] = newStatus;
    }

    private void loadLeaveRequests() {
        leaveData.clear();
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT e.name, lr.leave_type, lr.start_date, lr.end_date, lr.days_count, lr.reason, lr.status FROM leave_requests lr JOIN employees e ON lr.employee_id=e.id ORDER BY lr.created_at DESC LIMIT 50");
            while (rs.next()) {
                leaveData.add(new String[]{
                    rs.getString("name"),
                    rs.getString("leave_type"),
                    rs.getString("start_date"),
                    rs.getString("end_date"),
                    String.valueOf(rs.getInt("days_count")),
                    rs.getString("reason")!=null?rs.getString("reason"):"—",
                    rs.getString("status")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }

        // Seed demo data if empty
        if (leaveData.isEmpty()) {
            List<Employee> emps = employeeDAO.findAll();
            if (!emps.isEmpty()) {
                leaveData.addAll(
                    new String[]{emps.size()>0?emps.get(0).getName():"John Doe", "Annual Leave", "2024-11-01","2024-11-05","5","Family vacation","pending"},
                    new String[]{emps.size()>1?emps.get(1).getName():"Jane Smith","Sick Leave",   "2024-10-20","2024-10-22","3","Medical treatment","approved"},
                    new String[]{emps.size()>2?emps.get(2).getName():"Mike Otieno","Emergency",   "2024-10-15","2024-10-16","2","Family emergency","approved"}
                );
            }
        }
    }

    private void showApplyLeaveDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Apply for Leave"); dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color:white;"); dialog.getDialogPane().setPrefWidth(460);

        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));

        ComboBox<String> empCombo = new ComboBox<>();
        employeeDAO.findAll().forEach(e->empCombo.getItems().add(e.getId()+":"+e.getName()));
        if (!empCombo.getItems().isEmpty()) empCombo.setValue(empCombo.getItems().get(0));
        empCombo.setPrefWidth(200);

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("Annual Leave","Sick Leave","Maternity Leave","Paternity Leave","Emergency Leave","Unpaid Leave");
        typeCombo.setValue("Annual Leave"); typeCombo.setPrefWidth(200);

        DatePicker startDate = new DatePicker(LocalDate.now().plusDays(1)); startDate.setPrefWidth(200);
        DatePicker endDate   = new DatePicker(LocalDate.now().plusDays(5)); endDate.setPrefWidth(200);

        TextArea reasonArea = new TextArea(); reasonArea.setPromptText("Reason for leave request..."); reasonArea.setPrefRowCount(3); reasonArea.setPrefWidth(420);
        reasonArea.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;");

        Label validMsg = new Label(""); validMsg.setStyle("-fx-text-fill:#dc2626;-fx-font-size:12px;");

        form.addRow(0, lbl("Employee *"),   empCombo,  lbl("Leave Type *"), typeCombo);
        form.addRow(1, lbl("Start Date *"), startDate, lbl("End Date *"),   endDate);
        form.addRow(2, lbl("Reason *"),     reasonArea);
        form.add(validMsg, 0, 3, 4, 1);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtn=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK); okBtn.setText("Submit Request");

        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev->{
            if(reasonArea.getText().trim().isEmpty()||empCombo.getValue()==null||startDate.getValue()==null||endDate.getValue()==null){
                validMsg.setText("Please fill in all required fields."); ev.consume();
            } else if (!endDate.getValue().isAfter(startDate.getValue().minusDays(1))) {
                validMsg.setText("End date must be after start date."); ev.consume();
            }
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result==ButtonType.OK) {
                try {
                    int empId = Integer.parseInt(empCombo.getValue().split(":")[0]);
                    long days = startDate.getValue().until(endDate.getValue()).getDays() + 1;
                    try (Connection conn = DatabaseManager.getInstance().getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO leave_requests (employee_id, leave_type, start_date, end_date, days_count, reason, status) VALUES (?,?,?,?,?,?,'pending')")) {
                        ps.setInt(1, empId); ps.setString(2, typeCombo.getValue());
                        ps.setString(3, startDate.getValue().toString()); ps.setString(4, endDate.getValue().toString());
                        ps.setLong(5, days); ps.setString(6, reasonArea.getText().trim());
                        ps.executeUpdate();
                    }
                    loadLeaveRequests();
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    // ═══ SHARED: Add/Edit Employee Dialog ════════════════════════════════════

    private void showAddEditDialog(Employee existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing==null?"Add Employee":"Edit Employee"); dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color:white;"); dialog.getDialogPane().setPrefWidth(480);

        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));
        TextField nameField   = fld("Full Name", existing!=null?existing.getName():"");
        TextField phoneField  = fld("Phone",     existing!=null&&existing.getPhone()!=null?existing.getPhone():"");
        TextField emailField  = fld("Email",     existing!=null&&existing.getEmail()!=null?existing.getEmail():"");
        TextField posField    = fld("Position",  existing!=null&&existing.getPosition()!=null?existing.getPosition():"");
        TextField salaryField = fld("Monthly Salary (KES)", existing!=null?String.valueOf((int)existing.getSalary()):"0");

        ComboBox<String> deptCombo = new ComboBox<>();
        List<String[]> depts = employeeDAO.getDepartments();
        depts.forEach(d->deptCombo.getItems().add(d[0]+":"+d[1]));
        if (!deptCombo.getItems().isEmpty()) deptCombo.setValue(deptCombo.getItems().get(0));
        if (existing!=null) depts.forEach(d->{ if(d[0].equals(String.valueOf(existing.getDepartmentId()))) deptCombo.setValue(d[0]+":"+d[1]); });

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("full-time","part-time","contract");
        typeCombo.setValue(existing!=null&&existing.getEmploymentType()!=null?existing.getEmploymentType():"full-time");

        Label validMsg = new Label(""); validMsg.setStyle("-fx-text-fill:#dc2626;-fx-font-size:12px;");

        form.addRow(0, lbl("Full Name *"), nameField,   lbl("Phone"),      phoneField);
        form.addRow(1, lbl("Email"),       emailField,  lbl("Position"),   posField);
        form.addRow(2, lbl("Department"),  deptCombo,   lbl("Type"),       typeCombo);
        form.addRow(3, lbl("Salary"),      salaryField);
        form.add(validMsg, 0, 4, 4, 1);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtn=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev->{
            if(nameField.getText().trim().isEmpty()){validMsg.setText("Employee name is required."); nameField.setStyle(nameField.getStyle()+"-fx-border-color:#dc2626;"); ev.consume();}
        });

        dialog.showAndWait().ifPresent(result -> {
            if(result==ButtonType.OK) {
                Employee e=existing!=null?existing:new Employee();
                e.setName(nameField.getText().trim()); e.setPhone(phoneField.getText().trim());
                e.setEmail(emailField.getText().trim()); e.setPosition(posField.getText().trim());
                e.setEmploymentType(typeCombo.getValue()); e.setStatus("active");
                try{e.setSalary(Double.parseDouble(salaryField.getText().trim()));}catch(Exception ignored){}
                if(deptCombo.getValue()!=null&&!deptCombo.getValue().isEmpty()){
                    try{e.setDepartmentId(Integer.parseInt(deptCombo.getValue().split(":")[0]));}catch(Exception ignored){}
                }
                boolean ok=existing==null?employeeDAO.save(e):employeeDAO.update(e);
                if(ok) {
                    com.kaziflow.services.AuditLog.log(existing==null?"EMPLOYEE_CREATED":"EMPLOYEE_UPDATED",
                        (existing==null?"New employee: ":"Updated employee: ") + e.getName(), "employees", null);
                    Toast.success(SceneManager.getInstance().getStage(),
                        existing==null?"Employee added":"Employee updated", e.getName());
                    AsyncTask.run(employeeDAO::findAll, employeeData::setAll, err -> {});
                } else {
                    Toast.error(SceneManager.getInstance().getStage(), "Save failed", "Could not save employee record.");
                }
            }
        });
    }

    // ═══ SHARED HELPERS ═══════════════════════════════════════════════════════

    private HBox pageHeader(String title, String breadcrumb, Button action) {
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16,24,16,24));
        bar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        VBox tb = new VBox(2);
        Label h = new Label(title); h.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label bc = new Label(breadcrumb); bc.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12px;");
        tb.getChildren().addAll(h,bc);
        Region sp = new Region(); HBox.setHgrow(sp,Priority.ALWAYS);
        bar.getChildren().addAll(tb,sp);
        if(action!=null) bar.getChildren().add(action);
        return bar;
    }

    private HBox statsRow(VBox... cards) {
        HBox row = new HBox(16);
        for (VBox c : cards) { row.getChildren().add(c); HBox.setHgrow(c, Priority.ALWAYS); }
        return row;
    }

    private VBox statCard(String label, String value, String note, String color) {
        VBox card = new VBox(6); card.setStyle(CARD+"-fx-padding:20;");
        Label lbl = new Label(label); lbl.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12px;-fx-font-weight:bold;");
        Label val = new Label(value); val.setStyle("-fx-font-size:26px;-fx-font-weight:bold;-fx-text-fill:"+color+";");
        Label nt  = new Label(note);  nt .setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12px;");
        card.getChildren().addAll(lbl,val,nt); return card;
    }

    private TextField fld(String prompt, String val) {
        TextField tf = new TextField(val); tf.setPromptText(prompt); tf.setPrefWidth(200);
        tf.setStyle("-fx-pref-height:34px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");
        return tf;
    }

    private Label lbl(String text) {
        Label l = new Label(text); l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;"); return l;
    }
}
