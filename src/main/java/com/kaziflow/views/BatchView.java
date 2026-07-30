package com.kaziflow.views;

import com.kaziflow.dao.BatchDAO;
import com.kaziflow.dao.ProductDAO;
import com.kaziflow.dao.SupplierDAO;
import com.kaziflow.models.Product;
import com.kaziflow.models.Supplier;
import com.kaziflow.services.AuditLog;
import com.kaziflow.utils.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * BatchView — Batch/Lot tracking with expiry management.
 *
 * Activated when batch_enabled = true in settings.
 * Used by: Pharmacy, Chemist, Agrovet, Grocery, Liquor, Butchery.
 *
 * Features:
 *   - View all batches across all products (filterable)
 *   - Add batches per product with expiry date validation
 *   - Expiry alerts: colour-coded rows (red = expired, amber = expiring soon)
 *   - FEFO dispensing info (which batch will be consumed next)
 *   - Configure alert threshold (30/60/90 days)
 */
public class BatchView {

    private BorderPane root;
    private final BatchDAO batchDAO     = new BatchDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private ObservableList<String[]> tableData = FXCollections.observableArrayList();
    private String currentFilter = "active";
    private VBox expiringCard;
    private VBox expiredCard;

    public BatchView() {
        buildUI();
        refreshAll();
    }

    public BorderPane getRoot() { return root; }

    // ── Build ─────────────────────────────────────────────────────────────

