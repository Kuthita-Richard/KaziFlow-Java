package com.kaziflow.views;

import com.kaziflow.dao.AppointmentDAO;
import com.kaziflow.dao.EmployeeDAO;
import com.kaziflow.services.AuditLog;
import com.kaziflow.utils.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AppointmentView {

    private BorderPane root;
    private final AppointmentDAO dao    = new AppointmentDAO();
    private final EmployeeDAO    empDAO = new EmployeeDAO();
    private LocalDate selectedDate      = LocalDate.now();
    private ObservableList<String[]> dayData      = FXCollections.observableArrayList();
    private ObservableList<String[]> upcomingData = FXCollections.observableArrayList();
    private Label dayLabel;
    private Label todayLbl;
    private Label upcomingLbl;

    public AppointmentView() {
        dao.ensureTables();
        buildUI();
        loadDay(selectedDate);
        loadUpcoming();
    }

    public BorderPane getRoot() { return root; }

    private void buildUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color:#f8fafc;");

        Button calBtn   = tabBtn("📅  Calendar",   true);
        Button listBtn  = tabBtn("🕐  Upcoming",   false);
        Button typesBtn = tabBtn("⚙  Services",   false);

        VBox calView      = buildCalView();
        VBox listView     = buildListView();
        VBox typesView    = buildTypesView();

        StackPane area = new StackPane(calView);
        VBox.setVgrow(area, Priority.ALWAYS);

        calBtn  .setOnAction(e -> { area.getChildren().setAll(calView);   setActive(calBtn, listBtn, typesBtn);  loadDay(selectedDate); });
        listBtn .setOnAction(e -> { area.getChildren().setAll(listView);  setActive(listBtn, calBtn, typesBtn);  loadUpcoming(); });
        typesBtn.setOnAction(e -> { area.getChildren().setAll(typesView); setActive(typesBtn, calBtn, listBtn); });

        HBox tabBar = new HBox(0, calBtn, listBtn, typesBtn);
        tabBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;-fx-padding:0 24;");

        VBox layout = new VBox(0, buildHeader(), tabBar, area);
        VBox.setVgrow(area, Priority.ALWAYS);
        root.setCenter(layout);
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        HBox h = new HBox(16);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(16,24,16,24));
        h.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");

        VBox tb = new VBox(2);
        Label t = new Label("📅  Appointments");
        t.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label s = new Label("Booking calendar · Walk-in queue · Service history");
        s.setStyle("-fx-font-size:12px;-fx-text-fill:#64748b;");
        tb.getChildren().addAll(t, s);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        todayLbl    = new Label(String.valueOf(dao.getTodayCount()));
        upcomingLbl = new Label(String.valueOf(dao.getUpcomingCount()));
        todayLbl.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        upcomingLbl.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");

        Button newBtn = new Button("+ New Appointment");
        newBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        newBtn.setOnAction(e -> showBookingDialog());

        h.getChildren().addAll(tb, sp, statCard("Today", todayLbl), statCard("Upcoming", upcomingLbl), newBtn);
        return h;
    }

    private VBox statCard(String label, Label valLbl) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;-fx-font-weight:bold;");
        VBox card = new VBox(2, lbl, valLbl);
        card.setStyle("-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#e2e8f0;-fx-border-radius:10;-fx-border-width:1;-fx-padding:10 20;-fx-min-width:100px;");
        return card;
    }

    // ── Calendar View ──────────────────────────────────────────────────────

    private VBox buildCalView() {
        VBox v = new VBox(0); v.setStyle("-fx-background-color:#f8fafc;");

        HBox nav = new HBox(8);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.setPadding(new Insets(10,24,10,24));
        nav.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");

        Button prev = sBtn("◀"); Button next = sBtn("▶");
        Button today = new Button("Today");
        today.setStyle("-fx-background-color:#f1f5f9;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-pref-height:32px;-fx-padding:0 12;-fx-cursor:hand;");
        prev.setOnAction(e -> { selectedDate = selectedDate.minusDays(1); updateDayLabel(); loadDay(selectedDate); });
        next.setOnAction(e -> { selectedDate = selectedDate.plusDays(1);  updateDayLabel(); loadDay(selectedDate); });
        today.setOnAction(e -> { selectedDate = LocalDate.now(); updateDayLabel(); loadDay(selectedDate); });

        dayLabel = new Label(); dayLabel.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        updateDayLabel();

        DatePicker dp = new DatePicker(selectedDate); dp.setPrefWidth(140);
        dp.setOnAction(e -> { if (dp.getValue() != null) { selectedDate = dp.getValue(); updateDayLabel(); loadDay(selectedDate); } });

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        nav.getChildren().addAll(prev, today, next, dayLabel, sp, dp);

        TableView<String[]> tv = buildDayTable();
        VBox.setVgrow(tv, Priority.ALWAYS);
        v.getChildren().addAll(nav, tv);
        return v;
    }

    private TableView<String[]> buildDayTable() {
        TableView<String[]> tv = new TableView<>(dayData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;");

        // [0]=id [1]=apt_no [2]=cust_name [3]=phone [4]=provider [5]=type
        // [6]=start [7]=end [8]=duration [9]=status [10]=deposit [11]=amount [12]=notes

        TableColumn<String[], String> timeCol  = col("Time",     6, 80);
        TableColumn<String[], String> custCol  = new TableColumn<>("Client");
        custCol.setPrefWidth(150);
        custCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[2]));
        custCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                String[] row = getTableView().getItems().get(getIndex());
                VBox cell = new VBox(2);
                Label name = new Label(row[2]); name.setStyle("-fx-font-weight:bold;-fx-text-fill:#1e293b;");
                Label ph   = new Label(row[3]); ph.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");
                cell.getChildren().addAll(name, ph);
                setGraphic(cell);
            }
        });

        TableColumn<String[], String> typeCol = col("Service",  5, 140);
        TableColumn<String[], String> provCol = col("Provider", 4, 110);
        TableColumn<String[], String> durCol  = col("Min",      8, 55);

        TableColumn<String[], String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(110);
        statusCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[9]));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                Label b = new Label(v.replace("_"," ").toUpperCase());
                String color = switch(v) {
                    case "completed"   -> "-fx-background-color:#dcfce7;-fx-text-fill:#16a34a;";
                    case "confirmed"   -> "-fx-background-color:#dbeafe;-fx-text-fill:#2563eb;";
                    case "in_progress" -> "-fx-background-color:#fef3c7;-fx-text-fill:#d97706;";
                    case "cancelled","no_show" -> "-fx-background-color:#fee2e2;-fx-text-fill:#dc2626;";
                    default -> "-fx-background-color:#f1f5f9;-fx-text-fill:#64748b;";
                };
                b.setStyle(color+"-fx-font-size:10px;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:2 8;");
                setGraphic(b);
            }
        });

        TableColumn<String[], String> amtCol = new TableColumn<>("KES");
        amtCol.setPrefWidth(90);
        amtCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[11]));
        amtCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); return; }
                try { setText(KESFormatter.formatShort(Double.parseDouble(v))); }
                catch (Exception ex) { setText(v); }
            }
        });

        TableColumn<String[], Void> actCol = new TableColumn<>("");
        actCol.setPrefWidth(190);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button confirmBtn = btn2("✔ Confirm","#16a34a");
            private final Button doneBtn    = btn2("✓ Done",   "#2563eb");
            private final Button cancelBtn  = btn2("✕",        "#dc2626");
            private final HBox box = new HBox(5, confirmBtn, doneBtn, cancelBtn);
            {
                confirmBtn.setOnAction(e -> doStatus(getIndex(), "confirmed"));
                doneBtn   .setOnAction(e -> doStatus(getIndex(), "completed"));
                cancelBtn .setOnAction(e -> doStatus(getIndex(), "cancelled"));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                String st = getTableView().getItems().get(getIndex())[9];
                confirmBtn.setDisable("confirmed".equals(st)||"completed".equals(st));
                doneBtn.setDisable("completed".equals(st));
                cancelBtn.setDisable("cancelled".equals(st)||"completed".equals(st));
                setGraphic(box);
            }
        });

        tv.getColumns().addAll(timeCol, custCol, typeCol, provCol, durCol, statusCol, amtCol, actCol);
        return tv;
    }

    // ── Upcoming List View ─────────────────────────────────────────────────

    private VBox buildListView() {
        VBox v = new VBox(0); v.setStyle("-fx-background-color:white;");
        TableView<String[]> tv = new TableView<>(upcomingData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;");
        VBox.setVgrow(tv, Priority.ALWAYS);
        // [0]=id [1]=apt_no [2]=name [3]=phone [4]=provider [5]=type [6]=date [7]=time [8]=status [9]=amount
        String[] hdrs = {"Appt #","Client","Phone","Provider","Service","Date","Time","Status","Amount (KES)"};
        int[]    idxs = {1,2,3,4,5,6,7,8,9};
        for (int i = 0; i < hdrs.length; i++) {
            final int ci = idxs[i];
            TableColumn<String[],String> tc = new TableColumn<>(hdrs[i]);
            tc.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(ci<d.getValue().length?d.getValue()[ci]:""));
            tv.getColumns().add(tc);
        }
        TableColumn<String[],Void> waCol = new TableColumn<>("");
        waCol.setPrefWidth(110);
        waCol.setCellFactory(c -> new TableCell<>(){
            private final Button b = btn2("💬 Remind","#25D366");
            { b.setOnAction(e -> sendWa(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void v, boolean empty){ super.updateItem(v,empty); setGraphic(empty?null:b); }
        });
        tv.getColumns().add(waCol);
        v.getChildren().add(tv);
        return v;
    }

    // ── Service Types View ─────────────────────────────────────────────────

    private VBox buildTypesView() {
        VBox v = new VBox(0); v.setStyle("-fx-background-color:#f8fafc;");
        HBox hdr = new HBox(12);
        hdr.setAlignment(Pos.CENTER_LEFT);
        hdr.setPadding(new Insets(14,24,14,24));
        hdr.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        Label title = new Label("Service Types"); title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button addBtn = new Button("+ Add Service Type");
        addBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        addBtn.setOnAction(e -> showAddTypeDialog());
        hdr.getChildren().addAll(title, sp, addBtn);
        ObservableList<String[]> typeData = FXCollections.observableArrayList(dao.getTypes());
        TableView<String[]> tv = new TableView<>(typeData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;");
        VBox.setVgrow(tv, Priority.ALWAYS);
        String[] cols = {"ID","Service Name","Duration (min)","Price (KES)","Color"};
        for (int i = 0; i < cols.length; i++) {
            final int ci = i;
            TableColumn<String[],String> tc = new TableColumn<>(cols[i]);
            tc.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(ci<d.getValue().length?d.getValue()[ci]:""));
            tv.getColumns().add(tc);
        }
        v.getChildren().addAll(hdr, tv);
        return v;
    }

    // ── Booking Dialog ─────────────────────────────────────────────────────

    private void showBookingDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Appointment");
        dialog.getDialogPane().setStyle("-fx-background-color:white;");
        dialog.getDialogPane().setPrefWidth(480);

        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));

        TextField nameF  = fld("Client Name *","");
        TextField phoneF = fld("Phone Number","");

        List<String[]> types = dao.getTypes();
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.setPrefWidth(320);
        types.forEach(t -> typeCombo.getItems().add(t[0]+":"+t[1]+" ("+t[2]+"min) KES "+t[3]));
        if (!typeCombo.getItems().isEmpty()) typeCombo.setValue(typeCombo.getItems().get(0));

        ComboBox<String> provCombo = new ComboBox<>();
        provCombo.setPrefWidth(320); provCombo.getItems().add("0:Any available");
        empDAO.findAll().forEach(e -> provCombo.getItems().add(e.getId()+":"+e.getName()));
        provCombo.setValue(provCombo.getItems().get(0));

        DatePicker dp = new DatePicker(selectedDate); dp.setPrefWidth(320);

        ComboBox<String> timeCombo = new ComboBox<>();
        timeCombo.setPrefWidth(320);
        for (int h=8;h<=20;h++) for (int m=0;m<60;m+=15) timeCombo.getItems().add(String.format("%02d:%02d",h,m));
        timeCombo.setValue("09:00");

        TextField priceF   = fld("Total Amount (KES)","0");
        TextField depositF = fld("Deposit (KES)","0");
        TextField notesF   = fld("Notes","");

        typeCombo.setOnAction(e -> {
            String v = typeCombo.getValue();
            if (v != null) {
                String id = v.split(":")[0];
                types.stream().filter(t->t[0].equals(id)).findFirst().ifPresent(t->priceF.setText(t[3]));
            }
        });
        if (!types.isEmpty()) priceF.setText(types.get(0)[3]);

        form.addRow(0, lbl("Client Name"),  nameF);
        form.addRow(1, lbl("Phone"),        phoneF);
        form.addRow(2, lbl("Service"),      typeCombo);
        form.addRow(3, lbl("Provider"),     provCombo);
        form.addRow(4, lbl("Date"),         dp);
        form.addRow(5, lbl("Time"),         timeCombo);
        form.addRow(6, lbl("Amount (KES)"), priceF);
        form.addRow(7, lbl("Deposit"),      depositF);
        form.addRow(8, lbl("Notes"),        notesF);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK || nameF.getText().isBlank()) return;
            try {
                String typeSel = typeCombo.getValue();
                int typeId = Integer.parseInt(typeSel.split(":")[0]);
                String typeName = typeSel.split(":")[1].split("\\(")[0].trim();
                int duration = types.stream().filter(t->t[0].equals(String.valueOf(typeId)))
                    .findFirst().map(t->Integer.parseInt(t[2])).orElse(30);
                String provSel = provCombo.getValue();
                Integer provId = provSel.startsWith("0:") ? null : Integer.parseInt(provSel.split(":")[0]);
                String provName = provSel.split(":",2)[1];
                double amount  = Double.parseDouble(priceF.getText().trim());
                double deposit = Double.parseDouble(depositF.getText().trim());
                int userId = 1;
                try { userId = SessionManager.getInstance().getCurrentUser().getId(); } catch(Exception ignored){}
                int id = dao.create(nameF.getText().trim(), phoneF.getText().trim(), null,
                    provId, provName, typeId, typeName,
                    dp.getValue().toString(), timeCombo.getValue(), duration,
                    amount, deposit, notesF.getText().trim(), userId);
                if (id > 0) {
                    AuditLog.log("APPOINTMENT_CREATED", nameF.getText()+" — "+typeName, "appointments", id);
                    loadDay(selectedDate); loadUpcoming();
                    todayLbl.setText(String.valueOf(dao.getTodayCount()));
                    upcomingLbl.setText(String.valueOf(dao.getUpcomingCount()));
                    Toast.success(SceneManager.getInstance().getStage(), "Booked!", nameF.getText()+" at "+timeCombo.getValue());
                }
            } catch (Exception ex) {
                Toast.error(SceneManager.getInstance().getStage(), "Error", ex.getMessage());
            }
        });
    }

    private void showAddTypeDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Service Type");
        dialog.getDialogPane().setStyle("-fx-background-color:white;");
        dialog.getDialogPane().setPrefWidth(360);
        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));
        TextField nameF  = fld("e.g. Deep Tissue Massage","");
        TextField durF   = fld("Duration (minutes)","60");
        TextField priceF = fld("Price (KES)","0");
        form.addRow(0, lbl("Service Name"), nameF);
        form.addRow(1, lbl("Duration"),     durF);
        form.addRow(2, lbl("Price (KES)"),  priceF);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().ifPresent(r -> {
            if (r!=ButtonType.OK||nameF.getText().isBlank()) return;
            try {
                dao.saveType(nameF.getText().trim(), Integer.parseInt(durF.getText().trim()),
                    Double.parseDouble(priceF.getText().trim()), "#2563eb");
                Toast.success(SceneManager.getInstance().getStage(),"Service added", nameF.getText());
            } catch(Exception ex){
                Toast.error(SceneManager.getInstance().getStage(),"Invalid","Check duration and price.");
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void sendWa(String[] row) {
        String phone = row[3].replaceAll("[^0-9]","");
        if (phone.startsWith("0")) phone = "254"+phone.substring(1);
        if (phone.length()<9) return;
        String msg = "Reminder: "+row[2]+", your appointment for "+row[5]+" is on "+row[6]+" at "+row[7]+". Thank you!";
        String url = "https://wa.me/"+phone+"?text="+java.net.URLEncoder.encode(msg, java.nio.charset.StandardCharsets.UTF_8);
        try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); } catch(Exception ignored){}
    }

    private void loadDay(LocalDate date) {
        updateDayLabel();
        AsyncTask.run(() -> dao.findByDate(date.toString()), dayData::setAll, err->{});
    }

    private void loadUpcoming() {
        AsyncTask.run(() -> dao.findUpcoming(30), upcomingData::setAll, err->{});
    }

    private void updateDayLabel() {
        if (dayLabel == null) return;
        String s = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"));
        if (selectedDate.equals(LocalDate.now())) s += "  (Today)";
        dayLabel.setText(s);
    }

    private void doStatus(int idx, String status) {
        if (idx<0||idx>=dayData.size()) return;
        String[] row = dayData.get(idx);
        int id = Integer.parseInt(row[0]);
        if (dao.updateStatus(id,status)) {
            AuditLog.log("APPOINTMENT_STATUS","Appointment "+row[1]+" → "+status,"appointments",id);
            loadDay(selectedDate);
            todayLbl.setText(String.valueOf(dao.getTodayCount()));
            Toast.success(SceneManager.getInstance().getStage(),"Updated",status.replace("_"," "));
        }
    }

    private TableColumn<String[],String> col(String hdr, int idx, double w) {
        TableColumn<String[],String> c = new TableColumn<>(hdr); c.setPrefWidth(w);
        c.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(idx<d.getValue().length?d.getValue()[idx]:""));
        return c;
    }
    private Button tabBtn(String label, boolean active) {
        Button b = new Button(label);
        String base = "-fx-background-color:transparent;-fx-border-color:transparent;-fx-pref-height:44px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;";
        b.setStyle(base+(active?"-fx-text-fill:#2563eb;-fx-font-weight:bold;-fx-border-color:transparent transparent #2563eb transparent;-fx-border-width:0 0 2 0;":"-fx-text-fill:#64748b;"));
        return b;
    }
    private void setActive(Button active, Button... rest) {
        String base = "-fx-background-color:transparent;-fx-pref-height:44px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;";
        active.setStyle(base+"-fx-text-fill:#2563eb;-fx-font-weight:bold;-fx-border-color:transparent transparent #2563eb transparent;-fx-border-width:0 0 2 0;");
        for (Button b : rest) b.setStyle(base+"-fx-text-fill:#64748b;-fx-border-color:transparent;");
    }
    private Button sBtn(String t) {
        Button b = new Button(t);
        b.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-pref-height:32px;-fx-padding:0 10;-fx-cursor:hand;");
        return b;
    }
    private Button btn2(String t, String color) {
        Button b = new Button(t);
        b.setStyle("-fx-background-color:white;-fx-border-color:"+color+";-fx-border-radius:5;-fx-background-radius:5;-fx-font-size:11px;-fx-text-fill:"+color+";-fx-cursor:hand;-fx-padding:3 8;");
        return b;
    }
    private TextField fld(String prompt, String val) {
        TextField tf = new TextField(val); tf.setPromptText(prompt); tf.setPrefWidth(320);
        tf.setStyle("-fx-pref-height:34px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");
        return tf;
    }
    private Label lbl(String t) {
        Label l = new Label(t); l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;"); return l;
    }
}
