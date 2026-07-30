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

/**
 * Full Purchase Order creation screen.
 * Supplier selection → product search → line items → totals → save.
 */
public class NewPurchaseView {

    private VBox root;

    // State
    private Supplier selectedSupplier;
    private final ObservableList<PurchaseItem> cartItems = FXCollections.observableArrayList();
    private final List<Product> allProducts;

    // UI refs
    private Label supplierLabel;
    private TableView<PurchaseItem> itemsTable;
    private Label subtotalLabel, vatLabel, totalLabel;
    private ComboBox<String> productSearch;
    private TextField qtyField, unitCostField;
    private Button addItemBtn, saveBtn;
    private Label poNumberLabel;

    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final ProductDAO productDAO   = new ProductDAO();
    private final PurchaseDAO purchaseDAO = new PurchaseDAO();

    private final Runnable onSaved; // callback to refresh parent

    public NewPurchaseView(Runnable onSaved) {
        this.onSaved = onSaved;
        this.allProducts = productDAO.findAll();
        buildUI();
    }

    public VBox getRoot() { return root; }

    // ─────────────────────────────────────────────────────────────────────────

    private void buildUI() {
        root = new VBox(0);
        root.setStyle("-fx-background-color: #f8fafc;");

        root.getChildren().addAll(buildTopBar(), buildContent());
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(2);
        Label title = new Label("New Purchase Order");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        poNumberLabel = new Label("PO# will be auto-generated");
        poNumberLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        titleBox.getChildren().addAll(title, poNumberLabel);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button cancelBtn = new Button("✕ Cancel");
        cancelBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 38px; -fx-padding: 0 16; -fx-cursor: hand; -fx-font-size: 13px;");
        cancelBtn.setOnAction(e -> { if (onSaved != null) onSaved.run(); });

        saveBtn = new Button("✓ Save Purchase Order");
        saveBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 38px; -fx-padding: 0 18; -fx-cursor: hand; -fx-font-size: 13px;");
        saveBtn.setDisable(true);
        saveBtn.setOnAction(e -> savePurchaseOrder());

        bar.getChildren().addAll(titleBox, sp, cancelBtn, saveBtn);
        bar.setSpacing(10);
        return bar;
    }

    private HBox buildContent() {
        HBox row = new HBox(20);
        row.setPadding(new Insets(24));

        // ── Left: main form ──
        VBox left = new VBox(20);
        HBox.setHgrow(left, Priority.ALWAYS);
        left.getChildren().addAll(buildSupplierCard(), buildItemEntryCard(), buildItemsTable());

        // ── Right: summary + meta ──
        VBox right = buildRightPanel();
        right.setPrefWidth(300);

        row.getChildren().addAll(left, right);
        return row;
    }

    // ─── Supplier Card ──────────────────────────────────────────────────────

    private VBox buildSupplierCard() {
        VBox card = card();

        HBox header = new HBox(10); header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Supplier Details"); title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        header.getChildren().add(title);

        // Supplier search combo
        ComboBox<String> supplierCombo = new ComboBox<>();
        supplierCombo.setPromptText("Search and select supplier...");
        supplierCombo.setEditable(true);
        supplierCombo.setPrefWidth(340);
        supplierCombo.setPrefHeight(36);

        List<Supplier> suppliers = supplierDAO.findAll();
        suppliers.forEach(s -> supplierCombo.getItems().add(s.getId() + " — " + s.getName() + " (" + s.getPhone() + ")"));

        supplierLabel = new Label("No supplier selected");
        supplierLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        supplierCombo.valueProperty().addListener((obs, o, v) -> {
            if (v != null && v.contains(" — ")) {
                int id;
                try { id = Integer.parseInt(v.split(" — ")[0].trim()); } catch (Exception ex) { return; }
                selectedSupplier = suppliers.stream().filter(s -> s.getId() == id).findFirst().orElse(null);
                if (selectedSupplier != null) {
                    supplierLabel.setText(selectedSupplier.getName() + "  •  " + selectedSupplier.getPhone() + "  •  " + selectedSupplier.getPaymentTerms() + " days terms");
                    supplierLabel.setStyle("-fx-text-fill: #16a34a; -fx-font-size: 12px; -fx-font-weight: bold;");
                    refreshSaveButton();
                }
            }
        });

        card.getChildren().addAll(header, supplierCombo, supplierLabel);
        return card;
    }

