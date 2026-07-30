package com.kaziflow.views;

import com.kaziflow.dao.GymDAO;
import com.kaziflow.services.AuditLog;
import com.kaziflow.utils.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

public class GymView {

    private BorderPane root;
    private final GymDAO dao = new GymDAO();
    private ObservableList<String[]> memberData  = FXCollections.observableArrayList();
    private ObservableList<String[]> checkinData = FXCollections.observableArrayList();
    private String currentFilter = "all";
    private Label activeLbl, expiringLbl, checkinLbl;

    public GymView() {
        dao.ensureTables();
        dao.autoExpire();
        buildUI();
        loadMembers();
    }

    public BorderPane getRoot() { return root; }

    private void buildUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color:#f8fafc;");

        Button membersBtn  = tBtn("👥 Members",   true);
        Button checkinBtn  = tBtn("✔ Check-In",   false);
        Button plansBtn    = tBtn("📋 Plans",      false);

        VBox membersView  = buildMembersView();
        VBox checkinView  = buildCheckinView();
        VBox plansView    = buildPlansView();

        StackPane area = new StackPane(membersView);
        VBox.setVgrow(area, Priority.ALWAYS);

        membersBtn.setOnAction(e -> { area.getChildren().setAll(membersView); setActive(membersBtn, checkinBtn, plansBtn); loadMembers(); });
        checkinBtn.setOnAction(e -> { area.getChildren().setAll(checkinView); setActive(checkinBtn, membersBtn, plansBtn); loadCheckins(); });
        plansBtn  .setOnAction(e -> { area.getChildren().setAll(plansView);   setActive(plansBtn, membersBtn, checkinBtn); });

        HBox tabBar = new HBox(0, membersBtn, checkinBtn, plansBtn);
        tabBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;-fx-padding:0 24;");

