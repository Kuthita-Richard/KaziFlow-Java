package com.kaziflow.views;

import com.kaziflow.dao.SchoolCanteenDAO;
import com.kaziflow.services.AuditLog;
import com.kaziflow.utils.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class SchoolCanteenView {

    private BorderPane root;
    private final SchoolCanteenDAO dao = new SchoolCanteenDAO();
    private ObservableList<String[]> accountData = FXCollections.observableArrayList();
    private Label totalLbl, balanceLbl, salesLbl;

    public SchoolCanteenView() { dao.ensureTables(); buildUI(); loadAccounts(); }
    public BorderPane getRoot() { return root; }

    private void buildUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color:#f8fafc;");
        root.setTop(buildHeader());
        root.setCenter(buildContent());
    }

    private VBox buildHeader() {
        VBox header = new VBox(0);
        HBox h = new HBox(16); h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(16,24,16,24));
        h.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        VBox tb = new VBox(2);
        Label t = new Label("🏫  School Canteen"); t.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label s = new Label("Student accounts · Prepaid balance · Top-up · Purchase"); s.setStyle("-fx-font-size:12px;-fx-text-fill:#64748b;");
        tb.getChildren().addAll(t,s);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        totalLbl   = sv(String.valueOf(dao.getTotalAccounts()));
        balanceLbl = sv("KES "+KESFormatter.formatShort(dao.getTotalBalance()));
        salesLbl   = sv("KES "+KESFormatter.formatShort(dao.getTodaySales()));
        Button newBtn = new Button("+ Add Student");
        newBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        newBtn.setOnAction(e->showAddStudentDialog());
        h.getChildren().addAll(tb,sp,sc("Students",totalLbl),sc("Total Balance",balanceLbl),sc("Today Sales",salesLbl),newBtn);
        header.getChildren().add(h);
        return header;
    }

    private Label sv(String v){Label l=new Label(v);l.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");return l;}
    private VBox sc(String label,Label val){Label lbl=new Label(label);lbl.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;-fx-font-weight:bold;");VBox c=new VBox(2,lbl,val);c.setStyle("-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#e2e8f0;-fx-border-radius:10;-fx-border-width:1;-fx-padding:8 16;-fx-min-width:110px;");return c;}

    private VBox buildContent() {
        VBox content = new VBox(0); VBox.setVgrow(content,Priority.ALWAYS);

        // Quick action bar — top-up / purchase by student no
        HBox actionBar = new HBox(10); actionBar.setPadding(new Insets(12,24,12,24)); actionBar.setAlignment(Pos.CENTER_LEFT);
        actionBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        TextField scanF = new TextField(); scanF.setPromptText("Student No or Name..."); scanF.setPrefWidth(240);
        scanF.setStyle("-fx-pref-height:36px;-fx-background-color:#f8fafc;-fx-border-color:#e2e8f0;-fx-border-radius:8;-fx-background-radius:8;-fx-font-size:13px;-fx-padding:0 12;");
        scanF.textProperty().addListener((obs,old,val)->{ if(val.isBlank()) loadAccounts(); else AsyncTask.run(()->dao.findAll(null).stream().filter(r->r[1].toLowerCase().contains(val.toLowerCase())||r[2].toLowerCase().contains(val.toLowerCase())).collect(java.util.stream.Collectors.toList()),accountData::setAll,err->{}); });

        Button topupBtn = new Button("+ Top-Up");
        topupBtn.setStyle("-fx-background-color:#16a34a;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:6;-fx-pref-height:36px;-fx-padding:0 14;-fx-cursor:hand;");
        topupBtn.setOnAction(e->{ String query=scanF.getText().trim(); if(query.isBlank()){Toast.error(SceneManager.getInstance().getStage(),"Required","Enter student number first.");return;} quickTopup(query,scanF); });

        Button purchaseBtn = new Button("💰 Purchase");
        purchaseBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:6;-fx-pref-height:36px;-fx-padding:0 14;-fx-cursor:hand;");
        purchaseBtn.setOnAction(e->{ String query=scanF.getText().trim(); if(query.isBlank()){Toast.error(SceneManager.getInstance().getStage(),"Required","Enter student number first.");return;} quickPurchase(query,scanF); });

        actionBar.getChildren().addAll(new Label("Quick:"),scanF,topupBtn,purchaseBtn);

        TableView<String[]> tv = buildTable(); VBox.setVgrow(tv,Priority.ALWAYS);
        content.getChildren().addAll(actionBar,tv);
        return content;
    }

    private TableView<String[]> buildTable() {
        // [0]=id [1]=student_no [2]=name [3]=class [4]=phone [5]=balance [6]=status
        TableView<String[]> tv=new TableView<>(accountData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); tv.setStyle("-fx-background-color:white;");

        TableColumn<String[],String> noCol=col("Student No",1,100);
        TableColumn<String[],String> nameCol=new TableColumn<>("Student"); nameCol.setPrefWidth(160);
        nameCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue()[2]));
        nameCol.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(String v,boolean empty){super.updateItem(v,empty);if(empty){setGraphic(null);return;}String[] row=getTableView().getItems().get(getIndex());VBox cell=new VBox(2);Label name=new Label(row[2]);name.setStyle("-fx-font-weight:bold;-fx-text-fill:#1e293b;");Label cls=new Label("Class: "+row[3]);cls.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");cell.getChildren().addAll(name,cls);setGraphic(cell);}});

        TableColumn<String[],String> phoneCol=col("Phone",4,120);

        TableColumn<String[],String> balCol=new TableColumn<>("Balance (KES)"); balCol.setPrefWidth(130);
        balCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue()[5]));
        balCol.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(String v,boolean empty){super.updateItem(v,empty);if(empty||v==null){setText(null);return;}double bal=0;try{bal=Double.parseDouble(v);}catch(Exception ignored){}setText("KES "+KESFormatter.format(bal));setStyle(bal<100?"-fx-text-fill:#dc2626;-fx-font-weight:bold;":"-fx-text-fill:#16a34a;-fx-font-weight:bold;");}});

        TableColumn<String[],Void> actCol=new TableColumn<>(""); actCol.setPrefWidth(240);
        actCol.setCellFactory(c->new TableCell<>(){
            private final Button topBtn  =btn2("+ Top-Up",  "#16a34a");
            private final Button buyBtn  =btn2("💰 Buy",    "#2563eb");
            private final Button histBtn =btn2("📋 History","#7c3aed");
            private final HBox box=new HBox(5,topBtn,buyBtn,histBtn);
            {
                topBtn .setOnAction(e->{String[] row=getTableView().getItems().get(getIndex());showTopupDialog(row);});
                buyBtn .setOnAction(e->{String[] row=getTableView().getItems().get(getIndex());showPurchaseDialog(row);});
                histBtn.setOnAction(e->{String[] row=getTableView().getItems().get(getIndex());showHistory(row);});
            }
            @Override protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);setGraphic(empty?null:box);}
        });

        tv.getColumns().addAll(noCol,nameCol,phoneCol,balCol,col("Status",6,80),actCol);
        return tv;
    }

    private void showAddStudentDialog(){
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle("Add Student Account");
        d.getDialogPane().setStyle("-fx-background-color:white;"); d.getDialogPane().setPrefWidth(440);
        GridPane f=new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));
        TextField noF=fld("Student No *",""); TextField nameF=fld("Student Name *","");
        TextField classF=fld("Class/Grade",""); TextField phoneF=fld("Parent Phone","");
        TextField balF=fld("Initial Balance (KES)","0");
        f.addRow(0,lbl("Student No"),noF,lbl("Name"),nameF);
        f.addRow(1,lbl("Class"),classF,lbl("Parent Phone"),phoneF);
        f.addRow(2,lbl("Initial Balance"),balF);
        d.getDialogPane().setContent(f); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        d.showAndWait().ifPresent(r->{if(r!=ButtonType.OK||noF.getText().isBlank()||nameF.getText().isBlank()) return;
            try{double bal=Double.parseDouble(balF.getText().isBlank()?"0":balF.getText());
                int id=dao.createAccount(noF.getText().trim(),nameF.getText().trim(),classF.getText().trim(),phoneF.getText().trim(),bal);
                if(id>0){AuditLog.log("CANTEEN_ACCOUNT","New student: "+nameF.getText(),"canteen",id);loadAccounts();refreshStats();
                    Toast.success(SceneManager.getInstance().getStage(),"Added",nameF.getText());}
            }catch(Exception ex){Toast.error(SceneManager.getInstance().getStage(),"Error",ex.getMessage());}
        });
    }

    private void showTopupDialog(String[] row){
        TextInputDialog d=new TextInputDialog(); d.setTitle("Top-Up — "+row[2]); d.setHeaderText("Current Balance: KES "+row[5]); d.setContentText("Top-up amount (KES):");
        d.showAndWait().ifPresent(val->{try{double amount=Double.parseDouble(val);int uid=1;try{uid=SessionManager.getInstance().getCurrentUser().getId();}catch(Exception ignored){}
            if(dao.topUp(Integer.parseInt(row[0]),amount,"Top-up",uid)){AuditLog.log("CANTEEN_TOPUP",row[2]+" KES "+amount,"canteen",Integer.parseInt(row[0]));loadAccounts();refreshStats();Toast.success(SceneManager.getInstance().getStage(),"Top-up done","KES "+KESFormatter.format(amount)+" added");}
        }catch(Exception ex){Toast.error(SceneManager.getInstance().getStage(),"Invalid","Enter a valid amount.");}});
    }

    private void showPurchaseDialog(String[] row){
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle("Purchase — "+row[2]); d.getDialogPane().setStyle("-fx-background-color:white;"); d.getDialogPane().setPrefWidth(360);
        GridPane f=new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));
        Label balLbl=new Label("Balance: KES "+KESFormatter.format(Double.parseDouble(row[5]))); balLbl.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#16a34a;");
        TextField amtF=fld("Amount (KES)",""); TextField descF=fld("Item description","Canteen purchase");
        f.addRow(0,balLbl); f.addRow(1,lbl("Amount"),amtF); f.addRow(2,lbl("Description"),descF);
        d.getDialogPane().setContent(f); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        d.showAndWait().ifPresent(r->{if(r!=ButtonType.OK||amtF.getText().isBlank()) return;
            try{double amount=Double.parseDouble(amtF.getText());int uid=1;try{uid=SessionManager.getInstance().getCurrentUser().getId();}catch(Exception ignored){}
                if(dao.purchase(Integer.parseInt(row[0]),amount,descF.getText().trim(),uid)){AuditLog.log("CANTEEN_PURCHASE",row[2]+" KES "+amount,"canteen",Integer.parseInt(row[0]));loadAccounts();refreshStats();Toast.success(SceneManager.getInstance().getStage(),"Purchase OK","KES "+KESFormatter.format(amount)+" deducted");}
                else Toast.error(SceneManager.getInstance().getStage(),"Insufficient balance","Balance is KES "+row[5]);
            }catch(Exception ex){Toast.error(SceneManager.getInstance().getStage(),"Invalid","Enter a valid amount.");}
        });
    }

    private void showHistory(String[] row){
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle("Transactions — "+row[2]+" ("+row[1]+")");
        d.getDialogPane().setStyle("-fx-background-color:white;"); d.getDialogPane().setPrefWidth(560); d.getDialogPane().setPrefHeight(400);
        TableView<String[]> tv=new TableView<>(FXCollections.observableArrayList(dao.getTransactions(Integer.parseInt(row[0]))));
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); tv.setStyle("-fx-background-color:white;");
        String[] hdrs={"Type","Amount (KES)","Description","Balance After","Time"};
        for(int i=0;i<hdrs.length;i++){final int ci=i;TableColumn<String[],String> tc=new TableColumn<>(hdrs[i]);tc.setCellValueFactory(dd->new javafx.beans.property.SimpleStringProperty(ci<dd.getValue().length?dd.getValue()[ci]:""));tv.getColumns().add(tc);}
        d.getDialogPane().setContent(tv); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.showAndWait();
    }

    private void quickTopup(String query,TextField scanF){
        java.util.List<String[]> found=dao.findAll(null).stream().filter(r->r[1].equalsIgnoreCase(query)||r[2].toLowerCase().contains(query.toLowerCase())).collect(java.util.stream.Collectors.toList());
        if(found.isEmpty()){Toast.error(SceneManager.getInstance().getStage(),"Not found","Student: "+query);return;}
        showTopupDialog(found.get(0)); scanF.clear();
    }

    private void quickPurchase(String query,TextField scanF){
        java.util.List<String[]> found=dao.findAll(null).stream().filter(r->r[1].equalsIgnoreCase(query)||r[2].toLowerCase().contains(query.toLowerCase())).collect(java.util.stream.Collectors.toList());
        if(found.isEmpty()){Toast.error(SceneManager.getInstance().getStage(),"Not found","Student: "+query);return;}
        showPurchaseDialog(found.get(0)); scanF.clear();
    }

    private void loadAccounts(){AsyncTask.run(()->dao.findAll(null),accountData::setAll,err->{});}
    private void refreshStats(){totalLbl.setText(String.valueOf(dao.getTotalAccounts()));balanceLbl.setText("KES "+KESFormatter.formatShort(dao.getTotalBalance()));salesLbl.setText("KES "+KESFormatter.formatShort(dao.getTodaySales()));}
    private TableColumn<String[],String> col(String h,int idx,double w){TableColumn<String[],String> c=new TableColumn<>(h);c.setPrefWidth(w);c.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(idx<d.getValue().length?d.getValue()[idx]:""));return c;}
    private Button btn2(String t,String color){Button b=new Button(t);b.setStyle("-fx-background-color:white;-fx-border-color:"+color+";-fx-border-radius:5;-fx-background-radius:5;-fx-font-size:10px;-fx-text-fill:"+color+";-fx-cursor:hand;-fx-padding:2 6;");return b;}
    private TextField fld(String p,String v){TextField tf=new TextField(v);tf.setPromptText(p);tf.setPrefWidth(200);tf.setStyle("-fx-pref-height:34px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");return tf;}
    private Label lbl(String t){Label l=new Label(t);l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;");return l;}
}
