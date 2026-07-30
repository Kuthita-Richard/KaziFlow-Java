package com.kaziflow.views;

import com.kaziflow.dao.BranchDAO;
import com.kaziflow.dao.ProductDAO;
import com.kaziflow.models.Product;
import com.kaziflow.services.AuditLog;
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
import javafx.stage.Stage;

import java.util.List;

/**
 * Multi-branch management view.
 * Accessible via Settings → Branch Management button.
 * Shows branch list and stock transfer workflow.
 */
public class BranchView {

    private final BranchDAO branchDAO = new BranchDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final Stage stage;

    private ObservableList<String[]> branchData     = FXCollections.observableArrayList();
    private ObservableList<String[]> transferData   = FXCollections.observableArrayList();

    public BranchView() {
        stage = new Stage();
        stage.setTitle("Branch Management");
        stage.setMinWidth(1100);
        stage.setMinHeight(680);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab branchTab   = new Tab("🏢  Branches",        buildBranchesTab());
        Tab transferTab = new Tab("🔄  Stock Transfers",  buildTransfersTab());
        tabs.getTabs().addAll(branchTab, transferTab);

        javafx.scene.Scene scene = new javafx.scene.Scene(tabs, 1100, 680);
        stage.setScene(scene);
    }

    public void show() {
        stage.show();
        loadBranches();
        loadTransfers();
    }

    // ── Branches tab ──────────────────────────────────────────────────────

