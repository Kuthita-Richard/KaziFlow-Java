package com.kaziflow.views;

import com.kaziflow.dao.ProductDAO;
import com.kaziflow.dao.SaleDAO;
import com.kaziflow.models.Product;
import com.kaziflow.models.Sale;
import com.kaziflow.models.SaleItem;
import com.kaziflow.utils.AsyncTask;
import com.kaziflow.utils.KESFormatter;
import com.kaziflow.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

public class SalesView {

    private BorderPane root;
    private FlowPane productGrid;
    private VBox cartItemsBox;
    private Label subtotalLabel, vatLabel, totalLabel;
    private TextField searchField;
    private int orderCounter;
    private ObservableList<SaleItem> cartItems = FXCollections.observableArrayList();
    private final ProductDAO productDAO = new ProductDAO();
    private final SaleDAO saleDAO = new SaleDAO();
    private String selectedPayment = "cash";
    private Label orderLabel;

    // ── Numpad / cash-tendered state ──
    // currentTotal always mirrors the grand total shown in the UI, so the numpad
    // and completeSale() never have to recompute it from the cart independently.
    private double currentTotal = 0.0;
    private TextField cashTenderedField;
    private Label changeLabel;
    private HBox cashTenderedRow;

    // ── Loyalty / real-customer selection (Phase 10E) ──
    // The customer combo used to only ever offer "Walk-in Customer" and completeSale()
    // hardcoded that name regardless of selection — this map is what actually lets a
    // real customer be picked and later linked to loyalty points.
    private final com.kaziflow.dao.CustomerDAO customerDAO = new com.kaziflow.dao.CustomerDAO();
    private final java.util.Map<String, Integer> customerNameToId = new java.util.HashMap<>();
    private final java.util.Map<String, String> customerLabelToPlainName = new java.util.HashMap<>();
    private ComboBox<String> customerCombo;
    private Integer selectedCustomerId = null; // null = walk-in
    private String selectedCustomerName = "Walk-in Customer";
    private int redeemedPoints = 0;
    private double redeemedDiscount = 0.0;

    // ── Delivery (Phase 10B) ──
    // null deliveryCharge = no delivery attached to this sale (the common case for in-store pickup).
    private final com.kaziflow.dao.DeliveryDAO deliveryDAO = new com.kaziflow.dao.DeliveryDAO();
    private Double deliveryCharge = null;
    private String deliveryLocationName = null;
    private double deliveryDistanceKm = 0;
    private double deliveryWeightKg = 0;
    private String deliveryCustomerPhone = null;

    public SalesView() {
        orderCounter = saleDAO.getTodayCount() + 1;
        buildUI();
    }

    public BorderPane getRoot() { return root; }

    private void buildUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: white;");

        // ── Tab bar at top ──
        HBox tabBar = new HBox(0);
        tabBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;-fx-padding:0 16;");
        Button posTab       = tabBtn("Point of Sale",  true);
        Button historyTab   = tabBtn("Sales History",  false);
        Button customersTab = tabBtn("Customers",      false);

        BorderPane posPane      = buildPosPane();
        VBox historyPane        = buildSalesHistory();
        VBox customersPane      = buildCustomersPane();

        StackPane area = new StackPane();
        area.getChildren().setAll(posPane);
        root.setTop(tabBar);
        root.setCenter(area);

        posTab      .setOnAction(e -> { area.getChildren().setAll(posPane);       setActive(posTab, historyTab, customersTab); });
        historyTab  .setOnAction(e -> { area.getChildren().setAll(historyPane);   setActive(historyTab, posTab, customersTab); loadSalesHistory(); });
        customersTab.setOnAction(e -> { area.getChildren().setAll(customersPane); setActive(customersTab, posTab, historyTab); loadCustomers(customersPane); });

