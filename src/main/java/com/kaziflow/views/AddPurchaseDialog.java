package com.kaziflow.views;

import com.kaziflow.dao.ProductDAO;
import com.kaziflow.dao.PurchaseDAO;
import com.kaziflow.dao.SupplierDAO;
import com.kaziflow.models.Product;
import com.kaziflow.models.Purchase;
import com.kaziflow.models.PurchaseItem;
import com.kaziflow.models.Supplier;
import com.kaziflow.utils.KESFormatter;
import com.kaziflow.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Full Purchase Order form: supplier selection, line items, totals, payment.
 * Opens as a modal window. On save, calls PurchaseDAO.save() which also
 * increments stock quantities for each product.
 */
public class AddPurchaseDialog {

    private Stage stage;
    private final ObservableList<PurchaseItem> lineItems = FXCollections.observableArrayList();
    private final PurchaseDAO purchaseDAO = new PurchaseDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final ProductDAO productDAO = new ProductDAO();

    private ComboBox<String> supplierCombo;
    private DatePicker dueDatePicker;
    private TextArea notesArea;
    private VBox itemsBox;
    private Label subtotalLbl, vatLbl, totalLbl;
    private ComboBox<String> paymentStatusCombo;
    private TextField amountPaidField;
    private Runnable onSuccess;

    public AddPurchaseDialog(Runnable onSuccess) {
        this.onSuccess = onSuccess;
        buildStage();
    }

    public void show() {
        stage.show();
    }

