package com.kaziflow.views;

import com.kaziflow.dao.ProductDAO;
import com.kaziflow.dao.WorkshopDAO;
import com.kaziflow.services.AuditLog;
import com.kaziflow.utils.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class WorkshopView {

    private BorderPane root;
    private final WorkshopDAO dao     = new WorkshopDAO();
    private final ProductDAO  prodDAO = new ProductDAO();
    private ObservableList<String[]> jobData = FXCollections.observableArrayList();
    private String currentFilter = "all";
    private Label openLbl, readyLbl;

    public WorkshopView() { dao.ensureTables(); buildUI(); loadJobs(); }
    public BorderPane getRoot() { return root; }

    private void buildUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color:#f8fafc;");
        root.setTop(buildHeader());
        root.setCenter(buildContent());
    }

    private HBox buildHeader() {
        HBox h = new HBox(16); h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(16,24,16,24));
        h.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        VBox tb = new VBox(2);
        Label t = new Label("🔧  Workshop — Job Cards"); t.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label s = new Label("Job tracking · Parts used · Labour billing · Customer SMS"); s.setStyle("-fx-font-size:12px;-fx-text-fill:#64748b;");
        tb.getChildren().addAll(t,s);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        openLbl  = sVal(String.valueOf(dao.getOpenCount()));
        readyLbl = sVal(String.valueOf(dao.getReadyCount()));
        Button newBtn = new Button("+ New Job Card");
        newBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        newBtn.setOnAction(e -> showNewJobDialog());
        h.getChildren().addAll(tb,sp,sCard("Open Jobs",openLbl),sCard("Ready",readyLbl),newBtn);
        return h;
    }

    private Label sVal(String v) { Label l=new Label(v); l.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e293b;"); return l; }
    private VBox sCard(String label, Label val) {
        Label lbl=new Label(label); lbl.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;-fx-font-weight:bold;");
        VBox c=new VBox(2,lbl,val);
        c.setStyle("-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#e2e8f0;-fx-border-radius:10;-fx-border-width:1;-fx-padding:10 20;-fx-min-width:100px;");
        return c;
    }

    private VBox buildContent() {
        VBox content = new VBox(0); VBox.setVgrow(content, Priority.ALWAYS);
        HBox filterBar = new HBox(8); filterBar.setPadding(new Insets(12,24,12,24)); filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        ToggleGroup tg = new ToggleGroup();
        String[][] filters = {{"All","all"},{"Received","received"},{"In Progress","in_progress"},
            {"Waiting Parts","waiting_parts"},{"Ready","ready"},{"Collected","collected"}};
        boolean first = true;
        for (String[] f : filters) {
            ToggleButton btn = fTab(f[0],f[1],tg);
            if (first) { btn.setSelected(true); first=false; }
            final String fv = f[1];
            btn.setOnAction(e -> { currentFilter=fv; loadJobs(); });
            filterBar.getChildren().add(btn);
        }
        tg.selectedToggleProperty().addListener((obs, oldT, newT) -> { if (newT == null && oldT != null) tg.selectToggle(oldT); });
        TableView<String[]> tv = buildTable(); VBox.setVgrow(tv, Priority.ALWAYS);
        content.getChildren().addAll(filterBar,tv);
        return content;
    }

    private ToggleButton fTab(String label, String data, ToggleGroup group) {
        ToggleButton btn=new ToggleButton(label); btn.setToggleGroup(group); btn.setUserData(data);
        String base="-fx-background-radius:6;-fx-border-radius:6;-fx-border-width:1;-fx-pref-height:32px;-fx-padding:0 12;-fx-font-size:12px;-fx-cursor:hand;";
        String off=base+"-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-text-fill:#475569;";
        String on=base+"-fx-background-color:#2563eb;-fx-border-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;";
        btn.setStyle(off); btn.selectedProperty().addListener((o,w,is)->btn.setStyle(is?on:off));
        return btn;
    }

    private TableView<String[]> buildTable() {
        // [0]=id [1]=job_no [2]=cust_name [3]=phone [4]=item [5]=status [6]=tech [7]=total [8]=deposit [9]=date
        TableView<String[]> tv = new TableView<>(jobData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;");

        TableColumn<String[],String> jobNoCol = col("Job #",1,90);
        TableColumn<String[],String> custCol = new TableColumn<>("Customer"); custCol.setPrefWidth(150);
        custCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue()[2]));
        custCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String v, boolean empty){
                super.updateItem(v,empty); if(empty){setGraphic(null);return;}
                String[] row=getTableView().getItems().get(getIndex());
                VBox cell=new VBox(2);
                Label name=new Label(row[2]); name.setStyle("-fx-font-weight:bold;-fx-text-fill:#1e293b;");
                Label ph=new Label(row[3]); ph.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");
                cell.getChildren().addAll(name,ph); setGraphic(cell);
            }
        });

        TableColumn<String[],String> itemCol  = col("Item",4,160);
        TableColumn<String[],String> techCol  = col("Technician",6,110);
        TableColumn<String[],String> totalCol = new TableColumn<>("Total (KES)"); totalCol.setPrefWidth(110);
        totalCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue()[7]));
        totalCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String v, boolean empty){
                super.updateItem(v,empty); if(empty||v==null){setText(null);return;}
                try{setText("KES "+KESFormatter.format(Double.parseDouble(v)));}catch(Exception ex){setText(v);}
            }
        });

        TableColumn<String[],String> statusCol = new TableColumn<>("Status"); statusCol.setPrefWidth(120);
        statusCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue()[5]));
        statusCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String v, boolean empty){
                super.updateItem(v,empty); if(empty||v==null){setGraphic(null);return;}
                Label b=new Label(v.replace("_"," ").toUpperCase());
                String color=switch(v){
                    case "ready"->"#dcfce7;-fx-text-fill:#16a34a;";
                    case "in_progress"->"#dbeafe;-fx-text-fill:#2563eb;";
                    case "waiting_parts"->"#fef3c7;-fx-text-fill:#d97706;";
                    case "collected"->"#f1f5f9;-fx-text-fill:#64748b;";
                    case "cancelled"->"#fee2e2;-fx-text-fill:#dc2626;";
                    default->"#eff6ff;-fx-text-fill:#2563eb;";
                };
                b.setStyle("-fx-background-color:"+color+"-fx-font-size:10px;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:2 8;");
                setGraphic(b);
            }
        });

        TableColumn<String[],Void> actCol = new TableColumn<>(""); actCol.setPrefWidth(250);
        actCol.setCellFactory(c->new TableCell<>(){
            private final Button detBtn    = b2("📋 Details","#2563eb");
            private final Button partsBtn  = b2("🔩 Parts","#d97706");
            private final Button statBtn   = b2("↑ Status","#16a34a");
            private final Button waBtn     = b2("💬 Notify","#25D366");
            private final HBox box = new HBox(5,detBtn,partsBtn,statBtn,waBtn);
            {
                detBtn .setOnAction(e->showDetail(getTableView().getItems().get(getIndex())));
                partsBtn.setOnAction(e->showPartsDialog(getTableView().getItems().get(getIndex())));
                statBtn.setOnAction(e->showStatusDialog(getTableView().getItems().get(getIndex())));
                waBtn  .setOnAction(e->sendWa(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty){ super.updateItem(v,empty); setGraphic(empty?null:box); }
        });

        tv.getColumns().addAll(jobNoCol,custCol,itemCol,techCol,totalCol,statusCol,col("Date",9,120),actCol);
        return tv;
    }

    private void showNewJobDialog() {
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle("New Job Card");
        d.getDialogPane().setStyle("-fx-background-color:white;"); d.getDialogPane().setPrefWidth(480);
        GridPane f=new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));
        TextField custF=fld("Customer Name *",""); TextField phoneF=fld("Phone","");
        TextField itemF=fld("Item/Vehicle description *","");
        TextArea probA=new TextArea(); probA.setPromptText("Problem description"); probA.setPrefRowCount(2);
        TextField techF=fld("Technician",""); TextField estF=fld("Est. Cost (KES)","0");
        TextField depF=fld("Deposit (KES)","0");
        f.addRow(0,lbl("Customer"),custF,lbl("Phone"),phoneF);
        f.addRow(1,lbl("Item *"),itemF);
        f.addRow(2,lbl("Problem"),probA);
        f.addRow(3,lbl("Technician"),techF,lbl("Est. Cost"),estF);
        f.addRow(4,lbl("Deposit"),depF);
        d.getDialogPane().setContent(f);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        d.showAndWait().ifPresent(r->{
            if(r!=ButtonType.OK||custF.getText().isBlank()||itemF.getText().isBlank()) return;
            try{
                int uid=1; try{uid=SessionManager.getInstance().getCurrentUser().getId();}catch(Exception ignored){}
                int id=dao.createJob(custF.getText().trim(),phoneF.getText().trim(),
                    itemF.getText().trim(),probA.getText().trim(),techF.getText().trim(),
                    Double.parseDouble(estF.getText().isBlank()?"0":estF.getText()),
                    Double.parseDouble(depF.getText().isBlank()?"0":depF.getText()),
                    "",uid);
                if(id>0){AuditLog.log("JOB_CREATED",custF.getText()+" — "+itemF.getText(),"workshop",id); loadJobs();
                    Toast.success(SceneManager.getInstance().getStage(),"Job created",custF.getText());}
            }catch(Exception ex){Toast.error(SceneManager.getInstance().getStage(),"Error",ex.getMessage());}
        });
    }

    private void showDetail(String[] row) {
        int jobId=Integer.parseInt(row[0]);
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle("Job "+row[1]+" — "+row[2]);
        d.getDialogPane().setStyle("-fx-background-color:white;"); d.getDialogPane().setPrefWidth(580); d.getDialogPane().setPrefHeight(420);
        TabPane tabs=new TabPane(); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        TableView<String[]> pTv=new TableView<>(FXCollections.observableArrayList(dao.getParts(jobId)));
        pTv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        for(String h:new String[]{"Part","Qty","Unit Cost","Total"}){
            final int ci=java.util.Arrays.asList("Part","Qty","Unit Cost","Total").indexOf(h);
            TableColumn<String[],String> tc=new TableColumn<>(h);
            tc.setCellValueFactory(dd->new javafx.beans.property.SimpleStringProperty(ci<dd.getValue().length?dd.getValue()[ci]:""));
            pTv.getColumns().add(tc);
        }
        TableView<String[]> lTv=new TableView<>(FXCollections.observableArrayList(dao.getLabour(jobId)));
        lTv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        for(String h:new String[]{"Description","Hours","Rate","Total"}){
            final int ci=java.util.Arrays.asList("Description","Hours","Rate","Total").indexOf(h);
            TableColumn<String[],String> tc=new TableColumn<>(h);
            tc.setCellValueFactory(dd->new javafx.beans.property.SimpleStringProperty(ci<dd.getValue().length?dd.getValue()[ci]:""));
            lTv.getColumns().add(tc);
        }
        tabs.getTabs().addAll(new Tab("Parts",pTv),new Tab("Labour",lTv));
        VBox layout=new VBox(0,tabs,new Label("  Total: KES "+KESFormatter.format(Double.parseDouble(row[7]))));
        VBox.setVgrow(tabs,Priority.ALWAYS);
        d.getDialogPane().setContent(layout);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.showAndWait();
    }

    private void showPartsDialog(String[] row) {
        int jobId=Integer.parseInt(row[0]);
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle("Add Parts/Labour — "+row[1]);
        d.getDialogPane().setStyle("-fx-background-color:white;"); d.getDialogPane().setPrefWidth(460);
        TabPane tabs=new TabPane(); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        GridPane pf=new GridPane(); pf.setHgap(12); pf.setVgap(12); pf.setPadding(new Insets(16));
        ComboBox<String> prodCb=new ComboBox<>(); prodCb.setPrefWidth(300); prodCb.getItems().add("0:-- Manual --");
        prodDAO.findAll().forEach(p->prodCb.getItems().add(p.getId()+":"+p.getName()+" (KES "+p.getSellingPrice()+")"));
        prodCb.setValue(prodCb.getItems().get(0));
        TextField pnF=fld("Part name",""); TextField pqF=fld("Qty","1"); TextField pcF=fld("Unit cost","0");
        prodCb.setOnAction(e->{String v=prodCb.getValue(); if(v!=null&&!v.startsWith("0:")){
            String[] pts=v.split(":");
            prodDAO.findAll().stream().filter(p->p.getId()==Integer.parseInt(pts[0])).findFirst().ifPresent(p->{pnF.setText(p.getName());pcF.setText(String.valueOf(p.getSellingPrice()));});
        }});
        pf.addRow(0,lbl("Product"),prodCb); pf.addRow(1,lbl("Name"),pnF,lbl("Qty"),pqF); pf.addRow(2,lbl("Cost"),pcF);
        GridPane lf=new GridPane(); lf.setHgap(12); lf.setVgap(12); lf.setPadding(new Insets(16));
        TextField ldF=fld("Description",""); TextField lhF=fld("Hours","1"); TextField lrF=fld("Rate/hr","500");
        lf.addRow(0,lbl("Description"),ldF); lf.addRow(1,lbl("Hours"),lhF,lbl("Rate/hr"),lrF);
        tabs.getTabs().addAll(new Tab("Add Part",pf),new Tab("Add Labour",lf));
        d.getDialogPane().setContent(tabs); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        d.showAndWait().ifPresent(r->{
            if(r!=ButtonType.OK) return;
            try{
                if(tabs.getSelectionModel().getSelectedIndex()==0){
                    if(pnF.getText().isBlank()) return;
                    String ps=prodCb.getValue(); Integer pid=ps!=null&&!ps.startsWith("0:")?Integer.parseInt(ps.split(":")[0]):null;
                    dao.addPart(jobId,pid,pnF.getText().trim(),Double.parseDouble(pqF.getText()),Double.parseDouble(pcF.getText()));
                } else {
                    if(ldF.getText().isBlank()) return;
                    dao.addLabour(jobId,ldF.getText().trim(),Double.parseDouble(lhF.getText()),Double.parseDouble(lrF.getText()));
                }
                loadJobs(); Toast.success(SceneManager.getInstance().getStage(),"Added","Job "+row[1]+" updated");
            }catch(Exception ex){Toast.error(SceneManager.getInstance().getStage(),"Error",ex.getMessage());}
        });
    }

    private void showStatusDialog(String[] row) {
        ChoiceDialog<String> d=new ChoiceDialog<>(row[5],"received","diagnosing","in_progress","waiting_parts","ready","collected","cancelled");
        d.setTitle("Update Status — "+row[1]); d.setHeaderText("Current: "+row[5].replace("_"," ")); d.setContentText("New status:");
        d.showAndWait().ifPresent(st->{
            dao.updateStatus(Integer.parseInt(row[0]),st);
            AuditLog.log("JOB_STATUS",row[1]+" → "+st,"workshop",Integer.parseInt(row[0]));
            loadJobs(); Toast.success(SceneManager.getInstance().getStage(),"Updated",st.replace("_"," "));
        });
    }

    private void sendWa(String[] row) {
        String phone=row[3].replaceAll("[^0-9]","");
        if(phone.startsWith("0")) phone="254"+phone.substring(1);
        if(phone.length()<9){Toast.error(SceneManager.getInstance().getStage(),"No phone","No phone for "+row[2]);return;}
        String msg="Hello "+row[2]+"! Update on your job ("+row[1]+"): "+row[4]+" is now "+row[5].replace("_"," ")+". Thank you!";
        String url="https://wa.me/"+phone+"?text="+java.net.URLEncoder.encode(msg,java.nio.charset.StandardCharsets.UTF_8);
        try{java.awt.Desktop.getDesktop().browse(new java.net.URI(url));}catch(Exception e){e.printStackTrace();}
    }

    private void loadJobs() {
        AsyncTask.run(()->dao.findAll("all".equals(currentFilter)?null:currentFilter),jobData::setAll,err->{});
        openLbl.setText(String.valueOf(dao.getOpenCount()));
        readyLbl.setText(String.valueOf(dao.getReadyCount()));
    }

    private TableColumn<String[],String> col(String h,int idx,double w){
        TableColumn<String[],String> c=new TableColumn<>(h); c.setPrefWidth(w);
        c.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(idx<d.getValue().length?d.getValue()[idx]:""));
        return c;
    }
    private Button b2(String t,String color){Button b=new Button(t);b.setStyle("-fx-background-color:white;-fx-border-color:"+color+";-fx-border-radius:5;-fx-background-radius:5;-fx-font-size:10px;-fx-text-fill:"+color+";-fx-cursor:hand;-fx-padding:2 6;");return b;}
    private TextField fld(String p,String v){TextField tf=new TextField(v);tf.setPromptText(p);tf.setPrefWidth(220);tf.setStyle("-fx-pref-height:34px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");return tf;}
    private Label lbl(String t){Label l=new Label(t);l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;");return l;}
}
