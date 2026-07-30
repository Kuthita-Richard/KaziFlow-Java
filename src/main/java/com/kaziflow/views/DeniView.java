package com.kaziflow.views;

import com.kaziflow.dao.DeniDAO;
import com.kaziflow.services.AuditLog;
import com.kaziflow.utils.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * DeniView — Deni Book (Credit/Debt Tracker).
 *
 * "Andika kwa dafitari" = "Write it in the book" — the phrase every
 * Kenyan shopkeeper hears when a customer buys on credit.
 *
 * This replaces the physical notebook with a searchable, filterable,
 * WhatsApp-reminder-ready digital deni book.
 */
public class DeniView {

    private BorderPane root;
    private final DeniDAO dao = new DeniDAO();
    private ObservableList<String[]> tableData = FXCollections.observableArrayList();
    private TableView<String[]> table;
    private Label totalOutstandingLabel;
    private Label debtorCountLabel;
    private Label overdueLabel;

    public DeniView() {
        dao.ensureTables();
        buildUI();
        loadData(null);
    }

    public BorderPane getRoot() { return root; }

    private void buildUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #f8fafc;");
        root.setTop(buildHeader());
        root.setCenter(buildContent());
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private VBox buildHeader() {
        VBox header = new VBox(0);

        // Title bar
        HBox titleBar = new HBox(12);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(16, 24, 16, 24));
        titleBar.setStyle("-fx-background-color: white; " +
            "-fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        VBox titleBlock = new VBox(2);
        Label title = new Label("📒  Deni Book");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label subtitle = new Label("Track who owes you money and send reminders");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        titleBlock.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button addBtn = new Button("+ Add Deni");
        addBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; " +
            "-fx-background-radius: 8; -fx-pref-height: 38px; -fx-padding: 0 18; -fx-cursor: hand; -fx-font-size: 13px;");
        addBtn.setOnAction(e -> showAddDialog());

        titleBar.getChildren().addAll(titleBlock, sp, addBtn);

        // Stats row
        HBox statsRow = new HBox(16);
        statsRow.setPadding(new Insets(16, 24, 16, 24));
        statsRow.setStyle("-fx-background-color: white; " +
            "-fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        double outstanding = dao.getTotalOutstanding();
        int debtors = dao.getDebtorCount();
        int overdue = dao.getOverdueCount();

        totalOutstandingLabel = statCard("Total Outstanding",
            "KES " + KESFormatter.format(outstanding), "#dc2626", "💰");
        debtorCountLabel = statCard("People Owing",
            String.valueOf(debtors), "#d97706", "👥");
        overdueLabel = statCard("Overdue",
            String.valueOf(overdue) + " entries", "#7c3aed", "⚠");

        statsRow.getChildren().addAll(totalOutstandingLabel, debtorCountLabel, overdueLabel);
        header.getChildren().addAll(titleBar, statsRow);
        return header;
    }