    private void buildStage() {
        stage = new Stage();
        stage.setTitle("New Purchase Order");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setWidth(900);
        stage.setHeight(720);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f8fafc;");

        // ── Top bar ──
        HBox topBar = new HBox(16);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(16, 24, 16, 24));
        topBar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(2);
        Label title = new Label("New Purchase Order");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label sub = new Label("Record stock received from supplier");
        sub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        titleBox.getChildren().addAll(title, sub);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-pref-height: 36px; -fx-padding: 0 16; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> stage.close());

        Button saveBtn = new Button("💾  Save Purchase Order");
        saveBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 36px; -fx-padding: 0 18; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> savePurchase());

        topBar.getChildren().addAll(titleBox, cancelBtn, saveBtn);
        root.setTop(topBar);

        // ── Main content ──
        HBox content = new HBox(20);
        content.setPadding(new Insets(20));

        // Left: Supplier info + items
        VBox leftPanel = buildLeftPanel();
        HBox.setHgrow(leftPanel, Priority.ALWAYS);

        // Right: Totals & payment
        VBox rightPanel = buildRightPanel();
        rightPanel.setPrefWidth(280);

        content.getChildren().addAll(leftPanel, rightPanel);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #f8fafc; -fx-background: #f8fafc;");
        root.setCenter(scroll);

        Scene scene = new Scene(root);
        var cssUrl = getClass().getResource("/styles/main.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        stage.setScene(scene);
    }

    private VBox buildLeftPanel() {
        VBox panel = new VBox(16);

        // ── Supplier + Date card ──
        VBox headerCard = card("Order Details");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12);

        // Supplier combo
        supplierCombo = new ComboBox<>();
        List<Supplier> suppliers = supplierDAO.findAll();
        suppliers.forEach(s -> supplierCombo.getItems().add(s.getId() + ":" + s.getName()));
        if (!supplierCombo.getItems().isEmpty()) supplierCombo.setValue(supplierCombo.getItems().get(0));
        supplierCombo.setPrefWidth(300);
        supplierCombo.setStyle(fieldStyle());

        // Due date
        dueDatePicker = new DatePicker(LocalDate.now().plusDays(30));
        dueDatePicker.setPrefWidth(200);

        // Payment status
        paymentStatusCombo = new ComboBox<>();
        paymentStatusCombo.getItems().addAll("pending", "partial", "paid");
        paymentStatusCombo.setValue("pending");
        paymentStatusCombo.setPrefWidth(200);

        // Amount paid field
        amountPaidField = new TextField("0");
        amountPaidField.setStyle(fieldStyle());
        amountPaidField.setPrefWidth(200);

        grid.addRow(0, lbl("Supplier *"), supplierCombo, lbl("Due Date"), dueDatePicker);
        grid.addRow(1, lbl("Payment Status"), paymentStatusCombo, lbl("Amount Paid (KES)"), amountPaidField);

        notesArea = new TextArea();
        notesArea.setPromptText("Additional notes about this purchase order...");
        notesArea.setPrefRowCount(2);
        notesArea.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 8;");

        headerCard.getChildren().addAll(grid, lbl("Notes"), notesArea);
        panel.getChildren().add(headerCard);

        // ── Line Items ──
        VBox itemsCard = new VBox(0);
        itemsCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1;");

        HBox itemsHeader = new HBox(12);
        itemsHeader.setAlignment(Pos.CENTER_LEFT);
        itemsHeader.setPadding(new Insets(14, 16, 14, 16));
        itemsHeader.setStyle("-fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");
        Label itemsTitle = new Label("Line Items");
        itemsTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        HBox.setHgrow(itemsTitle, Priority.ALWAYS);

        Button addItemBtn = new Button("+ Add Item");
        addItemBtn.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 6 14; -fx-cursor: hand; -fx-border-color: transparent;");
        addItemBtn.setOnAction(e -> showAddItemDialog());

        itemsHeader.getChildren().addAll(itemsTitle, addItemBtn);

        // Table header
        HBox tableHeader = new HBox();
        tableHeader.setPadding(new Insets(8, 16, 8, 16));
        tableHeader.setStyle("-fx-background-color: #f8fafc;");
        tableHeader.getChildren().addAll(
            colHeader("PRODUCT", 280),
            colHeader("QTY", 80),
            colHeader("UNIT COST (KES)", 140),
            colHeader("TOTAL (KES)", 120),
            colHeader("", 50)
        );

        itemsBox = new VBox(0);

        itemsCard.getChildren().addAll(itemsHeader, tableHeader, itemsBox);

        // Empty state
        Label emptyLbl = new Label("No items added yet. Click '+ Add Item' to start.");
        emptyLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px; -fx-padding: 20;");
        itemsCard.getChildren().add(emptyLbl);

        lineItems.addListener((javafx.collections.ListChangeListener<PurchaseItem>) c -> {
            itemsCard.getChildren().remove(emptyLbl);
            refreshItemsBox();
            updateTotals();
            if (lineItems.isEmpty()) itemsCard.getChildren().add(emptyLbl);
        });

        panel.getChildren().add(itemsCard);
        return panel;
    }

    private VBox buildRightPanel() {
        VBox panel = new VBox(16);

        // Totals card
        VBox totalsCard = card("Order Summary");

        subtotalLbl = new Label("KES 0.00");
        vatLbl = new Label("KES 0.00");
        totalLbl = new Label("KES 0.00");
        totalLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        totalsCard.getChildren().addAll(
            summaryRow("Subtotal", subtotalLbl, false),
            summaryRow("VAT (16%)", vatLbl, false),
            divider(),
            summaryRow("TOTAL", totalLbl, true)
        );

        panel.getChildren().add(totalsCard);

        // Supplier info preview
        VBox supplierCard = card("Supplier Details");
        Label supplierInfo = new Label("Select a supplier above to see details.");
        supplierInfo.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-wrap-text: true;");
        supplierInfo.setWrapText(true);
        supplierCard.getChildren().add(supplierInfo);

        supplierCombo.setOnAction(e -> {
            String val = supplierCombo.getValue();
            if (val != null && val.contains(":")) {
                try {
                    int id = Integer.parseInt(val.split(":")[0]);
                    Supplier s = supplierDAO.findById(id);
                    if (s != null) {
                        supplierInfo.setText(
                            s.getName() + "\n" +
                            (s.getPhone() != null ? "☎ " + s.getPhone() + "\n" : "") +
                            (s.getEmail() != null ? "✉ " + s.getEmail() + "\n" : "") +
                            "Payment Terms: " + s.getPaymentTerms() + " days\n" +
                            "Outstanding: " + KESFormatter.format(s.getOutstandingBalance())
                        );
                        supplierInfo.setStyle("-fx-text-fill: #1e293b; -fx-font-size: 12px;");
                        dueDatePicker.setValue(LocalDate.now().plusDays(s.getPaymentTerms()));
                    }
                } catch (Exception ignored) {}
            }
        });

        panel.getChildren().add(supplierCard);
        return panel;
    }

    private void showAddItemDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Item");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(440);

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));

        // Product search combo
        ComboBox<String> productCombo = new ComboBox<>();
        productCombo.setEditable(true);
        productCombo.setPrefWidth(300);
        List<Product> products = productDAO.findAll();
        products.forEach(p -> productCombo.getItems().add(p.getId() + ":" + p.getName() + " [" + p.getSku() + "]"));
        if (!productCombo.getItems().isEmpty()) productCombo.setValue(productCombo.getItems().get(0));

        TextField qtyField = new TextField("1");
        qtyField.setPromptText("Quantity");
        qtyField.setPrefWidth(100);
        qtyField.setStyle(fieldStyle());

        TextField costField = new TextField("0");
        costField.setPromptText("Unit cost");
        costField.setPrefWidth(160);
        costField.setStyle(fieldStyle());

        // Auto-fill cost price from product
        productCombo.setOnAction(e -> {
            String val = productCombo.getValue();
            if (val != null && val.contains(":")) {
                try {
                    int id = Integer.parseInt(val.split(":")[0]);
                    products.stream().filter(p -> p.getId() == id).findFirst().ifPresent(p ->
                        costField.setText(String.valueOf(p.getCostPrice()))
                    );
                } catch (Exception ignored) {}
            }
        });

        // Live line total preview
        Label lineTotalLbl = new Label("KES 0.00");
        lineTotalLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #2563eb;");
        Runnable calcTotal = () -> {
            try {
                double qty = Double.parseDouble(qtyField.getText());
                double cost = Double.parseDouble(costField.getText());
                lineTotalLbl.setText(KESFormatter.format(qty * cost));
            } catch (Exception ignored) {}
        };
        qtyField.textProperty().addListener((o, old, v) -> calcTotal.run());
        costField.textProperty().addListener((o, old, v) -> calcTotal.run());

        form.addRow(0, lbl("Product *"), productCombo);
        form.addRow(1, lbl("Quantity *"), qtyField, lbl("Unit Cost (KES) *"), costField);
        form.addRow(2, lbl("Line Total"), lineTotalLbl);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                String val = productCombo.getValue();
                if (val == null || val.isEmpty()) return;
                try {
                    double qty  = Double.parseDouble(qtyField.getText().trim());
                    double cost = Double.parseDouble(costField.getText().trim());
                    if (qty <= 0 || cost < 0) { showValidationError("Quantity must be > 0 and cost ≥ 0."); return; }

                    int prodId = Integer.parseInt(val.split(":")[0]);
                    String prodName = val.substring(val.indexOf(":") + 1).replaceAll(" \\[.*]", "");

                    PurchaseItem item = new PurchaseItem(prodId, prodName, qty, cost);
                    lineItems.add(item);
                } catch (NumberFormatException ex) {
                    showValidationError("Please enter valid numbers for quantity and cost.");
                }
            }
        });
    }

    private void refreshItemsBox() {
        itemsBox.getChildren().clear();
        for (int i = 0; i < lineItems.size(); i++) {
            PurchaseItem item = lineItems.get(i);
            final int idx = i;

            HBox row = new HBox();
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 16, 10, 16));
            row.setStyle("-fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");

            Label name = new Label(item.getProductName());
            name.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
            name.setPrefWidth(280);

            // Inline qty editor
            TextField qtyEdit = new TextField(String.valueOf((int) item.getQuantity()));
            qtyEdit.setPrefWidth(70);
            qtyEdit.setStyle("-fx-pref-height: 30px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 12px; -fx-padding: 0 6;");
            qtyEdit.textProperty().addListener((o, old, v) -> {
                try {
                    item.setQuantity(Double.parseDouble(v));
                    updateTotals();
                    refreshTotalsOnly();
                } catch (Exception ignored) {}
            });

            // Inline cost editor
            TextField costEdit = new TextField(String.valueOf(item.getUnitCost()));
            costEdit.setPrefWidth(120);
            costEdit.setStyle(qtyEdit.getStyle());
            costEdit.textProperty().addListener((o, old, v) -> {
                try {
                    item.setUnitCost(Double.parseDouble(v));
                    updateTotals();
                    refreshTotalsOnly();
                } catch (Exception ignored) {}
            });

            Label lineTotal = new Label(KESFormatter.formatNumber(item.getLineTotal()));
            lineTotal.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
            lineTotal.setPrefWidth(120);

            Button removeBtn = new Button("✕");
            removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc2626; -fx-cursor: hand; -fx-font-size: 13px; -fx-border-color: transparent;");
            removeBtn.setOnAction(e -> lineItems.remove(idx));

            row.getChildren().addAll(name, qtyEdit, costEdit, lineTotal, removeBtn);
            itemsBox.getChildren().add(row);
        }
    }

    private void refreshTotalsOnly() {
        double subtotal = lineItems.stream().mapToDouble(PurchaseItem::getLineTotal).sum();
        double vat = subtotal * 0.16;
        subtotalLbl.setText(KESFormatter.format(subtotal));
        vatLbl.setText(KESFormatter.format(vat));
        totalLbl.setText(KESFormatter.format(subtotal + vat));
    }

    private void updateTotals() { refreshTotalsOnly(); }

    private void savePurchase() {
        // Validation
        if (supplierCombo.getValue() == null || supplierCombo.getValue().isEmpty()) {
            showValidationError("Please select a supplier."); return;
        }
        if (lineItems.isEmpty()) {
            showValidationError("Please add at least one item."); return;
        }

        double subtotal = lineItems.stream().mapToDouble(PurchaseItem::getLineTotal).sum();
        double vat = subtotal * 0.16;
        double total = subtotal + vat;
        double amountPaid = parseDouble(amountPaidField.getText());

        Purchase purchase = new Purchase();
        try { purchase.setSupplierId(Integer.parseInt(supplierCombo.getValue().split(":")[0])); }
        catch (Exception e) { showValidationError("Invalid supplier."); return; }

        purchase.setSubtotal(subtotal);
        purchase.setVatAmount(vat);
        purchase.setTotalAmount(total);
        purchase.setAmountPaid(amountPaid);
        purchase.setBalance(total - amountPaid);
        purchase.setPaymentStatus(paymentStatusCombo.getValue());
        purchase.setDueDate(dueDatePicker.getValue());
        purchase.setNotes(notesArea.getText());
        purchase.setItems(new ArrayList<>(lineItems));

        int userId = 1;
        try { userId = SessionManager.getInstance().getCurrentUser().getId(); } catch (Exception ignored) {}
        purchase.setReceivedBy(userId);

        Purchase saved = purchaseDAO.save(purchase);
        if (saved != null) {
            Alert ok = new Alert(Alert.AlertType.INFORMATION,
                "Purchase Order " + saved.getPurchaseNumber() + " saved.\n" +
                "Stock has been updated for all items.", ButtonType.OK);
            ok.setTitle("Purchase Saved"); ok.setHeaderText(null); ok.showAndWait();
            if (onSuccess != null) onSuccess.run();
            stage.close();
        } else {
            showValidationError("Failed to save purchase order. Please try again.");
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private VBox card(String titleText) {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        if (!titleText.isEmpty()) {
            Label t = new Label(titleText);
            t.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
            card.getChildren().add(t);
        }
        return card;
    }

    private HBox summaryRow(String labelText, Label valueLabel, boolean large) {
        HBox row = new HBox(); row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-size: " + (large ? "15" : "13") + "px;" + (large ? "-fx-font-weight: bold;" : "") + " -fx-text-fill: #475569;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        if (!large) valueLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        row.getChildren().addAll(lbl, sp, valueLabel);
        return row;
    }

    private Region divider() {
        Region r = new Region(); r.setPrefHeight(1); r.setMaxWidth(Double.MAX_VALUE);
        r.setStyle("-fx-background-color: #e2e8f0;");
        VBox.setMargin(r, new Insets(4, 0, 4, 0));
        return r;
    }

    private Label colHeader(String text, double width) {
        Label l = new Label(text); l.setPrefWidth(width);
        l.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        return l;
    }

    private Label lbl(String text) {
        Label l = new Label(text); l.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;"); return l;
    }

    private String fieldStyle() {
        return "-fx-pref-height: 36px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 10;";
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private void showValidationError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle("Validation Error"); a.setHeaderText(null); a.show();
    }
}