    private VBox buildBranchesTab() {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#f8fafc;");

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 24, 16, 24));
        header.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        Label title = new Label("Branches");
        title.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button addBtn = new Button("+ Add Branch");
        addBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:36px;-fx-padding:0 16;-fx-cursor:hand;");
        addBtn.setOnAction(e -> showBranchDialog(null));
        header.getChildren().addAll(title, sp, addBtn);

        // Table
        TableView<String[]> tv = new TableView<>(branchData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;");
        VBox.setVgrow(tv, Priority.ALWAYS);

        String[] cols = {"ID", "Branch Name", "Address", "Phone", "Manager", "Status"};
        for (int i = 0; i < cols.length; i++) {
            final int ci = i;
            TableColumn<String[], String> col = new TableColumn<>(cols[i]);
            col.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                ci < d.getValue().length ? d.getValue()[ci] : ""));
            if (i == 0) col.setPrefWidth(50);
            tv.getColumns().add(col);
        }

        TableColumn<String[], Void> actCol = new TableColumn<>("");
        actCol.setPrefWidth(90);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button editBtn = new Button("✎ Edit");
            {
                editBtn.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:5;-fx-background-radius:5;-fx-font-size:11px;-fx-cursor:hand;-fx-padding:3 10;");
                editBtn.setOnAction(e -> showBranchDialog(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v, empty); setGraphic(empty ? null : editBtn); }
        });
        tv.getColumns().add(actCol);

        root.getChildren().addAll(header, tv);
        return root;
    }

    private void loadBranches() {
        branchData.setAll(branchDAO.findAllBranches());
    }

    private void showBranchDialog(String[] existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Branch" : "Edit Branch");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color:white;");
        dialog.getDialogPane().setPrefWidth(420);

        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));
        TextField nameF    = field("Branch Name",   existing != null ? existing[1] : "");
        TextField addrF    = field("Address",        existing != null ? existing[2] : "");
        TextField phoneF   = field("Phone",          existing != null ? existing[3] : "");
        TextField managerF = field("Manager Name",   existing != null ? existing[4] : "");
        ComboBox<String> statusC = new ComboBox<>();
        statusC.getItems().addAll("active", "inactive");
        statusC.setValue(existing != null ? existing[5] : "active");

        form.addRow(0, lbl("Name"),    nameF);
        form.addRow(1, lbl("Address"), addrF);
        form.addRow(2, lbl("Phone"),   phoneF);
        form.addRow(3, lbl("Manager"), managerF);
        if (existing != null) form.addRow(4, lbl("Status"), statusC);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK) return;
            boolean ok = existing == null
                ? branchDAO.saveBranch(nameF.getText(), addrF.getText(), phoneF.getText(), managerF.getText())
                : branchDAO.updateBranch(Integer.parseInt(existing[0]), nameF.getText(), addrF.getText(),
                    phoneF.getText(), managerF.getText(), statusC.getValue());
            if (ok) {
                AuditLog.log(existing == null ? "BRANCH_CREATED" : "BRANCH_UPDATED",
                    (existing == null ? "New branch: " : "Updated branch: ") + nameF.getText(), "settings", null);
                loadBranches();
            }
        });
    }

    // ── Transfers tab ─────────────────────────────────────────────────────

    private VBox buildTransfersTab() {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#f8fafc;");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 24, 16, 24));
        header.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        Label title = new Label("Stock Transfers");
        title.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        // Stats
        Label pendingBadge = new Label();
        pendingBadge.setStyle("-fx-background-color:#fef3c7;-fx-text-fill:#d97706;-fx-font-weight:bold;-fx-background-radius:20;-fx-padding:4 12;-fx-font-size:12px;");
        int pending = branchDAO.getPendingTransferCount();
        pendingBadge.setText(pending + " Pending");

        Button newBtn = new Button("+ New Transfer");
        newBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:36px;-fx-padding:0 16;-fx-cursor:hand;");
        newBtn.setOnAction(e -> showTransferDialog());
        header.getChildren().addAll(title, sp, pendingBadge, newBtn);

        // Transfer table
        TableView<String[]> tv = new TableView<>(transferData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;");
        VBox.setVgrow(tv, Priority.ALWAYS);

        String[] cols = {"Transfer #", "From", "To", "Product", "Qty", "Status", "Notes", "Date"};
        for (int i = 0; i < cols.length - 1; i++) {
            final int ci = i;
            TableColumn<String[], String> col = new TableColumn<>(cols[i]);
            col.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                ci < d.getValue().length ? d.getValue()[ci] : ""));
            tv.getColumns().add(col);
        }

        // Status badge column
        TableColumn<String[], Void> statusCol = new TableColumn<>("Action");
        statusCol.setPrefWidth(130);
        statusCol.setCellFactory(c -> new TableCell<>() {
            private final Button recvBtn = new Button("✔ Receive");
            {
                recvBtn.setStyle("-fx-background-color:#dcfce7;-fx-text-fill:#16a34a;-fx-font-weight:bold;-fx-border-radius:5;-fx-background-radius:5;-fx-font-size:11px;-fx-cursor:hand;-fx-padding:4 10;");
                recvBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    if ("pending".equals(row[5]) || "dispatched".equals(row[5])) {
                        // Parse qty from row[4]
                        double qty = 0;
                        try { qty = Double.parseDouble(row[4]); } catch (Exception ignored) {}
                        // We need product_id — re-query by transfer_number
                        if (branchDAO.updateTransferStatus(row[0], "received")) {
                            AuditLog.log("TRANSFER_RECEIVED", "Transfer received: " + row[0], "inventory", null);
                            Toast.success(stage, "Transfer received", row[0] + " — " + row[3]);
                            loadTransfers();
                            SceneManager.getInstance().refreshView("inventory");
                            SceneManager.getInstance().refreshView("dashboard");
                        }
                    }
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                String status = getTableView().getItems().get(getIndex())[5];
                setGraphic("received".equals(status) ? null : recvBtn);
            }
        });
        tv.getColumns().add(statusCol);

        root.getChildren().addAll(header, tv);
        return root;
    }

    private void loadTransfers() {
        transferData.setAll(branchDAO.findAllTransfers());
    }

    private void showTransferDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Stock Transfer");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color:white;");
        dialog.getDialogPane().setPrefWidth(460);

        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));

        List<String[]> branches = branchDAO.findAllBranches();

        ComboBox<String> fromCombo = new ComboBox<>();
        ComboBox<String> toCombo   = new ComboBox<>();
        for (String[] b : branches) {
            fromCombo.getItems().add(b[0] + ":" + b[1]);
            toCombo.getItems().add(b[0] + ":" + b[1]);
        }
        if (!fromCombo.getItems().isEmpty()) fromCombo.setValue(fromCombo.getItems().get(0));
        if (toCombo.getItems().size() > 1)   toCombo.setValue(toCombo.getItems().get(1));
        fromCombo.setPrefWidth(300); toCombo.setPrefWidth(300);

        List<Product> products = productDAO.findAll();
        ComboBox<String> productCombo = new ComboBox<>();
        for (Product p : products) {
            productCombo.getItems().add(p.getId() + ":" + p.getName() + " (stock: " + (int) p.getStockQuantity() + ")");
        }
        if (!productCombo.getItems().isEmpty()) productCombo.setValue(productCombo.getItems().get(0));
        productCombo.setPrefWidth(300);

        TextField qtyField = new TextField("1"); qtyField.setPrefWidth(300);
        TextField notesField = new TextField(); notesField.setPromptText("Optional notes"); notesField.setPrefWidth(300);

        form.addRow(0, lbl("From Branch"),  fromCombo);
        form.addRow(1, lbl("To Branch"),    toCombo);
        form.addRow(2, lbl("Product"),      productCombo);
        form.addRow(3, lbl("Quantity"),     qtyField);
        form.addRow(4, lbl("Notes"),        notesField);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK) return;
            try {
                String[] fromParts = fromCombo.getValue().split(":", 2);
                String[] toParts   = toCombo.getValue().split(":", 2);
                String[] prodParts = productCombo.getValue().split(":", 2);
                int fromId = Integer.parseInt(fromParts[0]);
                int toId   = Integer.parseInt(toParts[0]);
                int prodId = Integer.parseInt(prodParts[0]);
                String prodName = prodParts[1].split("\\(")[0].trim();
                double qty = Double.parseDouble(qtyField.getText().trim());

                if (fromId == toId) {
                    new Alert(Alert.AlertType.WARNING, "Source and destination branches must differ.", ButtonType.OK).showAndWait();
                    return;
                }

                int userId = 1;
                try { userId = SessionManager.getInstance().getCurrentUser().getId(); } catch (Exception ignored) {}

                String result = branchDAO.createTransfer(
                    fromId, toId, fromParts[1], toParts[1],
                    prodId, prodName, qty, notesField.getText().trim(), userId);

                if (result == null) {
                    Toast.error(stage, "Transfer failed", "Could not create transfer.");
                } else if ("INSUFFICIENT_STOCK".equals(result)) {
                    Toast.error(stage, "Insufficient stock", "Not enough stock at source branch.");
                } else {
                    AuditLog.log("TRANSFER_CREATED", "Transfer " + result + ": " + prodName + " x" + (int) qty
                        + " from " + fromParts[1] + " to " + toParts[1], "inventory", null);
                    Toast.success(stage, "Transfer created", result);
                    loadTransfers();
                    SceneManager.getInstance().refreshView("inventory");
                }
            } catch (Exception ex) {
                Toast.error(stage, "Error", ex.getMessage());
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private TextField field(String prompt, String val) {
        TextField tf = new TextField(val); tf.setPromptText(prompt); tf.setPrefWidth(280);
        tf.setStyle("-fx-pref-height:34px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");
        return tf;
    }

    private Label lbl(String text) {
        Label l = new Label(text); l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;"); return l;
    }
}
