package com.kaziflow.views;

import com.kaziflow.dao.LaundryDAO;
import com.kaziflow.services.AuditLog;
import com.kaziflow.utils.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class LaundryView {

    private BorderPane root;
    private final LaundryDAO dao = new LaundryDAO();
    private ObservableList<String[]> orderData = FXCollections.observableArrayList();
    private String currentFilter = "all";
    private Label openLbl, readyLbl;

    public LaundryView() { dao.ensureTables(); buildUI(); loadOrders(); }
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
        Label t = new Label("👔  Laundry & Dry Cleaning"); t.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label s = new Label("Order tracking · Garment status · Collection SMS"); s.setStyle("-fx-font-size:12px;-fx-text-fill:#64748b;");
        tb.getChildren().addAll(t,s);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        openLbl  = sv(String.valueOf(dao.getOpenCount()));
        readyLbl = sv(String.valueOf(dao.getReadyCount()));
        Button newBtn = new Button("+ New Order");
        newBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        newBtn.setOnAction(e -> showNewOrderDialog());
        h.getChildren().addAll(tb,sp,sc("Open Orders",openLbl),sc("Ready",readyLbl),newBtn);
        return h;
    }

    private Label sv(String v){Label l=new Label(v);l.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");return l;}
    private VBox sc(String label,Label val){Label lbl=new Label(label);lbl.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;-fx-font-weight:bold;");VBox c=new VBox(2,lbl,val);c.setStyle("-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#e2e8f0;-fx-border-radius:10;-fx-border-width:1;-fx-padding:10 20;-fx-min-width:100px;");return c;}

    private VBox buildContent() {
        VBox content = new VBox(0); VBox.setVgrow(content, Priority.ALWAYS);
        HBox filterBar = new HBox(8); filterBar.setPadding(new Insets(12,24,12,24)); filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        ToggleGroup tg = new ToggleGroup();
        String[][] filters={{"All","all"},{"Received","received"},{"Washing","washing"},{"Ironing","ironing"},{"Ready","ready"},{"Collected","collected"}};
        boolean first=true;
        for(String[] f:filters){
            ToggleButton btn=fTab(f[0],f[1],tg);
            if(first){btn.setSelected(true);first=false;}
            final String fv=f[1];
            btn.setOnAction(e->{currentFilter=fv;loadOrders();});
            filterBar.getChildren().add(btn);
        }
        tg.selectedToggleProperty().addListener((obs, oldT, newT) -> { if (newT == null && oldT != null) tg.selectToggle(oldT); });
        TableView<String[]> tv=buildTable(); VBox.setVgrow(tv,Priority.ALWAYS);
        content.getChildren().addAll(filterBar,tv);
        return content;
    }

    private TableView<String[]> buildTable() {
        // [0]=id [1]=order_no [2]=cust_name [3]=phone [4]=service [5]=status [6]=due [7]=total [8]=deposit [9]=date
        TableView<String[]> tv=new TableView<>(orderData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;");

        TableColumn<String[],String> noCol=col("Order #",1,90);
        TableColumn<String[],String> custCol=new TableColumn<>("Customer"); custCol.setPrefWidth(150);
        custCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue()[2]));
        custCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String v,boolean empty){
                super.updateItem(v,empty);if(empty){setGraphic(null);return;}
                String[] row=getTableView().getItems().get(getIndex());
                VBox cell=new VBox(2);Label name=new Label(row[2]);name.setStyle("-fx-font-weight:bold;-fx-text-fill:#1e293b;");
                Label ph=new Label(row[3]);ph.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");cell.getChildren().addAll(name,ph);setGraphic(cell);}
        });
        TableColumn<String[],String> svcCol=col("Service",4,120);
        TableColumn<String[],String> dueCol=col("Due Date",6,100);
        TableColumn<String[],String> amtCol=new TableColumn<>("Total (KES)"); amtCol.setPrefWidth(110);
        amtCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue()[7]));
        amtCol.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(String v,boolean empty){super.updateItem(v,empty);if(empty||v==null){setText(null);return;}try{setText("KES "+KESFormatter.format(Double.parseDouble(v)));}catch(Exception ex){setText(v);}}});

        TableColumn<String[],String> statusCol=new TableColumn<>("Status"); statusCol.setPrefWidth(110);
        statusCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue()[5]));
        statusCol.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(String v,boolean empty){super.updateItem(v,empty);if(empty||v==null){setGraphic(null);return;}
            Label b=new Label(v.toUpperCase());String color=switch(v){case "ready"->"-fx-background-color:#dcfce7;-fx-text-fill:#16a34a;";case "washing",
            "ironing"->"-fx-background-color:#dbeafe;-fx-text-fill:#2563eb;";case "collected"->"-fx-background-color:#f1f5f9;-fx-text-fill:#64748b;";
            case "cancelled"->"-fx-background-color:#fee2e2;-fx-text-fill:#dc2626;";default->"-fx-background-color:#fef3c7;-fx-text-fill:#d97706;";};
            b.setStyle(color+"-fx-font-size:10px;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:2 8;");setGraphic(b);}});

        TableColumn<String[],Void> actCol=new TableColumn<>(""); actCol.setPrefWidth(200);
        actCol.setCellFactory(c->new TableCell<>(){
            private final Button stBtn=btn2("↑ Status","#16a34a");
            private final Button waBtn=btn2("💬 Notify","#25D366");
            private final Button colBtn=btn2("✓ Collected","#2563eb");
            private final HBox box=new HBox(5,stBtn,waBtn,colBtn);
            {
                stBtn.setOnAction(e->{String[] row=getTableView().getItems().get(getIndex());showStatusDialog(row);});
                waBtn.setOnAction(e->{String[] row=getTableView().getItems().get(getIndex());sendWa(row);});
                colBtn.setOnAction(e->{String[] row=getTableView().getItems().get(getIndex());
                    dao.updateStatus(Integer.parseInt(row[0]),"collected");
                    AuditLog.log("LAUNDRY_COLLECTED",row[1]+" collected by "+row[2],"laundry",Integer.parseInt(row[0]));
                    loadOrders();Toast.success(SceneManager.getInstance().getStage(),"Collected",row[1]);});
            }
            @Override protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);
                if(empty){setGraphic(null);return;}
                String st=getTableView().getItems().get(getIndex())[5];
                colBtn.setDisable("collected".equals(st)||"cancelled".equals(st));setGraphic(box);}
        });
        tv.getColumns().addAll(noCol,custCol,svcCol,dueCol,amtCol,statusCol,col("Date",9,110),actCol);
        return tv;
    }

    private void showNewOrderDialog(){
        Dialog<ButtonType> d=new Dialog<>();d.setTitle("New Laundry Order");
        d.getDialogPane().setStyle("-fx-background-color:white;");d.getDialogPane().setPrefWidth(460);
        GridPane f=new GridPane();f.setHgap(12);f.setVgap(12);f.setPadding(new Insets(20));
        TextField custF=fld("Customer Name *","");TextField phoneF=fld("Phone","");
        ComboBox<String> svcCb=new ComboBox<>();svcCb.getItems().addAll("wash_fold","dry_clean","iron_only","wash_iron","delicate");svcCb.setValue("wash_fold");svcCb.setPrefWidth(240);
        TextField dueF=fld("Due Date (YYYY-MM-DD)","");TextField amtF=fld("Total (KES)","0");TextField depF=fld("Deposit (KES)","0");
        TextField notesF=fld("Notes / Garment count","");
        f.addRow(0,lbl("Customer"),custF,lbl("Phone"),phoneF);
        f.addRow(1,lbl("Service"),svcCb,lbl("Due Date"),dueF);
        f.addRow(2,lbl("Total (KES)"),amtF,lbl("Deposit"),depF);
        f.addRow(3,lbl("Notes"),notesF);
        d.getDialogPane().setContent(f);d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        d.showAndWait().ifPresent(r->{
            if(r!=ButtonType.OK||custF.getText().isBlank()) return;
            try{int uid=1;try{uid=SessionManager.getInstance().getCurrentUser().getId();}catch(Exception ignored){}
                int id=dao.createOrder(custF.getText().trim(),phoneF.getText().trim(),svcCb.getValue(),dueF.getText().trim(),
                    Double.parseDouble(amtF.getText().isBlank()?"0":amtF.getText()),
                    Double.parseDouble(depF.getText().isBlank()?"0":depF.getText()),notesF.getText().trim(),uid);
                if(id>0){AuditLog.log("LAUNDRY_ORDER",custF.getText()+" — "+svcCb.getValue(),"laundry",id);loadOrders();
                    Toast.success(SceneManager.getInstance().getStage(),"Order created",custF.getText());}
            }catch(Exception ex){Toast.error(SceneManager.getInstance().getStage(),"Error",ex.getMessage());}
        });
    }

    private void showStatusDialog(String[] row){
        ChoiceDialog<String> d=new ChoiceDialog<>(row[5],"received","washing","ironing","ready","collected","cancelled");
        d.setTitle("Update Status — "+row[1]);d.setHeaderText("Current: "+row[5]);d.setContentText("New status:");
        d.showAndWait().ifPresent(st->{dao.updateStatus(Integer.parseInt(row[0]),st);loadOrders();
            Toast.success(SceneManager.getInstance().getStage(),"Updated",row[1]+" → "+st);});
    }

    private void sendWa(String[] row){
        String phone=row[3].replaceAll("[^0-9]","");if(phone.startsWith("0")) phone="254"+phone.substring(1);
        if(phone.length()<9){Toast.error(SceneManager.getInstance().getStage(),"No phone","No phone for "+row[2]);return;}
        String msg="Hello "+row[2]+"! Your laundry order "+row[1]+" is "+("ready".equals(row[5])?"READY for collection!":"currently "+row[5]+".")+" Total: KES "+row[7]+". Thank you!";
        String url="https://wa.me/"+phone+"?text="+java.net.URLEncoder.encode(msg,java.nio.charset.StandardCharsets.UTF_8);
        try{java.awt.Desktop.getDesktop().browse(new java.net.URI(url));}catch(Exception e){e.printStackTrace();}
    }

    private void loadOrders(){AsyncTask.run(()->dao.findAll("all".equals(currentFilter)?null:currentFilter),orderData::setAll,err->{});openLbl.setText(String.valueOf(dao.getOpenCount()));readyLbl.setText(String.valueOf(dao.getReadyCount()));}
    private TableColumn<String[],String> col(String h,int idx,double w){TableColumn<String[],String> c=new TableColumn<>(h);c.setPrefWidth(w);c.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(idx<d.getValue().length?d.getValue()[idx]:""));return c;}
    private ToggleButton fTab(String label,String data,ToggleGroup group){ToggleButton btn=new ToggleButton(label);btn.setToggleGroup(group);btn.setUserData(data);String base="-fx-background-radius:6;-fx-border-radius:6;-fx-border-width:1;-fx-pref-height:32px;-fx-padding:0 12;-fx-font-size:12px;-fx-cursor:hand;";String off=base+"-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-text-fill:#475569;";String on=base+"-fx-background-color:#2563eb;-fx-border-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;";btn.setStyle(off);btn.selectedProperty().addListener((o,w,is)->btn.setStyle(is?on:off));return btn;}
    private Button btn2(String t,String color){Button b=new Button(t);b.setStyle("-fx-background-color:white;-fx-border-color:"+color+";-fx-border-radius:5;-fx-background-radius:5;-fx-font-size:10px;-fx-text-fill:"+color+";-fx-cursor:hand;-fx-padding:2 6;");return b;}
    private TextField fld(String p,String v){TextField tf=new TextField(v);tf.setPromptText(p);tf.setPrefWidth(220);tf.setStyle("-fx-pref-height:34px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");return tf;}
    private Label lbl(String t){Label l=new Label(t);l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;");return l;}
}
