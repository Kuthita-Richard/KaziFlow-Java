package com.kaziflow.views;

import com.kaziflow.dao.RestaurantDAO;
import com.kaziflow.services.AuditLog;
import com.kaziflow.utils.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

public class RestaurantView {

    private BorderPane root;
    private final RestaurantDAO dao = new RestaurantDAO();
    private ObservableList<String[]> orderData = FXCollections.observableArrayList();
    private FlowPane tableGrid;
    private Label occupiedLbl, orderLbl, revenueLbl;

    public RestaurantView() {
        dao.ensureTables();
        buildUI();
    }

    public BorderPane getRoot() { return root; }

    private void buildUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color:#f8fafc;");

        Button tablesBtn = tabBtn("🪑  Tables",       true);
        Button ordersBtn = tabBtn("📋  Open Orders",  false);
        Button menuBtn   = tabBtn("🍽  Menu",         false);

        VBox tablesView = buildTablesView();
        VBox ordersView = buildOrdersView();
        VBox menuView   = buildMenuView();

        StackPane area = new StackPane(tablesView);
        VBox.setVgrow(area, Priority.ALWAYS);

        tablesBtn.setOnAction(e -> { area.getChildren().setAll(tablesView); setActive(tablesBtn, ordersBtn, menuBtn); refreshTables(); });
        ordersBtn.setOnAction(e -> { area.getChildren().setAll(ordersView); setActive(ordersBtn, tablesBtn, menuBtn); refreshOrders(); });
        menuBtn  .setOnAction(e -> { area.getChildren().setAll(menuView);   setActive(menuBtn, tablesBtn, ordersBtn); });

        HBox tabBar = new HBox(0, tablesBtn, ordersBtn, menuBtn);
        tabBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;-fx-padding:0 24;");

        VBox layout = new VBox(0, buildHeader(), tabBar, area);
        VBox.setVgrow(area, Priority.ALWAYS);
        root.setCenter(layout);

