package com.kaziflow.views;

import com.kaziflow.dao.CategoryDAO;
import com.kaziflow.dao.ProductDAO;
import com.kaziflow.dao.SupplierDAO;
import com.kaziflow.models.Product;
import com.kaziflow.models.Supplier;
import com.kaziflow.utils.AsyncTask;
import com.kaziflow.utils.KESFormatter;
import com.kaziflow.utils.SceneManager;
import com.kaziflow.utils.Toast;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

public class InventoryView {

    private VBox root;
    private TableView<Product> table;
    private ObservableList<Product> productData;
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();

    public InventoryView() {
        buildUI();
    }

    public VBox getRoot() { return root; }

    private void buildUI() {
        root = new VBox(0);
        root.setStyle("-fx-background-color: #f8fafc;");

        // ── Topbar ──
        HBox topbar = buildTopBar();

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));

        // ── Page Header ──
        VBox pageHeader = new VBox(4);
        Label title = new Label("Inventory Management");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label sub = new Label("Manage your products, track stock levels, and organize categories.");
        sub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        pageHeader.getChildren().addAll(title, sub);

        // ── Stats ──
        HBox statsRow = buildStatsRow();

        // ── Filter bar ──
        HBox filterBar = buildFilterBar();

        // ── Table card ──
        VBox tableCard = new VBox(0);
        tableCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1;");
        table = buildTable();
        tableCard.getChildren().add(table);

        content.getChildren().addAll(pageHeader, statsRow, filterBar, tableCard);
        root.getChildren().addAll(topbar, content);

        loadData();
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 24, 0, 24));
        bar.setPrefHeight(60);
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        TextField search = new TextField();
        search.setPromptText("Search products, orders, customers...");
        search.getStyleClass().add("search-box");
        search.setPrefWidth(320);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addSaleBtn = new Button("+ Add Sale");
        addSaleBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-size: 13px; -fx-background-radius: 8; -fx-pref-height: 36px; -fx-padding: 0 16; -fx-cursor: hand;");
        addSaleBtn.setOnAction(e -> com.kaziflow.utils.SceneManager.getInstance().navigateTo("sales"));

        Button addPurchBtn = new Button("Add Purchase");
        addPurchBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-size: 13px; -fx-pref-height: 36px; -fx-padding: 0 14; -fx-cursor: hand;");
        addPurchBtn.setOnAction(e -> com.kaziflow.utils.SceneManager.getInstance().navigateTo("purchases"));

        bar.getChildren().addAll(search, spacer, addPurchBtn, addSaleBtn);
        return bar;
    }

    private HBox buildStatsRow() {
        HBox row = new HBox(16);

        int total = productDAO.getTotalCount();
        double stockValue = productDAO.getTotalStockValue();
        int lowStock = productDAO.getLowStockCount();

        row.getChildren().addAll(
            statCard("Total Products", String.valueOf(total), "Items in stock", "#2563eb"),
            statCard("Total Stock Value", KESFormatter.formatShort(stockValue), "+2.4% vs last month", "#16a34a"),
            statCard("Low Stock Alerts", lowStock + " Items", "Requires attention", "#dc2626")
        );
        for (var child : row.getChildren()) HBox.setHgrow((Region) child, Priority.ALWAYS);
        return row;
    }

    private VBox statCard(String label, String value, String note, String color) {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-font-weight: bold;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label noteL = new Label(note);
        noteL.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        card.getChildren().addAll(lbl, val, noteL);
        return card;
    }

    private HBox buildFilterBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);

        TextField search = new TextField();
        search.setPromptText("Search by name, SKU, barcode...");
        search.getStyleClass().add("search-box");
        search.setPrefWidth(280);
        search.textProperty().addListener((obs, old, val) -> filterProducts(val));

        ComboBox<String> categoryFilter = new ComboBox<>();
        categoryFilter.getItems().add("All Categories");
        categoryDAO.findNames().forEach(categoryFilter.getItems()::add);
        categoryFilter.setValue("All Categories");
        categoryFilter.setPrefHeight(36);
        categoryFilter.setOnAction(e -> {
            String sel = categoryFilter.getValue();
            if (sel == null || sel.equals("All Categories")) loadData();
            else productData.setAll(productDAO.findAll().stream()
                .filter(p -> sel.equals(p.getCategoryName())).toList());
        });

        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All Status", "In Stock", "Low Stock", "Out of Stock");
        statusFilter.setValue("All Status");
        statusFilter.setPrefHeight(36);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button exportBtn = new Button("⬇ Export");
        exportBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 36px; -fx-padding: 0 14; -fx-cursor: hand; -fx-font-size: 13px;");
        exportBtn.setOnAction(e -> new com.kaziflow.services.ReportExportService().exportCSV(com.kaziflow.services.ReportExportService.ReportType.INVENTORY));

        Button bulkEditBtn = new Button("✎ Bulk Edit");
        bulkEditBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 36px; -fx-padding: 0 14; -fx-cursor: hand; -fx-font-size: 13px;");
        bulkEditBtn.setOnAction(e -> showBulkEditDialog());

        Button addBtn = new Button("+ Add New Product");
        addBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 36px; -fx-padding: 0 16; -fx-cursor: hand; -fx-font-size: 13px;");
        addBtn.setOnAction(e -> showAddEditDialog(null));

        Button batchBtn = new Button("📦 Batches");
        batchBtn.setStyle("-fx-background-color: white; -fx-border-color: #d97706; " +
            "-fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 36px; " +
            "-fx-padding: 0 12; -fx-font-size: 13px; -fx-text-fill: #d97706; " +
            "-fx-font-weight: bold; -fx-cursor: hand;");
        batchBtn.setTooltip(new Tooltip("Manage batches, lot numbers and expiry dates"));
        batchBtn.setOnAction(e -> com.kaziflow.utils.SceneManager.getInstance().navigateTo("batches"));
        batchBtn.setVisible(com.kaziflow.modules.ModuleRegistry.getInstance().isEnabled("batches"));
        batchBtn.setManaged(batchBtn.isVisible());

        Button stockTakeBtn = new Button("📋 Stock Take");
        stockTakeBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 36px; -fx-padding: 0 14; -fx-cursor: hand; -fx-font-size: 13px;");
        stockTakeBtn.setTooltip(new Tooltip("Physical inventory count — compare system stock against a real count"));
        stockTakeBtn.setOnAction(e -> com.kaziflow.views.StockTakeView.show(com.kaziflow.utils.SceneManager.getInstance().getStage()));

        bar.getChildren().addAll(search, categoryFilter, statusFilter, spacer, exportBtn, bulkEditBtn, stockTakeBtn, batchBtn, addBtn);
        return bar;
    }

    @SuppressWarnings("unchecked")
    private TableView<Product> buildTable() {
        TableView<Product> tv = new TableView<>();
        tv.setStyle("-fx-background-color: white; -fx-border-color: transparent;");
        tv.setPrefHeight(520);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Checkbox column
        TableColumn<Product, Void> checkCol = new TableColumn<>();
        checkCol.setPrefWidth(40);
        checkCol.setMaxWidth(40);

        // Product info column
        TableColumn<Product, String> nameCol = new TableColumn<>("PRODUCT INFO");
        nameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getName()));
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) { setGraphic(null); return; }
                Product p = getTableView().getItems().get(getIndex());
                VBox box = new VBox(2);
                Label n = new Label(p.getName());
                n.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
                Label sku = new Label(p.getSku() != null ? p.getSku() : "—");
                sku.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
                box.getChildren().addAll(n, sku);
                HBox row = new HBox(10, productAvatar(p, 38), box);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                setGraphic(row);
                setText(null);
            }
        });
        nameCol.setPrefWidth(300);   // wider: the photo/avatar takes ~48 px of the cell, so names need more room
        nameCol.setMinWidth(220);

        // Category column
        TableColumn<Product, String> catCol = new TableColumn<>("CATEGORY");
        catCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getCategoryName()));
        catCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String cat, boolean empty) {
                super.updateItem(cat, empty);
                if (empty || cat == null) { setGraphic(null); return; }
                Label badge = new Label(cat);
                badge.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #2563eb; -fx-font-size: 11px; -fx-background-radius: 20; -fx-padding: 3 10;");
                setGraphic(badge);
                setText(null);
            }
        });
        catCol.setPrefWidth(160);

        // Price column
        TableColumn<Product, String> priceCol = new TableColumn<>("PRICE DETAILS");
        priceCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(""));
        priceCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty) { setGraphic(null); return; }
                Product p = getTableView().getItems().get(getIndex());
                VBox box = new VBox(2);
                Label sell = new Label(KESFormatter.format(p.getSellingPrice()));
                sell.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
                Label cost = new Label("Cost: " + KESFormatter.formatNumber(p.getCostPrice()));
                cost.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
                if (com.kaziflow.utils.PermissionGuard.allowsAction(com.kaziflow.services.SpecialPermissions.VIEW_COST_PRICE)) box.getChildren().addAll(sell, cost);
                else box.getChildren().addAll(sell);
                setGraphic(box);
                setText(null);
            }
        });
        priceCol.setPrefWidth(130);

        // Stock level column
        TableColumn<Product, String> stockCol = new TableColumn<>("STOCK LEVEL");
        stockCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(""));
        stockCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty) { setGraphic(null); return; }
                Product p = getTableView().getItems().get(getIndex());
                VBox box = new VBox(2);

                String color = p.isOutOfStock() ? "#dc2626" : p.isLowStock() ? "#d97706" : "#16a34a";
                String unit = p.getUnit() != null ? p.getUnit() : "pcs";
                Label qty = new Label((int) p.getStockQuantity() + " " + unit.substring(0,1).toUpperCase() + unit.substring(1));
                qty.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: " + color + ";");

                Label minLabel = new Label("Min: " + (int) p.getMinStockLevel());
                minLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

                if (p.isOutOfStock() || p.isLowStock()) {
                    Label badge = new Label(p.getStockStatus());
                    badge.setStyle("-fx-background-color: " + (p.isOutOfStock() ? "#fee2e2" : "#fef3c7") + "; -fx-text-fill: " + color + "; -fx-font-size: 10px; -fx-background-radius: 4; -fx-padding: 1 6;");
                    box.getChildren().addAll(qty, badge);
                } else {
                    box.getChildren().addAll(qty, minLabel);
                }
                setGraphic(box);
                setText(null);
            }
        });
        stockCol.setPrefWidth(130);

        // Supplier column
        TableColumn<Product, String> supplierCol = new TableColumn<>("SUPPLIER");
        supplierCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue().getSupplierName() != null ? data.getValue().getSupplierName() : "—"));
        supplierCol.setPrefWidth(160);

        // Last updated
        TableColumn<Product, String> updatedCol = new TableColumn<>("LAST UPDATED");
        updatedCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty("—"));
        updatedCol.setPrefWidth(100);

        // Actions column
        TableColumn<Product, Void> actionsCol = new TableColumn<>("ACTIONS");
        actionsCol.setPrefWidth(100);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("✎");
            private final Button deleteBtn = new Button("🗑");
            private final HBox box = new HBox(6, editBtn, deleteBtn);

            {
                editBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-cursor: hand; -fx-font-size: 14px; -fx-border-color: transparent; -fx-padding: 2;");
                deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc2626; -fx-cursor: hand; -fx-font-size: 14px; -fx-border-color: transparent; -fx-padding: 2;");
                editBtn.setOnAction(e -> showAddEditDialog(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> {
                    Product p = getTableView().getItems().get(getIndex());
                    if (confirmDelete(p.getName())) {
                        productDAO.delete(p.getId());
                        com.kaziflow.services.AuditLog.log("PRODUCT_DELETED", "Product deleted: " + p.getName(), "inventory", p.getId());
                        SceneManager.getInstance().refreshView("dashboard");
                        loadData();
                    }
                });
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });

        tv.getColumns().addAll(checkCol, nameCol, catCol, priceCol, stockCol, supplierCol, updatedCol, actionsCol);
        productData = FXCollections.observableArrayList();
        tv.setItems(productData);
        return tv;
    }

    /** The product's photo in a rounded square, or a coloured letter when it has none. */
    private static javafx.scene.Node productAvatar(Product p, double size) {
        javafx.scene.layout.StackPane box = new javafx.scene.layout.StackPane();
        box.setMinSize(size, size); box.setPrefSize(size, size); box.setMaxSize(size, size);
        javafx.scene.image.ImageView img = com.kaziflow.utils.ProductImages.thumbView(p.getId(), size);
        if (img != null) {
            box.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(size, size);
            clip.setArcWidth(16); clip.setArcHeight(16);
            box.setClip(clip);
            box.getChildren().add(img);
        } else {
            String[] palette = {"#2563eb", "#7c3aed", "#0891b2", "#16a34a", "#d97706", "#db2777"};
            String name = p.getName() == null || p.getName().isBlank() ? "?" : p.getName();
            box.setStyle("-fx-background-color: " + palette[Math.floorMod(name.hashCode(), palette.length)] + "; -fx-background-radius: 8;");
            Label l = new Label(name.substring(0, 1).toUpperCase());
            l.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: " + (int) (size * 0.42) + "px;");
            box.getChildren().add(l);
        }
        return box;
    }

    private void loadData() {
        AsyncTask.run(
            () -> {
                var products = productDAO.findAll();
                // one batched query for all thumbnails, so scrolling/filtering never hits the database per row
                com.kaziflow.utils.ProductImages.prefetch(products.stream().map(Product::getId).toList());
                return products;
            },
            products -> productData.setAll(products),
            err -> Toast.error(SceneManager.getInstance().getStage(), "Load error", err)
        );
    }

    private void filterProducts(String query) {
        if (query == null || query.isBlank()) {
            loadData();
        } else {
            AsyncTask.run(
                () -> productDAO.search(query),
                products -> productData.setAll(products),
                err -> {}
            );
        }
    }

    private void showAddEditDialog(Product existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle(existing == null ? "Add New Product" : "Edit Product");
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        pane.setStyle("-fx-background-color: white;");
        pane.setPrefWidth(500);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.setPadding(new Insets(20));

        TextField nameField   = field("Product Name", existing != null ? existing.getName() : "");
        TextField skuField    = field("SKU", existing != null ? existing.getSku() : "");
        TextField priceField  = field("Selling Price (KES)", existing != null ? String.valueOf(existing.getSellingPrice()) : "");
        TextField costField   = field("Cost Price (KES)",  existing != null ? String.valueOf(existing.getCostPrice()) : "");
        // Cost price is restricted by the view_cost_price special action: hidden, and never overwritten on save.
        final boolean canCost = com.kaziflow.utils.PermissionGuard.allowsAction(com.kaziflow.services.SpecialPermissions.VIEW_COST_PRICE);
        if (!canCost) { costField.setText(""); costField.setPromptText("Restricted"); costField.setDisable(true); }
        TextField stockField  = field("Stock Quantity",    existing != null ? String.valueOf((int)existing.getStockQuantity()) : "0");
        TextField minField    = field("Min Stock Level",   existing != null ? String.valueOf((int)existing.getMinStockLevel()) : "0");
        TextField unitField   = field("Unit (pcs/kg/bags)", existing != null ? existing.getUnit() : "pcs");

        ComboBox<String> catCombo = new ComboBox<>();
        categoryDAO.findNames().forEach(catCombo.getItems()::add);
        if (catCombo.getItems().isEmpty()) catCombo.getItems().add("General");
        catCombo.setValue(existing != null && existing.getCategoryName() != null ? existing.getCategoryName() : catCombo.getItems().get(0));
        catCombo.setPrefWidth(200);

        ComboBox<String> suppCombo = new ComboBox<>();
        List<Supplier> suppliers = supplierDAO.findAll();
        suppliers.forEach(s -> suppCombo.getItems().add(s.getId() + ":" + s.getName()));
        suppCombo.setValue(existing != null && existing.getSupplierName() != null ? (existing.getSupplierId() + ":" + existing.getSupplierName()) : (suppCombo.getItems().isEmpty() ? "" : suppCombo.getItems().get(0)));
        suppCombo.setPrefWidth(200);

        form.addRow(0, lbl("Product Name"), nameField, lbl("SKU"), skuField);
        form.addRow(1, lbl("Selling Price"), priceField, lbl("Cost Price"), costField);
        form.addRow(2, lbl("Stock Qty"), stockField, lbl("Min Stock"), minField);
        form.addRow(3, lbl("Unit"), unitField, lbl("Category"), catCombo);
        form.addRow(4, lbl("Supplier"), suppCombo);
        ProductImagePicker picker = new ProductImagePicker(existing == null ? null : existing.getId());
        form.add(lbl("Photo"), 0, 5);
        form.add(picker.getNode(), 1, 5, 3, 1);

        pane.setContent(form);
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                Product p = existing != null ? existing : new Product();
                p.setName(nameField.getText());
                p.setSku(skuField.getText());
                p.setSellingPrice(parseDouble(priceField.getText()));
                if (canCost) p.setCostPrice(parseDouble(costField.getText()));
                p.setStockQuantity(parseDouble(stockField.getText()));
                p.setMinStockLevel(parseDouble(minField.getText()));
                p.setUnit(unitField.getText());
                p.setCategoryName(catCombo.getValue());
                p.setCategoryId(categoryDAO.getIdByName(catCombo.getValue()));

                if (!suppCombo.getValue().isEmpty()) {
                    String[] parts = suppCombo.getValue().split(":");
                    try { p.setSupplierId(Integer.parseInt(parts[0])); } catch (Exception ignored) {}
                }

                boolean ok = existing == null ? productDAO.save(p) : productDAO.update(p);
                if (ok && picker.hasChange()) {
                    int pid = existing != null ? existing.getId() : productDAO.findIdBySku(p.getSku());
                    String imgErr = pid > 0 ? picker.applyTo(pid) : "The photo could not be attached.";
                    if (imgErr != null) Toast.error(SceneManager.getInstance().getStage(), "Photo not saved", imgErr);
                }
                if (ok) {
                    com.kaziflow.services.AuditLog.logProductEdit(0, p.getName());
                    SceneManager.getInstance().refreshView("dashboard");
                    Toast.success(SceneManager.getInstance().getStage(),
                        existing == null ? "Product added" : "Product updated",
                        p.getName() + " saved successfully");
                    loadData();
                } else {
                    Toast.error(SceneManager.getInstance().getStage(), "Save failed", "Could not save product. Check all fields.");
                }
            }
        });
    }

    private boolean confirmDelete(String name) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + name + "?", ButtonType.YES, ButtonType.NO);
        com.kaziflow.utils.DialogUtils.makeResizable(alert);
        alert.setHeaderText(null);
        alert.setTitle("Confirm Delete");
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private TextField field(String prompt, String val) {
        TextField tf = new TextField(val);
        tf.setPromptText(prompt);
        tf.setPrefWidth(200);
        tf.setStyle("-fx-pref-height: 34px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 8;");
        return tf;
    }

    private Label lbl(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        return l;
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    /** Bulk price/category update applied to all currently visible (filtered) products. */
    private void showBulkEditDialog() {
        java.util.List<Product> visible = new java.util.ArrayList<>(productData);
        if (visible.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "No products to edit.", ButtonType.OK).showAndWait();
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle("Bulk Edit — " + visible.size() + " products");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(420);

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(14); form.setPadding(new Insets(20));

        // Operation choice
        ComboBox<String> opCombo = new ComboBox<>();
        opCombo.getItems().addAll(
            "Increase selling price by %",
            "Decrease selling price by %",
            "Set selling price markup over cost (%)",
            "Set minimum stock level"
        );
        opCombo.setValue("Increase selling price by %");
        opCombo.setPrefWidth(280);

        TextField valueField = new TextField("10");
        valueField.setPromptText("Value");
        valueField.setPrefWidth(280);

        Label previewLbl = new Label("Affects " + visible.size() + " products");
        previewLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        form.addRow(0, lbl("Operation"), opCombo);
        form.addRow(1, lbl("Value"), valueField);
        form.addRow(2, new Label(), previewLbl);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
            double val;
            try { val = Double.parseDouble(valueField.getText().trim()); }
            catch (Exception ex) { new Alert(Alert.AlertType.ERROR, "Invalid value.").showAndWait(); return; }

            String op = opCombo.getValue();
            int updated = 0;
            for (Product p : visible) {
                double newPrice = p.getSellingPrice();
                switch (op) {
                    case "Increase selling price by %"          -> newPrice = p.getSellingPrice() * (1 + val/100);
                    case "Decrease selling price by %"          -> newPrice = Math.max(p.getCostPrice(), p.getSellingPrice() * (1 - val/100));
                    case "Set selling price markup over cost (%)"-> newPrice = p.getCostPrice() * (1 + val/100);
                    default -> {}
                }
                if (op.equals("Set minimum stock level")) {
                    p.setMinStockLevel(val);
                } else {
                    p.setSellingPrice(Math.round(newPrice * 100.0) / 100.0);
                }
                if (productDAO.update(p)) updated++;
            }
            com.kaziflow.services.AuditLog.log("BULK_EDIT",
                "Bulk edit applied to " + updated + " products: " + op + " = " + val, "inventory", null);
            loadData();
            new Alert(Alert.AlertType.INFORMATION, updated + " products updated successfully.", ButtonType.OK).showAndWait();
        });
    }
}
