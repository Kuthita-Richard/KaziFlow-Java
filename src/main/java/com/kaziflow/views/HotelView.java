package com.kaziflow.views;

import com.kaziflow.dao.HotelDAO;
import com.kaziflow.services.AuditLog;
import com.kaziflow.utils.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.List;

public class HotelView {

    private BorderPane root;
    private final HotelDAO dao = new HotelDAO();
    private ObservableList<String[]> resData = FXCollections.observableArrayList();
    private FlowPane roomGrid;
    private Label occupiedLbl, availableLbl, revenueLbl;

    public HotelView() { dao.ensureTables(); buildUI(); refreshRooms(); loadReservations("all"); }
    public BorderPane getRoot() { return root; }

    private void buildUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color:#f8fafc;");
        Button roomsBtn = tBtn("🛏 Rooms",        true);
        Button resBtn   = tBtn("📋 Reservations",  false);
        VBox roomsView  = buildRoomsView();
        VBox resView    = buildResView();
        StackPane area  = new StackPane(roomsView);
        VBox.setVgrow(area, Priority.ALWAYS);
        roomsBtn.setOnAction(e -> { area.getChildren().setAll(roomsView); setActive(roomsBtn,resBtn); refreshRooms(); });
        resBtn  .setOnAction(e -> { area.getChildren().setAll(resView);   setActive(resBtn,roomsBtn); loadReservations("all"); });
        HBox tabBar = new HBox(0,roomsBtn,resBtn);
        tabBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;-fx-padding:0 24;");
        VBox layout = new VBox(0, buildHeader(), tabBar, area);
        VBox.setVgrow(area, Priority.ALWAYS);
        root.setCenter(layout);
    }

    private HBox buildHeader() {
        HBox h = new HBox(16); h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(16,24,16,24));
        h.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        VBox tb = new VBox(2);
        Label t = new Label("🏨  Hotel & Lodging"); t.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label s = new Label("Room management · Reservations · Guest folio"); s.setStyle("-fx-font-size:12px;-fx-text-fill:#64748b;");
        tb.getChildren().addAll(t,s);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        occupiedLbl  = sv(String.valueOf(dao.getOccupiedCount()));
        availableLbl = sv(String.valueOf(dao.getAvailableCount()));
        revenueLbl   = sv("KES "+KESFormatter.formatShort(dao.getTodayRevenue()));
        Button newBtn = new Button("+ New Reservation");
        newBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        newBtn.setOnAction(e -> showReservationDialog(null));
        h.getChildren().addAll(tb,sp,sc("Occupied",occupiedLbl),sc("Available",availableLbl),sc("Today Revenue",revenueLbl),newBtn);
        return h;
    }

    private Label sv(String v){Label l=new Label(v);l.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");return l;}
    private VBox sc(String label,Label val){Label lbl=new Label(label);lbl.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;-fx-font-weight:bold;");VBox c=new VBox(2,lbl,val);c.setStyle("-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#e2e8f0;-fx-border-radius:10;-fx-border-width:1;-fx-padding:8 16;-fx-min-width:100px;");return c;}

    // ── Rooms View ─────────────────────────────────────────────────────────

    private VBox buildRoomsView() {
        VBox v = new VBox(0); v.setStyle("-fx-background-color:#f1f5f9;");
        HBox legend = new HBox(20); legend.setPadding(new Insets(10,24,10,24)); legend.setAlignment(Pos.CENTER_LEFT);
        legend.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        legend.getChildren().addAll(legItem("#dcfce7","#16a34a","Available"),legItem("#fee2e2","#dc2626","Occupied"),legItem("#fef3c7","#d97706","Reserved"),legItem("#dbeafe","#2563eb","Cleaning"),legItem("#f1f5f9","#94a3b8","Maintenance"));
        roomGrid = new FlowPane(); roomGrid.setHgap(14); roomGrid.setVgap(14); roomGrid.setPadding(new Insets(20));
        ScrollPane scroll = new ScrollPane(roomGrid); scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:#f1f5f9;-fx-background:#f1f5f9;"); VBox.setVgrow(scroll, Priority.ALWAYS);
        v.getChildren().addAll(legend, scroll); return v;
    }

    private HBox legItem(String bg,String text,String label){StackPane box=new StackPane();box.setStyle("-fx-background-color:"+bg+";-fx-background-radius:5;-fx-min-width:18;-fx-min-height:18;");Label l=new Label(label);l.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");HBox item=new HBox(6,box,l);item.setAlignment(Pos.CENTER_LEFT);return item;}

    private void refreshRooms() {
        roomGrid.getChildren().clear();
        dao.getRooms().forEach(room -> roomGrid.getChildren().add(buildRoomCard(room)));
        occupiedLbl.setText(String.valueOf(dao.getOccupiedCount()));
        availableLbl.setText(String.valueOf(dao.getAvailableCount()));
        revenueLbl.setText("KES "+KESFormatter.formatShort(dao.getTodayRevenue()));
    }

    private VBox buildRoomCard(String[] room) {
        // [0]=id [1]=room_no [2]=type [3]=floor [4]=capacity [5]=rate [6]=status [7]=guest [8]=checkout
        String status = room[6];
        String bg,border,tc;
        switch(status){
            case "occupied"    ->{bg="#fee2e2";border="#dc2626";tc="#dc2626";}
            case "reserved"    ->{bg="#fef3c7";border="#d97706";tc="#d97706";}
            case "cleaning"    ->{bg="#dbeafe";border="#2563eb";tc="#2563eb";}
            case "maintenance" ->{bg="#f1f5f9";border="#94a3b8";tc="#64748b";}
            default            ->{bg="#dcfce7";border="#16a34a";tc="#16a34a";}
        }
        VBox card = new VBox(6); card.setAlignment(Pos.CENTER); card.setPrefWidth(120); card.setPrefHeight(120);
        card.setStyle("-fx-background-color:"+bg+";-fx-background-radius:12;-fx-border-color:"+border+";-fx-border-radius:12;-fx-border-width:2;-fx-padding:10;-fx-cursor:hand;");
        Label no=new Label(room[1]); no.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:"+tc+";");
        Label type=new Label(room[2]); type.setStyle("-fx-font-size:10px;-fx-text-fill:#64748b;");
        Label rate=new Label("KES "+KESFormatter.formatShort(Double.parseDouble(room[5]))+"/night"); rate.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:"+tc+";");
        card.getChildren().addAll(no,type,rate);
        if("occupied".equals(status)&&!room[7].isEmpty()){
            Label guest=new Label(room[7].split(" ")[0]); guest.setStyle("-fx-font-size:10px;-fx-text-fill:#dc2626;");
            card.getChildren().add(guest);
        }
        card.setOnMouseClicked(e->handleRoomClick(room));
        return card;
    }

    private void handleRoomClick(String[] room) {
        String status=room[6]; int roomId=Integer.parseInt(room[0]);
        if("available".equals(status)||"cleaning".equals(status)){
            Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Room "+room[1]+" — "+room[2]+"\nRate: KES "+room[5]+"/night\n\nNew reservation?",ButtonType.YES,ButtonType.NO);
            a.setHeaderText("Room "+room[1]);
            a.showAndWait().ifPresent(r->{if(r==ButtonType.YES) showReservationDialog(room);});
        } else if("occupied".equals(status)){
            Alert a=new Alert(Alert.AlertType.CONFIRMATION); a.setTitle("Room "+room[1]);
            a.setHeaderText("Guest: "+room[7]); a.setContentText("Check out or mark cleaning?");
            ButtonType coBtn=new ButtonType("Check Out"); ButtonType clBtn=new ButtonType("Mark Cleaning");
            a.getButtonTypes().setAll(coBtn,clBtn,ButtonType.CANCEL);
            a.showAndWait().ifPresent(r->{
                if(r==coBtn){ dao.getReservations("checked_in").stream()
                    .filter(res->res[5].equals(room[1])).findFirst().ifPresent(res->{
                        dao.checkOut(Integer.parseInt(res[0]),roomId);
                        AuditLog.log("HOTEL_CHECKOUT","Room "+room[1]+" — "+res[2],"hotel",Integer.parseInt(res[0]));
                        refreshRooms();Toast.success(SceneManager.getInstance().getStage(),"Checked Out","Room "+room[1]+" is now cleaning");
                    });
                } else if(r==clBtn){ dao.updateRoomStatus(roomId,"cleaning"); refreshRooms(); }
            });
        } else if("reserved".equals(status)){
            dao.getReservations("reserved").stream()
                .filter(res->res[5].equals(room[1])).findFirst().ifPresent(res->{
                    Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Check in "+res[2]+"?",ButtonType.YES,ButtonType.NO);
                    a.setHeaderText("Room "+room[1]+" — "+res[3]);
                    a.showAndWait().ifPresent(r->{if(r==ButtonType.YES){
                        dao.checkIn(Integer.parseInt(res[0]),roomId);
                        AuditLog.log("HOTEL_CHECKIN","Room "+room[1]+" — "+res[2],"hotel",Integer.parseInt(res[0]));
                        refreshRooms();Toast.success(SceneManager.getInstance().getStage(),"Checked In",res[2]);
                    }});
                });
        }
    }

    // ── Reservations View ──────────────────────────────────────────────────

    private VBox buildResView() {
        VBox v=new VBox(0); v.setStyle("-fx-background-color:white;");
        TableView<String[]> tv=new TableView<>(resData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); tv.setStyle("-fx-background-color:white;");
        VBox.setVgrow(tv,Priority.ALWAYS);
        // [0]=id [1]=res_no [2]=guest_name [3]=phone [4]=room_id [5]=room_no
        // [6]=checkin [7]=checkout [8]=nights [9]=total [10]=status
        String[] hdrs={"Res #","Guest","Phone","Room","Check-in","Check-out","Nights","Total (KES)","Status"};
        int[] idxs={1,2,3,5,6,7,8,9,10};
        for(int i=0;i<hdrs.length;i++){final int ci=idxs[i];TableColumn<String[],String> tc=new TableColumn<>(hdrs[i]);
            tc.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(ci<d.getValue().length?d.getValue()[ci]:""));tv.getColumns().add(tc);}
        TableColumn<String[],Void> actCol=new TableColumn<>(""); actCol.setPrefWidth(200);
        actCol.setCellFactory(c->new TableCell<>(){
            private final Button ciBtn=btn2("✔ Check-In","#16a34a");
            private final Button folioBtn=btn2("📋 Folio","#2563eb");
            private final Button cancelBtn=btn2("✕","#dc2626");
            private final HBox box=new HBox(5,ciBtn,folioBtn,cancelBtn);
            {
                ciBtn.setOnAction(e->{String[] row=getTableView().getItems().get(getIndex());
                    if("reserved".equals(row[10])){ dao.checkIn(Integer.parseInt(row[0]),Integer.parseInt(row[4])); loadReservations("all"); refreshRooms();
                        Toast.success(SceneManager.getInstance().getStage(),"Checked In",row[2]+" — Room "+row[5]); }
                });
                folioBtn.setOnAction(e->showFolioDialog(getTableView().getItems().get(getIndex())));
                cancelBtn.setOnAction(e->{String[] row=getTableView().getItems().get(getIndex());
                    if("reserved".equals(row[10])){ dao.updateRoomStatus(Integer.parseInt(row[4]),"available");
                        loadReservations("all"); refreshRooms(); }
                });
            }
            @Override protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);if(empty){setGraphic(null);return;}String st=getTableView().getItems().get(getIndex())[10];ciBtn.setDisable(!"reserved".equals(st));setGraphic(box);}
        });
        tv.getColumns().add(actCol);
        v.getChildren().add(tv); return v;
    }

    private void showFolioDialog(String[] res) {
        List<String[]> folio=dao.getFolio(Integer.parseInt(res[0]));
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle("Guest Folio — "+res[2]+" Room "+res[5]);
        d.getDialogPane().setStyle("-fx-background-color:white;"); d.getDialogPane().setPrefWidth(500); d.getDialogPane().setPrefHeight(400);
        VBox layout=new VBox(0);
        TableView<String[]> tv=new TableView<>(FXCollections.observableArrayList(folio));
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); VBox.setVgrow(tv,Priority.ALWAYS);
        String[] cols={"Description","Amount (KES)","Date"};
        for(int i=0;i<cols.length;i++){final int ci=i;TableColumn<String[],String> tc=new TableColumn<>(cols[i]);tc.setCellValueFactory(dd->new javafx.beans.property.SimpleStringProperty(ci<dd.getValue().length?dd.getValue()[ci]:""));tv.getColumns().add(tc);}
        HBox addBar=new HBox(8); addBar.setPadding(new Insets(10,16,10,16)); addBar.setAlignment(Pos.CENTER_LEFT);
        TextField descF=new TextField(); descF.setPromptText("Charge description e.g. Restaurant"); descF.setPrefWidth(220); descF.setStyle("-fx-pref-height:32px;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:12px;");
        TextField amtF=new TextField(); amtF.setPromptText("Amount (KES)"); amtF.setPrefWidth(100); amtF.setStyle(descF.getStyle());
        Button addBtn=new Button("+ Add Charge"); addBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-background-radius:6;-fx-pref-height:32px;-fx-cursor:hand;");
        addBtn.setOnAction(e->{if(descF.getText().isBlank()) return;try{dao.addFolioCharge(Integer.parseInt(res[0]),descF.getText().trim(),Double.parseDouble(amtF.getText()));tv.setItems(FXCollections.observableArrayList(dao.getFolio(Integer.parseInt(res[0]))));descF.clear();amtF.clear();}catch(Exception ex){}});
        addBar.getChildren().addAll(descF,amtF,addBtn);
        Label totalLbl=new Label("Grand Total: KES "+KESFormatter.format(Double.parseDouble(res[9]))); totalLbl.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1e293b;-fx-padding:8 16;");
        layout.getChildren().addAll(addBar,tv,totalLbl); VBox.setVgrow(tv,Priority.ALWAYS);
        d.getDialogPane().setContent(layout); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.showAndWait();
    }

    private void showReservationDialog(String[] room) {
        Dialog<ButtonType> d=new Dialog<>(); d.setTitle("New Reservation"+(room!=null?" — Room "+room[1]:""));
        d.getDialogPane().setStyle("-fx-background-color:white;"); d.getDialogPane().setPrefWidth(480);
        GridPane f=new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));
        TextField guestF=fld("Guest Name *",""); TextField phoneF=fld("Phone",""); TextField emailF=fld("Email","");
        List<String[]> rooms=dao.getRooms();
        ComboBox<String> roomCb=new ComboBox<>(); roomCb.setPrefWidth(300);
        rooms.stream().filter(r->"available".equals(r[6])||"reserved".equals(r[6])).forEach(r->roomCb.getItems().add(r[0]+":Room "+r[1]+" "+r[2]+" KES "+r[5]+"/night"));
        if(room!=null) roomCb.setValue(room[0]+":Room "+room[1]+" "+room[2]+" KES "+room[5]+"/night");
        else if(!roomCb.getItems().isEmpty()) roomCb.setValue(roomCb.getItems().get(0));
        TextField ciF=fld("Check-in (YYYY-MM-DD)",LocalDate.now().toString());
        TextField coF=fld("Check-out (YYYY-MM-DD)",LocalDate.now().plusDays(1).toString());
        TextField depF=fld("Deposit (KES)","0"); TextField adultF=fld("Adults","1"); TextField childF=fld("Children","0");
        TextField notesF=fld("Notes","");
        f.addRow(0,lbl("Guest Name"),guestF,lbl("Phone"),phoneF);
        f.addRow(1,lbl("Email"),emailF); f.addRow(2,lbl("Room"),roomCb);
        f.addRow(3,lbl("Check-in"),ciF,lbl("Check-out"),coF);
        f.addRow(4,lbl("Deposit"),depF,lbl("Adults"),adultF);
        f.addRow(5,lbl("Children"),childF,lbl("Notes"),notesF);
        d.getDialogPane().setContent(f); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        d.showAndWait().ifPresent(r->{
            if(r!=ButtonType.OK||guestF.getText().isBlank()||roomCb.getValue()==null) return;
            try{
                String[] rp=roomCb.getValue().split(":");
                int roomId=Integer.parseInt(rp[0]);
                String roomNo=rp[1].split(" ")[1];
                String[] selRoom=rooms.stream().filter(rm->rm[0].equals(String.valueOf(roomId))).findFirst().orElse(null);
                if(selRoom==null) return;
                double rate=Double.parseDouble(selRoom[5]);
                long nights=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(ciF.getText().trim()),LocalDate.parse(coF.getText().trim()));
                if(nights<1) nights=1;
                int uid=1; try{uid=SessionManager.getInstance().getCurrentUser().getId();}catch(Exception ignored){}
                int id=dao.createReservation(guestF.getText().trim(),phoneF.getText().trim(),emailF.getText().trim(),
                    roomId,roomNo,ciF.getText().trim(),coF.getText().trim(),(int)nights,rate,
                    Double.parseDouble(depF.getText().isBlank()?"0":depF.getText()),
                    Integer.parseInt(adultF.getText()),Integer.parseInt(childF.getText()),notesF.getText().trim(),uid);
                if(id>0){AuditLog.log("HOTEL_RESERVATION",guestF.getText()+" Room "+roomNo,"hotel",id);
                    refreshRooms();loadReservations("all");Toast.success(SceneManager.getInstance().getStage(),"Reserved!","Room "+roomNo+" for "+guestF.getText());}
            }catch(Exception ex){Toast.error(SceneManager.getInstance().getStage(),"Error",ex.getMessage());}
        });
    }

    private void loadReservations(String filter) { AsyncTask.run(()->dao.getReservations(filter),resData::setAll,err->{}); }

    private Button tBtn(String label,boolean active){Button b=new Button(label);String base="-fx-background-color:transparent;-fx-border-color:transparent;-fx-pref-height:44px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;";b.setStyle(base+(active?"-fx-text-fill:#2563eb;-fx-font-weight:bold;-fx-border-color:transparent transparent #2563eb transparent;-fx-border-width:0 0 2 0;":"-fx-text-fill:#64748b;"));return b;}
    private void setActive(Button active,Button...rest){String base="-fx-background-color:transparent;-fx-pref-height:44px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;";active.setStyle(base+"-fx-text-fill:#2563eb;-fx-font-weight:bold;-fx-border-color:transparent transparent #2563eb transparent;-fx-border-width:0 0 2 0;");for(Button b:rest)b.setStyle(base+"-fx-text-fill:#64748b;-fx-border-color:transparent;");}
    private Button btn2(String t,String color){Button b=new Button(t);b.setStyle("-fx-background-color:white;-fx-border-color:"+color+";-fx-border-radius:5;-fx-background-radius:5;-fx-font-size:10px;-fx-text-fill:"+color+";-fx-cursor:hand;-fx-padding:2 6;");return b;}
    private TextField fld(String p,String v){TextField tf=new TextField(v);tf.setPromptText(p);tf.setPrefWidth(220);tf.setStyle("-fx-pref-height:34px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");return tf;}
    private Label lbl(String t){Label l=new Label(t);l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;");return l;}
}