    // ─── Item Entry Card ────────────────────────────────────────────────────

    private VBox buildItemEntryCard() {
        VBox card = card();

        Label title = new Label("Add Items to Order");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        HBox entryRow = new HBox(12); entryRow.setAlignment(Pos.CENTER_LEFT);

        // Product search
        productSearch = new ComboBox<>();
        productSearch.setEditable(true);
        productSearch.setPromptText("Search product by name or SKU...");
        productSearch.setPrefWidth(300);
        productSearch.setPrefHeight(36);
        allProducts.forEach(p -> productSearch.getItems().add(p.getId() + " — " + p.getName() + " [" + p.getSku() + "]"));

        // Auto-fill unit cost when product selected
        productSearch.valueProperty().addListener((obs, o, v) -> {
            if (v != null && v.contains(" — ")) {
                int id;
                try { id = Integer.parseInt(v.split(" — ")[0].trim()); } catch (Exception ex) { return; }
                allProducts.stream().filter(p -> p.getId() == id).findFirst().ifPresent(p ->
                    unitCostField.setText(String.valueOf((int) p.getCostPrice()))
                );
            }
        });

        // Quantity
        Label qtyLbl = new Label("Qty"); qtyLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        qtyField = new TextField("1");
        qtyField.setPrefWidth(70); qtyField.setPrefHeight(36);
        qtyField.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;");

        // Unit cost
        Label costLbl = new Label("Unit Cost (KES)"); costLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        unitCostField = new TextField("0");
        unitCostField.setPrefWidth(110); unitCostField.setPrefHeight(36);
        unitCostField.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;");

        addItemBtn = new Button("+ Add to Order");
        addItemBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-pref-height: 36px; -fx-padding: 0 16; -fx-cursor: hand; -fx-font-size: 13px;");
        addItemBtn.setOnAction(e -> addItemToCart());

        entryRow.getChildren().addAll(
            productSearch,
            new VBox(2, qtyLbl, qtyField),
            new VBox(2, costLbl, unitCostField),
            addItemBtn
        );
        entryRow.setAlignment(Pos.BOTTOM_LEFT);

        card.getChildren().addAll(title, entryRow);
        return card;
    }

    // ─── Items Table ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private VBox buildItemsTable() {
        VBox card = card();

        HBox header = new HBox(); header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Order Items");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button clearBtn = new Button("✕ Clear All");
        clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc2626; -fx-cursor: hand; -fx-font-size: 12px; -fx-border-color: transparent;");
        clearBtn.setOnAction(e -> { cartItems.clear(); refreshTotals(); });
        header.getChildren().addAll(title, sp, clearBtn);

        itemsTable = new TableView<>();
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        itemsTable.setPrefHeight(220);
        itemsTable.setPlaceholder(new Label("No items added. Search for products above."));
        itemsTable.setStyle("-fx-background-color: white;");
        itemsTable.setItems(cartItems);