    private void buildUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #f8fafc;");
        root.setTop(buildHeader());
        root.setCenter(buildContent());
    }

    private VBox buildHeader() {
        VBox header = new VBox(0);

        // Title bar
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        VBox titleBlock = new VBox(2);
        Label title = new Label("📦  Batch & Expiry Management");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label sub = new Label("FEFO dispensing · Expiry alerts · Lot tracking");
        sub.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        titleBlock.getChildren().addAll(title, sub);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        // Alert threshold picker
        Label alertLbl = new Label("Alert before:");
        alertLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        ComboBox<String> alertBox = new ComboBox<>();
        alertBox.getItems().addAll("7 days", "14 days", "30 days", "60 days", "90 days");
        int alertDays = batchDAO.getAlertDays();
        alertBox.setValue(alertDays + " days");
        alertBox.setOnAction(e -> {
            int days = Integer.parseInt(alertBox.getValue().split(" ")[0]);
            batchDAO.setAlertDays(days);
            refreshAll();
        });

        Button addBtn = new Button("+ Add Batch");
        addBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold;" +
            "-fx-background-radius: 8; -fx-pref-height: 38px; -fx-padding: 0 18; -fx-cursor: hand; -fx-font-size: 13px;");
        addBtn.setOnAction(e -> showAddBatchDialog(null));

        bar.getChildren().addAll(titleBlock, sp, alertLbl, alertBox, addBtn);

        // Stats row
        HBox statsRow = new HBox(16);
        statsRow.setPadding(new Insets(14, 24, 14, 24));
        statsRow.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        int alertDaysVal = batchDAO.getAlertDays();
        int expiringSoon  = batchDAO.getExpiringSoonCount(alertDaysVal);
        int expired       = batchDAO.getExpiredCount();

        expiringCard = statCard("⚠  Expiring Soon",  String.valueOf(expiringSoon),
            "Within " + alertDaysVal + " days", "#d97706");
        expiredCard  = statCard("🚫 Expired (w/ stock)", String.valueOf(expired),
            "Needs disposal", "#dc2626");
        VBox fefoCard = statCard("✓  FEFO Active",
            "Enabled", "First-Expiry-First-Out", "#16a34a");

        statsRow.getChildren().addAll(expiringCard, expiredCard, fefoCard);
        header.getChildren().addAll(bar, statsRow);
        return header;
    }

    private VBox statCard(String label, String value, String note, String color) {
        VBox card = new VBox(4);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10;" +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-border-width: 1; -fx-padding: 12 20; -fx-min-width: 180px;");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        val.setUserData("value"); // tag so we can find it for updates
        Label noteLbl = new Label(note);
        noteLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px;");
        card.getChildren().addAll(lbl, val, noteLbl);
        return card;
    }

    private VBox buildContent() {
        VBox content = new VBox(0);
        VBox.setVgrow(content, Priority.ALWAYS);

        // Filter tabs
        HBox filterBar = new HBox(8);
        filterBar.setPadding(new Insets(12, 24, 12, 24));
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        ToggleGroup tg = new ToggleGroup();
        ToggleButton allBtn      = filterTab("All Batches",   "all",     tg);
        ToggleButton activeBtn   = filterTab("Active",        "active",  tg);
        ToggleButton expiringBtn = filterTab("⚠ Expiring",   "expiring",tg);
        ToggleButton expiredBtn  = filterTab("🚫 Expired",   "expired", tg);
        activeBtn.setSelected(true);

        tg.selectedToggleProperty().addListener((obs, old, nw) -> {
            if (nw == null) { activeBtn.setSelected(true); return; }
            currentFilter = (String) nw.getUserData();
            refreshTable();
        });

        // Mark expired button
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button markExpiredBtn = new Button("⟳ Update Expired");
        markExpiredBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0;" +
            "-fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 32px;" +
            "-fx-padding: 0 12; -fx-cursor: hand; -fx-font-size: 12px;");
        markExpiredBtn.setOnAction(e -> {
            int updated = batchDAO.updateExpiredBatches();
            refreshAll();
            Toast.success(SceneManager.getInstance().getStage(),
                "Updated", updated + " batches marked as expired");
        });
        filterBar.getChildren().addAll(allBtn, activeBtn, expiringBtn, expiredBtn, sp, markExpiredBtn);

        // Table
        TableView<String[]> table = buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        tableData.addListener((javafx.collections.ListChangeListener<String[]>) c -> {});

        content.getChildren().addAll(filterBar, table);
        return content;
    }

    private ToggleButton filterTab(String label, String data, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(label);
        btn.setToggleGroup(group);
        btn.setUserData(data);
        String base = "-fx-background-radius: 6; -fx-border-radius: 6; -fx-border-width: 1;" +
            "-fx-pref-height: 32px; -fx-padding: 0 14; -fx-font-size: 12px; -fx-cursor: hand;";
        String off  = base + "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-text-fill: #475569;";
        String on   = base + "-fx-background-color: #2563eb; -fx-border-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold;";
        btn.setStyle(off);
        btn.selectedProperty().addListener((obs, was, is) -> btn.setStyle(is ? on : off));
        return btn;
    }

    private TableView<String[]> buildTable() {
        TableView<String[]> tv = new TableView<>(tableData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color: white;");

        // Product + SKU
        TableColumn<String[], String> productCol = new TableColumn<>("Product");
        productCol.setPrefWidth(180);
        productCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[2]));
        productCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                String[] row = getTableView().getItems().get(getIndex());
                VBox cell = new VBox(2);
                Label name = new Label(row[2]);
                name.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");
                Label sku = new Label(row[3]);
                sku.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
                cell.getChildren().addAll(name, sku);
                setGraphic(cell);
            }
        });

        TableColumn<String[], String> batchCol = col("Batch #",    4, 110);
        TableColumn<String[], String> lotCol   = col("Lot #",      5, 90);
        TableColumn<String[], String> qtyCol   = col("Total Qty",  6, 80);
        TableColumn<String[], String> remCol   = col("Remaining",  7, 90);

        // Expiry date — colour coded
        TableColumn<String[], String> expiryCol = new TableColumn<>("Expiry Date");
        expiryCol.setPrefWidth(120);
        expiryCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[9]));
        expiryCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                String[] row = getTableView().getItems().get(getIndex());
                int daysLeft = 0;
                try { daysLeft = Integer.parseInt(row[11]); } catch (Exception ignored) {}
                setText(v);
                if ("expired".equals(row[11]) || daysLeft < 0) {
                    setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                } else if (daysLeft <= 7) {
                    setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                } else if (daysLeft <= 30) {
                    setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #16a34a;");
                }
            }
        });

        // Days left
        TableColumn<String[], String> daysCol = new TableColumn<>("Days Left");
        daysCol.setPrefWidth(90);
        daysCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[11]));
        daysCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setGraphic(null); return; }
                int days = 0;
                try { days = Integer.parseInt(v.trim()); } catch (Exception ignored) {}
                Label badge = new Label(v);
                String color = days < 0
                    ? "-fx-background-color:#fee2e2;-fx-text-fill:#dc2626;"
                    : days <= 7
                    ? "-fx-background-color:#fee2e2;-fx-text-fill:#dc2626;"
                    : days <= 30
                    ? "-fx-background-color:#fef3c7;-fx-text-fill:#d97706;"
                    : "-fx-background-color:#dcfce7;-fx-text-fill:#16a34a;";
                badge.setStyle(color + "-fx-font-size:10px;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:2 8;");
                setGraphic(badge);
            }
        });

        // Status badge
        TableColumn<String[], String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(90);
        statusCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[10]));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                Label badge = new Label(v.toUpperCase());
                String color = switch (v) {
                    case "active"   -> "-fx-background-color:#dcfce7;-fx-text-fill:#16a34a;";
                    case "expired"  -> "-fx-background-color:#fee2e2;-fx-text-fill:#dc2626;";
                    default         -> "-fx-background-color:#f1f5f9;-fx-text-fill:#64748b;";
                };
                badge.setStyle(color + "-fx-font-size:10px;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:2 8;");
                setGraphic(badge);
            }
        });

        // Actions
        TableColumn<String[], Void> actCol = new TableColumn<>("");
        actCol.setPrefWidth(130);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button viewBtn = btn("👁 Batches", "#2563eb");
            private final Button delBtn  = btn("🗑",         "#dc2626");
            private final HBox box = new HBox(6, viewBtn, delBtn);
            {
                viewBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    showProductBatches(Integer.parseInt(row[1]), row[2]);
                });
                delBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete batch " + row[4] + " for " + row[2] + "?\n" +
                        "This will reverse the remaining stock (" + row[6] + " units).",
                        ButtonType.YES, ButtonType.NO);
                    confirm.setHeaderText("Delete Batch");
                    confirm.showAndWait().ifPresent(r -> {
                        if (r == ButtonType.YES) {
                            // We need batch id from col 0 — but col 0 is id
                            // Find batch id from the table
                            String batchId = row[0];
                            if (batchDAO.deleteBatch(Integer.parseInt(batchId))) {
                                AuditLog.log("BATCH_DELETED",
                                    "Batch " + row[3] + " deleted for " + row[1], "inventory", null);
                                refreshAll();
                                Toast.success(SceneManager.getInstance().getStage(),
                                    "Deleted", "Batch " + row[4] + " removed");
                            }
                        }
                    });
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty); setGraphic(empty ? null : box);
            }
        });

        tv.getColumns().addAll(productCol, batchCol, lotCol, qtyCol, remCol,
            expiryCol, daysCol, statusCol, actCol);
        return tv;
    }

    // ── Add Batch Dialog ──────────────────────────────────────────────────

    private void showAddBatchDialog(Integer preselectedProductId) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Batch / Stock Intake");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(500);

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));

        // Product picker
        ComboBox<String> productCombo = new ComboBox<>();
        productCombo.setPrefWidth(340);
        List<Product> products = productDAO.findAll();
        products.forEach(p -> productCombo.getItems().add(p.getId() + ":" + p.getName() + " [" + p.getSku() + "]"));
        if (preselectedProductId != null) {
            products.stream().filter(p -> p.getId() == preselectedProductId)
                .findFirst().ifPresent(p ->
                    productCombo.setValue(p.getId() + ":" + p.getName() + " [" + p.getSku() + "]"));
        } else if (!productCombo.getItems().isEmpty()) {
            productCombo.setValue(productCombo.getItems().get(0));
        }

        // Supplier picker
        ComboBox<String> supplierCombo = new ComboBox<>();
        supplierCombo.setPrefWidth(340);
        supplierCombo.getItems().add("0:-- No supplier --");
        supplierDAO.findAll().forEach(s -> supplierCombo.getItems().add(s.getId() + ":" + s.getName()));
        supplierCombo.setValue(supplierCombo.getItems().get(0));

        TextField batchNumField = field("e.g. BN20241201", "");
        TextField lotNumField   = field("Lot / Reference number (optional)", "");
        TextField qtyField      = field("Quantity received", "");
        TextField costField     = field("Cost per unit (KES)", "");

        // Manufacture date
        TextField mfgField  = field("YYYY-MM-DD (optional)", "");

        // Expiry date — required
        TextField expField  = field("YYYY-MM-DD *", "");
        expField.setStyle(expField.getStyle() + "-fx-border-color: #f59e0b;");

        // Quick date buttons
        HBox quickDates = new HBox(6);
        String[] periods = {"3M", "6M", "1Y", "2Y", "3Y"};
        for (String p : periods) {
            Button qBtn = new Button(p);
            qBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569;" +
                "-fx-border-color: #e2e8f0; -fx-border-radius: 4; -fx-background-radius: 4;" +
                "-fx-font-size: 10px; -fx-padding: 2 8; -fx-cursor: hand;");
            qBtn.setOnAction(e -> {
                LocalDate date = LocalDate.now();
                date = switch (p) {
                    case "3M" -> date.plusMonths(3);
                    case "6M" -> date.plusMonths(6);
                    case "1Y" -> date.plusYears(1);
                    case "2Y" -> date.plusYears(2);
                    default   -> date.plusYears(3);
                };
                expField.setText(date.toString());
            });
            quickDates.getChildren().add(qBtn);
        }

        TextField notesField = field("Optional notes", "");

        form.addRow(0, lbl("Product *"),         productCombo);
        form.addRow(1, lbl("Supplier"),          supplierCombo);
        form.addRow(2, lbl("Batch Number *"),    batchNumField);
        form.addRow(3, lbl("Lot Number"),        lotNumField);
        form.addRow(4, lbl("Quantity *"),        qtyField);
        form.addRow(5, lbl("Cost Price/Unit"),   costField);
        form.addRow(6, lbl("Mfg Date"),          mfgField);
        form.addRow(7, lbl("Expiry Date *"),     expField);
        form.addRow(8, new Label(),              quickDates);
        form.addRow(9, lbl("Notes"),             notesField);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK) return;
            try {
                if (productCombo.getValue() == null || batchNumField.getText().isBlank()
                    || qtyField.getText().isBlank() || expField.getText().isBlank()) {
                    Toast.error(SceneManager.getInstance().getStage(),
                        "Missing fields", "Product, batch number, quantity and expiry date are required.");
                    return;
                }
                int productId = Integer.parseInt(productCombo.getValue().split(":")[0]);
                double qty    = Double.parseDouble(qtyField.getText().trim());
                double cost   = costField.getText().isBlank() ? 0 : Double.parseDouble(costField.getText().trim());
                String supplier = supplierCombo.getValue();
                Integer suppId = supplier.startsWith("0:") ? null
                    : Integer.parseInt(supplier.split(":")[0]);

                int userId = 1;
                try { userId = SessionManager.getInstance().getCurrentUser().getId(); }
                catch (Exception ignored) {}

                int result = batchDAO.addBatch(
                    productId, batchNumField.getText(), lotNumField.getText(),
                    qty, cost, mfgField.getText().trim(), expField.getText().trim(),
                    suppId, notesField.getText().trim(), userId);

                if (result == -2) {
                    Toast.error(SceneManager.getInstance().getStage(),
                        "Invalid date", "The expiry date is already in the past.");
                } else if (result < 0) {
                    Toast.error(SceneManager.getInstance().getStage(),
                        "Failed", "Could not add batch. Check for duplicate batch number.");
                } else {
                    AuditLog.log("BATCH_ADDED",
                        "Batch " + batchNumField.getText() + " added, qty=" + (int)qty,
                        "inventory", productId);
                    refreshAll();
                    SceneManager.getInstance().refreshView("inventory");
                    Toast.success(SceneManager.getInstance().getStage(),
                        "Batch added", "Stock increased by " + (int)qty + " units");
                }
            } catch (NumberFormatException ex) {
                Toast.error(SceneManager.getInstance().getStage(),
                    "Invalid input", "Check quantity and cost price are valid numbers.");
            }
        });
    }

    // ── Product Batch Detail Dialog ────────────────────────────────────────

    private void showProductBatches(int productId, String productName) {
        List<String[]> batches = batchDAO.getBatchesForProduct(productId);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Batches for: " + productName);
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(700);
        dialog.getDialogPane().setPrefHeight(500);

        VBox layout = new VBox(0);

        Label fefoNote = new Label("  ✓  Batches listed in FEFO order (earliest expiry will be dispensed first)");
        fefoNote.setStyle("-fx-background-color: #f0f9ff; -fx-text-fill: #0369a1;" +
            "-fx-font-size: 12px; -fx-padding: 8 16; -fx-font-weight: bold;");
        fefoNote.setMaxWidth(Double.MAX_VALUE);

        TableView<String[]> tv = new TableView<>(FXCollections.observableArrayList(batches));
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tv, Priority.ALWAYS);

        String[] headers = {"Batch #", "Lot #", "Total Qty", "Remaining",
            "Cost/Unit", "Mfg Date", "Expiry Date", "Status", "Days Left"};
        int[] idxs = {1, 2, 3, 4, 5, 6, 7, 8, 10};
        for (int i = 0; i < headers.length; i++) {
            final int ci = idxs[i];
            TableColumn<String[], String> c = new TableColumn<>(headers[i]);
            c.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                ci < d.getValue().length ? d.getValue()[ci] : ""));
            tv.getColumns().add(c);
        }

        Button addMoreBtn = new Button("+ Add Batch for this product");
        addMoreBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold;" +
            "-fx-background-radius: 8; -fx-pref-height: 36px; -fx-padding: 0 16; -fx-cursor: hand;");
        addMoreBtn.setOnAction(e -> {
            dialog.close();
            showAddBatchDialog(productId);
        });
        HBox btnRow = new HBox(addMoreBtn);
        btnRow.setPadding(new Insets(10, 16, 10, 16));

        layout.getChildren().addAll(fefoNote, tv, btnRow);
        dialog.getDialogPane().setContent(layout);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    // ── Refresh ────────────────────────────────────────────────────────────

    private void refreshAll() {
        batchDAO.updateExpiredBatches();
        refreshTable();
        // Update stat labels
        int alertDays    = batchDAO.getAlertDays();
        int expiringSoon = batchDAO.getExpiringSoonCount(alertDays);
        int expired      = batchDAO.getExpiredCount();
        if (expiringCard != null) updateStatCard(expiringCard, String.valueOf(expiringSoon));
        if (expiredCard  != null) updateStatCard(expiredCard,  String.valueOf(expired));
    }

    private void refreshTable() {
        String filter = currentFilter.equals("expiring")
            ? null : currentFilter.equals("all") ? null : currentFilter;
        AsyncTask.run(
            () -> {
                if ("expiring".equals(currentFilter)) {
                    return batchDAO.getExpiringSoon(batchDAO.getAlertDays()).stream()
                        .map(row -> new String[]{
                            // [0]=batch_id, [1]=product_id, [2]=product_name, [3]=sku,
                            // [4]=batch_number, [5]=lot, [6]=qty, [7]=remaining,
                            // [8]=cost, [9]=expiry_date, [10]=status, [11]=days_left
                            row[3],     // batch_id
                            row[0],     // product_id
                            row[1],     // product_name
                            row[2],     // sku
                            row[4],     // batch_number
                            "—",        // lot_number
                            row[5],     // quantity (remaining as qty)
                            row[5],     // remaining
                            "—",        // cost
                            row[6],     // expiry_date
                            "active",   // status
                            row[7]      // days_left
                        }).collect(java.util.stream.Collectors.toList());
                }
                return batchDAO.findAll("all".equals(currentFilter) ? null : currentFilter);
            },
            tableData::setAll,
            err -> {}
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void updateStatCard(VBox card, String newValue) {
        card.getChildren().stream()
            .filter(n -> n instanceof Label && "value".equals(n.getUserData()))
            .map(n -> (Label) n)
            .findFirst()
            .ifPresent(lbl -> lbl.setText(newValue));
    }

    private TableColumn<String[], String> col(String header, int idx, double width) {
        TableColumn<String[], String> c = new TableColumn<>(header);
        c.setPrefWidth(width);
        c.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            idx < d.getValue().length ? d.getValue()[idx] : ""));
        return c;
    }

    private Button btn(String label, String color) {
        Button b = new Button(label);
        b.setStyle("-fx-background-color: white; -fx-border-color: " + color +
            "; -fx-border-radius: 5; -fx-background-radius: 5; -fx-font-size: 11px;" +
            "-fx-text-fill: " + color + "; -fx-cursor: hand; -fx-padding: 3 8;");
        return b;
    }

    private TextField field(String prompt, String val) {
        TextField tf = new TextField(val);
        tf.setPromptText(prompt);
        tf.setPrefWidth(340);
        tf.setStyle("-fx-pref-height: 34px; -fx-background-color: white;" +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6;" +
            "-fx-font-size: 13px; -fx-padding: 0 8;");
        return tf;
    }

    private Label lbl(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        return l;
    }
}