        VBox layout = new VBox(0, buildHeader(), tabBar, area);
        VBox.setVgrow(area, Priority.ALWAYS);
        root.setCenter(layout);
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        HBox h = new HBox(16); h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(16,24,16,24));
        h.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        VBox tb = new VBox(2);
        Label t = new Label("💪  Gym & Fitness Management"); t.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label s = new Label("Memberships · Check-in · Renewal reminders"); s.setStyle("-fx-font-size:12px;-fx-text-fill:#64748b;");
        tb.getChildren().addAll(t,s);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        activeLbl   = sv(String.valueOf(dao.getActiveCount()));
        expiringLbl = sv(String.valueOf(dao.getExpiringCount(7)));
        checkinLbl  = sv(String.valueOf(dao.getTodayCheckinCount()));
        Button newBtn = new Button("+ Enroll Member");
        newBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        newBtn.setOnAction(e -> showEnrollDialog());
        h.getChildren().addAll(tb, sp, sc("Active",activeLbl), sc("Expiring (7d)",expiringLbl), sc("Today Check-ins",checkinLbl), newBtn);
        return h;
    }

    private Label sv(String v) { Label l=new Label(v); l.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e293b;"); return l; }
    private VBox sc(String label, Label val) {
        Label lbl=new Label(label); lbl.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;-fx-font-weight:bold;");
        VBox c=new VBox(2,lbl,val);
        c.setStyle("-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#e2e8f0;-fx-border-radius:10;-fx-border-width:1;-fx-padding:10 18;-fx-min-width:100px;");
        return c;
    }

    // ── Members View ───────────────────────────────────────────────────────

    private VBox buildMembersView() {
        VBox v = new VBox(0); v.setStyle("-fx-background-color:#f8fafc;");

        HBox filterBar = new HBox(8); filterBar.setPadding(new Insets(12,24,12,24)); filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        ToggleGroup tg = new ToggleGroup();
        String[][] filters = {{"All","all"},{"Active","active"},{"Expiring Soon","expiring"},{"Expired","expired"},{"Frozen","frozen"}};
        boolean first = true;
        for (String[] f : filters) {
            ToggleButton btn = fTab(f[0], f[1], tg);
            if (first) { btn.setSelected(true); first=false; }
            final String fv = f[1];
            btn.setOnAction(e -> {
                currentFilter = fv;
                if ("expiring".equals(fv)) {
                    AsyncTask.run(() -> dao.findAll("active").stream()
                        .filter(r -> { try { return Integer.parseInt(r[8]) <= 7 && Integer.parseInt(r[8]) >= 0; } catch(Exception ex){ return false; } })
                        .collect(java.util.stream.Collectors.toList()),
                        memberData::setAll, err->{});
                } else {
                    loadMembers();
                }
            });
            filterBar.getChildren().add(btn);
        }
        tg.selectedToggleProperty().addListener((obs, oldT, newT) -> { if (newT == null && oldT != null) tg.selectToggle(oldT); }); searchF.setPromptText("Search by name, member no, phone...");
        searchF.setPrefWidth(260); searchF.setStyle("-fx-pref-height:32px;-fx-background-color:#f8fafc;-fx-border-color:#e2e8f0;-fx-border-radius:8;-fx-background-radius:8;-fx-font-size:12px;-fx-padding:0 10;");
        searchF.textProperty().addListener((obs,old,val) -> {
            if (val.isBlank()) loadMembers();
            else AsyncTask.run(() -> dao.search(val), memberData::setAll, err->{});
        });
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        filterBar.getChildren().addAll(sp, searchF);

        TableView<String[]> tv = buildMembersTable(); VBox.setVgrow(tv, Priority.ALWAYS);
        v.getChildren().addAll(filterBar, tv);
        return v;
    }

    private TableView<String[]> buildMembersTable() {
        // [0]=id [1]=member_no [2]=name [3]=phone [4]=plan [5]=start [6]=end [7]=status [8]=days_left
        TableView<String[]> tv = new TableView<>(memberData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;");

        TableColumn<String[],String> noCol   = col("Member #", 1, 100);
        TableColumn<String[],String> nameCol = new TableColumn<>("Member"); nameCol.setPrefWidth(160);
        nameCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue()[2]));
        nameCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String v, boolean empty){
                super.updateItem(v,empty); if(empty){setGraphic(null);return;}
                String[] row=getTableView().getItems().get(getIndex());
                VBox cell=new VBox(2);
                Label name=new Label(row[2]); name.setStyle("-fx-font-weight:bold;-fx-text-fill:#1e293b;");
                Label ph=new Label(row[3]); ph.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");
                cell.getChildren().addAll(name,ph); setGraphic(cell);
            }
        });

        TableColumn<String[],String> planCol  = col("Plan",       4, 110);
        TableColumn<String[],String> startCol = col("Start Date", 5, 100);
        TableColumn<String[],String> endCol   = col("End Date",   6, 100);

        // Days left — colour coded
        TableColumn<String[],String> daysCol = new TableColumn<>("Days Left"); daysCol.setPrefWidth(90);
        daysCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue()[8]));
        daysCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String v, boolean empty){
                super.updateItem(v,empty); if(empty||v==null){setText(null);setStyle("");return;}
                int days=0; try{days=Integer.parseInt(v);}catch(Exception ignored){}
                setText(v + " days");
                setStyle(days<0?"-fx-text-fill:#dc2626;-fx-font-weight:bold;":
                    days<=7?"-fx-text-fill:#d97706;-fx-font-weight:bold;":"-fx-text-fill:#16a34a;");
            }
        });

        // Status badge
        TableColumn<String[],String> statusCol = new TableColumn<>("Status"); statusCol.setPrefWidth(90);
        statusCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue()[7]));
        statusCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String v, boolean empty){
                super.updateItem(v,empty); if(empty||v==null){setGraphic(null);return;}
                Label b=new Label(v.toUpperCase());
                String color=switch(v){
                    case "active"->"#dcfce7;-fx-text-fill:#16a34a;";
                    case "expired"->"#fee2e2;-fx-text-fill:#dc2626;";
                    case "frozen"->"#dbeafe;-fx-text-fill:#2563eb;";
                    default->"#f1f5f9;-fx-text-fill:#64748b;";
                };
                b.setStyle("-fx-background-color:"+color+"-fx-font-size:10px;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:2 8;");
                setGraphic(b);
            }
        });

        // Actions
        TableColumn<String[],Void> actCol = new TableColumn<>(""); actCol.setPrefWidth(220);
        actCol.setCellFactory(c->new TableCell<>(){
            private final Button ciBtn     = btn2("✔ Check-In", "#16a34a");
            private final Button renewBtn  = btn2("↺ Renew",    "#2563eb");
            private final Button freezeBtn = btn2("❄ Freeze",   "#64748b");
            private final Button waBtn     = btn2("💬",          "#25D366");
            private final HBox box = new HBox(4, ciBtn, renewBtn, freezeBtn, waBtn);
            {
                ciBtn    .setOnAction(e -> doCheckin(getTableView().getItems().get(getIndex())));
                renewBtn .setOnAction(e -> showRenewDialog(getTableView().getItems().get(getIndex())));
                freezeBtn.setOnAction(e -> {
                    String[] row=getTableView().getItems().get(getIndex());
                    String newStatus="frozen".equals(row[7])?"active":"frozen";
                    dao.updateStatus(Integer.parseInt(row[0]),newStatus);
                    loadMembers();
                });
                waBtn.setOnAction(e -> sendWaReminder(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty){
                super.updateItem(v,empty);
                if(empty){setGraphic(null);return;}
                String status=getTableView().getItems().get(getIndex())[7];
                ciBtn.setDisable(!"active".equals(status));
                setGraphic(box);
            }
        });

        tv.getColumns().addAll(noCol,nameCol,planCol,startCol,endCol,daysCol,statusCol,actCol);
        return tv;
    }

    // ── Check-In View ──────────────────────────────────────────────────────

    private VBox buildCheckinView() {
        VBox v = new VBox(0); v.setStyle("-fx-background-color:#f8fafc;");

        HBox scanBar = new HBox(10); scanBar.setPadding(new Insets(14,24,14,24)); scanBar.setAlignment(Pos.CENTER_LEFT);
        scanBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        Label scanLbl = new Label("Member # or Name:"); scanLbl.setStyle("-fx-font-weight:bold;-fx-text-fill:#475569;");
        TextField scanF = new TextField(); scanF.setPromptText("Type member number or scan card...");
        scanF.setPrefWidth(300); scanF.setStyle("-fx-pref-height:38px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:8;-fx-background-radius:8;-fx-font-size:14px;-fx-padding:0 12;");

        Button ciBtn = new Button("✔ Check In");
        ciBtn.setStyle("-fx-background-color:#16a34a;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;");
        ciBtn.setOnAction(e -> doCheckinBySearch(scanF.getText().trim(), scanF));
        scanF.setOnAction(e -> doCheckinBySearch(scanF.getText().trim(), scanF));

        scanBar.getChildren().addAll(scanLbl, scanF, ciBtn);

        TableView<String[]> tv = new TableView<>(checkinData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;"); VBox.setVgrow(tv,Priority.ALWAYS);
        String[] hdrs={"#","Member No","Name","Plan","Check-in Time"};
        int[] idxs={0,1,2,3,4};
        for(int i=0;i<hdrs.length;i++){
            final int ci=idxs[i]; TableColumn<String[],String> tc=new TableColumn<>(hdrs[i]);
            tc.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(ci<d.getValue().length?d.getValue()[ci]:""));
            tv.getColumns().add(tc);
        }
        v.getChildren().addAll(scanBar,tv);
        return v;
    }

    // ── Plans View ─────────────────────────────────────────────────────────

    private VBox buildPlansView() {
        VBox v = new VBox(0); v.setStyle("-fx-background-color:#f8fafc;");
        HBox hdr = new HBox(12); hdr.setAlignment(Pos.CENTER_LEFT); hdr.setPadding(new Insets(14,24,14,24));
        hdr.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        Label title=new Label("Membership Plans"); title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Region sp=new Region(); HBox.setHgrow(sp,Priority.ALWAYS);
        Button addBtn=new Button("+ Add Plan");
        addBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        addBtn.setOnAction(e->showAddPlanDialog());
        hdr.getChildren().addAll(title,sp,addBtn);
        TableView<String[]> tv=new TableView<>(FXCollections.observableArrayList(dao.getPlans()));
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;"); VBox.setVgrow(tv,Priority.ALWAYS);
        String[] cols={"ID","Plan Name","Duration (days)","Price (KES)","Description"};
        for(int i=0;i<cols.length;i++){
            final int ci=i; TableColumn<String[],String> tc=new TableColumn<>(cols[i]);
            tc.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(ci<d.getValue().length?d.getValue()[ci]:""));
            tv.getColumns().add(tc);
        }
        v.getChildren().addAll(hdr,tv);
        return v;
    }

    // ── Dialogs ────────────────────────────────────────────────────────────

    private void showEnrollDialog() {
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle("Enroll New Member");
        d.getDialogPane().setStyle("-fx-background-color:white;"); d.getDialogPane().setPrefWidth(460);
        GridPane f=new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));
        TextField fnF=fld("First Name *",""); TextField lnF=fld("Last Name *","");
        TextField phF=fld("Phone",""); TextField emF=fld("Email","");
        List<String[]> plans=dao.getPlans();
        ComboBox<String> planCb=new ComboBox<>(); planCb.setPrefWidth(300);
        plans.forEach(p->planCb.getItems().add(p[0]+":"+p[1]+" ("+p[2]+" days) KES "+p[3]));
        if(!planCb.getItems().isEmpty()) planCb.setValue(planCb.getItems().get(0));
        TextField notesF=fld("Notes","");
        f.addRow(0,lbl("First Name"),fnF,lbl("Last Name"),lnF);
        f.addRow(1,lbl("Phone"),phF,lbl("Email"),emF);
        f.addRow(2,lbl("Plan"),planCb);
        f.addRow(3,lbl("Notes"),notesF);
        d.getDialogPane().setContent(f); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        d.showAndWait().ifPresent(r->{
            if(r!=ButtonType.OK||fnF.getText().isBlank()) return;
            String ps=planCb.getValue(); if(ps==null) return;
            int planId=Integer.parseInt(ps.split(":")[0]);
            String planName=ps.split(":")[1].split("\\(")[0].trim();
            int days=Integer.parseInt(plans.stream().filter(p->p[0].equals(String.valueOf(planId))).findFirst().map(p->p[2]).orElse("30"));
            int id=dao.enroll(fnF.getText().trim(),lnF.getText().trim(),phF.getText().trim(),emF.getText().trim(),planId,planName,days,notesF.getText().trim());
            if(id>0){AuditLog.log("MEMBER_ENROLLED",fnF.getText()+" "+lnF.getText()+" — "+planName,"gym",id);
                loadMembers(); refreshStats();
                Toast.success(SceneManager.getInstance().getStage(),"Enrolled!",fnF.getText()+" "+lnF.getText());}
        });
    }

    private void showRenewDialog(String[] row) {
        List<String[]> plans=dao.getPlans();
        ComboBox<String> planCb=new ComboBox<>();
        plans.forEach(p->planCb.getItems().add(p[0]+":"+p[1]+" ("+p[2]+" days) KES "+p[3]));
        if(!planCb.getItems().isEmpty()) planCb.setValue(planCb.getItems().get(0));
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle("Renew — "+row[2]);
        d.getDialogPane().setStyle("-fx-background-color:white;"); d.getDialogPane().setContent(planCb);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        d.showAndWait().ifPresent(r->{
            if(r!=ButtonType.OK||planCb.getValue()==null) return;
            String ps=planCb.getValue(); int planId=Integer.parseInt(ps.split(":")[0]);
            String planName=ps.split(":")[1].split("\\(")[0].trim();
            int days=Integer.parseInt(plans.stream().filter(p->p[0].equals(String.valueOf(planId))).findFirst().map(p->p[2]).orElse("30"));
            dao.renew(Integer.parseInt(row[0]),planId,planName,days);
            AuditLog.log("MEMBERSHIP_RENEWED",row[2]+" — "+planName,"gym",Integer.parseInt(row[0]));
            loadMembers(); refreshStats();
            Toast.success(SceneManager.getInstance().getStage(),"Renewed!",row[2]+" — "+planName);
        });
    }

    private void showAddPlanDialog() {
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle("Add Membership Plan");
        d.getDialogPane().setStyle("-fx-background-color:white;"); d.getDialogPane().setPrefWidth(380);
        GridPane f=new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));
        TextField nF=fld("Plan name",""); TextField dF=fld("Duration (days)","30");
        TextField pF=fld("Price (KES)","0"); TextField descF=fld("Description","");
        f.addRow(0,lbl("Name"),nF); f.addRow(1,lbl("Days"),dF,lbl("Price"),pF); f.addRow(2,lbl("Description"),descF);
        d.getDialogPane().setContent(f); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        d.showAndWait().ifPresent(r->{
            if(r!=ButtonType.OK||nF.getText().isBlank()) return;
            try{dao.savePlan(nF.getText().trim(),Integer.parseInt(dF.getText()),Double.parseDouble(pF.getText()),descF.getText().trim());
                Toast.success(SceneManager.getInstance().getStage(),"Plan added",nF.getText());}
            catch(Exception ex){Toast.error(SceneManager.getInstance().getStage(),"Error",ex.getMessage());}
        });
    }

    // ── Check-in ───────────────────────────────────────────────────────────

    private void doCheckin(String[] row) {
        if(!"active".equals(row[7])){ Toast.error(SceneManager.getInstance().getStage(),"Inactive","Member is "+row[7]); return; }
        dao.checkIn(Integer.parseInt(row[0]),row[2]);
        AuditLog.log("GYM_CHECKIN",row[2]+" checked in","gym",Integer.parseInt(row[0]));
        refreshStats(); loadCheckins();
        Toast.success(SceneManager.getInstance().getStage(),"Check-in ✓",row[2]+" — welcome!");
    }

    private void doCheckinBySearch(String query, TextField scanF) {
        if(query.isBlank()) return;
        List<String[]> results=dao.search(query);
        if(results.isEmpty()){ Toast.error(SceneManager.getInstance().getStage(),"Not found","No member: "+query); return; }
        String[] row=results.get(0);
        doCheckin(row);
        scanF.clear();
    }

    private void sendWaReminder(String[] row) {
        String phone=row[3].replaceAll("[^0-9]","");
        if(phone.startsWith("0")) phone="254"+phone.substring(1);
        if(phone.length()<9){ Toast.error(SceneManager.getInstance().getStage(),"No phone","No phone for "+row[2]); return; }
        int daysLeft=0; try{daysLeft=Integer.parseInt(row[8]);}catch(Exception ignored){}
        String msg = daysLeft<0
            ? "Hello "+row[2]+"! Your gym membership has expired. Renew today to keep access. Thank you!"
            : "Hello "+row[2]+"! Your membership expires in "+daysLeft+" days ("+row[6]+"). Renew early to keep access!";
        String url="https://wa.me/"+phone+"?text="+java.net.URLEncoder.encode(msg,java.nio.charset.StandardCharsets.UTF_8);
        try{java.awt.Desktop.getDesktop().browse(new java.net.URI(url));}catch(Exception e){e.printStackTrace();}
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void loadMembers() {
        AsyncTask.run(()->dao.findAll("all".equals(currentFilter)?null:currentFilter),memberData::setAll,err->{});
    }
    private void loadCheckins() { AsyncTask.run(dao::getTodayCheckins,checkinData::setAll,err->{}); }
    private void refreshStats() {
        activeLbl.setText(String.valueOf(dao.getActiveCount()));
        expiringLbl.setText(String.valueOf(dao.getExpiringCount(7)));
        checkinLbl.setText(String.valueOf(dao.getTodayCheckinCount()));
    }

    private TableColumn<String[],String> col(String h,int idx,double w){
        TableColumn<String[],String> c=new TableColumn<>(h); c.setPrefWidth(w);
        c.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(idx<d.getValue().length?d.getValue()[idx]:""));
        return c;
    }
    private ToggleButton fTab(String label,String data,ToggleGroup group){
        ToggleButton btn=new ToggleButton(label); btn.setToggleGroup(group); btn.setUserData(data);
        String base="-fx-background-radius:6;-fx-border-radius:6;-fx-border-width:1;-fx-pref-height:32px;-fx-padding:0 12;-fx-font-size:12px;-fx-cursor:hand;";
        String off=base+"-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-text-fill:#475569;";
        String on=base+"-fx-background-color:#2563eb;-fx-border-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;";
        btn.setStyle(off); btn.selectedProperty().addListener((o,w,is)->btn.setStyle(is?on:off));
        return btn;
    }
    private Button tBtn(String label, boolean active){
        Button b=new Button(label);
        String base="-fx-background-color:transparent;-fx-border-color:transparent;-fx-pref-height:44px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;";
        b.setStyle(base+(active?"-fx-text-fill:#2563eb;-fx-font-weight:bold;-fx-border-color:transparent transparent #2563eb transparent;-fx-border-width:0 0 2 0;":"-fx-text-fill:#64748b;"));
        return b;
    }
    private void setActive(Button active,Button...rest){
        String base="-fx-background-color:transparent;-fx-pref-height:44px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;";
        active.setStyle(base+"-fx-text-fill:#2563eb;-fx-font-weight:bold;-fx-border-color:transparent transparent #2563eb transparent;-fx-border-width:0 0 2 0;");
        for(Button b:rest) b.setStyle(base+"-fx-text-fill:#64748b;-fx-border-color:transparent;");
    }
    private Button btn2(String t,String color){Button b=new Button(t);b.setStyle("-fx-background-color:white;-fx-border-color:"+color+";-fx-border-radius:5;-fx-background-radius:5;-fx-font-size:10px;-fx-text-fill:"+color+";-fx-cursor:hand;-fx-padding:2 6;");return b;}
    private TextField fld(String p,String v){TextField tf=new TextField(v);tf.setPromptText(p);tf.setPrefWidth(240);tf.setStyle("-fx-pref-height:34px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");return tf;}
    private Label lbl(String t){Label l=new Label(t);l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;");return l;}
}
