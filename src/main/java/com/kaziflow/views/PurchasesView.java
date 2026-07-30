package com.kaziflow.views;

import com.kaziflow.dao.PurchaseDAO;
import com.kaziflow.dao.ProductDAO;
import com.kaziflow.dao.SupplierDAO;
import com.kaziflow.models.Purchase;
import com.kaziflow.models.PurchaseItem;
import com.kaziflow.models.Product;
import com.kaziflow.models.Supplier;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PurchasesView {

    private VBox root;
    // Suppliers tab
    private ObservableList<Supplier> supplierData = FXCollections.observableArrayList();
    private TableView<Supplier> supplierTable;
    // Purchase orders tab
    private ObservableList<Purchase> purchaseData = FXCollections.observableArrayList();
    private TableView<Purchase> purchaseTable;

    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final PurchaseDAO purchaseDAO = new PurchaseDAO();
    private final ProductDAO  productDAO  = new ProductDAO();

    private static final String CARD = "-fx-background-color:white;-fx-background-radius:12;-fx-border-color:#e2e8f0;-fx-border-radius:12;-fx-border-width:1;";

    public PurchasesView() { buildUI(); }
    public VBox getRoot() { return root; }

    private void buildUI() {
        root = new VBox(0); root.setStyle("-fx-background-color:#f8fafc;");

        // Tab bar
        HBox tabBar = new HBox(0);
        tabBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;-fx-padding:0 24;");
        Button suppTab    = tabBtn("Suppliers",        true);
        Button orderTab   = tabBtn("Purchase Orders",  false);
        Button returnsTab = tabBtn("Returns",          false);

        StackPane area = new StackPane(); area.setStyle("-fx-background-color:#f8fafc;"); VBox.setVgrow(area, Priority.ALWAYS);
        VBox suppView    = buildSuppliersView();
        VBox orderView   = buildPurchaseOrdersView();
        VBox returnsView = buildReturnsView();
        area.getChildren().setAll(suppView);

        suppTab   .setOnAction(e -> { area.getChildren().setAll(suppView);    setActive(suppTab, orderTab, returnsTab); loadSuppliers(); });
        orderTab  .setOnAction(e -> { area.getChildren().setAll(orderView);   setActive(orderTab, suppTab, returnsTab); loadPurchases(); });
        returnsTab.setOnAction(e -> { area.getChildren().setAll(returnsView); setActive(returnsTab, suppTab, orderTab); loadReturns(returnsView); });

        tabBar.getChildren().addAll(suppTab, orderTab, returnsTab);
        root.getChildren().addAll(tabBar, area);
        loadSuppliers();
    }

    private void setActive(Button a, Button... rest) { applyTab(a,true); for (Button b:rest) applyTab(b,false); }
    private Button tabBtn(String label, boolean active) { Button b=new Button(label); applyTab(b,active); return b; }
    private void applyTab(Button b, boolean active) {
        if (active) b.setStyle("-fx-background-color:transparent;-fx-text-fill:#2563eb;-fx-font-size:13px;-fx-font-weight:bold;-fx-padding:14 16;-fx-border-color:transparent transparent #2563eb transparent;-fx-border-width:0 0 2 0;-fx-cursor:hand;");
        else        b.setStyle("-fx-background-color:transparent;-fx-text-fill:#94a3b8;-fx-font-size:13px;-fx-padding:14 16;-fx-border-color:transparent;-fx-cursor:hand;");
    }

    // ═══ SUPPLIERS TAB ════════════════════════════════════════════════════════

    private VBox buildSuppliersView() {
        VBox view = new VBox(0); view.setStyle("-fx-background-color:#f8fafc;");
        Button addBtn = new Button("+ Add Supplier");
        addBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        addBtn.setOnAction(e -> showAddEditSupplierDialog(null));
        view.getChildren().addAll(pageHeader("Supplier Management","Purchases › Suppliers",addBtn), buildSuppliersBody());
        return view;
    }

    private VBox buildSuppliersBody() {
        VBox content = new VBox(20); content.setPadding(new Insets(24));
        int total = supplierDAO.getTotalCount();
        double outstanding = supplierDAO.getTotalOutstandingBalance();
        int active = supplierDAO.getActiveCount();

        HBox stats = statsRow(
            statCard("Total Suppliers",      String.valueOf(total),             "Registered vendors",    "#1e293b"),
            statCard("Outstanding Balance",  KESFormatter.formatShort(outstanding), "Owed to suppliers",  "#dc2626"),
            statCard("Active Suppliers",     String.valueOf(active),            "93% active",            "#16a34a"),
            statCard("Avg Payment Terms",    "30 days",                         "Industry standard",     "#7c3aed")
        );

        HBox filterBar = new HBox(10); filterBar.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField(); search.setPromptText("Search suppliers..."); search.getStyleClass().add("search-box"); search.setPrefWidth(280);
        search.textProperty().addListener((obs,o,v) -> supplierData.setAll(v.isBlank()?supplierDAO.findAll():supplierDAO.search(v)));
        ComboBox<String> payFilter = new ComboBox<>();
        payFilter.getItems().addAll("Payment Status","Current","Pending","Overdue"); payFilter.setValue("Payment Status"); payFilter.setPrefHeight(36);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button exportBtn = new Button("⬇ Export CSV");
        exportBtn.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-pref-height:36px;-fx-padding:0 14;-fx-cursor:hand;-fx-font-size:13px;");
        exportBtn.setOnAction(e -> new com.kaziflow.services.ReportExportService().exportCSV(com.kaziflow.services.ReportExportService.ReportType.PURCHASES));
        filterBar.getChildren().addAll(search, payFilter, sp, exportBtn);

        VBox tableCard = new VBox(0); tableCard.setStyle(CARD);
        supplierTable = buildSupplierTable();
        tableCard.getChildren().add(supplierTable);

        content.getChildren().addAll(stats, filterBar, tableCard);
        return content;
    }

    @SuppressWarnings("unchecked")
    private TableView<Supplier> buildSupplierTable() {
        TableView<Supplier> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(440); tv.setStyle("-fx-background-color:white;"); tv.setItems(supplierData);

        TableColumn<Supplier,String> nameCol = new TableColumn<>("SUPPLIER");
        nameCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getName()));
        nameCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String n, boolean empty) {
                super.updateItem(n,empty); if(empty||n==null){setGraphic(null);return;}
                Supplier s=getTableView().getItems().get(getIndex()); VBox box=new VBox(2);
                Label name=new Label(s.getName()); name.setStyle("-fx-font-weight:bold;-fx-font-size:13px;-fx-text-fill:#1e293b;");
                Label code=new Label(s.getCode()!=null?s.getCode():"—"); code.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;");
                box.getChildren().addAll(name,code); setGraphic(box); setText(null);
            }
        }); nameCol.setPrefWidth(180);

        TableColumn<Supplier,String> contactCol = new TableColumn<>("CONTACT");
        contactCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(""));
        contactCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s,empty); if(empty){setGraphic(null);return;}
                Supplier sup=getTableView().getItems().get(getIndex()); VBox box=new VBox(2);
                Label phone=new Label(sup.getPhone()!=null?"📞 "+sup.getPhone():"—"); phone.setStyle("-fx-font-size:12px;-fx-text-fill:#1e293b;");
                Label email=new Label(sup.getEmail()!=null?sup.getEmail():"—"); email.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");
                box.getChildren().addAll(phone,email); setGraphic(box); setText(null);
            }
        }); contactCol.setPrefWidth(200);

        TableColumn<Supplier,String> catCol = new TableColumn<>("CATEGORY");
        catCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getCategory()!=null?d.getValue().getCategory():"General"));
        catCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String cat, boolean empty) {
                super.updateItem(cat,empty); if(empty||cat==null){setGraphic(null);return;}
                Label badge=new Label(cat); badge.setStyle("-fx-background-color:#eff6ff;-fx-text-fill:#2563eb;-fx-font-size:11px;-fx-background-radius:20;-fx-padding:3 10;");
                setGraphic(badge); setText(null);
            }
        }); catCol.setPrefWidth(150);

        TableColumn<Supplier,String> balCol = new TableColumn<>("OUTSTANDING");
        balCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(""));
        balCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s,empty); if(empty){setGraphic(null);return;}
                Supplier sup=getTableView().getItems().get(getIndex());
                String color=sup.getOutstandingBalance()>0?"#dc2626":"#16a34a";
                Label l=new Label(KESFormatter.format(sup.getOutstandingBalance())); l.setStyle("-fx-font-weight:bold;-fx-text-fill:"+color+";-fx-font-size:13px;");
                setGraphic(l); setText(null);
            }
        }); balCol.setPrefWidth(130);

        TableColumn<Supplier,String> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getPaymentStatus()!=null?d.getValue().getPaymentStatus():"current"));
        statusCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s,empty); if(empty||s==null){setGraphic(null);return;}
                String bg,fg;
                switch(s.toLowerCase()){case"overdue"->{bg="#fee2e2";fg="#dc2626";}case"pending"->{bg="#fef3c7";fg="#d97706";}default->{bg="#dcfce7";fg="#16a34a";}}
                Label badge=new Label(s.substring(0,1).toUpperCase()+s.substring(1)); badge.setStyle("-fx-background-color:"+bg+";-fx-text-fill:"+fg+";-fx-font-size:11px;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:3 10;");
                setGraphic(badge); setText(null);
            }
        }); statusCol.setPrefWidth(100);

        TableColumn<Supplier,Void> actCol = new TableColumn<>("");
        actCol.setPrefWidth(90);
        actCol.setCellFactory(c->new TableCell<>(){
            private final Button editBtn = new Button("✎ Edit");
            { editBtn.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:5;-fx-background-radius:5;-fx-text-fill:#475569;-fx-cursor:hand;-fx-font-size:11px;-fx-padding:4 8;");
              editBtn.setOnAction(e->showAddEditSupplierDialog(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);setGraphic(empty?null:editBtn);}
        });

        tv.getColumns().addAll(nameCol, contactCol, catCol, balCol, statusCol, actCol);
        return tv;
    }

    private void loadSuppliers() {
        AsyncTask.run(supplierDAO::findAll, supplierData::setAll, err -> {});
    }

    private void showAddEditSupplierDialog(Supplier existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing==null?"Add Supplier":"Edit Supplier"); dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color:white;"); dialog.getDialogPane().setPrefWidth(480);

        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));
        TextField nameF  = fld("Company Name",  existing!=null?existing.getName():"");
        TextField phoneF = fld("Phone (+254)",   existing!=null&&existing.getPhone()!=null?existing.getPhone():"");
        TextField emailF = fld("Email",          existing!=null&&existing.getEmail()!=null?existing.getEmail():"");
        TextField addrF  = fld("Address",        existing!=null&&existing.getAddress()!=null?existing.getAddress():"");
        TextField termsF = fld("Payment Terms",  existing!=null?String.valueOf(existing.getPaymentTerms()):"30");
        ComboBox<String> catCombo = new ComboBox<>();
        catCombo.getItems().addAll("Building Materials","Cement","Paints","Plumbing","Electrical","Safety","Tools","Steel","Timber","General");
        catCombo.setValue(existing!=null&&existing.getCategory()!=null?existing.getCategory():"General"); catCombo.setPrefWidth(200);

        Label validMsg = new Label(""); validMsg.setStyle("-fx-text-fill:#dc2626;-fx-font-size:12px;");
        form.addRow(0,lbl("Company Name *"),nameF,  lbl("Phone"),phoneF);
        form.addRow(1,lbl("Email"),emailF,           lbl("Category"),catCombo);
        form.addRow(2,lbl("Address"),addrF,          lbl("Payment Terms (days)"),termsF);
        form.add(validMsg,0,3,4,1);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        Button okBtn=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev->{
            if(nameF.getText().trim().isEmpty()){nameF.setStyle(nameF.getStyle()+"-fx-border-color:#dc2626;");validMsg.setText("Company name is required.");ev.consume();}
        });

        dialog.showAndWait().ifPresent(result->{
            if(result==ButtonType.OK){
                Supplier s=existing!=null?existing:new Supplier();
                s.setName(nameF.getText().trim()); s.setPhone(phoneF.getText().trim()); s.setEmail(emailF.getText().trim());
                s.setAddress(addrF.getText().trim()); s.setCategory(catCombo.getValue()); s.setStatus("active");
                try{s.setPaymentTerms(Integer.parseInt(termsF.getText().trim()));}catch(Exception ignored){s.setPaymentTerms(30);}
                boolean ok=existing==null?supplierDAO.save(s):supplierDAO.update(s);
                if(ok) {
                    com.kaziflow.services.AuditLog.log(existing==null?"SUPPLIER_CREATED":"SUPPLIER_UPDATED",
                        (existing==null?"New supplier: ":"Updated supplier: ") + s.getName(), "purchases", null);
                    Toast.success(SceneManager.getInstance().getStage(),
                        existing==null?"Supplier added":"Supplier updated", s.getName());
                    loadSuppliers();
                } else {
                    Toast.error(SceneManager.getInstance().getStage(), "Save failed", "Could not save supplier.");
                }
            }
        });
    }

    // ═══ PURCHASE ORDERS TAB ═════════════════════════════════════════════════

    private VBox buildPurchaseOrdersView() {
        VBox view = new VBox(0); view.setStyle("-fx-background-color:#f8fafc;");
        Button newOrderBtn = new Button("+ New Purchase Order");
        newOrderBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        newOrderBtn.setOnAction(e -> showNewPurchaseOrderDialog());
        view.getChildren().addAll(pageHeader("Purchase Orders","Purchases › Purchase Orders",newOrderBtn), buildPurchaseOrdersBody());
        return view;
    }

    private VBox buildPurchaseOrdersBody() {
        VBox content = new VBox(20); content.setPadding(new Insets(24));

        double outstanding = purchaseDAO.getTotalOutstanding();
        HBox stats = statsRow(
            statCard("Total Orders",      "—",                                     "All time",         "#1e293b"),
            statCard("Outstanding",       KESFormatter.formatShort(outstanding),   "Awaiting payment", "#dc2626"),
            statCard("This Month",        "—",                                     "October 2024",     "#d97706"),
            statCard("Avg Order Value",   "—",                                     "Per order",        "#7c3aed")
        );

        HBox filterBar = new HBox(10); filterBar.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField(); search.setPromptText("Search purchase orders..."); search.getStyleClass().add("search-box"); search.setPrefWidth(280);
        search.textProperty().addListener((obs,o,v) -> { if(v.isBlank()) loadPurchases(); /* else filter */ });
        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All Status","pending","received","partial"); statusFilter.setValue("All Status"); statusFilter.setPrefHeight(36);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        filterBar.getChildren().addAll(search, statusFilter, sp);

        VBox tableCard = new VBox(0); tableCard.setStyle(CARD);
        purchaseTable = buildPurchaseTable();
        tableCard.getChildren().add(purchaseTable);

        content.getChildren().addAll(stats, filterBar, tableCard);
        return content;
    }

    @SuppressWarnings("unchecked")
    private TableView<Purchase> buildPurchaseTable() {
        TableView<Purchase> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(440); tv.setStyle("-fx-background-color:white;"); tv.setItems(purchaseData);

        TableColumn<Purchase,String> poCol = new TableColumn<>("PO NUMBER");
        poCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getPurchaseNumber()!=null?d.getValue().getPurchaseNumber():"—")); poCol.setPrefWidth(120);

        TableColumn<Purchase,String> supplierCol = new TableColumn<>("SUPPLIER");
        supplierCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getSupplierName()!=null?d.getValue().getSupplierName():"—")); supplierCol.setPrefWidth(180);

        TableColumn<Purchase,String> dateCol = new TableColumn<>("DATE");
        dateCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(
            d.getValue().getCreatedAt()!=null?d.getValue().getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")):"—")); dateCol.setPrefWidth(120);

        TableColumn<Purchase,String> totalCol = new TableColumn<>("TOTAL AMOUNT");
        totalCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(KESFormatter.format(d.getValue().getTotalAmount())));
        totalCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String a, boolean empty){
                super.updateItem(a,empty); if(empty||a==null){setText(null);return;}
                Label l=new Label(a); l.setStyle("-fx-font-weight:bold;-fx-font-size:13px;-fx-text-fill:#1e293b;"); setGraphic(l); setText(null);
            }
        }); totalCol.setPrefWidth(130);

        TableColumn<Purchase,String> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getStatus()!=null?d.getValue().getStatus():"pending"));
        statusCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String s, boolean empty){
                super.updateItem(s,empty); if(empty||s==null){setGraphic(null);return;}
                String bg,fg;
                switch(s){case"received"->{bg="#dcfce7";fg="#16a34a";}case"partial"->{bg="#fef3c7";fg="#d97706";}default->{bg="#f1f5f9";fg="#475569";}}
                Label badge=new Label(s.substring(0,1).toUpperCase()+s.substring(1)); badge.setStyle("-fx-background-color:"+bg+";-fx-text-fill:"+fg+";-fx-font-size:11px;-fx-background-radius:20;-fx-padding:3 10;-fx-font-weight:bold;");
                setGraphic(badge); setText(null);
            }
        }); statusCol.setPrefWidth(100);

        TableColumn<Purchase,String> paymentCol = new TableColumn<>("PAYMENT STATUS");
        paymentCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getPaymentStatus()!=null?d.getValue().getPaymentStatus():"unpaid"));
        paymentCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String s, boolean empty){
                super.updateItem(s,empty); if(empty||s==null){setGraphic(null);return;}
                String color=s.equals("paid")?"#16a34a":s.equals("partial")?"#d97706":"#dc2626";
                Label l=new Label("● "+s.substring(0,1).toUpperCase()+s.substring(1)); l.setStyle("-fx-text-fill:"+color+";-fx-font-size:12px;-fx-font-weight:bold;");
                setGraphic(l); setText(null);
            }
        }); paymentCol.setPrefWidth(130);

        TableColumn<Purchase,Void> actCol = new TableColumn<>("");
        actCol.setPrefWidth(80);
        actCol.setCellFactory(c->new TableCell<>(){
            private final Button viewBtn=new Button("👁 View");
            {viewBtn.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:5;-fx-background-radius:5;-fx-text-fill:#475569;-fx-cursor:hand;-fx-font-size:11px;-fx-padding:4 8;");
             viewBtn.setOnAction(e->showPurchaseDetail(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);setGraphic(empty?null:viewBtn);}
        });

        tv.getColumns().addAll(poCol, supplierCol, dateCol, totalCol, statusCol, paymentCol, actCol);
        return tv;
    }

    private void loadPurchases() {
        AsyncTask.run(purchaseDAO::findAll, purchaseData::setAll, err -> {});
    }

    private void showPurchaseDetail(Purchase p) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Purchase Order — " + p.getPurchaseNumber());
        alert.setHeaderText("Supplier: " + p.getSupplierName() + "\nTotal: " + KESFormatter.format(p.getTotalAmount()) + "\nStatus: " + p.getStatus());
        alert.setContentText("Open the full purchase order view to see individual line items and update payment status.");
        alert.show();
    }

    private void showNewPurchaseOrderDialog() {
        // ── Cart state ──
        List<PurchaseItem> lineItems = new ArrayList<>();
        ObservableList<String> lineDisplay = FXCollections.observableArrayList();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Purchase Order"); dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color:white;"); dialog.getDialogPane().setPrefWidth(700);

        VBox main = new VBox(16); main.setPadding(new Insets(20));

        // ── Header: Supplier + Date ──
        HBox headerRow = new HBox(16); headerRow.setAlignment(Pos.CENTER_LEFT);
        ComboBox<String> supplierCombo = new ComboBox<>();
        List<Supplier> suppliers = supplierDAO.findAll();
        suppliers.forEach(s -> supplierCombo.getItems().add(s.getId()+":"+s.getName()));
        if (!supplierCombo.getItems().isEmpty()) supplierCombo.setValue(supplierCombo.getItems().get(0));
        supplierCombo.setPrefWidth(240);

        DatePicker poDate = new DatePicker(LocalDate.now()); poDate.setPrefWidth(160);

        ComboBox<String> pmtCombo = new ComboBox<>();
        pmtCombo.getItems().addAll("cash","mpesa","bank transfer","cheque","credit"); pmtCombo.setValue("credit"); pmtCombo.setPrefWidth(130);

        headerRow.getChildren().addAll(lbl("Supplier:"), supplierCombo, lbl("Date:"), poDate, lbl("Payment:"), pmtCombo);

        // ── Add Item Row ──
        HBox addItemRow = new HBox(10); addItemRow.setAlignment(Pos.CENTER_LEFT);
        addItemRow.setStyle("-fx-background-color:#f8fafc;-fx-padding:12;-fx-background-radius:8;");

        ComboBox<String> productCombo = new ComboBox<>();
        List<Product> products = productDAO.findAll();
        products.forEach(p -> productCombo.getItems().add(p.getId()+":"+p.getName()+" ["+p.getSku()+"]"));
        if (!productCombo.getItems().isEmpty()) productCombo.setValue(productCombo.getItems().get(0));
        productCombo.setPrefWidth(260);

        TextField qtyField  = fld("Qty","1");   qtyField.setPrefWidth(70);
        TextField costField = fld("Cost/Unit","0"); costField.setPrefWidth(100);

        Button addItemBtn = new Button("+ Add to Order");
        addItemBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-background-radius:6;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;-fx-font-size:13px;");

        addItemRow.getChildren().addAll(lbl("Product:"), productCombo, lbl("Qty:"), qtyField, lbl("Cost:"), costField, addItemBtn);

        // When product selected, auto-fill cost with its cost price
        productCombo.setOnAction(e -> {
            String sel = productCombo.getValue();
            if (sel==null||sel.isEmpty()) return;
            try {
                int pid = Integer.parseInt(sel.split(":")[0]);
                products.stream().filter(p->p.getId()==pid).findFirst().ifPresent(p->costField.setText(String.valueOf(p.getCostPrice())));
            } catch (Exception ignored) {}
        });

        // ── Line Items Table ──
        VBox itemsCard = new VBox(0);
        itemsCard.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:8;-fx-border-width:1;");

        // Table header
        HBox tblHdr = new HBox(); tblHdr.setStyle("-fx-background-color:#f8fafc;-fx-padding:10 16;");
        for (String col : new String[]{"PRODUCT","SKU","QTY","UNIT COST","LINE TOTAL",""}) {
            Label cl = new Label(col); cl.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#94a3b8;");
            HBox.setHgrow(cl, Priority.ALWAYS); tblHdr.getChildren().add(cl);
        }
        itemsCard.getChildren().add(tblHdr);

        VBox itemRows = new VBox(0);
        Label emptyLabel = new Label("No items added yet. Use the form above to add products.");
        emptyLabel.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:13px;-fx-padding:20;");
        itemRows.getChildren().add(emptyLabel);
        itemsCard.getChildren().add(itemRows);

        // Total row
        HBox totalRow = new HBox(); totalRow.setAlignment(Pos.CENTER_RIGHT);
        totalRow.setStyle("-fx-border-color:#e2e8f0 transparent transparent transparent;-fx-border-width:1 0 0 0;-fx-padding:10 16;");
        Label totalLabel = new Label("Total: KES 0.00"); totalLabel.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        totalRow.getChildren().add(totalLabel);
        itemsCard.getChildren().add(totalRow);

        // Add item action
        addItemBtn.setOnAction(e -> {
            String sel = productCombo.getValue();
            if (sel==null||sel.isEmpty()) return;
            try {
                int pid = Integer.parseInt(sel.split(":")[0]);
                double qty = Double.parseDouble(qtyField.getText().trim());
                double cost = Double.parseDouble(costField.getText().trim());
                if (qty<=0||cost<=0) return;

                Product prod = products.stream().filter(p->p.getId()==pid).findFirst().orElse(null);
                if (prod==null) return;

                // Check if already in list
                boolean found=false;
                for (PurchaseItem pi : lineItems) {
                    if (pi.getProductId()==pid){pi.setQuantity(pi.getQuantity()+qty);pi.setLineTotal(pi.getQuantity()*pi.getUnitCost());found=true;break;}
                }
                if (!found) {
                    PurchaseItem pi = new PurchaseItem();
                    pi.setProductId(pid); pi.setProductName(prod.getName()); pi.setSku(prod.getSku());
                    pi.setQuantity(qty); pi.setUnitCost(cost); pi.setLineTotal(qty*cost);
                    lineItems.add(pi);
                }

                // Rebuild item rows
                itemRows.getChildren().clear();
                double grandTotal = 0;
                for (PurchaseItem pi : lineItems) {
                    HBox iRow = new HBox(); iRow.setAlignment(Pos.CENTER_LEFT);
                    iRow.setPadding(new Insets(10,16,10,16));
                    iRow.setStyle("-fx-border-color:transparent transparent #f1f5f9 transparent;-fx-border-width:0 0 1 0;");
                    for (String v : new String[]{pi.getProductName(),pi.getSku()!=null?pi.getSku():"—",String.format("%.0f",pi.getQuantity()),KESFormatter.format(pi.getUnitCost()),KESFormatter.format(pi.getLineTotal())}) {
                        Label vl=new Label(v); vl.setStyle("-fx-font-size:12px;-fx-text-fill:#1e293b;"); HBox.setHgrow(vl,Priority.ALWAYS); iRow.getChildren().add(vl);
                    }
                    final PurchaseItem finalPi = pi;
                    Button removeBtn=new Button("✕"); removeBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:#dc2626;-fx-cursor:hand;-fx-border-color:transparent;-fx-font-size:12px;");
                    removeBtn.setOnAction(ev->{ lineItems.remove(finalPi); addItemBtn.fire(); /* refresh */ });
                    iRow.getChildren().add(removeBtn);
                    itemRows.getChildren().add(iRow);
                    grandTotal += pi.getLineTotal();
                }
                if (lineItems.isEmpty()) itemRows.getChildren().add(emptyLabel);
                totalLabel.setText("Total: " + KESFormatter.format(grandTotal));
            } catch (Exception ex) { ex.printStackTrace(); }
        });

        // Notes
        TextField notesField = fld("Additional notes or delivery instructions","");
        notesField.setPrefWidth(Double.MAX_VALUE);

        main.getChildren().addAll(headerRow, addItemRow, itemsCard, new HBox(lbl("Notes:"), notesField));

        dialog.getDialogPane().setContent(main);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Create Purchase Order");
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            if (supplierCombo.getValue()==null||supplierCombo.getValue().isEmpty()) {ev.consume();return;}
            if (lineItems.isEmpty()) {
                Alert err = new Alert(Alert.AlertType.WARNING,"Please add at least one product to the order.",ButtonType.OK);
                err.setHeaderText(null); err.show(); ev.consume();
            }
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result==ButtonType.OK && !lineItems.isEmpty()) {
                try {
                    String[] suppParts = supplierCombo.getValue().split(":",2);
                    int suppId = Integer.parseInt(suppParts[0]);

                    Purchase po = new Purchase();
                    po.setSupplierId(suppId);
                    po.setSupplierName(suppParts[1]);
                    po.setStatus("pending");
                    po.setPaymentMethod(pmtCombo.getValue());
                    po.setPaymentStatus("unpaid");
                    po.setNotes(notesField.getText().trim());

                    double total=0; for(PurchaseItem pi:lineItems) total+=pi.getLineTotal();
                    po.setTotalAmount(total);
                    po.setItems(lineItems);

                    try { po.setCreatedBy(SessionManager.getInstance().getCurrentUser().getId()); } catch(Exception ignored){}

                    Purchase saved = purchaseDAO.save(po);
                    if (saved!=null) {
                        com.kaziflow.services.AuditLog.logPurchaseOrder(saved.getId(), saved.getPurchaseNumber(), saved.getTotalAmount());
                        SceneManager.getInstance().refreshView("dashboard");
                        Toast.success(SceneManager.getInstance().getStage(),
                            "Purchase Order created",
                            saved.getPurchaseNumber() + " — KES " + KESFormatter.format(saved.getTotalAmount()));
                        loadPurchases();
                    } else {
                        Toast.error(SceneManager.getInstance().getStage(), "PO failed", "Purchase order could not be saved.");
                    }
                } catch (Exception e) { e.printStackTrace(); }
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
        TextField tf = new TextField(val); tf.setPromptText(prompt);
        tf.setStyle("-fx-pref-height:34px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");
        return tf;
    }

    private Label lbl(String text) {
        Label l = new Label(text); l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;"); return l;
    }

    // ── Purchase Returns Tab ──────────────────────────────────────────────

    private final com.kaziflow.dao.PurchaseReturnDAO returnDAO = new com.kaziflow.dao.PurchaseReturnDAO();

    private VBox buildReturnsView() {
        returnDAO.ensureTables();
        VBox view = new VBox(0);
        view.setStyle("-fx-background-color:#f8fafc;");

        // Header
        HBox header = new HBox(12);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 24, 14, 24));
        header.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        Label title = new Label("Purchase Returns");
        title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label sub = new Label("Return goods to supplier • deducts stock • credits balance");
        sub.setStyle("-fx-font-size:12px;-fx-text-fill:#94a3b8;");
        VBox titleBlock = new VBox(2, title, sub);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button addBtn = new Button("+ New Return");
        addBtn.setStyle("-fx-background-color:#dc2626;-fx-text-fill:white;-fx-font-weight:bold;" +
            "-fx-background-radius:8;-fx-pref-height:36px;-fx-padding:0 16;-fx-cursor:hand;");
        addBtn.setOnAction(e -> showPurchaseReturnDialog(view));
        header.getChildren().addAll(titleBlock, sp, addBtn);

        // Table placeholder — populated by loadReturns()
        TableView<String[]> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;");
        tv.setUserData("returns-table");
        VBox.setVgrow(tv, Priority.ALWAYS);

        String[] cols = {"Return #","Supplier","Product","Qty","Unit Cost","Credit","Reason","Status","Date"};
        for (int i = 0; i < cols.length; i++) {
            final int ci = i + 1;
            TableColumn<String[], String> col = new TableColumn<>(cols[i]);
            col.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                ci < d.getValue().length ? d.getValue()[ci] : ""));
            tv.getColumns().add(col);
        }

        view.getChildren().addAll(header, tv);
        return view;
    }

    @SuppressWarnings("unchecked")
    private void loadReturns(VBox returnsView) {
        returnsView.getChildren().stream()
            .filter(n -> n instanceof TableView && "returns-table".equals(n.getUserData()))
            .findFirst()
            .ifPresent(n -> {
                TableView<String[]> tv = (TableView<String[]>) n;
                com.kaziflow.utils.AsyncTask.run(
                    returnDAO::findAll,
                    items -> tv.setItems(javafx.collections.FXCollections.observableArrayList(items)),
                    err -> {}
                );
            });
    }

    private void showPurchaseReturnDialog(VBox returnsView) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Purchase Return");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color:white;");
        dialog.getDialogPane().setPrefWidth(460);

        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));

        // Supplier picker
        ComboBox<String> supplierCombo = new ComboBox<>();
        supplierCombo.setPrefWidth(300);
        com.kaziflow.dao.SupplierDAO supDAO = new com.kaziflow.dao.SupplierDAO();
        supDAO.findAll().forEach(s -> supplierCombo.getItems().add(s.getId() + ":" + s.getName()));
        if (!supplierCombo.getItems().isEmpty()) supplierCombo.setValue(supplierCombo.getItems().get(0));

        // Product picker
        ComboBox<String> productCombo = new ComboBox<>();
        productCombo.setPrefWidth(300);
        new com.kaziflow.dao.ProductDAO().findAll().forEach(p ->
            productCombo.getItems().add(p.getId() + ":" + p.getName() +
                " (cost: KES " + p.getCostPrice() + ")"));
        if (!productCombo.getItems().isEmpty()) productCombo.setValue(productCombo.getItems().get(0));

        TextField qtyField    = fld("Quantity to return", "1");
        TextField costField   = fld("Unit Cost (KES)", "0");
        TextField reasonField = fld("Reason", "");

        // Auto-fill cost when product changes
        productCombo.setOnAction(e -> {
            String val = productCombo.getValue();
            if (val != null && val.contains("cost: KES ")) {
                String cost = val.split("cost: KES ")[1].replace(")", "").trim();
                costField.setText(cost);
            }
        });

        form.addRow(0, lbl("Supplier"),  supplierCombo);
        form.addRow(1, lbl("Product"),   productCombo);
        form.addRow(2, lbl("Quantity"),  qtyField);
        form.addRow(3, lbl("Unit Cost"), costField);
        form.addRow(4, lbl("Reason"),    reasonField);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK) return;
            try {
                String[] supParts  = supplierCombo.getValue().split(":", 2);
                String[] prodParts = productCombo.getValue().split(":", 2);
                int suppId   = Integer.parseInt(supParts[0]);
                int prodId   = Integer.parseInt(prodParts[0]);
                String suppName  = supParts[1];
                String prodName  = prodParts[1].split("\\(")[0].trim();
                double qty       = Double.parseDouble(qtyField.getText().trim());
                double unitCost  = Double.parseDouble(costField.getText().trim());

                int userId = 1;
                try { userId = com.kaziflow.utils.SessionManager.getInstance().getCurrentUser().getId(); }
                catch (Exception ignored) {}

                String result = returnDAO.processReturn(suppId, suppName, prodId,
                    prodName, qty, unitCost, reasonField.getText().trim(), userId);

                if (result == null) {
                    com.kaziflow.utils.Toast.error(
                        com.kaziflow.utils.SceneManager.getInstance().getStage(),
                        "Failed", "Could not process return.");
                } else if ("INSUFFICIENT_STOCK".equals(result)) {
                    com.kaziflow.utils.Toast.error(
                        com.kaziflow.utils.SceneManager.getInstance().getStage(),
                        "Insufficient stock", "Not enough stock to return.");
                } else {
                    com.kaziflow.services.AuditLog.log("PURCHASE_RETURN",
                        "Return " + result + ": " + prodName + " x" + (int)qty +
                        " to " + suppName, "purchases", null);
                    loadReturns(returnsView);
                    com.kaziflow.utils.Toast.success(
                        com.kaziflow.utils.SceneManager.getInstance().getStage(),
                        "Return processed", result + " — credit KES " +
                        com.kaziflow.utils.KESFormatter.format(qty * unitCost));
                }
            } catch (Exception ex) {
                com.kaziflow.utils.Toast.error(
                    com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Error", ex.getMessage());
            }
        });
    }
}