        TableColumn<PurchaseItem,String> nameCol = new TableColumn<>("PRODUCT");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProductName()));
        nameCol.setPrefWidth(240);

        TableColumn<PurchaseItem,String> qtyCol = new TableColumn<>("QTY");
        qtyCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf((int) d.getValue().getQuantity())));
        qtyCol.setCellFactory(c -> editableQtyCell());
        qtyCol.setPrefWidth(80);

        TableColumn<PurchaseItem,String> unitCol = new TableColumn<>("UNIT COST");
        unitCol.setCellValueFactory(d -> new SimpleStringProperty(KESFormatter.formatNumber(d.getValue().getUnitCost())));
        unitCol.setPrefWidth(110);

        TableColumn<PurchaseItem,String> totalCol = new TableColumn<>("SUBTOTAL");
        totalCol.setCellValueFactory(d -> new SimpleStringProperty(KESFormatter.format(d.getValue().getLineTotal())));
        totalCol.setPrefWidth(120);

        TableColumn<PurchaseItem,Void> removeCol = new TableColumn<>("");
        removeCol.setPrefWidth(50);
        removeCol.setCellFactory(c -> new TableCell<>() {
            private final Button btn = new Button("✕");
            { btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc2626; -fx-cursor: hand; -fx-font-size: 13px; -fx-border-color: transparent;");
              btn.setOnAction(e -> { cartItems.remove(getTableView().getItems().get(getIndex())); refreshTotals(); }); }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v, empty); setGraphic(empty ? null : btn); }
        });

        itemsTable.getColumns().addAll(nameCol, qtyCol, unitCol, totalCol, removeCol);

        card.getChildren().addAll(header, itemsTable);
        return card;
    }

    private TableCell<PurchaseItem,String> editableQtyCell() {
        return new TableCell<>() {
            private final TextField tf = new TextField();
            {
                tf.setPrefWidth(60);
                tf.setStyle("-fx-font-size: 13px;");
                tf.setOnAction(e -> commitEdit(tf.getText()));
                tf.focusedProperty().addListener((obs, o, focused) -> { if (!focused) commitEdit(tf.getText()); });
            }
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                tf.setText(v);
                setGraphic(tf);
            }
            @Override public void commitEdit(String newVal) {
                super.commitEdit(newVal);
                try {
                    double qty = Double.parseDouble(newVal);
                    if (qty <= 0) return;
                    getTableView().getItems().get(getIndex()).setQuantity(qty);
                    refreshTotals();
                    getTableView().refresh();
                } catch (NumberFormatException ignored) {}
            }
        };
    }

    // ─── Right Panel ────────────────────────────────────────────────────────

    private VBox buildRightPanel() {
        VBox panel = new VBox(16);

        // Order summary
        VBox summaryCard = card();
        Label summaryTitle = new Label("Order Summary");
        summaryTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        subtotalLabel = new Label("KES 0.00");
        vatLabel      = new Label("KES 0.00");
        totalLabel    = new Label("KES 0.00");

        summaryCard.getChildren().addAll(
            summaryTitle,
            summaryRow("Subtotal:", subtotalLabel),
            summaryRow("VAT (16%):", vatLabel),
            divider(),
            summaryRowBold("TOTAL:", totalLabel)
        );

        // Order details
        VBox detailsCard = card();
        Label detailsTitle = new Label("Order Details");
        detailsTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        // Expected delivery date
        Label delivLabel = new Label("Expected Delivery");
        delivLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        DatePicker delivDate = new DatePicker(LocalDate.now().plusDays(7));
        delivDate.setPrefWidth(230); delivDate.setPrefHeight(34);

        // Payment terms
        Label termsLabel = new Label("Payment Terms");
        termsLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        ComboBox<String> termsCombo = new ComboBox<>();
        termsCombo.getItems().addAll("Immediate", "Net 7", "Net 15", "Net 30", "Net 60");
        termsCombo.setValue("Net 30"); termsCombo.setPrefWidth(230); termsCombo.setPrefHeight(34);

        // Status
        Label statusLabel = new Label("Order Status");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("draft", "pending", "confirmed", "received");
        statusCombo.setValue("confirmed"); statusCombo.setPrefWidth(230); statusCombo.setPrefHeight(34);

        // Notes
        Label notesLabel = new Label("Notes / Remarks");
        notesLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Optional notes for this purchase order...");
        notesArea.setPrefRowCount(3); notesArea.setPrefWidth(230);
        notesArea.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 12px;");

        detailsCard.getChildren().addAll(detailsTitle, delivLabel, delivDate, termsLabel, termsCombo, statusLabel, statusCombo, notesLabel, notesArea);

        // Wire save button metadata
        saveBtn.setUserData(new Object[]{ delivDate, termsCombo, statusCombo, notesArea });

        panel.getChildren().addAll(summaryCard, detailsCard);
        return panel;
    }

    // ─── Logic ───────────────────────────────────────────────────────────────

    private void addItemToCart() {
        String productVal = productSearch.getValue();
        if (productVal == null || productVal.isBlank()) {
            showError("Select a product first.");
            return;
        }

        int productId;
        try { productId = Integer.parseInt(productVal.split(" — ")[0].trim()); }
        catch (Exception e) { showError("Invalid product selection."); return; }

        Product product = allProducts.stream().filter(p -> p.getId() == productId).findFirst().orElse(null);
        if (product == null) { showError("Product not found."); return; }

        double qty, cost;
        try { qty = Double.parseDouble(qtyField.getText()); }
        catch (Exception e) { showError("Invalid quantity."); return; }
        try { cost = Double.parseDouble(unitCostField.getText()); }
        catch (Exception e) { showError("Invalid unit cost."); return; }

        if (qty <= 0) { showError("Quantity must be greater than 0."); return; }
        if (cost < 0)  { showError("Unit cost cannot be negative."); return; }

        // Check if product already in cart
        for (PurchaseItem existing : cartItems) {
            if (existing.getProductId() == productId) {
                existing.setQuantity(existing.getQuantity() + qty);
                itemsTable.refresh();
                refreshTotals();
                return;
            }
        }

        PurchaseItem item = new PurchaseItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(qty);
        item.setUnitCost(cost);

        cartItems.add(item);
        refreshTotals();
        refreshSaveButton();

        // Reset entry fields
        productSearch.setValue(null);
        qtyField.setText("1");
        unitCostField.setText("0");
    }

    private void refreshTotals() {
        double subtotal = cartItems.stream().mapToDouble(PurchaseItem::getLineTotal).sum();
        double vat = subtotal * 0.16;
        double total = subtotal + vat;

        subtotalLabel.setText(KESFormatter.format(subtotal));
        vatLabel.setText(KESFormatter.format(vat));
        totalLabel.setText(KESFormatter.format(total));
        totalLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        refreshSaveButton();
    }

    private void refreshSaveButton() {
        saveBtn.setDisable(selectedSupplier == null || cartItems.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private void savePurchaseOrder() {
        if (selectedSupplier == null || cartItems.isEmpty()) return;

        Object[] meta = (Object[]) saveBtn.getUserData();
        DatePicker delivDate    = meta != null ? (DatePicker)     meta[0] : null;
        ComboBox<String> terms  = meta != null ? (ComboBox<String>)meta[1] : null;
        ComboBox<String> status = meta != null ? (ComboBox<String>)meta[2] : null;
        TextArea notes          = meta != null ? (TextArea)        meta[3] : null;

        double subtotal = cartItems.stream().mapToDouble(PurchaseItem::getLineTotal).sum();
        double vat = subtotal * 0.16;
        double total = subtotal + vat;

        Purchase po = new Purchase();
        po.setSupplierId(selectedSupplier.getId());
        po.setSupplierName(selectedSupplier.getName());
        po.setSubtotal(subtotal);
        po.setVatAmount(vat);
        po.setTotalAmount(total);
        po.setStatus(status != null ? status.getValue() : "confirmed");
        po.setPaymentTerms(terms != null ? terms.getValue() : "Net 30");
        po.setNotes(notes != null ? notes.getText() : "");
        po.setDueDate(delivDate != null ? delivDate.getValue() : LocalDate.now().plusDays(7));
        po.setCreatedBy(SessionManager.getInstance().getCurrentUser() != null
            ? SessionManager.getInstance().getCurrentUser().getId() : 1);
        po.setItems(new ArrayList<>(cartItems));

        Purchase saved = purchaseDAO.save(po);
        if (saved != null) {
            Alert ok = new Alert(Alert.AlertType.INFORMATION,
                "Purchase Order " + saved.getPurchaseNumber() + " saved!\nTotal: " + KESFormatter.format(total),
                ButtonType.OK);
            ok.setTitle("Purchase Order Created"); ok.setHeaderText(null);
            ok.showAndWait();
            if (onSaved != null) onSaved.run();
        } else {
            showError("Failed to save purchase order. Please try again.");
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private VBox card() {
        VBox c = new VBox(12);
        c.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        return c;
    }

    private HBox summaryRow(String label, Label valueLabel) {
        HBox row = new HBox(); row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));
        Label lbl = new Label(label); lbl.setStyle("-fx-text-fill: #475569; -fx-font-size: 13px;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        valueLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
        row.getChildren().addAll(lbl, sp, valueLabel);
        return row;
    }

    private HBox summaryRowBold(String label, Label valueLabel) {
        HBox row = summaryRow(label, valueLabel);
        row.getChildren().stream().filter(n -> n instanceof Label).forEach(n ->
            ((Label) n).setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;")
        );
        return row;
    }

    private Region divider() {
        Region r = new Region(); r.setPrefHeight(1);
        r.setStyle("-fx-background-color: #e2e8f0; -fx-margin: 4 0;");
        return r;
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.setHeaderText(null); a.setTitle("Validation"); a.show();
    }
}
