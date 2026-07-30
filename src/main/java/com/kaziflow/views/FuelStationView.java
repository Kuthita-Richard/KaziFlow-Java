package com.kaziflow.views;

import com.kaziflow.dao.FuelStationDAO;
import com.kaziflow.services.AuditLog;
import com.kaziflow.utils.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

public class FuelStationView {

    private BorderPane root;
    private final FuelStationDAO dao = new FuelStationDAO();
    private ObservableList<String[]> salesData = FXCollections.observableArrayList();
    private ObservableList<String[]> shiftData = FXCollections.observableArrayList();
    private Label revLbl, litresLbl, shiftLbl;

    public FuelStationView() { dao.ensureTables(); buildUI(); refreshStats(); loadSales(); loadShifts(); }
    public BorderPane getRoot() { return root; }

    private void buildUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color:#f8fafc;");
        Button salesBtn  = tBtn("⛽ Sales",     true);
        Button shiftsBtn = tBtn("🔄 Shifts",    false);
        Button pricesBtn = tBtn("💲 Prices",    false);
        Button dipBtn    = tBtn("📏 Dip Reading",false);
        VBox salesView   = buildSalesView();
        VBox shiftsView  = buildShiftsView();
        VBox pricesView  = buildPricesView();
        VBox dipView     = buildDipView();
        StackPane area   = new StackPane(salesView);
        VBox.setVgrow(area, Priority.ALWAYS);
        salesBtn .setOnAction(e->{area.getChildren().setAll(salesView);  setActive(salesBtn,shiftsBtn,pricesBtn,dipBtn); loadSales();});
        shiftsBtn.setOnAction(e->{area.getChildren().setAll(shiftsView); setActive(shiftsBtn,salesBtn,pricesBtn,dipBtn); loadShifts();});
        pricesBtn.setOnAction(e->{area.getChildren().setAll(pricesView); setActive(pricesBtn,salesBtn,shiftsBtn,dipBtn);});
        dipBtn   .setOnAction(e->{area.getChildren().setAll(dipView);    setActive(dipBtn,salesBtn,shiftsBtn,pricesBtn);});
        HBox tabBar=new HBox(0,salesBtn,shiftsBtn,pricesBtn,dipBtn);
        tabBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;-fx-padding:0 24;");
        VBox layout=new VBox(0,buildHeader(),tabBar,area);
        VBox.setVgrow(area,Priority.ALWAYS);
        root.setCenter(layout);
    }

    private HBox buildHeader() {
        HBox h=new HBox(16); h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(16,24,16,24));
        h.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        VBox tb=new VBox(2);
        Label t=new Label("⛽  Fuel Station Manager"); t.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label s=new Label("Pump sales · Shift handover · Dip readings · Price management"); s.setStyle("-fx-font-size:12px;-fx-text-fill:#64748b;");
        tb.getChildren().addAll(t,s);
        Region sp=new Region(); HBox.setHgrow(sp,Priority.ALWAYS);
        revLbl    = sv("KES 0");
        litresLbl = sv("0 L");
        shiftLbl  = sv("None");
        Button saleBtn=new Button("+ Record Sale");
        saleBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        saleBtn.setOnAction(e->showSaleDialog());
        Button shiftBtn=new Button("⏱ Shift");
        shiftBtn.setStyle("-fx-background-color:#d97706;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 14;-fx-cursor:hand;-fx-font-size:13px;");
        shiftBtn.setOnAction(e->showShiftDialog());
        h.getChildren().addAll(tb,sp,sc("Today Revenue",revLbl),sc("Litres Sold",litresLbl),sc("Open Shift",shiftLbl),saleBtn,shiftBtn);
        return h;
    }

    private Label sv(String v){Label l=new Label(v);l.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");return l;}
    private VBox sc(String label,Label val){Label lbl=new Label(label);lbl.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:10px;-fx-font-weight:bold;");VBox c=new VBox(2,lbl,val);c.setStyle("-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#e2e8f0;-fx-border-radius:10;-fx-border-width:1;-fx-padding:8 14;-fx-min-width:100px;");return c;}

    private VBox buildSalesView() {
        VBox v=new VBox(0); v.setStyle("-fx-background-color:white;");
        TableView<String[]> tv=new TableView<>(salesData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); tv.setStyle("-fx-background-color:white;");
        VBox.setVgrow(tv,Priority.ALWAYS);
        // [0]=id [1]=pump [2]=fuel_type [3]=litres [4]=price/L [5]=amount [6]=payment [7]=vehicle [8]=customer [9]=time
        String[] hdrs={"Pump","Fuel Type","Litres","Price/L","Amount (KES)","Payment","Vehicle","Customer","Time"};
        int[] idxs={1,2,3,4,5,6,7,8,9};
        for(int i=0;i<hdrs.length;i++){final int ci=idxs[i];TableColumn<String[],String> tc=new TableColumn<>(hdrs[i]);
            tc.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(ci<d.getValue().length?d.getValue()[ci]:""));
            if(hdrs[i].contains("Amount")){tc.setCellFactory(c->new TableCell<>(){@Override protected void updateItem(String v2,boolean empty){super.updateItem(v2,empty);if(empty||v2==null){setText(null);return;}try{setText("KES "+KESFormatter.format(Double.parseDouble(v2)));}catch(Exception ex){setText(v2);}setStyle("-fx-font-weight:bold;");}});}
            tv.getColumns().add(tc);}
        v.getChildren().add(tv); return v;
    }

    private VBox buildShiftsView() {
        VBox v=new VBox(0); v.setStyle("-fx-background-color:white;");
        TableView<String[]> tv=new TableView<>(shiftData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); tv.setStyle("-fx-background-color:white;");
        VBox.setVgrow(tv,Priority.ALWAYS);
        // [0]=id [1]=shift_no [2]=attendant [3]=status [4]=total_sales [5]=start [6]=end
        String[] hdrs={"Shift #","Attendant","Status","Total Sales (KES)","Start","End"};
        int[] idxs={1,2,3,4,5,6};
        for(int i=0;i<hdrs.length;i++){final int ci=idxs[i];TableColumn<String[],String> tc=new TableColumn<>(hdrs[i]);
            tc.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(ci<d.getValue().length?d.getValue()[ci]:""));
            tv.getColumns().add(tc);}
        v.getChildren().add(tv); return v;
    }

    private VBox buildPricesView() {
        VBox v=new VBox(0); v.setStyle("-fx-background-color:#f8fafc;");
        HBox hdr=new HBox(12); hdr.setAlignment(Pos.CENTER_LEFT); hdr.setPadding(new Insets(14,24,14,24));
        hdr.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        Label title=new Label("Fuel Prices"); title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Region sp=new Region(); HBox.setHgrow(sp,Priority.ALWAYS);
        Button updateBtn=new Button("Update Price"); updateBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        updateBtn.setOnAction(e->showUpdatePriceDialog());
        hdr.getChildren().addAll(title,sp,updateBtn);
        TableView<String[]> tv=new TableView<>(FXCollections.observableArrayList(dao.getPrices()));
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); tv.setStyle("-fx-background-color:white;");
        VBox.setVgrow(tv,Priority.ALWAYS);
        String[] cols={"ID","Fuel Type","Price per Litre (KES)","Last Updated"};
        for(int i=0;i<cols.length;i++){final int ci=i;TableColumn<String[],String> tc=new TableColumn<>(cols[i]);tc.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(ci<d.getValue().length?d.getValue()[ci]:""));tv.getColumns().add(tc);}
        v.getChildren().addAll(hdr,tv); return v;
    }

    private VBox buildDipView() {
        VBox v=new VBox(0); v.setStyle("-fx-background-color:#f8fafc;");
        HBox hdr=new HBox(12); hdr.setAlignment(Pos.CENTER_LEFT); hdr.setPadding(new Insets(14,24,14,24));
        hdr.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        Label title=new Label("Dip Readings — Tank Levels"); title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        hdr.getChildren().add(title);
        GridPane f=new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(24));
        ComboBox<String> fuelCb=new ComboBox<>(); fuelCb.getItems().addAll("Petrol","Diesel","Super Petrol"); fuelCb.setValue("Petrol"); fuelCb.setPrefWidth(200);
        TextField litresF=fld("Reading in litres",""); TextField attendantF=fld("Attendant name",""); TextField notesF=fld("Notes","");
        Button saveBtn=new Button("Save Dip Reading");
        saveBtn.setStyle("-fx-background-color:#16a34a;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;");
        saveBtn.setOnAction(e->{
            if(litresF.getText().isBlank()) return;
            try{dao.addDipReading(fuelCb.getValue(),Double.parseDouble(litresF.getText()),attendantF.getText().trim(),notesF.getText().trim());
                AuditLog.log("DIP_READING","Dip: "+fuelCb.getValue()+" "+litresF.getText()+"L","fuel",null);
                litresF.clear(); notesF.clear();
                Toast.success(SceneManager.getInstance().getStage(),"Saved","Dip reading recorded");
            }catch(Exception ex){Toast.error(SceneManager.getInstance().getStage(),"Error",ex.getMessage());}
        });
        f.addRow(0,lbl("Fuel Type"),fuelCb); f.addRow(1,lbl("Litres"),litresF); f.addRow(2,lbl("Attendant"),attendantF); f.addRow(3,lbl("Notes"),notesF); f.addRow(4,new Label(),saveBtn);
        v.getChildren().addAll(hdr,f); return v;
    }

    private void showSaleDialog() {
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle("Record Fuel Sale");
        d.getDialogPane().setStyle("-fx-background-color:white;"); d.getDialogPane().setPrefWidth(420);
        GridPane f=new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));
        ComboBox<String> pumpCb=new ComboBox<>(); pumpCb.getItems().addAll("P1","P2","P3","P4"); pumpCb.setValue("P1"); pumpCb.setPrefWidth(200);
        ComboBox<String> fuelCb=new ComboBox<>(); fuelCb.getItems().addAll("Petrol","Diesel","Super Petrol"); fuelCb.setValue("Petrol"); fuelCb.setPrefWidth(200);
        ComboBox<String> paymentCb=new ComboBox<>(); paymentCb.getItems().addAll("cash","mpesa","card","credit"); paymentCb.setValue("cash"); paymentCb.setPrefWidth(200);
        TextField litresF=fld("Litres",""); TextField priceF=fld("Price per litre","");
        TextField vehicleF=fld("Vehicle No (optional)",""); TextField custF=fld("Customer (optional)","");
        Label totalLbl=new Label("Amount: KES 0.00"); totalLbl.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#2563eb;");
        // Auto-fill price from prices list
        dao.getPrices().stream().filter(p->p[1].equals("Petrol")).findFirst().ifPresent(p->priceF.setText(p[2]));
        fuelCb.setOnAction(e->dao.getPrices().stream().filter(p->p[1].equals(fuelCb.getValue())).findFirst().ifPresent(p->priceF.setText(p[2])));
        Runnable calcTotal=()->{try{double l=Double.parseDouble(litresF.getText());double pr=Double.parseDouble(priceF.getText());totalLbl.setText("Amount: KES "+KESFormatter.format(l*pr));}catch(Exception ignored){}};
        litresF.textProperty().addListener((o,w,nv)->calcTotal.run());
        priceF.textProperty().addListener((o,w,nv)->calcTotal.run());
        f.addRow(0,lbl("Pump"),pumpCb,lbl("Fuel Type"),fuelCb);
        f.addRow(1,lbl("Litres"),litresF,lbl("Price/Litre"),priceF);
        f.addRow(2,lbl("Payment"),paymentCb,lbl("Vehicle"),vehicleF);
        f.addRow(3,lbl("Customer"),custF); f.addRow(4,totalLbl);
        d.getDialogPane().setContent(f); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        d.showAndWait().ifPresent(r->{
            if(r!=ButtonType.OK||litresF.getText().isBlank()) return;
            try{
                String[] openShift=dao.getOpenShift();
                Integer shiftId=openShift!=null?Integer.parseInt(openShift[0]):null;
                double litres=Double.parseDouble(litresF.getText()); double price=Double.parseDouble(priceF.getText());
                int id=dao.recordSale(shiftId,pumpCb.getValue(),fuelCb.getValue(),litres,price,paymentCb.getValue(),vehicleF.getText().trim(),custF.getText().trim());
                if(id>0){AuditLog.log("FUEL_SALE",fuelCb.getValue()+" "+litres+"L KES "+KESFormatter.format(litres*price),"fuel",id);
                    refreshStats();loadSales();Toast.success(SceneManager.getInstance().getStage(),"Sale recorded","KES "+KESFormatter.format(litres*price));}
            }catch(Exception ex){Toast.error(SceneManager.getInstance().getStage(),"Error",ex.getMessage());}
        });
    }

    private void showShiftDialog() {
        String[] open=dao.getOpenShift();
        if(open!=null){
            Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Close shift "+open[1]+" for "+open[2]+"?",ButtonType.YES,ButtonType.NO);
            a.setHeaderText("Close Shift"); a.showAndWait().ifPresent(r->{
                if(r==ButtonType.YES){
                    TextInputDialog inp=new TextInputDialog("0"); inp.setTitle("Closing Cash"); inp.setHeaderText("Enter closing cash (KES):"); inp.setContentText("Amount:");
                    inp.showAndWait().ifPresent(cash->{try{dao.closeShift(Integer.parseInt(open[0]),Double.parseDouble(cash));AuditLog.log("SHIFT_CLOSED","Shift "+open[1]+" closed","fuel",Integer.parseInt(open[0]));loadShifts();refreshStats();Toast.success(SceneManager.getInstance().getStage(),"Shift closed","Shift "+open[1]);}catch(Exception ex){}});
                }
            });
        } else {
            Dialog<ButtonType> d=new Dialog<>(); d.setTitle("Open New Shift");
            d.getDialogPane().setStyle("-fx-background-color:white;"); d.getDialogPane().setPrefWidth(380);
            GridPane f=new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));
            TextField attF=fld("Attendant Name",""); TextField cashF=fld("Opening Cash (KES)","0");
            f.addRow(0,lbl("Attendant"),attF); f.addRow(1,lbl("Opening Cash"),cashF);
            d.getDialogPane().setContent(f); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
            d.showAndWait().ifPresent(r->{if(r!=ButtonType.OK||attF.getText().isBlank()) return;
                try{int uid=1;try{uid=SessionManager.getInstance().getCurrentUser().getId();}catch(Exception ignored){}
                    int id=dao.openShift(attF.getText().trim(),Double.parseDouble(cashF.getText().isBlank()?"0":cashF.getText()),"",uid);
                    if(id>0){AuditLog.log("SHIFT_OPENED","Shift opened for "+attF.getText(),"fuel",id);loadShifts();refreshStats();Toast.success(SceneManager.getInstance().getStage(),"Shift opened",attF.getText());}
                }catch(Exception ex){Toast.error(SceneManager.getInstance().getStage(),"Error",ex.getMessage());}
            });
        }
    }

    private void showUpdatePriceDialog() {
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle("Update Fuel Price");
        d.getDialogPane().setStyle("-fx-background-color:white;"); d.getDialogPane().setPrefWidth(360);
        GridPane f=new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));
        ComboBox<String> fuelCb=new ComboBox<>(); fuelCb.getItems().addAll("Petrol","Diesel","Super Petrol"); fuelCb.setValue("Petrol"); fuelCb.setPrefWidth(240);
        TextField priceF=fld("New price per litre (KES)","");
        f.addRow(0,lbl("Fuel Type"),fuelCb); f.addRow(1,lbl("Price/Litre"),priceF);
        d.getDialogPane().setContent(f); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        d.showAndWait().ifPresent(r->{if(r!=ButtonType.OK||priceF.getText().isBlank()) return;
            try{dao.updatePrice(fuelCb.getValue(),Double.parseDouble(priceF.getText()));Toast.success(SceneManager.getInstance().getStage(),"Updated",fuelCb.getValue()+" KES "+priceF.getText()+"/L");}
            catch(Exception ex){Toast.error(SceneManager.getInstance().getStage(),"Error",ex.getMessage());}
        });
    }

    private void refreshStats(){
        revLbl.setText("KES "+KESFormatter.formatShort(dao.getTodayRevenue()));
        litresLbl.setText(String.format("%.1f",dao.getTodayLitres())+" L");
        String[] open=dao.getOpenShift();
        shiftLbl.setText(open!=null?open[2]:"None");
    }
    private void loadSales(){AsyncTask.run(dao::getTodaySales,salesData::setAll,err->{});}
    private void loadShifts(){AsyncTask.run(dao::getShifts,shiftData::setAll,err->{});}
    private Button tBtn(String label,boolean active){Button b=new Button(label);String base="-fx-background-color:transparent;-fx-border-color:transparent;-fx-pref-height:44px;-fx-padding:0 16;-fx-cursor:hand;-fx-font-size:12px;";b.setStyle(base+(active?"-fx-text-fill:#2563eb;-fx-font-weight:bold;-fx-border-color:transparent transparent #2563eb transparent;-fx-border-width:0 0 2 0;":"-fx-text-fill:#64748b;"));return b;}
    private void setActive(Button active,Button...rest){String base="-fx-background-color:transparent;-fx-pref-height:44px;-fx-padding:0 16;-fx-cursor:hand;-fx-font-size:12px;";active.setStyle(base+"-fx-text-fill:#2563eb;-fx-font-weight:bold;-fx-border-color:transparent transparent #2563eb transparent;-fx-border-width:0 0 2 0;");for(Button b:rest)b.setStyle(base+"-fx-text-fill:#64748b;-fx-border-color:transparent;");}
    private TextField fld(String p,String v){TextField tf=new TextField(v);tf.setPromptText(p);tf.setPrefWidth(200);tf.setStyle("-fx-pref-height:34px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");return tf;}
    private Label lbl(String t){Label l=new Label(t);l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;");return l;}
}