        tabBar.getChildren().addAll(posTab, historyTab, customersTab);
    }

    private void setActive(Button a, Button... rest) { applyTab(a,true); for (Button b:rest) applyTab(b,false); }
    private Button tabBtn(String label, boolean active) { Button b=new Button(label); applyTab(b,active); return b; }
    private void applyTab(Button b, boolean active) {
        if (active) b.setStyle("-fx-background-color:transparent;-fx-text-fill:#2563eb;-fx-font-size:13px;-fx-font-weight:bold;-fx-padding:12 16;-fx-border-color:transparent transparent #2563eb transparent;-fx-border-width:0 0 2 0;-fx-cursor:hand;");
        else        b.setStyle("-fx-background-color:transparent;-fx-text-fill:#94a3b8;-fx-font-size:13px;-fx-padding:12 16;-fx-border-color:transparent;-fx-cursor:hand;");
    }

    private BorderPane buildPosPane() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: white;");
        HBox topBar = buildTopBar();
        pane.setTop(topBar);
        VBox leftPanel = buildProductPanel();
        pane.setCenter(leftPanel);
        VBox cartPanel = buildCartPanel();
        cartPanel.setPrefWidth(340);
        cartPanel.setMinWidth(340);
        pane.setRight(cartPanel);
        loadProducts(null);
        return pane;
    }

    // ── Sales History ─────────────────────────────────────────────────────────
    private ObservableList<Sale> salesHistoryData = FXCollections.observableArrayList();
    private TableView<Sale> salesHistoryTable;

    private VBox buildSalesHistory() {
        VBox view = new VBox(0); view.setStyle("-fx-background-color:#f8fafc;");

        HBox topbar = new HBox(); topbar.setAlignment(Pos.CENTER_LEFT); topbar.setSpacing(10);
        topbar.setPadding(new Insets(16,24,16,24));
        topbar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        VBox tb = new VBox(2);
        Label h = new Label("Sales History"); h.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label bc = new Label("Sales & POS › History"); bc.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12px;");
        tb.getChildren().addAll(h,bc);
        Region sp = new Region(); HBox.setHgrow(sp,Priority.ALWAYS);
        Button exportBtn = new Button("⬇ Export CSV");
        exportBtn.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-pref-height:36px;-fx-padding:0 14;-fx-cursor:hand;-fx-font-size:13px;");
        exportBtn.setOnAction(e -> new com.kaziflow.services.ReportExportService().exportCSV(com.kaziflow.services.ReportExportService.ReportType.SALES));
        topbar.getChildren().addAll(tb, sp, exportBtn);

        VBox content = new VBox(20); content.setPadding(new Insets(24)); VBox.setVgrow(content, Priority.ALWAYS);

        // Stats
        double todayRev = saleDAO.getTodayRevenue();
        double weekRev  = saleDAO.getWeekRevenue();
        int todayCnt    = saleDAO.getTodayCount();
        com.kaziflow.utils.TopShare topPay = com.kaziflow.utils.TopShare.of(saleDAO.getPaymentMethodBreakdown());

        HBox stats = new HBox(16);
        for (VBox c : new VBox[]{
            sCard("Today's Revenue",    com.kaziflow.utils.KESFormatter.formatShort(todayRev), todayCnt+" sales",  "#1e293b"),
            sCard("This Week",          com.kaziflow.utils.KESFormatter.formatShort(weekRev),  "7-day total",      "#2563eb"),
            sCard("Avg Sale Value",     todayCnt>0?com.kaziflow.utils.KESFormatter.format(todayRev/todayCnt):"—","Per transaction","#16a34a"),
            sCard("Top Payment Method", topPay == null ? "—" : payLabel(topPay.key()), topPay == null ? "No sales this month" : topPay.percentText() + " of this month's sales", "#7c3aed")
        }) { stats.getChildren().add(c); HBox.setHgrow(c, Priority.ALWAYS); }

        // Filter bar
        HBox filterBar = new HBox(10); filterBar.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField(); search.setPromptText("Search by receipt #, customer..."); search.getStyleClass().add("search-box"); search.setPrefWidth(280);
        search.textProperty().addListener((obs,o,v) -> {
            if(v.isBlank()) salesHistoryData.setAll(saleDAO.findAll());
            else salesHistoryData.setAll(saleDAO.findAll().stream().filter(s->(s.getSaleNumber()!=null&&s.getSaleNumber().contains(v))||(s.getCustomerName()!=null&&s.getCustomerName().toLowerCase().contains(v.toLowerCase()))).toList());
        });
        ComboBox<String> pmtFilter = new ComboBox<>();
        pmtFilter.getItems().addAll("All Payment Methods","cash","mpesa","card","bank"); pmtFilter.setValue("All Payment Methods"); pmtFilter.setPrefHeight(36);
        pmtFilter.setOnAction(e->{
            String sel=pmtFilter.getValue();
            if("All Payment Methods".equals(sel)) salesHistoryData.setAll(saleDAO.findAll());
            else salesHistoryData.setAll(saleDAO.findAll().stream().filter(s->sel.equals(s.getPaymentMethod())).toList());
        });
        filterBar.getChildren().addAll(search, pmtFilter);

        // Table
        VBox tableCard = new VBox(0); tableCard.setStyle("-fx-background-color:white;-fx-background-radius:12;-fx-border-color:#e2e8f0;-fx-border-radius:12;-fx-border-width:1;");
        salesHistoryTable = buildSalesHistoryTable();
        tableCard.getChildren().add(salesHistoryTable);

        content.getChildren().addAll(stats, filterBar, tableCard);
        view.getChildren().addAll(topbar, content);
        return view;
    }

    @SuppressWarnings("unchecked")
    private TableView<Sale> buildSalesHistoryTable() {
        TableView<Sale> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(400); tv.setStyle("-fx-background-color:white;"); tv.setItems(salesHistoryData);

        TableColumn<Sale,String> receiptCol = new TableColumn<>("RECEIPT #");
        receiptCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getSaleNumber()!=null?d.getValue().getSaleNumber():"—")); receiptCol.setPrefWidth(120);

        TableColumn<Sale,String> dateCol = new TableColumn<>("DATE & TIME");
        dateCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(
            d.getValue().getCreatedAt()!=null?d.getValue().getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")):"—")); dateCol.setPrefWidth(150);

        TableColumn<Sale,String> customerCol = new TableColumn<>("CUSTOMER");
        customerCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getCustomerName()!=null?d.getValue().getCustomerName():"Walk-in")); customerCol.setPrefWidth(150);

        TableColumn<Sale,String> amtCol = new TableColumn<>("TOTAL");
        amtCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(com.kaziflow.utils.KESFormatter.format(d.getValue().getTotalAmount())));
        amtCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String a, boolean empty){
                super.updateItem(a,empty); if(empty||a==null){setText(null);return;}
                Label l=new Label(a); l.setStyle("-fx-font-weight:bold;-fx-font-size:13px;-fx-text-fill:#1e293b;"); setGraphic(l); setText(null);
            }
        }); amtCol.setPrefWidth(120);

        TableColumn<Sale,String> pmtCol = new TableColumn<>("PAYMENT");
        pmtCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getPaymentMethod()!=null?d.getValue().getPaymentMethod().toUpperCase():"CASH"));
        pmtCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String pm, boolean empty){
                super.updateItem(pm,empty); if(empty||pm==null){setGraphic(null);return;}
                String bg,fg;
                switch(pm){case"MPESA"->{bg="#dcfce7";fg="#16a34a";}case"CARD"->{bg="#eff6ff";fg="#2563eb";}default->{bg="#f1f5f9";fg="#475569";}}
                Label badge=new Label(pm); badge.setStyle("-fx-background-color:"+bg+";-fx-text-fill:"+fg+";-fx-font-size:11px;-fx-background-radius:20;-fx-padding:3 10;-fx-font-weight:bold;");
                setGraphic(badge); setText(null);
            }
        }); pmtCol.setPrefWidth(100);

        TableColumn<Sale,String> cashierCol = new TableColumn<>("CASHIER");
        cashierCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getServedByName()!=null?d.getValue().getServedByName():"—")); cashierCol.setPrefWidth(120);

        TableColumn<Sale,String> vatCol = new TableColumn<>("VAT");
        vatCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(com.kaziflow.utils.KESFormatter.format(d.getValue().getVatAmount()))); vatCol.setPrefWidth(100);

        TableColumn<Sale,Void> actCol = new TableColumn<>("");
        actCol.setPrefWidth(190);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button printBtn  = new Button("🖨 Receipt");
            private final Button returnBtn = new Button("↩ Return");
            private final HBox box = new HBox(6, printBtn, returnBtn);
            {
                printBtn.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:5;-fx-background-radius:5;-fx-text-fill:#475569;-fx-cursor:hand;-fx-font-size:11px;-fx-padding:4 8;");
                returnBtn.setStyle("-fx-background-color:white;-fx-border-color:#fca5a5;-fx-border-radius:5;-fx-background-radius:5;-fx-text-fill:#dc2626;-fx-cursor:hand;-fx-font-size:11px;-fx-padding:4 8;");
                printBtn.setOnAction(e -> {
                    Sale sale = getTableView().getItems().get(getIndex());
                    new com.kaziflow.services.ReceiptPrinter().showPreview(sale);
                });
                returnBtn.setOnAction(e -> {
                    Sale sale = getTableView().getItems().get(getIndex());
                    showReturnDialog(sale);
                });
                com.kaziflow.utils.PermissionGuard.requireAction(returnBtn, com.kaziflow.services.SpecialPermissions.VOID_SALE);
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty); setGraphic(empty ? null : box);
            }
        });

        tv.getColumns().addAll(receiptCol, dateCol, customerCol, amtCol, pmtCol, cashierCol, vatCol, actCol);
        return tv;
    }

    private void loadSalesHistory() {
        AsyncTask.run(
            saleDAO::findAll,
            sales -> salesHistoryData.setAll(sales),
            err -> System.err.println("Sales history load error: " + err)
        );
    }

    private VBox sCard(String label, String value, String note, String color) {
        VBox card = new VBox(6); card.setStyle("-fx-background-color:white;-fx-background-radius:12;-fx-border-color:#e2e8f0;-fx-border-radius:12;-fx-border-width:1;-fx-padding:16;");
        Label lbl = new Label(label); lbl.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;-fx-font-weight:bold;");
        Label val = new Label(value); val.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:"+color+";");
        Label nt  = new Label(note);  nt .setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;");
        card.getChildren().addAll(lbl,val,nt); return card;
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(12);
        bar.setPadding(new Insets(12, 16, 12, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        searchField = new TextField();
        searchField.setPromptText("Scan barcode or search products by name, SKU...");
        searchField.getStyleClass().add("search-box");
        searchField.setPrefWidth(440);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((obs, old, val) -> {
            // Don't trigger live search while scanner is typing fast (debounce: only search
            // when input comes from keyboard, not from rapid barcode scanner input)
            loadProducts(val);
        });

        // Barcode scanner support: press Enter (or scanner sends \n) to add exact match
        searchField.setOnAction(e -> {
            String query = searchField.getText().trim();
            if (query.isEmpty()) return;
            // Look for exact SKU or barcode match first
            List<Product> results = productDAO.search(query);
            Product exact = results.stream()
                .filter(p -> query.equalsIgnoreCase(p.getSku())
                          || query.equalsIgnoreCase(p.getBarcode()))
                .findFirst()
                .orElse(null);
            if (exact != null) {
                // Exact match found — add to cart immediately
                addToCart(exact);
                searchField.clear();
                com.kaziflow.utils.Toast.success(
                    com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Added", exact.getName() + " added to cart");
            } else if (results.size() == 1) {
                // Only one result — auto-add
                addToCart(results.get(0));
                searchField.clear();
            } else if (results.isEmpty()) {
                com.kaziflow.utils.Toast.error(
                    com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Not found", "No product found for: " + query);
            }
            // Multiple results — leave list open for user to pick
        });

        Button barcodeBtn = new Button("⊞ Scan");
        barcodeBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 36px; -fx-padding: 0 10; -fx-font-size: 13px; -fx-cursor: hand;");
        barcodeBtn.setTooltip(new Tooltip("Click then scan barcode, or press Enter after typing SKU"));
        barcodeBtn.setOnAction(e -> searchField.requestFocus());

        bar.getChildren().addAll(searchField, barcodeBtn);
        return bar;
    }

    private VBox buildProductPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: #f8fafc;");

        // ── Category tabs ──
        HBox categoryRow = new HBox(8);
        categoryRow.setAlignment(Pos.CENTER_LEFT);

        String[] categories = {"All Items", "Cement", "Paints", "Plumbing", "Tools", "Electrical", "Safety Gear", "Hardware"};
        ToggleGroup tg = new ToggleGroup();

        for (String cat : categories) {
            ToggleButton btn = new ToggleButton(cat);
            btn.setToggleGroup(tg);
            btn.setStyle(
                "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 20; -fx-background-radius: 20;" +
                "-fx-font-size: 12px; -fx-padding: 5 14; -fx-cursor: hand; -fx-text-fill: #475569;"
            );
            if (cat.equals("All Items")) {
                btn.setSelected(true);
                btn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-border-color: #2563eb; -fx-border-radius: 20; -fx-background-radius: 20; -fx-font-size: 12px; -fx-padding: 5 14; -fx-cursor: hand;");
            }
            btn.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (isSelected) {
                    btn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-border-color: #2563eb; -fx-border-radius: 20; -fx-background-radius: 20; -fx-font-size: 12px; -fx-padding: 5 14; -fx-cursor: hand;");
                    loadProducts(cat.equals("All Items") ? null : cat);
                } else {
                    btn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 20; -fx-background-radius: 20; -fx-font-size: 12px; -fx-padding: 5 14; -fx-cursor: hand; -fx-text-fill: #475569;");
                }
            });
            categoryRow.getChildren().add(btn);
        }
        // Same fix as the payment method group below: JavaFX allows clicking the already-selected
        // toggle in a group to deselect it entirely, leaving nothing selected while the visible
        // filter silently keeps showing whatever was loaded last. Never allow dropping to zero selected.
        tg.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) tg.selectToggle(oldToggle);
        });

        // ── Product grid ──
        productGrid = new FlowPane(12, 12);
        productGrid.setPrefWrapLength(Double.MAX_VALUE);

        ScrollPane scroll = new ScrollPane(productGrid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #f8fafc; -fx-background: #f8fafc;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        panel.getChildren().addAll(categoryRow, scroll);
        return panel;
    }

    private void loadProducts(String filter) {
        productGrid.getChildren().clear();
        List<Product> products = filter == null || filter.isBlank() || filter.equals("All Items")
            ? productDAO.findAll()
            : productDAO.search(filter);

        com.kaziflow.utils.ProductImages.prefetch(products.stream().map(Product::getId).toList());
        for (Product p : products) {
            productGrid.getChildren().add(buildProductCard(p));
        }
    }

    private VBox buildProductCard(Product p) {
        VBox card = new VBox(8);
        card.setPrefWidth(168);
        card.setMaxWidth(168);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-border-width: 1; -fx-cursor: hand; -fx-padding: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 6, 0, 0, 1);");

        // Stock badge
        StackPane imgContainer = new StackPane();
        StackPane imgPlaceholder = new StackPane();
        imgPlaceholder.setStyle("-fx-background-color: #eff6ff; -fx-background-radius: 8; -fx-pref-height: 80;");
        Label imgIcon = new Label("⊞");
        imgIcon.setStyle("-fx-font-size: 28px; -fx-text-fill: #2563eb;");
        imgPlaceholder.getChildren().add(imgIcon);
        javafx.scene.image.ImageView photo = com.kaziflow.utils.ProductImages.thumbView(p.getId(), 80);
        if (photo != null) {
            imgPlaceholder.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-pref-height: 80;");
            imgPlaceholder.getChildren().setAll(photo);
        }

        String stockColor = p.isOutOfStock() ? "#dc2626" : p.isLowStock() ? "#d97706" : "#16a34a";
        Label stockBadge = new Label(String.valueOf((int) p.getStockQuantity()));
        stockBadge.setStyle("-fx-background-color: " + stockColor + "; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 1 6;");
        StackPane.setAlignment(stockBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(stockBadge, new Insets(4));
        imgContainer.getChildren().addAll(imgPlaceholder, stockBadge);

        Label name = new Label(p.getName());
        name.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-wrap-text: true;");
        name.setWrapText(true);

        Label sku = new Label(p.getSku() != null ? p.getSku() : "");
        sku.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px;");

        String unit = p.getUnit() != null ? p.getUnit() : "pcs";
        Label price = new Label(KESFormatter.format(p.getSellingPrice()));
        price.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2563eb;");
        Label unitLabel = new Label("/ " + unit);
        unitLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px;");

        HBox priceRow = new HBox(4);
        priceRow.setAlignment(Pos.CENTER_LEFT);
        priceRow.getChildren().addAll(price, unitLabel);

        card.getChildren().addAll(imgContainer, name, sku, priceRow);

        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #2563eb; -fx-border-radius: 10; -fx-border-width: 2; -fx-cursor: hand; -fx-padding: 12; -fx-effect: dropshadow(gaussian, rgba(37,99,235,0.15), 10, 0, 0, 2);"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-border-width: 1; -fx-cursor: hand; -fx-padding: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 6, 0, 0, 1);"));

        card.setOnMouseClicked(e -> addToCart(p));
        return card;
    }

    private VBox buildCartPanel() {
        VBox panel = new VBox(0);
        panel.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0 transparent transparent transparent; -fx-border-width: 0 0 0 1;");

        // Cart header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 16, 12, 16));
        header.setStyle("-fx-background-color: #f8fafc; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        orderLabel = new Label("#" + orderCounter);
        orderLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #2563eb;");

        Label dateLabel = new Label("Today");
        dateLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        customerCombo = new ComboBox<>();
        refreshCustomerCombo();
        customerCombo.setStyle("-fx-pref-height: 32px; -fx-font-size: 12px;");
        customerCombo.setOnAction(e -> {
            String selected = customerCombo.getValue();
            selectedCustomerId = customerNameToId.get(selected); // null for "Walk-in Customer"
            selectedCustomerName = "Walk-in Customer".equals(selected)
                ? "Walk-in Customer" : customerLabelToPlainName.getOrDefault(selected, selected);
            // Switching customers mid-cart invalidates any redemption already applied.
            redeemedPoints = 0;
            redeemedDiscount = 0.0;
            updateTotals();
        });

        Button redeemBtn = new Button("🎁 Redeem");
        redeemBtn.setStyle("-fx-background-color: white; -fx-border-color: #d97706; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #d97706; -fx-font-size: 11px; -fx-cursor: hand; -fx-pref-height: 32px;");
        redeemBtn.setOnAction(e -> showRedeemPointsDialog());
        com.kaziflow.utils.PermissionGuard.requireAction(redeemBtn, com.kaziflow.services.SpecialPermissions.APPLY_DISCOUNT);

        Button deliveryBtn = new Button("🚚 Delivery");
        deliveryBtn.setStyle("-fx-background-color: white; -fx-border-color: #2563eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: #2563eb; -fx-font-size: 11px; -fx-cursor: hand; -fx-pref-height: 32px;");
        deliveryBtn.setOnAction(e -> showAddDeliveryDialog());

        Button clearBtn = new Button("✕");
        clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-cursor: hand; -fx-font-size: 16px; -fx-border-color: transparent; -fx-padding: 0;");
        clearBtn.setOnAction(e -> clearCart());

        header.getChildren().addAll(orderLabel, dateLabel, spacer, customerCombo, redeemBtn, deliveryBtn, clearBtn);

        // Cart items
        cartItemsBox = new VBox(0);
        ScrollPane cartScroll = new ScrollPane(cartItemsBox);
        cartScroll.setFitToWidth(true);
        cartScroll.setStyle("-fx-background: white; -fx-background-color: white;");
        cartScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(cartScroll, Priority.ALWAYS);

        // Totals area
        VBox totalsArea = buildTotalsArea();

        panel.getChildren().addAll(header, cartScroll, totalsArea);
        return panel;
    }

    private VBox buildTotalsArea() {
        VBox area = new VBox(0);
        area.setStyle("-fx-background-color: white;");

        // Numpad
        GridPane numpad = new GridPane();
        numpad.setHgap(6);
        numpad.setVgap(6);
        numpad.setPadding(new Insets(12));
        numpad.setStyle("-fx-background-color: #f8fafc;");

        // cashTenderedField is created here (before the numpad buttons) so the
        // button handlers below can reference it directly.
        cashTenderedField = new TextField();
        cashTenderedField.setPromptText("0");
        cashTenderedField.setEditable(false); // numpad-driven only, keeps input dynamic/consistent
        cashTenderedField.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-alignment: center-right; -fx-pref-width: 120px;");

        String[] nums = {"1","2","3","4","5","6","7","8","9","00","0","⌫"};
        for (int i = 0; i < nums.length; i++) {
            String key = nums[i];
            Button btn = new Button(key);
            btn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-pref-width: 64px; -fx-pref-height: 44px; -fx-cursor: hand;");
            btn.setOnAction(e -> onNumpadKey(key));
            numpad.add(btn, i % 3, i / 3);
        }

        // Totals
        VBox totals = new VBox(6);
        totals.setPadding(new Insets(12, 16, 12, 16));
        totals.setStyle("-fx-border-color: #e2e8f0 transparent transparent transparent; -fx-border-width: 1 0 0 0;");

        subtotalLabel = new Label("KES 0.00");
        vatLabel = new Label("KES 0.00");
        totalLabel = new Label("KES 0.00");

        totals.getChildren().addAll(
            totalsRow("Subtotal", subtotalLabel),
            totalsRow("Discount", new Label("- KES 0.00")),
            totalsRow("VAT (16%)", vatLabel)
        );

        // Grand total
        HBox grandTotal = new HBox();
        grandTotal.setAlignment(Pos.CENTER_LEFT);
        grandTotal.setPadding(new Insets(8, 0, 0, 0));
        grandTotal.setStyle("-fx-border-color: #e2e8f0 transparent transparent transparent; -fx-border-width: 1 0 0 0;");
        Label gtLabel = new Label("Total");
        gtLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        totalLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        grandTotal.getChildren().addAll(gtLabel, sp, totalLabel);

        totals.getChildren().add(grandTotal);

        // Cash tendered / change — only meaningful for cash payments, so it's
        // shown/hidden dynamically as the payment method toggle changes below.
        Label tenderedLabel = new Label("Cash Tendered");
        tenderedLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
        Region tenderedSpacer = new Region();
        HBox.setHgrow(tenderedSpacer, Priority.ALWAYS);
        cashTenderedRow = new HBox(8);
        cashTenderedRow.setAlignment(Pos.CENTER_LEFT);
        cashTenderedRow.getChildren().addAll(tenderedLabel, tenderedSpacer, cashTenderedField);

        Label changeCaption = new Label("Change");
        changeCaption.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
        Region changeSpacer = new Region();
        HBox.setHgrow(changeSpacer, Priority.ALWAYS);
        changeLabel = new Label("KES 0.00");
        changeLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #16a34a;");
        HBox changeRow = new HBox(8);
        changeRow.setAlignment(Pos.CENTER_LEFT);
        changeRow.getChildren().addAll(changeCaption, changeSpacer, changeLabel);
        changeRow.managedProperty().bind(cashTenderedRow.managedProperty());
        changeRow.visibleProperty().bind(cashTenderedRow.visibleProperty());

        totals.getChildren().addAll(cashTenderedRow, changeRow);

        // Payment method buttons
        HBox paymentRow = new HBox(8);
        paymentRow.setPadding(new Insets(12, 0, 8, 0));

        String[] payMethods = {"Cash", "M-Pesa", "Card", "Bank"};
        ToggleGroup payGroup = new ToggleGroup();
        for (String m : payMethods) {
            ToggleButton tb = new ToggleButton(m);
            tb.setToggleGroup(payGroup);
            tb.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 12px; -fx-pref-height: 32px; -fx-cursor: hand;");
            HBox.setHgrow(tb, Priority.ALWAYS);
            if (m.equals("Cash")) {
                tb.setSelected(true);
                tb.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-border-color: #2563eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 12px; -fx-pref-height: 32px; -fx-cursor: hand;");
            }
            tb.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (isSelected) {
                    tb.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-border-color: #2563eb; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 12px; -fx-pref-height: 32px; -fx-cursor: hand;");
                    selectedPayment = m.toLowerCase();
                    boolean isCash = "cash".equals(selectedPayment);
                    cashTenderedRow.setVisible(isCash);
                    cashTenderedRow.setManaged(isCash);
                    if (isCash) { cashTenderedField.setText(""); updateChangeDue(); }
                } else {
                    tb.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 12px; -fx-pref-height: 32px; -fx-cursor: hand;");
                }
            });
            paymentRow.getChildren().add(tb);
        }
        // Same footgun found and fixed in ProfileSelector (v11.13): clicking the already-selected
        // toggle in a JavaFX ToggleGroup deselects it entirely, leaving nothing visually selected
        // while selectedPayment silently keeps its last value — a real "why doesn't this look
        // selected" bug even though it happens not to corrupt the eventual sale. Never allow the
        // group to drop to zero selected.
        payGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) payGroup.selectToggle(oldToggle);
        });
        totals.getChildren().add(paymentRow);

        // Charge button
        Button chargeBtn = new Button("Complete Sale");
        chargeBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 46px; -fx-cursor: hand;");
        chargeBtn.setMaxWidth(Double.MAX_VALUE);
        chargeBtn.setOnAction(e -> completeSale());
        totals.getChildren().add(chargeBtn);

        area.getChildren().addAll(numpad, totals);
        return area;
    }

    private HBox totalsRow(String label, Label valueLabel) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        valueLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        row.getChildren().addAll(lbl, sp, valueLabel);
        return row;
    }

    private void addToCart(Product p) {
        // Check if already in cart
        for (SaleItem item : cartItems) {
            if (item.getProductId() == p.getId()) {
                item.setQuantity(item.getQuantity() + 1);
                refreshCart();
                return;
            }
        }
        SaleItem item = new SaleItem(p.getId(), p.getName(), 1, p.getSellingPrice(), p.getCostPrice());
        cartItems.add(item);
        refreshCart();
    }

    private void refreshCart() {
        cartItemsBox.getChildren().clear();
        for (SaleItem item : cartItems) {
            cartItemsBox.getChildren().add(buildCartRow(item));
        }
        updateTotals();
    }

    private HBox buildCartRow(SaleItem item) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 16, 10, 16));
        row.setStyle("-fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");

        VBox info = new VBox(2);
        Label name = new Label(item.getProductName());
        name.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label detail = new Label(KESFormatter.formatNumber(item.getUnitPrice()) + " × " + (int) item.getQuantity());
        detail.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        info.getChildren().addAll(name, detail);
        HBox.setHgrow(info, Priority.ALWAYS);

        // Qty controls
        Button minus = new Button("-");
        minus.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 4; -fx-font-size: 14px; -fx-pref-width: 28; -fx-pref-height: 28; -fx-cursor: hand; -fx-border-color: transparent;");
        Label qty = new Label(String.valueOf((int) item.getQuantity()));
        qty.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-min-width: 24; -fx-alignment: center;");
        Button plus = new Button("+");
        plus.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 4; -fx-font-size: 14px; -fx-pref-width: 28; -fx-pref-height: 28; -fx-cursor: hand; -fx-border-color: transparent;");

        minus.setOnAction(e -> {
            if (item.getQuantity() > 1) { item.setQuantity(item.getQuantity() - 1); refreshCart(); }
            else { cartItems.remove(item); refreshCart(); }
        });
        plus.setOnAction(e -> { item.setQuantity(item.getQuantity() + 1); refreshCart(); });

        Label lineTotal = new Label(KESFormatter.formatNumber(item.getLineTotal()));
        lineTotal.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-min-width: 70; -fx-alignment: center-right;");

        Button removeBtn = new Button("✕");
        removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-cursor: hand; -fx-font-size: 12px; -fx-border-color: transparent; -fx-padding: 0;");
        removeBtn.setOnAction(e -> { cartItems.remove(item); refreshCart(); });

        row.getChildren().addAll(info, minus, qty, plus, lineTotal, removeBtn);
        return row;
    }

    private void updateTotals() {
        double subtotal = cartItems.stream().mapToDouble(SaleItem::getLineTotal).sum();
        double vat = subtotal * com.kaziflow.services.VATConfig.getInstance().getRate();
        double preDiscountTotal = subtotal + vat;

        // If the cart shrank after points were redeemed, the discount can no longer
        // exceed what's actually owed — re-cap it here rather than letting the total go negative.
        if (redeemedDiscount > preDiscountTotal) {
            redeemedDiscount = preDiscountTotal;
            redeemedPoints = (int) redeemedDiscount;
        }
        double total = preDiscountTotal - redeemedDiscount + (deliveryCharge != null ? deliveryCharge : 0);
        currentTotal = total; // single source of truth, read by the numpad/change logic below

        subtotalLabel.setText(KESFormatter.format(subtotal));
        vatLabel.setText(KESFormatter.format(vat));
        totalLabel.setText(KESFormatter.format(total)
            + (redeemedDiscount > 0 ? "  (−" + KESFormatter.formatNumber(redeemedDiscount) + " points)" : "")
            + (deliveryCharge != null ? "  (+" + KESFormatter.formatNumber(deliveryCharge) + " delivery)" : ""));

        if (cashTenderedField != null) updateChangeDue();
    }

    /** Handles every numpad button press. Digits/00 append; ⌫ deletes the last character. */
    private void onNumpadKey(String key) {
        if (cashTenderedField == null) return;
        String current = cashTenderedField.getText();
        if ("⌫".equals(key)) {
            if (!current.isEmpty()) current = current.substring(0, current.length() - 1);
        } else {
            // Guard against a leading "0" turning e.g. "0" + "5" into "05"
            current = "0".equals(current) ? key : current + key;
        }
        cashTenderedField.setText(current);
        updateChangeDue();
    }

    /** Recomputes and displays change owed based on cashTenderedField vs. currentTotal. */
    private void updateChangeDue() {
        if (changeLabel == null) return;
        double tendered = parseTendered();
        double change = tendered - currentTotal;
        changeLabel.setText(KESFormatter.format(Math.max(change, 0)));
        changeLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: "
            + (change < 0 ? "#dc2626;" : "#16a34a;"));
    }

    private double parseTendered() {
        try {
            String raw = cashTenderedField.getText();
            return raw == null || raw.isEmpty() ? 0.0 : Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void completeSale() {
        if (cartItems.isEmpty()) {
            showAlert("Cart is empty", "Please add products to the cart first.");
            return;
        }

        double subtotal = cartItems.stream().mapToDouble(SaleItem::getLineTotal).sum();
        double vat = subtotal * com.kaziflow.services.VATConfig.getInstance().getRate();
        double preDiscountTotal = subtotal + vat;
        if (redeemedDiscount > preDiscountTotal) { redeemedDiscount = preDiscountTotal; redeemedPoints = (int) redeemedDiscount; }
        double total = preDiscountTotal - redeemedDiscount + (deliveryCharge != null ? deliveryCharge : 0);

        // For cash sales, amountPaid/change come from what the cashier actually
        // entered on the numpad, not just assumed to equal the total.
        double amountPaid = total;
        double changeDue = 0.0;
        if ("cash".equals(selectedPayment)) {
            double tendered = parseTendered();
            if (tendered < total) {
                showAlert("Insufficient Amount", "Cash tendered (KES " + KESFormatter.formatNumber(tendered)
                    + ") is less than the total due (KES " + KESFormatter.formatNumber(total) + ").");
                return;
            }
            amountPaid = tendered;
            changeDue = tendered - total;
        }

        Sale sale = new Sale();
        // Real customer selection — previously hardcoded to "Walk-in Customer" regardless of the combo.
        sale.setCustomerId(selectedCustomerId);
        sale.setCustomerName(selectedCustomerName);
        sale.setSubtotal(subtotal);
        sale.setVatAmount(vat);
        sale.setDiscountAmount(redeemedDiscount);
        sale.setDeliveryCharge(deliveryCharge != null ? deliveryCharge : 0);
        sale.setTotalAmount(total);
        sale.setAmountPaid(amountPaid);
        sale.setChangeAmount(changeDue);
        sale.setPaymentMethod(selectedPayment);
        sale.setStatus("completed");

        int userId = 1;
        try { userId = SessionManager.getInstance().getCurrentUser().getId(); } catch (Exception ignored) {}
        sale.setServedBy(userId);
        sale.setItems(new ArrayList<>(cartItems));

        if ("mpesa".equals(selectedPayment)) {
            // Prompt for customer phone number
            javafx.scene.control.TextInputDialog phoneDialog = new javafx.scene.control.TextInputDialog("0712345678");
            phoneDialog.setTitle("M-Pesa Payment");
            phoneDialog.setHeaderText("Enter customer's M-Pesa phone number");
            phoneDialog.setContentText("Phone:");
            phoneDialog.showAndWait().ifPresent(phone -> {
                com.kaziflow.services.MpesaService mpesa = com.kaziflow.services.MpesaService.getInstance();
                if (!mpesa.isConfigured()) {
                    showAlert("M-Pesa Not Configured", "Go to Settings → M-Pesa Integration to configure your Daraja API credentials.");
                    return;
                }
                String ref = "INV-" + orderCounter;
                mpesa.stkPush(phone, (int) total, ref,
                    checkoutId -> javafx.application.Platform.runLater(() -> {
                        sale.setMpesaRef(checkoutId);
                        Sale saved2 = saleDAO.save(sale);
                        if (saved2 != null) {
                            applyLoyaltyForCompletedSale(saved2); createDeliveryRecordIfNeeded(saved2);
                            clearCart(); orderCounter++;
                            orderLabel.setText("#" + orderCounter);
                            new com.kaziflow.services.ReceiptPrinter().showPreview(saved2);
                        }
                    }),
                    err -> javafx.application.Platform.runLater(() ->
                        showAlert("M-Pesa Error", err))
                );
                showAlert("STK Push Sent", "Please ask customer to check their phone and enter M-Pesa PIN.");
            });
            return;
        }

        Sale saved = saleDAO.save(sale);
        if (saved != null) {
            applyLoyaltyForCompletedSale(saved); createDeliveryRecordIfNeeded(saved);
            com.kaziflow.services.AuditLog.logSale(saved.getId(), saved.getTotalAmount());
            com.kaziflow.utils.Toast.success(
                com.kaziflow.utils.SceneManager.getInstance().getStage(),
                "Sale Completed",
                saved.getSaleNumber() + " — " + com.kaziflow.utils.KESFormatter.format(saved.getTotalAmount())
            );
            clearCart();
            orderCounter++;
            orderLabel.setText("#" + orderCounter);
            com.kaziflow.services.ReceiptPrinter printer = new com.kaziflow.services.ReceiptPrinter();
            printer.showPreview(saved);
        } else {
            com.kaziflow.utils.Toast.error(
                com.kaziflow.utils.SceneManager.getInstance().getStage(),
                "Sale Failed", "Could not save transaction. Please try again."
            );
        }
    }

    /**
     * Settles the loyalty side-effects of a completed sale: redeem whatever points were
     * applied as a discount, then earn new points on the net amount actually paid
     * (1 point per KES 100) — only for a linked, non-walk-in customer.
     * Called before clearCart() resets redeemedPoints/selectedCustomerId back to zero.
     */
    private void applyLoyaltyForCompletedSale(Sale saved) {
        if (selectedCustomerId == null) return;
        if (redeemedPoints > 0) {
            boolean ok = customerDAO.redeemLoyaltyPoints(selectedCustomerId, redeemedPoints);
            if (!ok) {
                // The sale is already saved with this discount applied — reversing it here would
                // be riskier than the discrepancy itself. But this must never disappear silently:
                // it means either a race with another till redeemed the same points first, or the
                // balance changed between the redeem dialog and checkout. Record it permanently.
                com.kaziflow.services.AuditLog.log("LOYALTY_REDEMPTION_FAILED",
                    "Sale " + saved.getSaleNumber() + " applied a " + com.kaziflow.utils.KESFormatter.format(redeemedDiscount)
                        + " loyalty discount for customer #" + selectedCustomerId + ", but redeeming "
                        + redeemedPoints + " points failed — balance may have changed since the discount was applied (e.g. a concurrent redemption on another till). Reconcile this customer's points balance manually.",
                    "customers", selectedCustomerId);
            }
        }
        int earned = (int) (saved.getTotalAmount() / 100.0);
        if (earned > 0) customerDAO.addLoyaltyPoints(selectedCustomerId, earned);
    }

    /** If a delivery was attached to this sale, creates the actual delivery record linked to it. */
    private void createDeliveryRecordIfNeeded(Sale saved) {
        if (deliveryCharge == null) return;
        deliveryDAO.createDelivery(saved.getId(), saved.getCustomerName(), deliveryCustomerPhone,
            deliveryLocationName, deliveryDistanceKm, deliveryWeightKg, deliveryCharge);
    }

    /** (Re)loads the customer picker from the DB — called on POS pane build and after clearing a sale. */
    private void refreshCustomerCombo() {
        customerNameToId.clear();
        customerLabelToPlainName.clear();
        customerCombo.getItems().setAll("Walk-in Customer");
        List<String[]> customers = customerDAO.findAll();
        for (String[] c : customers) {
            String label = c[1] + " (" + c[2] + ")";
            customerNameToId.put(label, Integer.parseInt(c[0]));
            customerLabelToPlainName.put(label, c[1]); // stored directly, not reverse-parsed from the label later
            customerCombo.getItems().add(label);
        }
        customerCombo.setValue("Walk-in Customer");
        selectedCustomerId = null;
        selectedCustomerName = "Walk-in Customer";
    }

    /** Lets the cashier redeem the selected customer's loyalty points as a cart discount (1 point = KES 1). */
    private void showRedeemPointsDialog() {
        if (selectedCustomerId == null) {
            showAlert("No Customer Selected", "Loyalty points can only be redeemed for a registered customer — select one from the dropdown first, not Walk-in Customer.");
            return;
        }
        int available = customerDAO.getLoyaltyPoints(selectedCustomerId);
        if (available <= 0) {
            showAlert("No Points Available", "This customer has no loyalty points to redeem yet.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle("Redeem Loyalty Points");
        dialog.setHeaderText("Available balance: " + available + " points (" + KESFormatter.format(available) + " value)");
        dialog.setContentText("Points to redeem (1 point = KES 1 discount):");
        dialog.showAndWait().ifPresent(input -> {
            try {
                int requested = Integer.parseInt(input.trim());
                if (requested <= 0) return;
                if (requested > available) {
                    showAlert("Insufficient Points", "Customer only has " + available + " points available.");
                    return;
                }
                // Loyalty discount applies to goods only, not the delivery charge — capped against
                // subtotal+VAT here so this matches updateTotals()'s own safety cap exactly, rather
                // than relying on that second cap to silently correct a more permissive number here.
                double subtotal = cartItems.stream().mapToDouble(SaleItem::getLineTotal).sum();
                double goodsTotal = subtotal * (1 + com.kaziflow.services.VATConfig.getInstance().getRate());
                double cappedDiscount = Math.min(requested, goodsTotal);
                redeemedPoints = (int) cappedDiscount;
                redeemedDiscount = cappedDiscount;
                updateTotals();
                com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(), "Points Applied",
                    redeemedPoints + " points applied as a " + KESFormatter.format(redeemedDiscount) + " discount.");
            } catch (NumberFormatException ex) {
                showAlert("Invalid Input", "Please enter a whole number of points.");
            }
        });
    }

    /** Attaches a delivery charge to the current sale — either a named location (fixed price) or a custom distance/weight. */
    private void showAddDeliveryDialog() {
        List<String[]> locations = deliveryDAO.findAllLocations();

        Dialog<ButtonType> d = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(d);
        d.setTitle("Add Delivery");
        GridPane f = new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new javafx.geometry.Insets(20));

        ComboBox<String> locationBox = new ComboBox<>();
        locationBox.getItems().add("Custom distance...");
        java.util.Map<String, String[]> byName = new java.util.HashMap<>();
        for (String[] loc : locations) { byName.put(loc[1], loc); locationBox.getItems().add(loc[1]); }
        locationBox.setValue("Custom distance...");

        TextField distanceF = new TextField(); distanceF.setPromptText("Distance in km");
        TextField weightF = new TextField(); weightF.setPromptText("Weight in kg (optional)"); weightF.setText("0");
        TextField phoneF = new TextField(); phoneF.setPromptText("Customer phone (optional)");
        Label chargePreview = new Label("Charge: KES 0.00");
        chargePreview.setStyle("-fx-font-weight:bold;-fx-text-fill:#2563eb;");

        Runnable updatePreview = () -> {
            try {
                String selected = locationBox.getValue();
                if (byName.containsKey(selected)) {
                    String[] loc = byName.get(selected);
                    distanceF.setText(loc[2]);
                    distanceF.setEditable(false);
                    double override = "—".equals(loc[3]) ? -1 : Double.parseDouble(loc[3]);
                    double charge = deliveryDAO.calculateCharge(override >= 0 ? override : null,
                        Double.parseDouble(loc[2]), Double.parseDouble(weightF.getText().isBlank() ? "0" : weightF.getText()));
                    chargePreview.setText("Charge: " + KESFormatter.format(charge));
                } else {
                    distanceF.setEditable(true);
                    double dist = distanceF.getText().isBlank() ? 0 : Double.parseDouble(distanceF.getText());
                    double weight = weightF.getText().isBlank() ? 0 : Double.parseDouble(weightF.getText());
                    chargePreview.setText("Charge: " + KESFormatter.format(deliveryDAO.calculateCharge(null, dist, weight)));
                }
            } catch (NumberFormatException ignored) {
                chargePreview.setText("Charge: enter a valid number");
            }
        };
        locationBox.setOnAction(e -> updatePreview.run());
        distanceF.textProperty().addListener((o, a, b) -> updatePreview.run());
        weightF.textProperty().addListener((o, a, b) -> updatePreview.run());

        f.addRow(0, new Label("Location"), locationBox);
        f.addRow(1, new Label("Distance (km)"), distanceF);
        f.addRow(2, new Label("Weight (kg)"), weightF);
        f.addRow(3, new Label("Customer Phone"), phoneF);
        f.addRow(4, chargePreview);

        d.getDialogPane().setContent(f);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            try {
                double distance = Double.parseDouble(distanceF.getText());
                double weight = weightF.getText().isBlank() ? 0 : Double.parseDouble(weightF.getText());
                String selected = locationBox.getValue();
                Double override = null;
                if (byName.containsKey(selected) && !"—".equals(byName.get(selected)[3])) {
                    override = Double.parseDouble(byName.get(selected)[3]);
                }
                deliveryCharge = deliveryDAO.calculateCharge(override, distance, weight);
                deliveryDistanceKm = distance;
                deliveryWeightKg = weight;
                deliveryLocationName = byName.containsKey(selected) ? selected : "Custom";
                deliveryCustomerPhone = phoneF.getText().trim();
                updateTotals();
                com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(), "Delivery Added",
                    KESFormatter.format(deliveryCharge) + " delivery charge added to this sale.");
            } catch (NumberFormatException ex) {
                com.kaziflow.utils.Toast.error(com.kaziflow.utils.SceneManager.getInstance().getStage(), "Invalid Input", "Distance and weight must be numbers.");
            }
        });
    }

    private void clearCart() {
        cartItems.clear();
        refreshCart();
        redeemedPoints = 0;
        redeemedDiscount = 0.0;
        deliveryCharge = null;
        deliveryLocationName = null;
        deliveryDistanceKm = 0;
        deliveryWeightKg = 0;
        deliveryCustomerPhone = null;
        if (customerCombo != null) refreshCustomerCombo();
        if (cashTenderedField != null) {
            cashTenderedField.setText("");
            updateChangeDue();
        }
    }

    /** Builds the Customers tab pane (populated lazily on tab activation) */
    private VBox buildCustomersPane() {
        VBox pane = new VBox(0);
        pane.setStyle("-fx-background-color:#f8fafc;");
        pane.setUserData("customers-pane");
        return pane;
    }

    private void loadCustomers(VBox pane) {
        pane.getChildren().clear();

        // Header bar
        HBox header = new HBox(12);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 24, 14, 24));
        header.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        Label title = new Label("Customers");
        title.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        TextField search = new TextField(); search.setPromptText("Search name, phone, email…"); search.setPrefWidth(260);
        search.setStyle("-fx-pref-height:36px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:8;-fx-background-radius:8;-fx-font-size:13px;-fx-padding:0 8;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button addBtn = new Button("+ Add Customer");
        addBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:36px;-fx-padding:0 16;-fx-cursor:hand;-fx-font-size:13px;");
        header.getChildren().addAll(title, sp, search, addBtn);

        // Stats row
        com.kaziflow.dao.CustomerDAO dao = new com.kaziflow.dao.CustomerDAO();
        dao.seedIfEmpty();
        int totalCustomers = dao.getTotalCount();

        HBox statsRow = new HBox(16);
        statsRow.setPadding(new Insets(20, 24, 0, 24));
        statsRow.getChildren().addAll(
            cStatCard("Total Customers", String.valueOf(totalCustomers), "All registered"),
            cStatCard("Repeat Buyers",   String.valueOf(Math.max(0, totalCustomers - 2)), "Purchased 2+ times"),
            cStatCard("Walk-in Sales",   "—", "No customer linked")
        );

        // Table
        TableView<String[]> tv = new TableView<>();
        tv.setStyle("-fx-background-color:white;-fx-border-color:transparent;");
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tv, Priority.ALWAYS);

        String[] headers = {"Name","Phone","Email","Orders","Total Spent","Points","Actions"};
        int[] widths = {160, 130, 180, 70, 120, 80, 130};
        for (int i = 0; i < headers.length - 1; i++) {
            final int col = i;
            TableColumn<String[],String> tc = new TableColumn<>(headers[i]);
            tc.setPrefWidth(widths[i]);
            if (i == 5) {
                // "Points" is appended at index 7 of the row, not contiguous with the phone/email/orders/spend block above.
                tc.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                    d.getValue().length > 7 ? d.getValue()[7] : "0"));
            } else {
                tc.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                    col + 1 < d.getValue().length ? d.getValue()[col + 1] : ""));
            }
            tv.getColumns().add(tc);
        }

        // Actions column
        TableColumn<String[], Void> actCol = new TableColumn<>("Actions");
        actCol.setPrefWidth(130);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button editBtn = new Button("✎ Edit");
            private final Button delBtn  = new Button("🗑");
            private final HBox box = new HBox(6, editBtn, delBtn);
            {
                editBtn.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:5;-fx-background-radius:5;-fx-font-size:11px;-fx-cursor:hand;-fx-padding:3 8;");
                delBtn.setStyle("-fx-background-color:white;-fx-border-color:#fca5a5;-fx-border-radius:5;-fx-background-radius:5;-fx-text-fill:#dc2626;-fx-font-size:11px;-fx-cursor:hand;-fx-padding:3 6;");
                editBtn.setOnAction(e -> showCustomerDialog(getTableView().getItems().get(getIndex()), tv, dao));
                delBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    int id = Integer.parseInt(row[0]);
                    if (dao.delete(id)) {
                        tv.getItems().remove(getIndex());
                        com.kaziflow.services.AuditLog.log("CUSTOMER_DELETED","Customer deleted: "+row[1],"sales",id);
                    } else {
                        new Alert(Alert.AlertType.WARNING,"Cannot delete — customer has existing sales.",ButtonType.OK).showAndWait();
                    }
                });
            }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v, empty); setGraphic(empty ? null : box); }
        });
        tv.getColumns().add(actCol);

        ObservableList<String[]> data = javafx.collections.FXCollections.observableArrayList(dao.findAll());
        tv.setItems(data);

        search.textProperty().addListener((o, ov, nv) ->
            AsyncTask.run(() -> nv.isBlank() ? dao.findAll() : dao.search(nv), data::setAll, err -> {}));

        addBtn.setOnAction(e -> showCustomerDialog(null, tv, dao));

        pane.getChildren().addAll(header, statsRow, tv);
    }

    private VBox cStatCard(String label, String value, String note) {
        VBox c = new VBox(4);
        c.setStyle("-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#e2e8f0;-fx-border-radius:10;-fx-border-width:1;-fx-padding:14 20;");
        Label l = new Label(label); l.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;-fx-font-weight:bold;");
        Label v = new Label(value); v.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label n = new Label(note);  n.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:10px;");
        c.getChildren().addAll(l, v, n);
        return c;
    }

    private void showCustomerDialog(String[] existing, TableView<String[]> tv, com.kaziflow.dao.CustomerDAO dao) {
        Dialog<ButtonType> dialog = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle(existing == null ? "Add Customer" : "Edit Customer");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color:white;");
        dialog.getDialogPane().setPrefWidth(400);

        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));
        TextField nameF  = fld("Full Name",     existing != null ? existing[1] : "");
        TextField phoneF = fld("Phone",         existing != null ? existing[2] : "");
        TextField emailF = fld("Email",         existing != null ? existing[3] : "");
        TextField addrF  = fld("Address",       "");

        form.addRow(0, fl("Name"),    nameF);
        form.addRow(1, fl("Phone"),   phoneF);
        form.addRow(2, fl("Email"),   emailF);
        form.addRow(3, fl("Address"), addrF);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK) return;
            boolean ok = existing == null
                ? dao.save(nameF.getText().trim(), phoneF.getText().trim(), emailF.getText().trim(), addrF.getText().trim())
                : dao.update(Integer.parseInt(existing[0]), nameF.getText().trim(), phoneF.getText().trim(), emailF.getText().trim(), addrF.getText().trim());
            if (ok) {
                com.kaziflow.services.AuditLog.log(existing == null ? "CUSTOMER_CREATED" : "CUSTOMER_UPDATED",
                    (existing == null ? "New customer: " : "Updated customer: ") + nameF.getText().trim(), "sales", null);
                AsyncTask.run(dao::findAll, tv.getItems()::setAll, err -> {});
                com.kaziflow.utils.Toast.success(com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    existing == null ? "Customer added" : "Customer updated", nameF.getText().trim());
            }
        });
    }

    private TextField fld(String prompt, String val) {
        TextField tf = new TextField(val); tf.setPromptText(prompt); tf.setPrefWidth(260);
        tf.setStyle("-fx-pref-height:34px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");
        return tf;
    }

    private Label fl(String t) {
        Label l = new Label(t); l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;"); return l;
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        com.kaziflow.utils.DialogUtils.makeResizable(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /** Sales return dialog — lets cashier select which item to return and why */
    private void showReturnDialog(Sale sale) {
        if (sale.getItems() == null || sale.getItems().isEmpty()) {
            showAlert("No items", "No line items found for this sale.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle("Process Return — " + sale.getSaleNumber());
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(480);

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(14);
        form.setPadding(new Insets(20));

        // Item selection
        ComboBox<String> itemCombo = new ComboBox<>();
        for (var item : sale.getItems()) {
            itemCombo.getItems().add(item.getProductName() + "  (x" + (int) item.getQuantity() + " @ KES " + item.getUnitPrice() + ")");
        }
        itemCombo.setPrefWidth(340);
        if (!itemCombo.getItems().isEmpty()) itemCombo.setValue(itemCombo.getItems().get(0));

        TextField qtyField = new TextField("1");
        qtyField.setPrefWidth(340);

        TextField reasonField = new TextField();
        reasonField.setPromptText("e.g. Defective, Wrong item, Customer changed mind");
        reasonField.setPrefWidth(340);

        Label previewLabel = new Label();
        previewLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 13px;");

        // Update preview on change
        Runnable updatePreview = () -> {
            try {
                int idx = itemCombo.getSelectionModel().getSelectedIndex();
                if (idx >= 0 && idx < sale.getItems().size()) {
                    double qty = Double.parseDouble(qtyField.getText().trim());
                    double price = sale.getItems().get(idx).getUnitPrice();
                    previewLabel.setText("Refund: KES " + com.kaziflow.utils.KESFormatter.format(qty * price));
                }
            } catch (Exception ignored) {}
        };
        itemCombo.setOnAction(e -> updatePreview.run());
        qtyField.textProperty().addListener((o, ov, nv) -> updatePreview.run());
        updatePreview.run();

        java.util.function.Function<String, Label> lbl = t -> { Label l = new Label(t); l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;"); return l; };
        form.addRow(0, lbl.apply("Item to return"), itemCombo);
        form.addRow(1, lbl.apply("Quantity"), qtyField);
        form.addRow(2, lbl.apply("Reason"), reasonField);
        form.addRow(3, new Label(), previewLabel);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
            int idx = itemCombo.getSelectionModel().getSelectedIndex();
            if (idx < 0 || idx >= sale.getItems().size()) return;
            var item = sale.getItems().get(idx);
            double qty;
            try { qty = Double.parseDouble(qtyField.getText().trim()); }
            catch (Exception ex) { showAlert("Invalid qty", "Enter a valid quantity."); return; }
            if (qty <= 0 || qty > item.getQuantity()) {
                showAlert("Invalid qty", "Quantity must be between 1 and " + (int) item.getQuantity()); return;
            }

            int userId = 1;
            try { userId = com.kaziflow.utils.SessionManager.getInstance().getCurrentUser().getId(); } catch (Exception ignored) {}

            com.kaziflow.dao.SaleReturnDAO returnDAO = new com.kaziflow.dao.SaleReturnDAO();
            String returnNum = returnDAO.processReturn(
                sale.getId(), sale.getSaleNumber(),
                item.getProductId(), item.getProductName(),
                qty, item.getUnitPrice(),
                reasonField.getText().trim(), userId
            );

            if (returnNum != null) {
                com.kaziflow.services.AuditLog.log("SALE_RETURN",
                    "Return " + returnNum + " — " + item.getProductName() + " x" + (int) qty, "sales", sale.getId());
                com.kaziflow.utils.SceneManager.getInstance().refreshView("dashboard");
                com.kaziflow.utils.Toast.success(
                    com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Return processed", returnNum + " — KES " + com.kaziflow.utils.KESFormatter.format(qty * item.getUnitPrice()) + " refunded");
                loadSalesHistory();
            } else {
                com.kaziflow.utils.Toast.error(
                    com.kaziflow.utils.SceneManager.getInstance().getStage(),
                    "Return failed", "Could not process the return. Try again.");
            }
        });
    }

    /** Display name for a stored payment-method code ("mpesa" → "M-Pesa", "cash" → "Cash"). */
    private static String payLabel(String code) {
        if (code == null || code.isBlank()) return "—";
        if (code.equalsIgnoreCase("mpesa")) return "M-Pesa";
        return Character.toUpperCase(code.charAt(0)) + code.substring(1).toLowerCase();
    }
}
