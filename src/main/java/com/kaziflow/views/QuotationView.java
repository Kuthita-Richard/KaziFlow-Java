package com.kaziflow.views;

import com.kaziflow.dao.ProductDAO;
import com.kaziflow.dao.QuotationDAO;
import com.kaziflow.models.Product;
import com.kaziflow.services.AuditLog;
import com.kaziflow.utils.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

public class QuotationView {

    private BorderPane root;
    private final QuotationDAO dao = new QuotationDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private ObservableList<String[]> tableData = FXCollections.observableArrayList();

    public QuotationView() {
        dao.ensureTables();
        buildUI();
        loadQuotations();
    }

    public BorderPane getRoot() { return root; }

    private void buildUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #f8fafc;");
        root.setTop(buildHeader());
        root.setCenter(buildTable());
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 24, 16, 24));
        header.setStyle("-fx-background-color: white; " +
            "-fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        VBox titleBlock = new VBox(2);
        Label title = new Label("📋  Quotations & Proforma Invoices");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label sub = new Label("Create price quotes, send to customers, convert to sales");
        sub.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        titleBlock.getChildren().addAll(title, sub);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Label countLbl = new Label(dao.getTotalCount() + " quotations");
        countLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");

        Button newBtn = new Button("+ New Quotation");
        newBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold;" +
            "-fx-background-radius: 8; -fx-pref-height: 38px; -fx-padding: 0 18; -fx-cursor: hand; -fx-font-size: 13px;");
        newBtn.setOnAction(e -> showQuotationBuilder(null));

        header.getChildren().addAll(titleBlock, sp, countLbl, newBtn);
        return header;
    }

    // ── Table ──────────────────────────────────────────────────────────────

    private TableView<String[]> buildTable() {
        TableView<String[]> tv = new TableView<>(tableData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color: white;");

        // Quote number
        TableColumn<String[], String> numCol = col("Quote #", 1, 100);

        // Customer
        TableColumn<String[], String> custCol = new TableColumn<>("Customer");
        custCol.setPrefWidth(180);
        custCol.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(d.getValue()[2]));
        custCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                String[] row = getTableView().getItems().get(getIndex());
                VBox cell = new VBox(2);
                Label name = new Label(row[2]);
                name.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");
                Label phone = new Label(row[3]);
                phone.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
                cell.getChildren().addAll(name, phone);
                setGraphic(cell);
            }
        });

        TableColumn<String[], String> totalCol = new TableColumn<>("Total (KES)");
        totalCol.setPrefWidth(130);
        totalCol.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(d.getValue()[4]));
        totalCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); return; }
                setText("KES " + KESFormatter.format(Double.parseDouble(v)));
                setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");
            }
        });

        // Status badge
        TableColumn<String[], String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(110);
        statusCol.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(d.getValue()[5]));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                Label badge = new Label(v.toUpperCase());
                String color = switch (v) {
                    case "converted" -> "-fx-background-color:#dcfce7;-fx-text-fill:#16a34a;";
                    case "accepted"  -> "-fx-background-color:#dbeafe;-fx-text-fill:#2563eb;";
                    case "sent"      -> "-fx-background-color:#fef3c7;-fx-text-fill:#d97706;";
                    case "declined"  -> "-fx-background-color:#fee2e2;-fx-text-fill:#dc2626;";
                    default          -> "-fx-background-color:#f1f5f9;-fx-text-fill:#64748b;";
                };
                badge.setStyle(color + "-fx-font-size:10px;-fx-font-weight:bold;" +
                    "-fx-background-radius:20;-fx-padding:3 10;");
                setGraphic(badge);
            }
        });

        TableColumn<String[], String> expiryCol = col("Expires", 6, 110);
        TableColumn<String[], String> dateCol   = col("Created", 7, 130);

        // Actions
        TableColumn<String[], Void> actCol = new TableColumn<>("Actions");
        actCol.setPrefWidth(240);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button viewBtn    = btn("👁 View",    "#2563eb");
            private final Button printBtn   = btn("🖨 Print",   "#475569");
            private final Button convertBtn = btn("→ Convert", "#16a34a");
            private final Button delBtn     = btn("🗑",         "#dc2626");
            private final HBox box = new HBox(5, viewBtn, printBtn, convertBtn, delBtn);

            {
                viewBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    showQuotationBuilder(row);
                });
                printBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    printQuotation(row);
                });
                convertBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    convertToSale(row);
                });
                delBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    if (dao.delete(Integer.parseInt(row[0]))) {
                        tableData.remove(getIndex());
                    }
                });
            }

            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                String[] row = getTableView().getItems().get(getIndex());
                convertBtn.setDisable("converted".equals(row[5]));
                setGraphic(box);
            }
        });

        tv.getColumns().addAll(numCol, custCol, totalCol, statusCol, expiryCol, dateCol, actCol);
        return tv;
    }

    // ── Quotation Builder Dialog ────────────────────────────────────────────

    private void showQuotationBuilder(String[] existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "New Quotation" : "Quotation " + existing[1]);
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(680);
        dialog.getDialogPane().setPrefHeight(640);

        VBox layout = new VBox(0);

        // Customer section
        GridPane custForm = new GridPane();
        custForm.setHgap(12); custForm.setVgap(10);
        custForm.setPadding(new Insets(16, 20, 12, 20));
        custForm.setStyle("-fx-background-color: white;-fx-border-color: transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");

        TextField nameF  = field("Customer Name *",  existing != null ? existing[2] : "");
        TextField phoneF = field("Phone",             existing != null ? existing[3] : "");
        TextField emailF = field("Email",             existing != null ? existing[4] : "");
        TextField notesF = field("Notes / Terms",     existing != null ? existing[9] : "");
        ComboBox<String> validBox = new ComboBox<>();
        validBox.getItems().addAll("7 days", "14 days", "30 days", "60 days", "90 days");
        validBox.setValue("7 days");

        custForm.addRow(0, lbl("Customer Name"), nameF, lbl("Phone"), phoneF);
        custForm.addRow(1, lbl("Email"), emailF, lbl("Valid For"), validBox);
        custForm.addRow(2, lbl("Notes"), notesF);

        // Line items section
        Label itemsLbl = new Label("Line Items");
        itemsLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b; -fx-padding: 12 20 6 20;");

        // Item entry row
        HBox itemEntry = new HBox(8);
        itemEntry.setPadding(new Insets(0, 20, 10, 20));
        itemEntry.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> productCombo = new ComboBox<>();
        productCombo.setPromptText("Select product...");
        productCombo.setPrefWidth(220);
        List<Product> products = productDAO.findAll();
        products.forEach(p -> productCombo.getItems().add(p.getId() + ":" + p.getName()));

        TextField descEntry = field("Description", "");
        descEntry.setPrefWidth(200);
        TextField qtyEntry  = field("Qty", "1");    qtyEntry.setPrefWidth(60);
        TextField priceEntry= field("Price", "");   priceEntry.setPrefWidth(90);
        TextField discEntry = field("Disc%", "0");  discEntry.setPrefWidth(60);
        Button addItemBtn = new Button("+ Add");
        addItemBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold;" +
            "-fx-background-radius: 6; -fx-pref-height: 32px; -fx-padding: 0 14; -fx-cursor: hand;");

        // Auto-fill price when product selected
        productCombo.setOnAction(e -> {
            String val = productCombo.getValue();
            if (val != null && val.contains(":")) {
                int pid = Integer.parseInt(val.split(":")[0]);
                products.stream().filter(p -> p.getId() == pid).findFirst().ifPresent(p -> {
                    descEntry.setText(p.getName());
                    priceEntry.setText(String.valueOf(p.getSellingPrice()));
                });
            }
        });

        itemEntry.getChildren().addAll(productCombo, descEntry, qtyEntry, priceEntry, discEntry, addItemBtn);

        // Items table
        ObservableList<String[]> itemData = FXCollections.observableArrayList();
        TableView<String[]> itemTable = new TableView<>(itemData);
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        itemTable.setPrefHeight(200);
        itemTable.setStyle("-fx-background-color: white;");

        TableColumn<String[], String> iDesc  = icol("Description", 0, 200);
        TableColumn<String[], String> iQty   = icol("Qty",         1, 60);
        TableColumn<String[], String> iPrice = icol("Unit Price",  2, 100);
        TableColumn<String[], String> iDisc  = icol("Disc%",       3, 60);
        TableColumn<String[], String> iTotal = icol("Total",       4, 100);
        TableColumn<String[], Void> iDel = new TableColumn<>("");
        iDel.setPrefWidth(50);
        iDel.setCellFactory(cc -> new TableCell<>() {
            private final Button del = new Button("✕");
            { del.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc2626; -fx-cursor: hand;");
              del.setOnAction(ev -> itemData.remove(getIndex())); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty); setGraphic(empty ? null : del);
            }
        });
        itemTable.getColumns().addAll(iDesc, iQty, iPrice, iDisc, iTotal, iDel);

        // Total labels
        Label subtotalLbl = new Label("Subtotal: KES 0.00");
        Label taxLbl      = new Label("VAT (16%): KES 0.00");
        Label totalLbl    = new Label("TOTAL: KES 0.00");
        totalLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1e293b;");
        VBox totals = new VBox(4, subtotalLbl, taxLbl, totalLbl);
        totals.setPadding(new Insets(10, 20, 10, 20));
        totals.setAlignment(Pos.CENTER_RIGHT);

        Runnable recalc = () -> {
            double sub = itemData.stream()
                .mapToDouble(r -> Double.parseDouble(r[4])).sum();
            double tax = sub * 0.16;
            subtotalLbl.setText("Subtotal: KES " + KESFormatter.format(sub));
            taxLbl.setText("VAT (16%): KES " + KESFormatter.format(tax));
            totalLbl.setText("TOTAL: KES " + KESFormatter.format(sub + tax));
        };

        addItemBtn.setOnAction(ev -> {
            try {
                String desc  = descEntry.getText().trim();
                double qty   = Double.parseDouble(qtyEntry.getText().trim());
                double price = Double.parseDouble(priceEntry.getText().trim());
                double disc  = Double.parseDouble(discEntry.getText().trim());
                double total = qty * price * (1 - disc / 100);
                if (desc.isEmpty()) return;
                itemData.add(new String[]{
                    desc, String.valueOf(qty), String.valueOf(price),
                    String.valueOf(disc), String.format("%.2f", total)
                });
                recalc.run();
                descEntry.clear(); qtyEntry.setText("1");
                priceEntry.clear(); discEntry.setText("0");
                productCombo.setValue(null);
            } catch (Exception ex) {
                Toast.error(SceneManager.getInstance().getStage(),
                    "Invalid", "Check quantity, price, and discount values.");
            }
        });

        itemData.addListener((javafx.collections.ListChangeListener<String[]>) c -> recalc.run());

        // Load existing items if viewing
        if (existing != null) {
            dao.getItems(Integer.parseInt(existing[0])).forEach(itemData::add);
            recalc.run();
        }

        layout.getChildren().addAll(custForm, itemsLbl, itemEntry, itemTable, totals);

        dialog.getDialogPane().setContent(layout);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK || nameF.getText().isBlank() || itemData.isEmpty()) return;
            int userId = 1;
            try { userId = SessionManager.getInstance().getCurrentUser().getId(); }
            catch (Exception ignored) {}

            int days = Integer.parseInt(validBox.getValue().split(" ")[0]);
            int qid = dao.createQuotation(nameF.getText().trim(), phoneF.getText().trim(),
                emailF.getText().trim(), notesF.getText().trim(), days, userId);

            if (qid > 0) {
                for (String[] item : itemData) {
                    dao.addItem(qid, null, item[0],
                        Double.parseDouble(item[1]),
                        Double.parseDouble(item[2]),
                        Double.parseDouble(item[3]));
                }
                AuditLog.log("QUOTATION_CREATED",
                    "New quotation for " + nameF.getText(), "quotations", qid);
                loadQuotations();
                Toast.success(SceneManager.getInstance().getStage(),
                    "Quotation created", "For " + nameF.getText());
            }
        });
    }

    // ── Actions ────────────────────────────────────────────────────────────

    private void printQuotation(String[] row) {
        String[] q = dao.getById(Integer.parseInt(row[0]));
        if (q == null) return;
        List<String[]> items = dao.getItems(Integer.parseInt(row[0]));

        StringBuilder sb = new StringBuilder();
        sb.append("================================================\n");
        sb.append("              PROFORMA INVOICE / QUOTATION\n");
        sb.append("================================================\n");
        sb.append("Quote #  : ").append(q[1]).append("\n");
        sb.append("Date     : ").append(q[11]).append("\n");
        sb.append("Valid for: ").append(q[10]).append(" days\n");
        sb.append("------------------------------------------------\n");
        sb.append("Customer : ").append(q[2]).append("\n");
        sb.append("Phone    : ").append(q[3]).append("\n");
        if (!q[4].isEmpty()) sb.append("Email    : ").append(q[4]).append("\n");
        sb.append("------------------------------------------------\n");
        sb.append(String.format("%-28s %6s %12s\n", "Item", "Qty", "Total"));
        sb.append("------------------------------------------------\n");
        for (String[] item : items) {
            sb.append(String.format("%-28s %6s  KES %8s\n",
                item[1].length() > 28 ? item[1].substring(0, 25) + "..." : item[1],
                item[2], item[5]));
        }
        sb.append("------------------------------------------------\n");
        sb.append(String.format("%-28s        KES %8s\n", "Subtotal", q[5]));
        sb.append(String.format("%-28s        KES %8s\n", "VAT (16%)", q[6]));
        sb.append(String.format("%-28s        KES %8s\n", "TOTAL DUE", q[7]));
        sb.append("================================================\n");
        if (!q[9].isEmpty()) sb.append("Notes: ").append(q[9]).append("\n");
        sb.append("This is not a tax invoice. Valid for ").append(q[10]).append(" days.\n");

        // Show in dialog
        TextArea ta = new TextArea(sb.toString());
        ta.setFont(javafx.scene.text.Font.font("Courier New", 12));
        ta.setEditable(false);
        ta.setPrefWidth(480);
        ta.setPrefHeight(500);

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Quotation " + q[1]);
        d.getDialogPane().setContent(ta);
        d.getDialogPane().getButtonTypes().addAll(
            new ButtonType("Print", ButtonBar.ButtonData.LEFT),
            ButtonType.CLOSE);
        d.showAndWait().ifPresent(res -> {
            if (res.getButtonData() == ButtonBar.ButtonData.LEFT) {
                try { javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
                    if (job != null && job.showPrintDialog(null)) {
                        job.printPage(ta); job.endJob();
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void convertToSale(String[] row) {
        int quotationId = Integer.parseInt(row[0]);
        List<String[]> items = dao.getItems(quotationId);

        // sale_items.product_id is NOT NULL — a quotation can contain free-text
        // lines with no linked product (e.g. a custom service charge), and those
        // can't become a real sale line. Surface that clearly rather than either
        // silently dropping them or letting the insert fail on a FK violation.
        List<String[]> linkedItems = new ArrayList<>();
        List<String> unlinkedDescriptions = new ArrayList<>();
        for (String[] item : items) {
            int productId = Integer.parseInt(item[6]);
            if (productId > 0) linkedItems.add(item);
            else unlinkedDescriptions.add(item[1]);
        }

        if (linkedItems.isEmpty()) {
            Toast.error(SceneManager.getInstance().getStage(), "Cannot Convert",
                "None of this quotation's line items are linked to an inventory product, so there's nothing to create a sale from.");
            return;
        }

        StringBuilder msg = new StringBuilder("Convert quotation " + row[1] + " to a completed sale?\n"
            + "This will create a real sale for " + linkedItems.size() + " item(s), reduce stock accordingly, "
            + "and mark the quotation as converted.");
        if (!unlinkedDescriptions.isEmpty()) {
            msg.append("\n\nNote: ").append(unlinkedDescriptions.size())
               .append(" line item(s) without a linked product will be excluded from the sale: ")
               .append(String.join(", ", unlinkedDescriptions));
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, msg.toString(), ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Convert to Sale");
        confirm.showAndWait().ifPresent(r -> {
            if (r != ButtonType.YES) return;

            com.kaziflow.models.Sale sale = new com.kaziflow.models.Sale();
            sale.setCustomerName(row[2]);
            double subtotal = 0, total = 0;
            List<com.kaziflow.models.SaleItem> saleItems = new ArrayList<>();
            for (String[] item : linkedItems) {
                com.kaziflow.models.SaleItem si = new com.kaziflow.models.SaleItem();
                si.setProductId(Integer.parseInt(item[6]));
                si.setProductName(item[1]);
                si.setQuantity(Double.parseDouble(item[2]));
                si.setUnitPrice(Double.parseDouble(item[3]));
                si.setDiscount(Double.parseDouble(item[4]));
                si.setLineTotal(Double.parseDouble(item[5]));
                saleItems.add(si);
                subtotal += Double.parseDouble(item[3]) * Double.parseDouble(item[2]);
                total += Double.parseDouble(item[5]);
            }
            double vatAmount = Math.max(total - subtotal, 0);
            sale.setItems(saleItems);
            sale.setSubtotal(subtotal);
            sale.setVatAmount(vatAmount);
            sale.setTotalAmount(total);
            sale.setAmountPaid(total);
            sale.setChangeAmount(0);
            sale.setPaymentMethod("cash");
            try { sale.setServedBy(com.kaziflow.utils.SessionManager.getInstance().getCurrentUser().getId()); } catch (Exception ignored) {}

            com.kaziflow.models.Sale saved = new com.kaziflow.dao.SaleDAO().save(sale);
            if (saved == null || saved.getId() <= 0) {
                Toast.error(SceneManager.getInstance().getStage(), "Conversion Failed",
                    "Could not create the sale. The quotation was left unchanged.");
                return;
            }

            dao.updateStatus(quotationId, "converted");
            AuditLog.log("QUOTATION_CONVERTED",
                "Quotation " + row[1] + " converted to sale " + saved.getSaleNumber(), "quotations", quotationId);
            loadQuotations();
            Toast.success(SceneManager.getInstance().getStage(),
                "Converted", "Sale " + saved.getSaleNumber() + " created for " + KESFormatter.format(total) + ".");
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void loadQuotations() {
        AsyncTask.run(dao::findAll, tableData::setAll, err -> {});
    }

    private TableColumn<String[], String> col(String header, int idx, double width) {
        TableColumn<String[], String> c = new TableColumn<>(header);
        c.setPrefWidth(width);
        c.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(
                idx < d.getValue().length ? d.getValue()[idx] : ""));
        return c;
    }

    private TableColumn<String[], String> icol(String header, int idx, double width) {
        TableColumn<String[], String> c = new TableColumn<>(header);
        c.setPrefWidth(width);
        c.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(
                idx < d.getValue().length ? d.getValue()[idx] : ""));
        return c;
    }

    private TextField field(String prompt, String val) {
        TextField tf = new TextField(val);
        tf.setPromptText(prompt);
        tf.setPrefWidth(220);
        tf.setStyle("-fx-pref-height:32px;-fx-background-color:white;-fx-border-color:#e2e8f0;" +
            "-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");
        return tf;
    }

    private Label lbl(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;");
        return l;
    }

    private Button btn(String label, String color) {
        Button b = new Button(label);
        b.setStyle("-fx-background-color:white;-fx-border-color:" + color +
            ";-fx-border-radius:5;-fx-background-radius:5;-fx-font-size:11px;" +
            "-fx-text-fill:" + color + ";-fx-cursor:hand;-fx-padding:3 7;");
        return b;
    }
}
