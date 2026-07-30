package com.kaziflow.views;

import com.kaziflow.dao.StockTakeDAO;
import com.kaziflow.utils.KESFormatter;
import com.kaziflow.utils.Toast;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

/**
 * StockTakeView — physical inventory count workflow (Phase 10E).
 * Opened from Inventory's toolbar. Two screens in one dialog:
 *   1) History list of past counts + "Start New Count"
 *   2) The active count grid: system qty vs. a counted-qty field per product,
 *      auto-saved per row, with a "Complete" step that applies variances.
 */
public class StockTakeView {

    private final StockTakeDAO stockTakeDAO = new StockTakeDAO();
    private Stage dialogStage;
    private BorderPane root;

    public static void show(Stage owner) {
        new StockTakeView().open(owner);
    }

    private void open(Stage owner) {
        stockTakeDAO.ensureTables();

        dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle("Stock Take");

        root = new BorderPane();
        root.setStyle("-fx-background-color: #f8fafc;");
        showHistoryScreen();

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 720, 560);
        dialogStage.setScene(scene);
        dialogStage.show();
    }

    // ── Screen 1: history + start new ─────────────────────────────────────

    private void showHistoryScreen() {
        VBox content = new VBox(14);
        content.setPadding(new Insets(20));

        HBox header = new HBox();
        Label title = new Label("Stock Take History");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button newBtn = new Button("+ Start New Count");
        newBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 36px; -fx-padding: 0 16; -fx-cursor: hand;");
        newBtn.setOnAction(e -> startNewCount());
        header.getChildren().addAll(title, spacer, newBtn);

        TableView<String[]> table = new TableView<>();
        table.getColumns().add(col("Reference", 1, 160));
        table.getColumns().add(col("Status", 2, 100));
        table.getColumns().add(col("Started", 3, 160));
        table.getColumns().add(col("Completed", 4, 160));
        table.getColumns().add(col("Products", 5, 90));
        table.getItems().addAll(stockTakeDAO.findAll());
        VBox.setVgrow(table, Priority.ALWAYS);

        table.setRowFactory(tv -> {
            javafx.scene.control.TableRow<String[]> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty() && e.getClickCount() == 1) {
                    String[] item = row.getItem();
                    openCountScreen(Integer.parseInt(item[0]), "completed".equals(item[2]));
                }
            });
            return row;
        });

        content.getChildren().addAll(header, table);
        root.setCenter(content);
    }

    private TableColumn<String[], String> col(String label, int index, double width) {
        TableColumn<String[], String> c = new TableColumn<>(label);
        c.setPrefWidth(width);
        c.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
            data.getValue()[index] != null ? data.getValue()[index] : "—"));
        return c;
    }

    private void startNewCount() {
        TextInputDialog notesDialog = new TextInputDialog();
        notesDialog.setTitle("Start New Stock Take");
        notesDialog.setHeaderText("Snapshot current stock levels for every product and begin counting.");
        notesDialog.setContentText("Notes (optional):");
        notesDialog.showAndWait().ifPresent(notes -> {
            int id = stockTakeDAO.startNewCount(notes);
            if (id > 0) {
                openCountScreen(id, false);
            } else {
                Toast.error(dialogStage, "Could Not Start Count", "Please try again.");
            }
        });
    }

    // ── Screen 2: counting grid ─────────────────────────────────────────

    private void openCountScreen(int stockTakeId, boolean readOnly) {
        VBox content = new VBox(14);
        content.setPadding(new Insets(20));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Button backBtn = new Button("← Back");
        backBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 13px;");
        backBtn.setOnAction(e -> showHistoryScreen());
        Label title = new Label(readOnly ? "Completed Count — Read Only" : "Counting in Progress");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button completeBtn = new Button("✓ Complete & Apply Adjustments");
        completeBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 36px; -fx-padding: 0 16; -fx-cursor: hand;");
        completeBtn.setDisable(readOnly);
        completeBtn.setOnAction(e -> confirmComplete(stockTakeId));

        header.getChildren().addAll(backBtn, title, spacer, completeBtn);

        List<Object[]> items = stockTakeDAO.getItems(stockTakeId);
        TableView<Object[]> table = new TableView<>();

        TableColumn<Object[], String> nameCol = new TableColumn<>("Product");
        nameCol.setPrefWidth(240);
        nameCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty((String) d.getValue()[2]));

        TableColumn<Object[], String> sysCol = new TableColumn<>("System Qty");
        sysCol.setPrefWidth(110);
        sysCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            KESFormatter.formatNumber((double) d.getValue()[3])));

        TableColumn<Object[], Void> countedCol = new TableColumn<>("Counted Qty");
        countedCol.setPrefWidth(150);
        countedCol.setCellFactory(tc -> new javafx.scene.control.TableCell<>() {
            private final TextField field = new TextField();
            {
                field.setPromptText("Enter count");
                field.setStyle("-fx-pref-width: 100px;");
                field.setEditable(!readOnly);
                field.setOnAction(e -> commit());
                field.focusedProperty().addListener((obs, was, is) -> { if (!is) commit(); });
            }
            private void commit() {
                if (readOnly || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) return;
                Object[] row = getTableView().getItems().get(getIndex());
                try {
                    double counted = Double.parseDouble(field.getText());
                    int itemId = (int) row[0];
                    stockTakeDAO.saveCountedQty(itemId, counted);
                    // Update the in-memory row too, not just the DB — otherwise the Variance
                    // column (which reads from this same array) stays stale until the whole
                    // screen is reopened, even though the save genuinely succeeded.
                    row[4] = counted;
                    getTableView().refresh();
                } catch (NumberFormatException ignored) {
                    // leave uncounted rather than saving garbage — completeStockTake() skips nulls anyway
                }
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Object[] row = getTableView().getItems().get(getIndex());
                Double counted = (Double) row[4];
                field.setText(counted != null ? KESFormatter.formatNumber(counted) : "");
                setGraphic(field);
            }
        });

        TableColumn<Object[], String> varianceCol = new TableColumn<>("Variance");
        varianceCol.setPrefWidth(110);
        varianceCol.setCellValueFactory(d -> {
            Double counted = (Double) d.getValue()[4];
            double system = (double) d.getValue()[3];
            String text = counted == null ? "—" : KESFormatter.formatNumber(counted - system);
            return new javafx.beans.property.SimpleStringProperty(text);
        });

        table.getColumns().addAll(List.of(nameCol, sysCol, countedCol, varianceCol));
        table.getItems().addAll(items);
        VBox.setVgrow(table, Priority.ALWAYS);

        content.getChildren().addAll(header, table);
        root.setCenter(content);
    }

    private void confirmComplete(int stockTakeId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Complete Stock Take");
        confirm.setHeaderText("Apply counted quantities to live stock?");
        confirm.setContentText("Every product with a counted quantity will have its stock level updated to match, "
            + "and a stock adjustment will be logged. Products left blank are skipped. This cannot be undone.");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                int adjusted = stockTakeDAO.completeStockTake(stockTakeId);
                Toast.success(dialogStage, "Stock Take Completed",
                    adjusted + " product" + (adjusted == 1 ? "" : "s") + " adjusted.");
                showHistoryScreen();
            }
        });
    }
}
