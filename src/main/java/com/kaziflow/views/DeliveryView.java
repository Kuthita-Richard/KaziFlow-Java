package com.kaziflow.views;

import com.kaziflow.dao.DeliveryDAO;
import com.kaziflow.utils.KESFormatter;
import com.kaziflow.utils.SceneManager;
import com.kaziflow.utils.Toast;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * DeliveryView — delivery management (Phase 10B): named locations with
 * distance/override pricing, business-wide per-km (and optional per-kg) rates,
 * and a live list of dispatched deliveries with rider assignment/status.
 */
public class DeliveryView {

    private final DeliveryDAO dao = new DeliveryDAO();
    private BorderPane root;

    public DeliveryView() {
        dao.ensureTables();
        build();
    }

    public BorderPane getRoot() { return root; }

    private void build() {
        root = new BorderPane();
        root.setStyle("-fx-background-color:#f8fafc;");

        Label header = new Label("Delivery Management");
        header.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#1e293b;-fx-padding:20 20 0 20;");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            new Tab("Deliveries", buildDeliveriesPane()),
            new Tab("Named Locations", buildLocationsPane()),
            new Tab("Pricing", buildPricingPane())
        );

        VBox content = new VBox(10, header, tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.setCenter(content);
    }

    private VBox buildDeliveriesPane() {
        VBox pane = new VBox(12);
        pane.setPadding(new Insets(16));

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Button assignBtn = new Button("🏍 Assign Rider");
        assignBtn.setStyle("-fx-background-color:white;-fx-border-color:#d97706;-fx-text-fill:#d97706;-fx-border-radius:8;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        Button deliveredBtn = new Button("✓ Mark Delivered");
        deliveredBtn.setStyle("-fx-background-color:white;-fx-border-color:#16a34a;-fx-text-fill:#16a34a;-fx-border-radius:8;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        Button cancelBtn = new Button("✕ Cancel");
        cancelBtn.setStyle("-fx-background-color:white;-fx-border-color:#dc2626;-fx-text-fill:#dc2626;-fx-border-radius:8;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        toolbar.getChildren().addAll(assignBtn, deliveredBtn, cancelBtn);

        TableView<String[]> table = new TableView<>();
        table.getColumns().add(col("Delivery #", 1, 130));
        table.getColumns().add(col("Customer", 2, 140));
        table.getColumns().add(col("Phone", 3, 110));
        table.getColumns().add(col("Location", 4, 130));
        table.getColumns().add(col("Distance (km)", 5, 100));
        table.getColumns().add(col("Charge", 6, 100));
        table.getColumns().add(col("Rider", 7, 120));
        table.getColumns().add(col("Status", 8, 100));
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable refresh = () -> table.getItems().setAll(dao.findAllDeliveries());
        refresh.run();

        assignBtn.setOnAction(e -> withSelected(table, "pending", d -> {
            Dialog<ButtonType> dlg = new Dialog<>();
            dlg.setTitle("Assign Rider — " + d[1]);
            GridPane f = new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));
            TextField riderF = new TextField(); riderF.setPromptText("Rider name");
            TextField phoneF = new TextField(); phoneF.setPromptText("Rider phone");
            f.addRow(0, new Label("Rider"), riderF);
            f.addRow(1, new Label("Phone"), phoneF);
            dlg.getDialogPane().setContent(f);
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dlg.showAndWait().ifPresent(btn -> {
                if (btn != ButtonType.OK) return;
                dao.assignRider(Integer.parseInt(d[0]), riderF.getText(), phoneF.getText());
                Toast.success(stage(), "Dispatched", "Delivery " + d[1] + " assigned to " + riderF.getText() + ".");
                refresh.run();
            });
        }));
        deliveredBtn.setOnAction(e -> withSelected(table, "dispatched", d -> {
            dao.markDelivered(Integer.parseInt(d[0]));
            Toast.success(stage(), "Delivered", "Delivery " + d[1] + " marked as delivered.");
            refresh.run();
        }));
        cancelBtn.setOnAction(e -> {
            String[] selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Toast.error(stage(), "No Delivery Selected", "Select a delivery first."); return; }
            dao.cancelDelivery(Integer.parseInt(selected[0]));
            Toast.success(stage(), "Cancelled", "Delivery " + selected[1] + " cancelled.");
            refresh.run();
        });

        pane.getChildren().addAll(toolbar, table);
        return pane;
    }