        refreshTables();
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        HBox h = new HBox(16);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(16, 24, 16, 24));
        h.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");

        VBox tb = new VBox(2);
        Label t = new Label("🍽  Restaurant Manager");
        t.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label s = new Label("Table management · Orders · KOT · Kitchen");
        s.setStyle("-fx-font-size:12px;-fx-text-fill:#64748b;");
        tb.getChildren().addAll(t, s);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        occupiedLbl = statVal(String.valueOf(dao.getOpenTableCount()));
        orderLbl    = statVal(String.valueOf(dao.getOpenOrderCount()));
        revenueLbl  = statVal("KES " + KESFormatter.formatShort(dao.getTodayRevenue()));

        Button takeawayBtn = new Button("+ Takeaway Order");
        takeawayBtn.setStyle("-fx-background-color:#7c3aed;-fx-text-fill:white;-fx-font-weight:bold;" +
            "-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 16;-fx-cursor:hand;-fx-font-size:13px;");
        takeawayBtn.setOnAction(e -> openOrderDialog(0, "Takeaway", "takeaway"));

        h.getChildren().addAll(tb, sp,
            statCard("Occupied Tables", occupiedLbl),
            statCard("Open Orders",     orderLbl),
            statCard("Today Revenue",   revenueLbl),
            takeawayBtn);
        return h;
    }

    private Label statVal(String v) {
        Label l = new Label(v); l.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#1e293b;"); return l;
    }
    private VBox statCard(String label, Label valLbl) {
        Label lbl = new Label(label); lbl.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;-fx-font-weight:bold;");
        VBox card = new VBox(2, lbl, valLbl);
        card.setStyle("-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#e2e8f0;-fx-border-radius:10;-fx-border-width:1;-fx-padding:8 16;-fx-min-width:110px;");
        return card;
    }

    // ── Tables View ────────────────────────────────────────────────────────

    private VBox buildTablesView() {
        VBox v = new VBox(0); v.setStyle("-fx-background-color:#f1f5f9;");

        // Legend
        HBox legend = new HBox(20);
        legend.setPadding(new Insets(10, 24, 10, 24));
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        legend.getChildren().addAll(
            legendItem("#dcfce7","#16a34a","Free"),
            legendItem("#fee2e2","#dc2626","Occupied"),
            legendItem("#fef3c7","#d97706","Reserved"),
            legendItem("#f1f5f9","#64748b","Cleaning")
        );

        tableGrid = new FlowPane();
        tableGrid.setHgap(16); tableGrid.setVgap(16);
        tableGrid.setPadding(new Insets(24));

        ScrollPane scroll = new ScrollPane(tableGrid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:#f1f5f9;-fx-background:#f1f5f9;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        v.getChildren().addAll(legend, scroll);
        return v;
    }

    private HBox legendItem(String bg, String text, String label) {
        StackPane box = new StackPane();
        box.setStyle("-fx-background-color:" + bg + ";-fx-background-radius:6;-fx-min-width:20;-fx-min-height:20;");
        Label l = new Label(label); l.setStyle("-fx-text-fill:#64748b;-fx-font-size:12px;");
        HBox item = new HBox(6, box, l); item.setAlignment(Pos.CENTER_LEFT); return item;
    }

    private void refreshTables() {
        tableGrid.getChildren().clear();
        List<String[]> tables = dao.getTables();
        for (String[] tbl : tables) {
            tableGrid.getChildren().add(buildTableCard(tbl));
        }
        occupiedLbl.setText(String.valueOf(dao.getOpenTableCount()));
        orderLbl.setText(String.valueOf(dao.getOpenOrderCount()));
        revenueLbl.setText("KES " + KESFormatter.formatShort(dao.getTodayRevenue()));
    }

    private VBox buildTableCard(String[] tbl) {
        // [0]=id [1]=table_no [2]=capacity [3]=section [4]=status [5]=order_no [6]=order_total
        String status = tbl[4];
        String bg, border, txtColor;
        switch (status) {
            case "occupied"  -> { bg="#fee2e2"; border="#dc2626"; txtColor="#dc2626"; }
            case "reserved"  -> { bg="#fef3c7"; border="#d97706"; txtColor="#d97706"; }
            case "cleaning"  -> { bg="#f1f5f9"; border="#94a3b8"; txtColor="#64748b"; }
            default          -> { bg="#dcfce7"; border="#16a34a"; txtColor="#16a34a"; }
        }

        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(130); card.setPrefHeight(130);
        card.setStyle("-fx-background-color:" + bg + ";-fx-background-radius:14;" +
            "-fx-border-color:" + border + ";-fx-border-radius:14;-fx-border-width:2;" +
            "-fx-padding:12;-fx-cursor:hand;");

        Label tableNo = new Label(tbl[1]);
        tableNo.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:" + txtColor + ";");
        Label section = new Label(tbl[3] + " · " + tbl[2] + " seats");
        section.setStyle("-fx-font-size:10px;-fx-text-fill:#64748b;");
        Label statusLbl = new Label(status.toUpperCase());
        statusLbl.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:" + txtColor + ";");

        card.getChildren().addAll(tableNo, section, statusLbl);

        if ("occupied".equals(status) && !tbl[5].isEmpty()) {
            Label ordLbl = new Label(tbl[5]);
            ordLbl.setStyle("-fx-font-size:10px;-fx-text-fill:#dc2626;");
            Label amtLbl = new Label("KES " + KESFormatter.formatShort(Double.parseDouble(tbl[6])));
            amtLbl.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#dc2626;");
            card.getChildren().addAll(ordLbl, amtLbl);
        }

        // Click action
        card.setOnMouseClicked(e -> handleTableClick(tbl));

        // Hover effect
        card.setOnMouseEntered(e -> card.setStyle(card.getStyle() +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.15),8,0,0,2);"));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle().replace(
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.15),8,0,0,2);", "")));

        return card;
    }

    private void handleTableClick(String[] tbl) {
        String status = tbl[4];
        int tableId = Integer.parseInt(tbl[0]);

        if ("free".equals(status)) {
            // Open new order
            openOrderDialog(tableId, tbl[1], "dine_in");
        } else if ("occupied".equals(status)) {
            // Show order options
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            com.kaziflow.utils.DialogUtils.makeResizable(alert);
            alert.setTitle("Table " + tbl[1]);
            alert.setHeaderText("Table " + tbl[1] + " — " + tbl[5] + " — KES " + tbl[6]);
            alert.setContentText("What would you like to do?");
            ButtonType addItemsBtn = new ButtonType("+ Add Items");
            ButtonType viewBillBtn = new ButtonType("View Bill");
            ButtonType freeBtn     = new ButtonType("Free Table");
            ButtonType cancelBtn   = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(addItemsBtn, viewBillBtn, freeBtn, cancelBtn);
            alert.showAndWait().ifPresent(r -> {
                if (r == addItemsBtn) openOrderDialog(tableId, tbl[1], "dine_in");
                else if (r == viewBillBtn) showBillDialog(tbl[5], tableId);
                else if (r == freeBtn) {
                    dao.updateTableStatus(tableId, "free");
                    refreshTables();
                }
            });
        }
    }

    // ── Order Dialog ───────────────────────────────────────────────────────

    private void openOrderDialog(int tableId, String tableNo, String orderType) {
        Dialog<ButtonType> dialog = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle("Order — " + tableNo);
        dialog.getDialogPane().setStyle("-fx-background-color:white;");
        dialog.getDialogPane().setPrefWidth(720);
        dialog.getDialogPane().setPrefHeight(580);

        // Split: menu left, cart right
        HBox content = new HBox(0);

        // Left — menu
        VBox menuPane = new VBox(0);
        menuPane.setPrefWidth(400);
        menuPane.setStyle("-fx-background-color:#f8fafc;");

        // Category filter
        HBox catBar = new HBox(6);
        catBar.setPadding(new Insets(10, 12, 10, 12));
        catBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        ToggleGroup catGroup = new ToggleGroup();
        ToggleButton allCat = catFilterBtn("All", null, catGroup, true);
        catBar.getChildren().add(allCat);
        dao.getMenuCategories().forEach(cat -> catBar.getChildren().add(
            catFilterBtn(cat[1], Integer.parseInt(cat[0]), catGroup, false)));
        catGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> { if (newT == null && oldT != null) catGroup.selectToggle(oldT); });

        // Menu items grid
        FlowPane menuGrid = new FlowPane();
        menuGrid.setHgap(8); menuGrid.setVgap(8); menuGrid.setPadding(new Insets(12));
        ScrollPane menuScroll = new ScrollPane(menuGrid);
        menuScroll.setFitToWidth(true);
        menuScroll.setStyle("-fx-background:#f8fafc;-fx-background-color:#f8fafc;");
        VBox.setVgrow(menuScroll, Priority.ALWAYS);
        menuPane.getChildren().addAll(catBar, menuScroll);

        // Right — cart
        VBox cartPane = new VBox(0);
        cartPane.setPrefWidth(320);
        cartPane.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent transparent #e2e8f0;-fx-border-width:0 0 0 1;");

        Label cartTitle = new Label("Order — " + tableNo);
        cartTitle.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1e293b;-fx-padding:14 16;");

        ObservableList<String[]> cartItems = FXCollections.observableArrayList();
        ListView<String[]> cartList = new ListView<>(cartItems);
        VBox.setVgrow(cartList, Priority.ALWAYS);
        cartList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String[] item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                Label name = new Label(item[0]); name.setStyle("-fx-font-size:12px;-fx-font-weight:bold;"); HBox.setHgrow(name, Priority.ALWAYS);
                Label qty = new Label("x" + item[1]); qty.setStyle("-fx-font-size:12px;-fx-text-fill:#64748b;");
                Label price = new Label("KES " + item[2]); price.setStyle("-fx-font-size:12px;-fx-font-weight:bold;");
                Button del = new Button("✕"); del.setStyle("-fx-background-color:transparent;-fx-text-fill:#dc2626;-fx-cursor:hand;");
                del.setOnAction(e -> cartItems.remove(item));
                row.getChildren().addAll(name, qty, price, del);
                setGraphic(row);
            }
        });

        // Total row
        Label totalLbl = new Label("KES 0.00");
        totalLbl.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#1e293b;-fx-padding:0 16;");
        cartItems.addListener((javafx.collections.ListChangeListener<String[]>) c -> {
            double total = cartItems.stream()
                .mapToDouble(i -> { try { return Double.parseDouble(i[2]); } catch (Exception ex) { return 0; } })
                .sum();
            totalLbl.setText("KES " + KESFormatter.format(total));
        });

        HBox totalRow = new HBox(new Label("Total: "), totalLbl);
        totalRow.setPadding(new Insets(8, 16, 8, 16));
        totalRow.setAlignment(Pos.CENTER_LEFT);
        totalRow.setStyle("-fx-border-color:#e2e8f0 transparent transparent transparent;-fx-border-width:1 0 0 0;");

        // Waiter field
        TextField waiterF = new TextField(); waiterF.setPromptText("Waiter name");
        waiterF.setStyle("-fx-margin:8;-fx-pref-height:32px;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:12px;-fx-padding:0 8;");
        HBox waiterRow = new HBox(waiterF); waiterRow.setPadding(new Insets(8,16,8,16)); HBox.setHgrow(waiterF, Priority.ALWAYS);

        cartPane.getChildren().addAll(cartTitle, cartList, totalRow, waiterRow);
        content.getChildren().addAll(menuPane, cartPane);

        // Load menu items
        Runnable loadMenu = () -> {
            Integer catId = catGroup.getSelectedToggle() != null &&
                catGroup.getSelectedToggle().getUserData() != null
                ? (Integer) catGroup.getSelectedToggle().getUserData() : null;
            menuGrid.getChildren().clear();
            dao.getMenuItems(catId).forEach(item -> {
                VBox card = new VBox(4);
                card.setAlignment(Pos.CENTER);
                card.setPrefWidth(110); card.setPrefHeight(90);
                card.setStyle("-fx-background-color:white;-fx-background-radius:10;-fx-border-color:#e2e8f0;-fx-border-radius:10;-fx-border-width:1;-fx-padding:8;-fx-cursor:hand;");
                Label nameL = new Label(item[1]); nameL.setWrapText(true); nameL.setMaxWidth(100);
                nameL.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#1e293b;-fx-text-alignment:center;");
                Label priceL = new Label("KES " + KESFormatter.formatShort(Double.parseDouble(item[3])));
                priceL.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#2563eb;");
                card.getChildren().addAll(nameL, priceL);
                card.setOnMouseClicked(e -> {
                    double unitPrice = Double.parseDouble(item[3]);
                    // Check if already in cart
                    boolean found = false;
                    for (String[] ci : cartItems) {
                        if (ci[3].equals(item[0])) {
                            ci[1] = String.valueOf(Integer.parseInt(ci[1]) + 1);
                            ci[2] = String.format("%.2f", Double.parseDouble(ci[1]) * unitPrice);
                            cartItems.set(cartItems.indexOf(ci), ci);
                            found = true; break;
                        }
                    }
                    if (!found) cartItems.add(new String[]{item[1], "1", item[3], item[0], item[4]});
                    // Trigger total recalc
                    totalLbl.setText("KES " + KESFormatter.format(
                        cartItems.stream().mapToDouble(i -> { try { return Double.parseDouble(i[2]); } catch(Exception ex){ return 0; } }).sum()));
                });
                menuGrid.getChildren().add(card);
            });
        };

        catGroup.selectedToggleProperty().addListener((obs, old, nw) -> loadMenu.run());
        loadMenu.run();

        dialog.getDialogPane().setContent(content);
        ButtonType kotType = new ButtonType("🖨 Send KOT", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(kotType, ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(bt -> bt);
        dialog.showAndWait().ifPresent(r -> {
            if (r == ButtonType.CANCEL || cartItems.isEmpty()) return;
            int userId = 1;
            try { userId = SessionManager.getInstance().getCurrentUser().getId(); } catch(Exception ignored){}
            // Create or get existing order
            int orderId = dao.createOrder(tableId, tableNo, orderType, waiterF.getText().trim(), "", "", userId);
            if (orderId < 0) { Toast.error(SceneManager.getInstance().getStage(), "Error", "Could not create order."); return; }
            // Add items
            for (String[] ci : cartItems) {
                dao.addOrderItem(orderId, Integer.parseInt(ci[3]), ci[0],
                    Integer.parseInt(ci[1]), Double.parseDouble(ci[2]) / Integer.parseInt(ci[1]),
                    "", ci[4]);
            }
            if (r == kotType || r == ButtonType.OK) {
                dao.sendKOT(orderId);
                AuditLog.log("KOT_SENT", "KOT for " + tableNo + " — " + cartItems.size() + " items", "restaurant", orderId);
                Toast.success(SceneManager.getInstance().getStage(), "KOT Sent!", "Order sent to kitchen");
            }
            refreshTables();
            refreshOrders();
        });
    }

    private ToggleButton catFilterBtn(String label, Integer catId, ToggleGroup group, boolean selected) {
        ToggleButton btn = new ToggleButton(label);
        btn.setToggleGroup(group); btn.setUserData(catId); btn.setSelected(selected);
        String base = "-fx-background-radius:20;-fx-border-radius:20;-fx-border-width:1;-fx-pref-height:28px;-fx-padding:0 12;-fx-font-size:11px;-fx-cursor:hand;";
        String off = base + "-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-text-fill:#475569;";
        String on  = base + "-fx-background-color:#2563eb;-fx-border-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;";
        btn.setStyle(selected ? on : off);
        btn.selectedProperty().addListener((obs, was, is) -> btn.setStyle(is ? on : off));
        return btn;
    }

    // ── Bill Dialog ────────────────────────────────────────────────────────

    private void showBillDialog(String orderNo, int tableId) {
        if (orderNo.isEmpty()) return;
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        com.kaziflow.utils.DialogUtils.makeResizable(alert);
        alert.setTitle("Bill — " + orderNo);
        alert.setHeaderText("Ready to close bill for " + orderNo + "?");
        alert.setContentText("Mark order as paid and free the table?");
        alert.getButtonTypes().setAll(new ButtonType("✓ Mark Paid"), ButtonType.CANCEL);
        alert.showAndWait().ifPresent(r -> {
            if (r.getText().contains("Paid")) {
                // Find order id from open orders
                dao.getOpenOrders().stream()
                    .filter(o -> o[1].equals(orderNo))
                    .findFirst()
                    .ifPresent(o -> {
                        dao.updateOrderStatus(Integer.parseInt(o[0]), "paid");
                        AuditLog.log("ORDER_PAID", "Order " + orderNo + " paid", "restaurant", Integer.parseInt(o[0]));
                        refreshTables();
                        refreshOrders();
                        Toast.success(SceneManager.getInstance().getStage(), "Paid!", orderNo + " closed");
                    });
            }
        });
    }

    // ── Orders View ────────────────────────────────────────────────────────

    private VBox buildOrdersView() {
        VBox v = new VBox(0); v.setStyle("-fx-background-color:white;");
        TableView<String[]> tv = new TableView<>(orderData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;");
        VBox.setVgrow(tv, Priority.ALWAYS);

        String[] hdrs = {"Order #","Table","Type","Waiter","Status","Total (KES)","Time"};
        int[]    idxs = {1,2,3,4,5,6,7};
        for (int i=0;i<hdrs.length;i++) {
            final int ci=idxs[i];
            TableColumn<String[],String> tc = new TableColumn<>(hdrs[i]);
            tc.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(ci<d.getValue().length?d.getValue()[ci]:""));
            tv.getColumns().add(tc);
        }
        TableColumn<String[],Void> actCol = new TableColumn<>("");
        actCol.setPrefWidth(160);
        actCol.setCellFactory(c -> new TableCell<>(){
            private final Button kotBtn  = btn2("🖨 KOT",    "#d97706");
            private final Button paidBtn = btn2("✓ Paid",   "#16a34a");
            private final Button cancelBtn = btn2("✕",      "#dc2626");
            private final HBox box = new HBox(5, kotBtn, paidBtn, cancelBtn);
            {
                kotBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    dao.sendKOT(Integer.parseInt(row[0]));
                    refreshOrders();
                    Toast.success(SceneManager.getInstance().getStage(), "KOT Sent!", row[1]);
                });
                paidBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    dao.updateOrderStatus(Integer.parseInt(row[0]), "paid");
                    AuditLog.log("ORDER_PAID", "Order " + row[1] + " marked paid", "restaurant", Integer.parseInt(row[0]));
                    refreshOrders(); refreshTables();
                    Toast.success(SceneManager.getInstance().getStage(), "Paid!", row[1]);
                });
                cancelBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    dao.updateOrderStatus(Integer.parseInt(row[0]), "cancelled");
                    refreshOrders(); refreshTables();
                });
            }
            @Override protected void updateItem(Void v, boolean empty){
                super.updateItem(v,empty); setGraphic(empty?null:box);
            }
        });
        tv.getColumns().add(actCol);
        v.getChildren().add(tv);
        return v;
    }

    private void refreshOrders() {
        AsyncTask.run(dao::getOpenOrders, orderData::setAll, err->{});
    }

    // ── Menu Management View ───────────────────────────────────────────────

    private VBox buildMenuView() {
        VBox v = new VBox(0); v.setStyle("-fx-background-color:#f8fafc;");

        HBox hdr = new HBox(12);
        hdr.setAlignment(Pos.CENTER_LEFT);
        hdr.setPadding(new Insets(14,24,14,24));
        hdr.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        Label title = new Label("Menu Items"); title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button addBtn = new Button("+ Add Menu Item");
        addBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        addBtn.setOnAction(e -> showAddMenuItemDialog());
        hdr.getChildren().addAll(title, sp, addBtn);

        TableView<String[]> tv = new TableView<>(FXCollections.observableArrayList(dao.getMenuItems(null)));
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;");
        VBox.setVgrow(tv, Priority.ALWAYS);
        String[] cols = {"ID","Name","Description","Price (KES)","Station","Prep (min)","Category"};
        for (int i=0;i<cols.length;i++) {
            final int ci=i;
            TableColumn<String[],String> tc = new TableColumn<>(cols[i]);
            tc.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(ci<d.getValue().length?d.getValue()[ci]:""));
            tv.getColumns().add(tc);
        }
        v.getChildren().addAll(hdr, tv);
        return v;
    }

    private void showAddMenuItemDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle("Add Menu Item");
        dialog.getDialogPane().setStyle("-fx-background-color:white;");
        dialog.getDialogPane().setPrefWidth(440);

        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));
        TextField nameF  = fld("Item name","");
        TextField descF  = fld("Description","");
        TextField priceF = fld("Price (KES)","0");
        TextField prepF  = fld("Prep time (minutes)","15");
        ComboBox<String> catBox = new ComboBox<>();
        catBox.setPrefWidth(280);
        dao.getMenuCategories().forEach(cat -> catBox.getItems().add(cat[0]+":"+cat[1]));
        if (!catBox.getItems().isEmpty()) catBox.setValue(catBox.getItems().get(0));
        ComboBox<String> stationBox = new ComboBox<>();
        stationBox.getItems().addAll("Main Kitchen","Bar","Grill","Bakery","Cold Kitchen");
        stationBox.setValue("Main Kitchen"); stationBox.setPrefWidth(280);

        form.addRow(0, lbl("Name"),     nameF,    lbl("Price"), priceF);
        form.addRow(1, lbl("Category"), catBox,   lbl("Station"), stationBox);
        form.addRow(2, lbl("Prep (min)"), prepF,  lbl("Description"), descF);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK || nameF.getText().isBlank()) return;
            try {
                String catSel = catBox.getValue();
                Integer catId = catSel != null ? Integer.parseInt(catSel.split(":")[0]) : null;
                dao.saveMenuItem(catId, nameF.getText().trim(), descF.getText().trim(),
                    Double.parseDouble(priceF.getText().trim()), stationBox.getValue(),
                    Integer.parseInt(prepF.getText().trim()));
                Toast.success(SceneManager.getInstance().getStage(), "Item added", nameF.getText());
            } catch (Exception ex) {
                Toast.error(SceneManager.getInstance().getStage(), "Error", ex.getMessage());
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Button tabBtn(String label, boolean active) {
        Button b = new Button(label);
        String base="-fx-background-color:transparent;-fx-border-color:transparent;-fx-pref-height:44px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;";
        b.setStyle(base+(active?"-fx-text-fill:#2563eb;-fx-font-weight:bold;-fx-border-color:transparent transparent #2563eb transparent;-fx-border-width:0 0 2 0;":"-fx-text-fill:#64748b;"));
        return b;
    }
    private void setActive(Button active, Button... rest) {
        String base="-fx-background-color:transparent;-fx-pref-height:44px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;";
        active.setStyle(base+"-fx-text-fill:#2563eb;-fx-font-weight:bold;-fx-border-color:transparent transparent #2563eb transparent;-fx-border-width:0 0 2 0;");
        for (Button b:rest) b.setStyle(base+"-fx-text-fill:#64748b;-fx-border-color:transparent;");
    }
    private Button btn2(String t, String color){
        Button b=new Button(t);
        b.setStyle("-fx-background-color:white;-fx-border-color:"+color+";-fx-border-radius:5;-fx-background-radius:5;-fx-font-size:11px;-fx-text-fill:"+color+";-fx-cursor:hand;-fx-padding:3 8;");
        return b;
    }
    private TextField fld(String prompt, String val){
        TextField tf=new TextField(val); tf.setPromptText(prompt); tf.setPrefWidth(240);
        tf.setStyle("-fx-pref-height:34px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");
        return tf;
    }
    private Label lbl(String t){Label l=new Label(t);l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;");return l;}
}
