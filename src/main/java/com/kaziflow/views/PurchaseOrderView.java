package com.kaziflow.views;

import com.kaziflow.dao.ProductDAO;
import com.kaziflow.dao.PurchaseDAO;
import com.kaziflow.dao.SupplierDAO;
import com.kaziflow.models.Product;
import com.kaziflow.models.Purchase;
import com.kaziflow.models.PurchaseItem;
import com.kaziflow.models.Supplier;
import com.kaziflow.utils.FormValidator;
import com.kaziflow.utils.KESFormatter;
import com.kaziflow.utils.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PurchaseOrderView extends VBox {

    // ── State ──
    private final ObservableList<PurchaseItem> lineItems = FXCollections.observableArrayList();
    private final ObservableList<Purchase> historyData = FXCollections.observableArrayList();

    private final PurchaseDAO purchaseDAO = new PurchaseDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final ProductDAO productDAO = new ProductDAO();

    // ── PO form fields ──
    private ComboBox<Supplier> supplierCombo;
    private DatePicker deliveryDate;
    private ComboBox<String> paymentTerms;
    private TextArea notesField;
    private Label poNumber;
    private Label subtotalLabel;
    private Label vatLabel;
    private Label totalLabel;
    private TableView<PurchaseItem> lineItemTable;

    // ── Product picker ──
    private ComboBox<Product> productPicker;
    private TextField qtyField;
    private TextField unitCostField;

    public PurchaseOrderView() {
        buildUI();
    }

    private void buildUI() {
        setStyle("-fx-background-color: #f8fafc;");
        setSpacing(0);

        // ── Top bar ──
        HBox topbar = buildTopBar();

        // ── Content ──
        HBox content = new HBox(20);
        content.setPadding(new Insets(24));

        VBox leftPanel = buildPoForm();
        HBox.setHgrow(leftPanel, Priority.ALWAYS);

        VBox rightPanel = buildSummaryPanel();
        rightPanel.setPrefWidth(340);

        content.getChildren().addAll(leftPanel, rightPanel);

        // ── History table below ──
        VBox historySection = buildHistorySection();
        historySection.setPadding(new Insets(0, 24, 24, 24));

        getChildren().addAll(topbar, content, historySection);
        loadHistory();
    }

    // ─── Top Bar ─────────────────────────────────────────────────────────────

    private HBox buildTopBar() {
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(2);
        Label title = new Label("Purchase Orders"); title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label bc = new Label("Purchases › New Purchase Order"); bc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        titleBox.getChildren().addAll(title, bc);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        // Stats
        HBox stats = new HBox(24);
        stats.setAlignment(Pos.CENTER);
        stats.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 8; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-border-width: 1; -fx-padding: 8 20;");
        double outstanding = purchaseDAO.getTotalOutstanding();
        stats.getChildren().addAll(
            miniStat("Total Outstanding", KESFormatter.formatShort(outstanding)),
            miniStat("Pending POs", String.valueOf(historyData.size()))
        );

        bar.getChildren().addAll(titleBox, sp, stats);
        return bar;
    }

    private VBox miniStat(String label, String value) {
        VBox v = new VBox(2);
        Label lbl = new Label(label); lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8; -fx-font-weight: bold;");
        Label val = new Label(value); val.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        v.getChildren().addAll(lbl, val);
        return v;
    }

    // ─── PO Form ─────────────────────────────────────────────────────────────

    private VBox buildPoForm() {
        VBox panel = new VBox(16);

        // ── Header card ──
        VBox headerCard = card();
        Label headerTitle = new Label("Purchase Order Details");
        headerTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        // PO number (auto-generated)
        HBox poNumRow = new HBox(12); poNumRow.setAlignment(Pos.CENTER_LEFT);
        Label poNumLabel = new Label("PO Number:");
        poNumLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        poNumber = new Label(purchaseDAO.generatePurchaseNumber());
        poNumber.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2563eb; " +
            "-fx-background-color: #eff6ff; -fx-padding: 4 12; -fx-background-radius: 6;");
        poNumRow.getChildren().addAll(poNumLabel, poNumber);

        // Supplier + Date row
        GridPane grid = new GridPane(); grid.setHgap(16); grid.setVgap(12);

        supplierCombo = new ComboBox<>();
        supplierCombo.setItems(FXCollections.observableArrayList(supplierDAO.findAll()));
        supplierCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Supplier s) { return s == null ? "" : s.getName(); }
            @Override public Supplier fromString(String s) { return null; }
        });
        supplierCombo.setPromptText("Select Supplier...");
        supplierCombo.setPrefWidth(220);
        supplierCombo.setPrefHeight(36);

        deliveryDate = new DatePicker(LocalDate.now().plusDays(7));
        deliveryDate.setPrefWidth(180);
        deliveryDate.setPrefHeight(36);

        paymentTerms = new ComboBox<>();
        paymentTerms.getItems().addAll("Cash on Delivery", "Net 7", "Net 15", "Net 30", "Net 60");
        paymentTerms.setValue("Net 30");
        paymentTerms.setPrefWidth(180);
        paymentTerms.setPrefHeight(36);

        notesField = new TextArea();
        notesField.setPromptText("Additional notes, delivery instructions...");
        notesField.setPrefRowCount(2);
        notesField.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px;");

        grid.add(formLbl("Supplier *"), 0, 0); grid.add(supplierCombo, 1, 0);
        grid.add(formLbl("Expected Delivery"), 2, 0); grid.add(deliveryDate, 3, 0);
        grid.add(formLbl("Payment Terms"), 0, 1); grid.add(paymentTerms, 1, 1);
        grid.add(formLbl("Notes"), 2, 1); grid.add(notesField, 3, 1);

        headerCard.getChildren().addAll(headerTitle, poNumRow, grid);

        // ── Line Items card ──
        VBox itemsCard = card();
        Label itemsTitle = new Label("Order Items");
        itemsTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        // Product picker row
        HBox pickerRow = new HBox(10); pickerRow.setAlignment(Pos.CENTER_LEFT);
        pickerRow.setStyle("-fx-background-color: #f8fafc; -fx-padding: 12; -fx-background-radius: 8; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-border-width: 1;");

        productPicker = new ComboBox<>();
        productPicker.setItems(FXCollections.observableArrayList(productDAO.findAll()));
        productPicker.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Product p) { return p == null ? "" : p.getName() + " (" + p.getSku() + ")"; }
            @Override public Product fromString(String s) { return null; }
        });
        productPicker.setPromptText("Search and select product...");
        productPicker.setPrefWidth(280);
        productPicker.setPrefHeight(36);

        // Auto-fill cost from product
        productPicker.setOnAction(e -> {
            Product selected = productPicker.getValue();
            if (selected != null && (unitCostField.getText().isBlank() || unitCostField.getText().equals("0.0"))) {
                unitCostField.setText(String.valueOf(selected.getCostPrice()));
            }
        });

        qtyField = inputField("Qty", "1", 70);
        unitCostField = inputField("Unit Cost (KES)", "", 150);

        Button addItemBtn = new Button("+ Add Item");
        addItemBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 36px; -fx-padding: 0 16; -fx-cursor: hand;");
        addItemBtn.setOnAction(e -> addLineItem());

        pickerRow.getChildren().addAll(
            new Label("Product:"), productPicker,
            new Label("Qty:"), qtyField,
            new Label("Unit Cost:"), unitCostField,
            addItemBtn
        );

        // Line items table
        lineItemTable = buildLineItemTable();

        itemsCard.getChildren().addAll(itemsTitle, pickerRow, lineItemTable);
        panel.getChildren().addAll(headerCard, itemsCard);
        return panel;
    }

    @SuppressWarnings("unchecked")
    private TableView<PurchaseItem> buildLineItemTable() {
        TableView<PurchaseItem> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(280);
        tv.setPlaceholder(new Label("No items added yet — search and add products above."));
        tv.setStyle("-fx-background-color: white;");
        tv.setItems(lineItems);

        TableColumn<PurchaseItem,String> nameCol = new TableColumn<>("PRODUCT");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProductName()));
        nameCol.setPrefWidth(220);

        TableColumn<PurchaseItem,String> qtyCol = new TableColumn<>("QTY");
        qtyCol.setCellValueFactory(d -> new SimpleStringProperty(String.format("%.0f", d.getValue().getQuantity())));
        qtyCol.setPrefWidth(70);

        TableColumn<PurchaseItem,String> unitCol = new TableColumn<>("UNIT COST");
        unitCol.setCellValueFactory(d -> new SimpleStringProperty(KESFormatter.format(d.getValue().getUnitCost())));
        unitCol.setPrefWidth(130);

        TableColumn<PurchaseItem,String> totalCol = new TableColumn<>("LINE TOTAL");
        totalCol.setCellValueFactory(d -> new SimpleStringProperty(KESFormatter.format(d.getValue().getLineTotal())));
        totalCol.setPrefWidth(130);

        // Remove button
        TableColumn<PurchaseItem,Void> removeCol = new TableColumn<>("");
        removeCol.setPrefWidth(50);
        removeCol.setCellFactory(c -> new TableCell<>() {
            private final Button btn = new Button("✕");
            { btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc2626; -fx-cursor: hand; -fx-font-size: 13px; -fx-border-color: transparent;");
              btn.setOnAction(e -> { lineItems.remove(getTableView().getItems().get(getIndex())); refreshTotals(); }); }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v, empty); setGraphic(empty ? null : btn); }
        });

        tv.getColumns().addAll(nameCol, qtyCol, unitCol, totalCol, removeCol);
        return tv;
    }

    private void addLineItem() {
        Product product = productPicker.getValue();
        if (product == null) { showError("Please select a product."); return; }
        double qty = 0, cost = 0;
        try { qty = Double.parseDouble(qtyField.getText().trim()); } catch (Exception e) { showError("Invalid quantity."); return; }
        try { cost = Double.parseDouble(unitCostField.getText().trim()); } catch (Exception e) { showError("Invalid unit cost."); return; }
        if (qty <= 0) { showError("Quantity must be greater than 0."); return; }
        if (cost < 0)  { showError("Unit cost cannot be negative."); return; }

        // Merge with existing if same product
        for (PurchaseItem existing : lineItems) {
            if (existing.getProductId() == product.getId()) {
                existing.setQuantity(existing.getQuantity() + qty);
                existing.setLineTotal(existing.getQuantity() * existing.getUnitCost());
                lineItemTable.refresh();
                refreshTotals();
                productPicker.setValue(null); qtyField.setText("1"); unitCostField.setText("");
                return;
            }
        }

        PurchaseItem item = new PurchaseItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(qty);
        item.setUnitCost(cost);
        item.setLineTotal(qty * cost);
        lineItems.add(item);
        refreshTotals();

        productPicker.setValue(null);
        qtyField.setText("1");
        unitCostField.setText("");
    }

    // ─── Summary Panel ────────────────────────────────────────────────────────

    private VBox buildSummaryPanel() {
        VBox panel = new VBox(16);

        VBox summaryCard = card();
        Label summaryTitle = new Label("Order Summary");
        summaryTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        subtotalLabel = new Label("KES 0.00"); subtotalLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #1e293b;");
        vatLabel      = new Label("KES 0.00"); vatLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #1e293b;");
        totalLabel    = new Label("KES 0.00"); totalLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2563eb;");

        VBox rows = new VBox(0);
        rows.getChildren().addAll(
            summaryRow("Subtotal", subtotalLabel),
            summaryRow("VAT (16%)", vatLabel)
        );

        // Total row with separator
        HBox totalRow = new HBox(); totalRow.setAlignment(Pos.CENTER_LEFT);
        totalRow.setStyle("-fx-border-color: #e2e8f0 transparent transparent transparent; -fx-border-width: 1 0 0 0; -fx-padding: 10 0 0 0;");
        Label totalLbl = new Label("TOTAL"); totalLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        totalRow.getChildren().addAll(totalLbl, sp, totalLabel);

        // Submit button
        Button submitBtn = new Button("Submit Purchase Order");
        submitBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 44px; -fx-font-size: 14px; -fx-cursor: hand;");
        submitBtn.setMaxWidth(Double.MAX_VALUE);
        submitBtn.setOnAction(e -> submitOrder());

        Button clearBtn = new Button("Clear Order");
        clearBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-pref-height: 36px; -fx-cursor: hand;");
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.setOnAction(e -> { lineItems.clear(); supplierCombo.setValue(null); refreshTotals(); });

        summaryCard.getChildren().addAll(summaryTitle, rows, totalRow, submitBtn, clearBtn);

        // ── Supplier quick-view card ──
        VBox supplierCard = card();
        Label supplierTitle = new Label("Supplier Details");
        supplierTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label supplierHint = new Label("Select a supplier above to view details");
        supplierHint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-wrap-text: true;");
        supplierHint.setWrapText(true);
        supplierCard.getChildren().addAll(supplierTitle, supplierHint);

        supplierCombo.setOnAction(e -> {
            Supplier s = supplierCombo.getValue();
            supplierCard.getChildren().clear();
            supplierCard.getChildren().add(supplierTitle);
            if (s != null) {
                supplierCard.getChildren().addAll(
                    detailRow("Name", s.getName()),
                    detailRow("Phone", s.getPhone() != null ? s.getPhone() : "—"),
                    detailRow("Email", s.getEmail() != null ? s.getEmail() : "—"),
                    detailRow("Outstanding", KESFormatter.format(s.getOutstandingBalance())),
                    detailRow("Payment Terms", s.getPaymentTerms() + " days")
                );
            } else {
                supplierCard.getChildren().add(supplierHint);
            }
        });

        panel.getChildren().addAll(summaryCard, supplierCard);
        return panel;
    }

    private HBox summaryRow(String label, Label valueLabel) {
        HBox row = new HBox(); row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 0, 8, 0));
        row.setStyle("-fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");
        Label lbl = new Label(label); lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        row.getChildren().addAll(lbl, sp, valueLabel);
        return row;
    }

    private HBox detailRow(String label, String value) {
        HBox row = new HBox(8); row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 0, 5, 0));
        row.setStyle("-fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");
        Label lbl = new Label(label + ":"); lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8; -fx-min-width: 90;");
        Label val = new Label(value); val.setStyle("-fx-font-size: 12px; -fx-text-fill: #1e293b; -fx-font-weight: bold;");
        row.getChildren().addAll(lbl, val);
        return row;
    }

    // ─── History Table ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private VBox buildHistorySection() {
        VBox section = new VBox(12);

        HBox hdr = new HBox();
        Label title = new Label("Recent Purchase Orders");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        hdr.getChildren().add(title);

        TableView<Purchase> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(240);
        tv.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1;");
        tv.setItems(historyData);

        TableColumn<Purchase,String> poCol = new TableColumn<>("PO NUMBER");
        poCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPurchaseNumber()));
        poCol.setPrefWidth(130);

        TableColumn<Purchase,String> suppCol = new TableColumn<>("SUPPLIER");
        suppCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSupplierName()));
        suppCol.setPrefWidth(200);

        TableColumn<Purchase,String> dateCol = new TableColumn<>("DATE");
        dateCol.setCellValueFactory(d -> {
            var dt = d.getValue().getCreatedAt();
            return new SimpleStringProperty(dt != null ? dt.toLocalDate().toString() : "—");
        });
        dateCol.setPrefWidth(110);

        TableColumn<Purchase,String> amtCol = new TableColumn<>("TOTAL AMOUNT");
        amtCol.setCellValueFactory(d -> new SimpleStringProperty(KESFormatter.format(d.getValue().getTotalAmount())));
        amtCol.setPrefWidth(140);

        TableColumn<Purchase,String> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPaymentStatus()));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                String bg, fg;
                switch (s.toLowerCase()) {
                    case "paid"    -> { bg = "#dcfce7"; fg = "#16a34a"; }
                    case "partial" -> { bg = "#fef3c7"; fg = "#d97706"; }
                    default        -> { bg = "#fee2e2"; fg = "#dc2626"; }
                }
                Label badge = new Label(s.substring(0,1).toUpperCase() + s.substring(1));
                badge.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";-fx-font-size:11px;-fx-background-radius:20;-fx-padding:3 10;-fx-font-weight:bold;");
                setGraphic(badge); setText(null);
            }
        }); statusCol.setPrefWidth(100);

        TableColumn<Purchase,String> itemsCol = new TableColumn<>("ITEMS");
        itemsCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getItemCount() + " products"));
        itemsCol.setPrefWidth(100);

        tv.getColumns().addAll(poCol, suppCol, dateCol, amtCol, statusCol, itemsCol);
        section.getChildren().addAll(hdr, tv);
        return section;
    }

    private void loadHistory() {
        historyData.setAll(purchaseDAO.findAll());
    }

    // ─── Submit ───────────────────────────────────────────────────────────────

    private void submitOrder() {
        if (supplierCombo.getValue() == null) { showError("Please select a supplier."); return; }
        if (lineItems.isEmpty()) { showError("Please add at least one product to the order."); return; }

        Purchase p = new Purchase();
        p.setSupplierId(supplierCombo.getValue().getId());
        p.setSupplierName(supplierCombo.getValue().getName());
        p.setPaymentStatus("pending");
        p.setNotes(notesField.getText());

        // Set items
        List<PurchaseItem> items = new ArrayList<>(lineItems);
        double subtotal = items.stream().mapToDouble(PurchaseItem::getLineTotal).sum();
        double vat = subtotal * 0.16;
        p.setSubtotal(subtotal);
        p.setVatAmount(vat);
        p.setTotalAmount(subtotal + vat);

        var session = SessionManager.getInstance();
        if (session.getCurrentUser() != null) p.setCreatedBy(session.getCurrentUser().getId());

        // Pass items list to DAO (via Purchase object)
        p.setItems(items);

        Purchase saved = purchaseDAO.save(p);
        if (saved != null) {
            Alert ok = new Alert(Alert.AlertType.INFORMATION,
                "Purchase Order " + saved.getPurchaseNumber() + " submitted successfully!\n" +
                "Stock has been updated.", ButtonType.OK);
            ok.setHeaderText(null); ok.setTitle("Order Submitted");
            ok.showAndWait();

            // Reset form
            lineItems.clear();
            supplierCombo.setValue(null);
            poNumber.setText(purchaseDAO.generatePurchaseNumber());
            notesField.clear();
            refreshTotals();
            loadHistory();
        } else {
            showError("Failed to save purchase order. Please try again.");
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void refreshTotals() {
        double subtotal = lineItems.stream().mapToDouble(PurchaseItem::getLineTotal).sum();
        double vat = subtotal * 0.16;
        double total = subtotal + vat;
        subtotalLabel.setText(KESFormatter.format(subtotal));
        vatLabel.setText(KESFormatter.format(vat));
        totalLabel.setText(KESFormatter.format(total));
    }

    private VBox card() {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        return card;
    }

    private Label formLbl(String text) {
        Label l = new Label(text); l.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569; -fx-min-width: 100;"); return l;
    }

    private TextField inputField(String prompt, String val, int width) {
        TextField tf = new TextField(val); tf.setPromptText(prompt); tf.setPrefWidth(width);
        tf.setStyle("-fx-pref-height: 36px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;");
        return tf;
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText(null); a.setTitle("Validation Error"); a.show();
    }

    // ── Public accessor ────────────────────────────────────────────────────────
    public VBox getRoot() { return this; }
}