    private Label statCard(String label, String value, String color, String icon) {
        VBox card = new VBox(4);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-border-width: 1; " +
            "-fx-padding: 12 20; -fx-min-width: 180px;");
        Label lbl = new Label(icon + "  " + label);
        lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        card.getChildren().addAll(lbl, val);
        // Return the value label so we can update it
        return val;
    }

    // ── Content ────────────────────────────────────────────────────────────

    private VBox buildContent() {
        VBox content = new VBox(0);
        VBox.setVgrow(content, Priority.ALWAYS);

        // Filter bar
        HBox filterBar = new HBox(10);
        filterBar.setPadding(new Insets(14, 24, 14, 24));
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setStyle("-fx-background-color: white; " +
            "-fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        TextField search = new TextField();
        search.setPromptText("Search by name, phone...");
        search.setPrefWidth(280);
        search.setStyle("-fx-pref-height: 34px; -fx-background-color: #f8fafc; " +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; " +
            "-fx-font-size: 13px; -fx-padding: 0 10;");

        ToggleGroup statusGroup = new ToggleGroup();
        ToggleButton allBtn     = filterTab("All",     "all",     statusGroup);
        ToggleButton unpaidBtn  = filterTab("Unpaid",  "unpaid",  statusGroup);
        ToggleButton partialBtn = filterTab("Partial", "partial", statusGroup);
        ToggleButton paidBtn    = filterTab("Paid",    "paid",    statusGroup);
        allBtn.setSelected(true);

        statusGroup.selectedToggleProperty().addListener((obs, old, nw) -> {
            if (nw == null) { allBtn.setSelected(true); return; }
            String filter = (String) nw.getUserData();
            AsyncTask.run(() -> dao.findAll("all".equals(filter) ? null : filter),
                tableData::setAll, err -> {});
        });

        search.textProperty().addListener((obs, old, val) -> {
            if (val.isBlank()) {
                AsyncTask.run(() -> dao.findAll(null), tableData::setAll, err -> {});
            } else {
                AsyncTask.run(() -> dao.search(val), tableData::setAll, err -> {});
            }
        });

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        filterBar.getChildren().addAll(search, sp, allBtn, unpaidBtn, partialBtn, paidBtn);

        // Table
        table = buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        content.getChildren().addAll(filterBar, table);
        return content;
    }

    private ToggleButton filterTab(String label, String data, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(label);
        btn.setToggleGroup(group);
        btn.setUserData(data);
        String base = "-fx-background-radius: 6; -fx-border-radius: 6; -fx-border-width: 1; " +
            "-fx-pref-height: 34px; -fx-padding: 0 14; -fx-font-size: 13px; -fx-cursor: hand;";
        btn.setStyle(base + "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-text-fill: #475569;");
        btn.selectedProperty().addListener((obs, was, is) ->
            btn.setStyle(base + (is
                ? "-fx-background-color: #dc2626; -fx-border-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold;"
                : "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-text-fill: #475569;")));
        return btn;
    }

    private TableView<String[]> buildTable() {
        TableView<String[]> tv = new TableView<>(tableData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color: white;");

        // Name + phone
        TableColumn<String[], String> nameCol = col("Debtor", 1, 160);
        nameCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                String[] row = getTableView().getItems().get(getIndex());
                VBox cell = new VBox(2);
                Label name = new Label(row[1]);
                name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
                Label phone = new Label(row[2]);
                phone.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
                cell.getChildren().addAll(name, phone);
                setGraphic(cell);
            }
        });

        TableColumn<String[], String> descCol   = col("Description",  3, 180);
        TableColumn<String[], String> amountCol  = col("Amount (KES)", 4, 110);
        TableColumn<String[], String> paidCol    = col("Paid (KES)",   5, 110);

        // Balance — colored
        TableColumn<String[], String> balanceCol = new TableColumn<>("Balance (KES)");
        balanceCol.setPrefWidth(120);
        balanceCol.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(d.getValue()[6]));
        balanceCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); return; }
                setText("KES " + KESFormatter.formatShort(Double.parseDouble(v)));
                setStyle("-fx-font-weight: bold; -fx-text-fill: #dc2626;");
            }
        });

        // Status badge
        TableColumn<String[], String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(90);
        statusCol.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(d.getValue()[7]));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                Label badge = new Label(v.toUpperCase());
                String color = switch (v) {
                    case "paid"    -> "-fx-background-color:#dcfce7; -fx-text-fill:#16a34a;";
                    case "partial" -> "-fx-background-color:#fef3c7; -fx-text-fill:#d97706;";
                    default        -> "-fx-background-color:#fee2e2; -fx-text-fill:#dc2626;";
                };
                badge.setStyle(color + "-fx-font-size:10px; -fx-font-weight:bold; " +
                    "-fx-background-radius:20; -fx-padding:3 10;");
                setGraphic(badge);
            }
        });

        TableColumn<String[], String> dueCol  = col("Due Date",  8, 100);
        TableColumn<String[], String> ageCol  = col("Since",     9, 90);

        // Actions
        TableColumn<String[], Void> actCol = new TableColumn<>("Actions");
        actCol.setPrefWidth(280);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button payBtn      = actionBtn("✔ Pay",      "#16a34a");
            private final Button whatsappBtn = actionBtn("💬 WhatsApp", "#25D366");
            private final Button smsBtn      = actionBtn("✉ SMS",      "#2563eb");
            private final Button delBtn      = actionBtn("🗑",           "#dc2626");
            private final HBox box = new HBox(6, payBtn, whatsappBtn, smsBtn, delBtn);

            {
                payBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    if (!"paid".equals(row[7])) showPaymentDialog(row);
                });
                whatsappBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    sendWhatsAppReminder(row);
                });
                smsBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    sendSmsReminder(row);
                });
                delBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    if (dao.delete(Integer.parseInt(row[0]))) {
                        tableData.remove(getIndex());
                        refreshStats();
                    }
                });
            }

            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                String[] row = getTableView().getItems().get(getIndex());
                payBtn.setDisable("paid".equals(row[7]));
                whatsappBtn.setDisable("paid".equals(row[7]));
                smsBtn.setDisable("paid".equals(row[7]));
                setGraphic(box);
            }
        });

        tv.getColumns().addAll(nameCol, descCol, amountCol, paidCol,
            balanceCol, statusCol, dueCol, ageCol, actCol);
        return tv;
    }

    private TableColumn<String[], String> col(String header, int idx, double width) {
        TableColumn<String[], String> col = new TableColumn<>(header);
        col.setPrefWidth(width);
        col.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(
                idx < d.getValue().length ? d.getValue()[idx] : ""));
        return col;
    }

    private Button actionBtn(String label, String color) {
        Button btn = new Button(label);
        btn.setStyle("-fx-background-color: white; -fx-border-color: " + color + "; " +
            "-fx-border-radius: 5; -fx-background-radius: 5; -fx-font-size: 11px; " +
            "-fx-text-fill: " + color + "; -fx-cursor: hand; -fx-padding: 3 8;");
        return btn;
    }

    // ── Dialogs ────────────────────────────────────────────────────────────

    private void showAddDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Deni Entry");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(420);

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));

        TextField nameF   = field("Customer Name *");
        TextField phoneF  = field("Phone Number (07XX...)");
        TextField descF   = field("What for? e.g. Cement 2 bags");
        TextField amtF    = field("Amount (KES) *");
        TextField dueF    = field("Due Date (YYYY-MM-DD, optional)");

        form.addRow(0, lbl("Name"),        nameF);
        form.addRow(1, lbl("Phone"),       phoneF);
        form.addRow(2, lbl("Description"), descF);
        form.addRow(3, lbl("Amount"),      amtF);
        form.addRow(4, lbl("Due Date"),    dueF);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK) return;
            if (nameF.getText().isBlank() || amtF.getText().isBlank()) {
                Toast.error(SceneManager.getInstance().getStage(),
                    "Validation", "Name and amount are required.");
                return;
            }
            double amount;
            try { amount = Double.parseDouble(amtF.getText().trim()); }
            catch (Exception ex) {
                Toast.error(SceneManager.getInstance().getStage(), "Invalid amount", "Enter a valid number.");
                return;
            }
            int userId = 1;
            try { userId = SessionManager.getInstance().getCurrentUser().getId(); }
            catch (Exception ignored) {}

            int id = dao.addEntry(nameF.getText().trim(), phoneF.getText().trim(),
                descF.getText().trim(), amount, dueF.getText().trim(), userId);
            if (id > 0) {
                AuditLog.log("DENI_ADDED",
                    "Deni added: " + nameF.getText() + " KES " + amount, "deni", id);
                loadData(null);
                Toast.success(SceneManager.getInstance().getStage(),
                    "Deni added", nameF.getText() + " — KES " + KESFormatter.format(amount));
            }
        });
    }

    private void showPaymentDialog(String[] row) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Record Payment — " + row[1]);
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(380);

        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));

        double balance = Double.parseDouble(row[6]);
        Label balLbl = new Label("Balance outstanding: KES " + KESFormatter.format(balance));
        balLbl.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 13px;");

        TextField amtF  = field("Amount Paid (KES)");
        amtF.setText(String.valueOf(balance)); // prefill with full balance
        TextField noteF = field("Payment note (e.g. M-Pesa SB32XY7Q)");

        form.addRow(0, balLbl);
        form.addRow(1, lbl("Amount"), amtF);
        form.addRow(2, lbl("Note"),   noteF);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK) return;
            double amount;
            try { amount = Double.parseDouble(amtF.getText().trim()); }
            catch (Exception ex) { return; }

            int id = Integer.parseInt(row[0]);
            if (dao.recordPayment(id, amount, noteF.getText().trim())) {
                AuditLog.log("DENI_PAYMENT",
                    "Payment KES " + amount + " from " + row[1], "deni", id);
                loadData(null);
                Toast.success(SceneManager.getInstance().getStage(),
                    "Payment recorded", row[1] + " paid KES " + KESFormatter.format(amount));
            }
        });
    }

    private void sendWhatsAppReminder(String[] row) {
        String phone = row[2].replaceAll("[^0-9]", "");
        if (phone.startsWith("0")) phone = "254" + phone.substring(1);
        if (phone.length() < 9) {
            Toast.error(SceneManager.getInstance().getStage(),
                "No phone", "No valid phone number for " + row[1]);
            return;
        }
        String balance = KESFormatter.format(Double.parseDouble(row[6]));
        String msg = "Habari " + row[1] + "! Ukumbusho wa deni: " +
            "Una deni ya KES " + balance + " kwetu. " +
            "Tafadhali lipa hivi karibuni. Asante!";
        String url = "https://wa.me/" + phone + "?text=" +
            java.net.URLEncoder.encode(msg, java.nio.charset.StandardCharsets.UTF_8);
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception e) {
            Toast.error(SceneManager.getInstance().getStage(),
                "Error", "Could not open WhatsApp: " + e.getMessage());
        }
    }

    private void sendSmsReminder(String[] row) {
        var sms = com.kaziflow.services.AfricasTalkingService.getInstance();
        if (!sms.isConfigured()) {
            Toast.error(SceneManager.getInstance().getStage(),
                "SMS not configured", "Go to Settings → SMS (Africa's Talking) to set it up.");
            return;
        }
        String balance = KESFormatter.format(Double.parseDouble(row[6]));
        String msg = "Habari " + row[1] + ", ukumbusho wa deni: Una salio la KES " +
            balance + ". Tafadhali lipa hivi karibuni. Asante - KaziFlow.";
        AsyncTask.run(
            () -> sms.send(row[2], msg),
            result -> {
                if (result.startsWith("✅")) {
                    Toast.success(SceneManager.getInstance().getStage(), "SMS Sent", row[1] + " — " + result);
                } else {
                    Toast.error(SceneManager.getInstance().getStage(), "SMS Failed", result);
                }
            },
            err -> Toast.error(SceneManager.getInstance().getStage(), "SMS Error", err)
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void loadData(String filter) {
        AsyncTask.run(() -> dao.findAll(filter), list -> {
            tableData.setAll(list);
            refreshStats();
        }, err -> {});
    }

    private void refreshStats() {
        double outstanding = dao.getTotalOutstanding();
        int debtors = dao.getDebtorCount();
        int overdue  = dao.getOverdueCount();
        totalOutstandingLabel.setText("KES " + KESFormatter.format(outstanding));
        debtorCountLabel.setText(String.valueOf(debtors));
        overdueLabel.setText(overdue + " entries");
    }

    private TextField field(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setPrefWidth(280);
        tf.setStyle("-fx-pref-height: 34px; -fx-background-color: white; " +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; " +
            "-fx-font-size: 13px; -fx-padding: 0 8;");
        return tf;
    }

    private Label lbl(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        return l;
    }
}