    private void withSelected(TableView<String[]> table, String requiredStatus, java.util.function.Consumer<String[]> action) {
        String[] selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { Toast.error(stage(), "No Delivery Selected", "Select a delivery from the table first."); return; }
        if (!requiredStatus.equals(selected[8])) {
            Toast.error(stage(), "Wrong Status", "This delivery must be '" + requiredStatus + "' for that action (currently '" + selected[8] + "').");
            return;
        }
        action.accept(selected);
    }

    private VBox buildLocationsPane() {
        VBox pane = new VBox(12);
        pane.setPadding(new Insets(16));

        Label note = new Label("Named locations give a fixed, known price that always overrides the per-km calculation "
            + "for that destination — like a courier's zone pricing for frequent routes. Leave the override blank to fall back to the standard per-km rate.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Button addBtn = new Button("+ Add Named Location");
        addBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        Button deleteBtn = new Button("Delete Selected");
        deleteBtn.setStyle("-fx-background-color:white;-fx-border-color:#dc2626;-fx-text-fill:#dc2626;-fx-border-radius:8;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        toolbar.getChildren().addAll(addBtn, deleteBtn);

        TableView<String[]> table = new TableView<>();
        table.getColumns().add(col("Name", 1, 180));
        table.getColumns().add(col("Distance (km)", 2, 120));
        table.getColumns().add(col("Override Price", 3, 130));
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable refresh = () -> table.getItems().setAll(dao.findAllLocations());
        refresh.run();

        addBtn.setOnAction(e -> {
            Dialog<ButtonType> d = new Dialog<>();
            d.setTitle("New Named Location");
            GridPane f = new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));
            TextField nameF = new TextField(); nameF.setPromptText("e.g. Kiambu Town");
            TextField distF = new TextField(); distF.setPromptText("Distance in km");
            TextField overrideF = new TextField(); overrideF.setPromptText("Leave blank to use per-km rate");
            f.addRow(0, new Label("Location Name"), nameF);
            f.addRow(1, new Label("Distance (km)"), distF);
            f.addRow(2, new Label("Override Price (KES)"), overrideF);
            d.getDialogPane().setContent(f);
            d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            d.showAndWait().ifPresent(btn -> {
                if (btn != ButtonType.OK) return;
                try {
                    double dist = Double.parseDouble(distF.getText());
                    Double override = overrideF.getText().isBlank() ? null : Double.parseDouble(overrideF.getText());
                    dao.addLocation(nameF.getText().trim(), dist, override);
                    Toast.success(stage(), "Added", nameF.getText() + " saved.");
                    refresh.run();
                } catch (NumberFormatException ex) {
                    Toast.error(stage(), "Invalid Input", "Distance and override price must be numbers.");
                }
            });
        });
        deleteBtn.setOnAction(e -> {
            String[] selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Toast.error(stage(), "No Location Selected", "Select a location first."); return; }
            dao.deleteLocation(Integer.parseInt(selected[0]));
            refresh.run();
        });

        pane.getChildren().addAll(note, toolbar, table);
        return pane;
    }

    private VBox buildPricingPane() {
        VBox pane = new VBox(16);
        pane.setPadding(new Insets(20));
        pane.setMaxWidth(420);

        Label note = new Label("Sets the business-wide default delivery rate. Named locations with an override price "
            + "always win over this calculation.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");

        TextField perKmF = new TextField(String.valueOf(dao.getRatePerKm()));
        TextField perKgF = new TextField(String.valueOf(dao.getRatePerKg()));
        Button saveBtn = new Button("Save Rates");
        saveBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:36px;-fx-padding:0 16;-fx-cursor:hand;");
        saveBtn.setOnAction(e -> {
            try {
                double perKm = Double.parseDouble(perKmF.getText());
                double perKg = Double.parseDouble(perKgF.getText());
                dao.saveRates(perKm, perKg);
                Toast.success(stage(), "Saved", "Delivery pricing updated.");
            } catch (NumberFormatException ex) {
                Toast.error(stage(), "Invalid Input", "Both rates must be numbers.");
            }
        });

        GridPane f = new GridPane(); f.setHgap(12); f.setVgap(12);
        f.addRow(0, new Label("Rate per km (KES)"), perKmF);
        f.addRow(1, new Label("Rate per kg (KES, optional)"), perKgF);

        pane.getChildren().addAll(note, f, saveBtn);
        return pane;
    }

    private TableColumn<String[], String> col(String label, int index, double width) {
        TableColumn<String[], String> c = new TableColumn<>(label);
        c.setPrefWidth(width);
        c.setCellValueFactory(d -> new SimpleStringProperty(index < d.getValue().length ? d.getValue()[index] : "—"));
        return c;
    }

    private javafx.stage.Stage stage() { return SceneManager.getInstance().getStage(); }
}
