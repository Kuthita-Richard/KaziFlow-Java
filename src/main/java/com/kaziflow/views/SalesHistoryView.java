package com.kaziflow.views;

import com.kaziflow.dao.SaleDAO;
import com.kaziflow.models.Sale;
import com.kaziflow.models.SaleItem;
import com.kaziflow.services.ReceiptPrinter;
import com.kaziflow.utils.KESFormatter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class SalesHistoryView {

    private VBox root;
    private TableView<Sale> table;
    private ObservableList<Sale> data = FXCollections.observableArrayList();
    private final SaleDAO saleDAO = new SaleDAO();
    private Label totalRevLabel, countLabel;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public SalesHistoryView() { buildUI(); }
    public VBox getRoot() { return root; }

    private void buildUI() {
        root = new VBox(0);
        root.setStyle("-fx-background-color: #f8fafc;");
        root.getChildren().addAll(buildTopBar(), buildContent());
        loadData();
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(2);
        Label title = new Label("Sales History"); title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label bc = new Label("Sales & POS › History"); bc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        titleBox.getChildren().addAll(title, bc);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button exportBtn = new Button("⬇ Export");
        exportBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-pref-height: 36px; -fx-padding: 0 14; -fx-cursor: hand; -fx-font-size: 13px;");
        exportBtn.setOnAction(e -> new com.kaziflow.services.ReportExportService().exportCSV(com.kaziflow.services.ReportExportService.ReportType.SALES));

        bar.getChildren().addAll(titleBox, sp, exportBtn);
        return bar;
    }

    private VBox buildContent() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(24));

        // Stats row
        HBox statsRow = new HBox(16);
        totalRevLabel = new Label("KES 0");
        countLabel    = new Label("0");

        statsRow.getChildren().addAll(
            statCard("Total Revenue", totalRevLabel, "All time", "#16a34a"),
            statCard("Total Transactions", countLabel, "Completed sales", "#2563eb"),
            statCard("Today's Revenue", new Label(KESFormatter.format(saleDAO.getTodayRevenue())), "Today", "#1e293b"),
            statCard("This Week", new Label(KESFormatter.format(saleDAO.getWeekRevenue())), "Last 7 days", "#7c3aed")
        );
        for (var c : statsRow.getChildren()) HBox.setHgrow((Region)c, Priority.ALWAYS);

        // Filter bar
        HBox filterBar = new HBox(10); filterBar.setAlignment(Pos.CENTER_LEFT);

        TextField search = new TextField();
        search.setPromptText("Search by receipt #, customer, cashier...");
        search.getStyleClass().add("search-box");
        search.setPrefWidth(300);
        search.textProperty().addListener((obs, o, v) -> filterData(v));

        ComboBox<String> payFilter = new ComboBox<>();
        payFilter.getItems().addAll("Payment Method", "Cash", "M-Pesa", "Card", "Bank");
        payFilter.setValue("Payment Method"); payFilter.setPrefHeight(36);
        payFilter.setOnAction(e -> {
            String val = payFilter.getValue();
            if (val.equals("Payment Method")) loadData();
            else data.setAll(saleDAO.findAll().stream()
                .filter(s -> val.equalsIgnoreCase(s.getPaymentMethod())).toList());
        });

        ComboBox<String> dateFilter = new ComboBox<>();
        dateFilter.getItems().addAll("All Time", "Today", "This Week", "This Month");
        dateFilter.setValue("All Time"); dateFilter.setPrefHeight(36);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        filterBar.getChildren().addAll(search, payFilter, dateFilter);

        // Table
        VBox tableCard = new VBox(0);
        tableCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1;");
        table = buildTable();
        tableCard.getChildren().add(table);

        content.getChildren().addAll(statsRow, filterBar, tableCard);
        return content;
    }

    @SuppressWarnings("unchecked")
    private TableView<Sale> buildTable() {
        TableView<Sale> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(520);
        tv.setStyle("-fx-background-color: white;");

        // Receipt #
        TableColumn<Sale, String> refCol = new TableColumn<>("RECEIPT #");
        refCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getSaleNumber()));
        refCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); return; }
                Label l = new Label(v); l.setStyle("-fx-font-weight: bold; -fx-text-fill: #2563eb;");
                setGraphic(l); setText(null);
            }
        }); refCol.setPrefWidth(120);

        // Date
        TableColumn<Sale, String> dateCol = new TableColumn<>("DATE & TIME");
        dateCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getCreatedAt() != null ? d.getValue().getCreatedAt().format(FMT) : "—"));
        dateCol.setPrefWidth(150);

        // Customer
        TableColumn<Sale, String> custCol = new TableColumn<>("CUSTOMER");
        custCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getCustomerName() != null ? d.getValue().getCustomerName() : "Walk-in"));
        custCol.setPrefWidth(150);

        // Cashier
        TableColumn<Sale, String> cashierCol = new TableColumn<>("CASHIER");
        cashierCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getServedByName() != null ? d.getValue().getServedByName() : "—"));
        cashierCol.setPrefWidth(130);

        // Payment
        TableColumn<Sale, String> payCol = new TableColumn<>("PAYMENT");
        payCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getPaymentMethod() != null ? d.getValue().getPaymentMethod() : "cash"));
        payCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) { setGraphic(null); return; }
                String bg, fg;
                switch (m.toLowerCase()) {
                    case "mpesa" -> { bg = "#dcfce7"; fg = "#16a34a"; }
                    case "card"  -> { bg = "#eff6ff"; fg = "#2563eb"; }
                    case "bank"  -> { bg = "#ede9fe"; fg = "#7c3aed"; }
                    default      -> { bg = "#f1f5f9"; fg = "#475569"; }
                }
                Label badge = new Label(m.toUpperCase());
                badge.setStyle("-fx-background-color:"+bg+";-fx-text-fill:"+fg+";-fx-font-size:10px;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:3 10;");
                setGraphic(badge); setText(null);
            }
        }); payCol.setPrefWidth(100);

        // Total
        TableColumn<Sale, String> totalCol = new TableColumn<>("TOTAL");
        totalCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            KESFormatter.format(d.getValue().getTotalAmount())));
        totalCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); return; }
                Label l = new Label(v); l.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
                setGraphic(l); setText(null);
            }
        }); totalCol.setPrefWidth(130);

        // Status
        TableColumn<Sale, String> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getStatus() != null ? d.getValue().getStatus() : "completed"));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                String bg = s.equals("refunded") ? "#fee2e2" : "#dcfce7";
                String fg = s.equals("refunded") ? "#dc2626" : "#16a34a";
                Label badge = new Label(s.substring(0,1).toUpperCase()+s.substring(1));
                badge.setStyle("-fx-background-color:"+bg+";-fx-text-fill:"+fg+";-fx-font-size:10px;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:3 10;");
                setGraphic(badge); setText(null);
            }
        }); statusCol.setPrefWidth(100);

        // Actions
        TableColumn<Sale, Void> actCol = new TableColumn<>("ACTIONS");
        actCol.setPrefWidth(120);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button viewBtn    = new Button("View");
            private final Button receiptBtn = new Button("Receipt");
            private final HBox box = new HBox(6, viewBtn, receiptBtn);
            {
                String btnStyle = "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 11px; -fx-cursor: hand; -fx-pref-height: 26px; -fx-padding: 0 8;";
                viewBtn.setStyle(btnStyle); receiptBtn.setStyle(btnStyle);
                viewBtn.setOnAction(e    -> showSaleDetail(getTableView().getItems().get(getIndex())));
                receiptBtn.setOnAction(e -> {
                    Sale s = getTableView().getItems().get(getIndex());
                    Sale full = saleDAO.findById(s.getId());
                    if (full != null) new ReceiptPrinter().showPreview(full);
                });
            }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v,empty); setGraphic(empty?null:box); }
        });

        tv.getColumns().addAll(refCol, dateCol, custCol, cashierCol, payCol, totalCol, statusCol, actCol);
        tv.setItems(data);
        return tv;
    }

    private VBox statCard(String label, Label valueLabel, String note, String color) {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        Label lbl = new Label(label); lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        valueLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label noteL = new Label(note); noteL.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        card.getChildren().addAll(lbl, valueLabel, noteL);
        return card;
    }

    private void loadData() {
        List<Sale> sales = saleDAO.findAll();
        data.setAll(sales);
        double total = sales.stream().filter(s -> "completed".equals(s.getStatus())).mapToDouble(Sale::getTotalAmount).sum();
        totalRevLabel.setText(KESFormatter.formatShort(total));
        countLabel.setText(String.valueOf(sales.size()));
    }

    private void filterData(String q) {
        if (q == null || q.isBlank()) { loadData(); return; }
        String lower = q.toLowerCase();
        data.setAll(saleDAO.findAll().stream().filter(s ->
            (s.getSaleNumber() != null && s.getSaleNumber().toLowerCase().contains(lower)) ||
            (s.getCustomerName() != null && s.getCustomerName().toLowerCase().contains(lower)) ||
            (s.getServedByName() != null && s.getServedByName().toLowerCase().contains(lower))
        ).toList());
    }

    private void showSaleDetail(Sale s) {
        Sale full = saleDAO.findById(s.getId());
        if (full == null) return;

        Stage stage = new Stage();
        stage.setTitle("Sale Detail — " + s.getSaleNumber());
        stage.initModality(Modality.APPLICATION_MODAL);

        VBox root = new VBox(16); root.setPadding(new Insets(24)); root.setStyle("-fx-background-color: #f8fafc;");

        // Header card
        VBox headerCard = card("");
        HBox headerRow = new HBox(20); headerRow.setAlignment(Pos.CENTER_LEFT);
        VBox info = new VBox(4);
        Label recLabel = new Label(full.getSaleNumber()); recLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label dateLabel = new Label(full.getCreatedAt() != null ? full.getCreatedAt().format(FMT) : "—");
        dateLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        Label custLabel = new Label("Customer: " + (full.getCustomerName() != null ? full.getCustomerName() : "Walk-in"));
        custLabel.setStyle("-fx-text-fill: #475569; -fx-font-size: 13px;");
        info.getChildren().addAll(recLabel, dateLabel, custLabel);
        headerRow.getChildren().add(info);
        headerCard.getChildren().add(headerRow);

        // Items table
        VBox itemsCard = card("Items Purchased");
        TableView<SaleItem> itemsTable = new TableView<>();
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        itemsTable.setPrefHeight(200);

        TableColumn<SaleItem,String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getProductName()));
        TableColumn<SaleItem,String> qtyCol  = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.valueOf((int)d.getValue().getQuantity())));
        TableColumn<SaleItem,String> upCol   = new TableColumn<>("Unit Price");
        upCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(KESFormatter.format(d.getValue().getUnitPrice())));
        TableColumn<SaleItem,String> ltCol   = new TableColumn<>("Line Total");
        ltCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(KESFormatter.format(d.getValue().getLineTotal())));

        itemsTable.getColumns().addAll(nameCol, qtyCol, upCol, ltCol);
        if (full.getItems() != null) itemsTable.setItems(FXCollections.observableArrayList(full.getItems()));
        itemsCard.getChildren().add(itemsTable);

        // Totals card
        VBox totalsCard = card("Payment Summary");
        totalsCard.getChildren().addAll(
            totalRow("Subtotal",     KESFormatter.format(full.getSubtotal()),      false),
            totalRow("Discount",     "- " + KESFormatter.format(full.getDiscountAmount()), false),
            totalRow("VAT (16%)",    KESFormatter.format(full.getVatAmount()),      false),
            totalRow("TOTAL",        KESFormatter.format(full.getTotalAmount()),    true),
            totalRow("Payment Method", full.getPaymentMethod() != null ? full.getPaymentMethod().toUpperCase() : "CASH", false)
        );
        if (full.getMpesaRef() != null && !full.getMpesaRef().isEmpty())
            totalsCard.getChildren().add(totalRow("M-Pesa Ref", full.getMpesaRef(), false));

        // Actions
        HBox actions = new HBox(12);
        Button printBtn = new Button("🖨 Print Receipt");
        printBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 38px; -fx-padding: 0 18; -fx-cursor: hand;");
        printBtn.setOnAction(e -> new ReceiptPrinter().showPreview(full));

        Button refundBtn = new Button("↩ Process Refund");
        refundBtn.setStyle("-fx-background-color: white; -fx-border-color: #dc2626; -fx-border-radius: 8; -fx-background-radius: 8; -fx-text-fill: #dc2626; -fx-pref-height: 38px; -fx-padding: 0 16; -fx-cursor: hand;");
        if ("refunded".equals(full.getStatus())) refundBtn.setDisable(true);
        refundBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Refund " + full.getSaleNumber() + "?\nKES " + KESFormatter.format(full.getTotalAmount()) + " will be reversed.", ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText(null); confirm.setTitle("Confirm Refund");
            confirm.showAndWait().ifPresent(btn -> { if (btn == ButtonType.YES) { stage.close(); loadData(); }});
        });

        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-pref-height: 38px; -fx-padding: 0 16; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> stage.close());

        actions.getChildren().addAll(printBtn, refundBtn, closeBtn);

        root.getChildren().addAll(headerCard, itemsCard, totalsCard, actions);

        Scene scene = new Scene(new ScrollPane(root) {{ setFitToWidth(true); setStyle("-fx-background: #f8fafc;"); }}, 620, 680);
        stage.setScene(scene);
        stage.show();
    }

    private VBox card(String titleText) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        if (!titleText.isEmpty()) {
            Label t = new Label(titleText); t.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
            card.getChildren().add(t);
        }
        return card;
    }

    private HBox totalRow(String label, String value, boolean bold) {
        HBox row = new HBox(); row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));
        String style = bold ? "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;" : "-fx-font-size: 13px; -fx-text-fill: #475569;";
        Label lbl = new Label(label); lbl.setStyle(style);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label val = new Label(value); val.setStyle(style);
        row.getChildren().addAll(lbl, sp, val);
        return row;
    }
}
