package com.kaziflow.views;

import com.kaziflow.dao.ExpenseDAO;
import com.kaziflow.models.Expense;
import com.kaziflow.utils.KESFormatter;
import com.kaziflow.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ExpenseView {

    private VBox root;
    private TableView<Expense> table;
    private ObservableList<Expense> data = FXCollections.observableArrayList();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private Label monthTotalLabel, totalLabel;

    private static final String[] CATEGORIES = {
        "Rent & Utilities", "Payroll & Salaries", "Transport & Logistics",
        "Office Supplies", "Marketing & Advertising", "Maintenance & Repairs",
        "Loan Repayment", "Insurance", "Tax & Licenses", "Miscellaneous"
    };

    public ExpenseView() { buildUI(); }
    public VBox getRoot() { return root; }

    private void buildUI() {
        root = new VBox(0);
        root.setStyle("-fx-background-color: #f8fafc;");
        root.getChildren().addAll(buildTopBar(), buildContent());
        loadData();
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(2);
        Label title = new Label("Expense Management"); title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label bc = new Label("Finance › Expenses"); bc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        titleBox.getChildren().addAll(title, bc);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button addBtn = new Button("+ Record Expense");
        addBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-pref-height: 38px; -fx-padding: 0 18; -fx-cursor: hand; -fx-font-size: 13px;");
        addBtn.setOnAction(e -> showAddDialog());

        bar.getChildren().addAll(titleBox, sp, addBtn);
        return bar;
    }

    private VBox buildContent() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(24));

        // Stats
        HBox statsRow = new HBox(16);
        monthTotalLabel = new Label("KES 0");
        totalLabel      = new Label("KES 0");

        statsRow.getChildren().addAll(
            statCard("This Month", monthTotalLabel, "Current month expenses", "#dc2626"),
            statCard("Total Expenses", totalLabel, "All time", "#1e293b"),
            statCard("Top Category", new Label("Payroll"), "Highest spend", "#d97706"),
            statCard("Avg per Month", new Label("—"), "Based on history", "#7c3aed")
        );
        for (var c : statsRow.getChildren()) HBox.setHgrow((Region)c, Priority.ALWAYS);

        // Category breakdown
        HBox mainRow = new HBox(20);

        VBox tableSection = new VBox(16);
        HBox.setHgrow(tableSection, Priority.ALWAYS);

        // Filter bar
        HBox filterBar = new HBox(10); filterBar.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField();
        search.setPromptText("Search expenses...");
        search.getStyleClass().add("search-box");
        search.setPrefWidth(260);
        search.textProperty().addListener((obs, o, v) -> {
            if (v.isBlank()) loadData();
            else data.setAll(expenseDAO.findAll().stream()
                .filter(ex -> ex.getDescription().toLowerCase().contains(v.toLowerCase()) ||
                             (ex.getCategory() != null && ex.getCategory().toLowerCase().contains(v.toLowerCase())))
                .toList());
        });

        ComboBox<String> catFilter = new ComboBox<>();
        catFilter.getItems().add("All Categories");
        catFilter.getItems().addAll(CATEGORIES);
        catFilter.setValue("All Categories"); catFilter.setPrefHeight(36);

        ComboBox<String> monthFilter = new ComboBox<>();
        monthFilter.getItems().addAll("All Time", "This Month", "Last Month", "This Quarter");
        monthFilter.setValue("All Time"); monthFilter.setPrefHeight(36);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button exportBtn = new Button("⬇ Export");
        exportBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 36px; -fx-padding: 0 14; -fx-cursor: hand; -fx-font-size: 13px;");
        exportBtn.setOnAction(e -> new com.kaziflow.services.ReportExportService().exportCSV(com.kaziflow.services.ReportExportService.ReportType.EXPENSES));

        filterBar.getChildren().addAll(search, catFilter, monthFilter, sp, exportBtn);

        // Table
        VBox tableCard = new VBox(0);
        tableCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1;");
        table = buildTable();
        tableCard.getChildren().add(table);

        tableSection.getChildren().addAll(filterBar, tableCard);

        // Right: category breakdown
        VBox breakdown = buildBreakdownPanel();
        breakdown.setPrefWidth(280);

        mainRow.getChildren().addAll(tableSection, breakdown);
        content.getChildren().addAll(statsRow, mainRow);
        return content;
    }

    @SuppressWarnings("unchecked")
    private TableView<Expense> buildTable() {
        TableView<Expense> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(460);
        tv.setStyle("-fx-background-color: white;");

        TableColumn<Expense,String> descCol = new TableColumn<>("DESCRIPTION");
        descCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getDescription()));
        descCol.setPrefWidth(220);

        TableColumn<Expense,String> catCol = new TableColumn<>("CATEGORY");
        catCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getCategory()));
        catCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String cat, boolean empty) {
                super.updateItem(cat, empty);
                if (empty || cat == null) { setGraphic(null); return; }
                Label badge = new Label(cat);
                badge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-size: 11px; -fx-background-radius: 20; -fx-padding: 3 10;");
                setGraphic(badge); setText(null);
            }
        }); catCol.setPrefWidth(170);

        TableColumn<Expense,String> amtCol = new TableColumn<>("AMOUNT");
        amtCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(KESFormatter.format(d.getValue().getAmount())));
        amtCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); return; }
                Label l = new Label(v); l.setStyle("-fx-font-weight: bold; -fx-text-fill: #dc2626;");
                setGraphic(l); setText(null);
            }
        }); amtCol.setPrefWidth(130);

        TableColumn<Expense,String> payCol = new TableColumn<>("PAYMENT");
        payCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getPaymentMethod() != null ? d.getValue().getPaymentMethod().toUpperCase() : "CASH"));
        payCol.setPrefWidth(90);

        TableColumn<Expense,String> rcptCol = new TableColumn<>("RECEIPT #");
        rcptCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getReceiptNumber() != null ? d.getValue().getReceiptNumber() : "—"));
        rcptCol.setPrefWidth(100);

        TableColumn<Expense,String> dateCol = new TableColumn<>("DATE");
        dateCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getExpenseDate() != null ? d.getValue().getExpenseDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "—"));
        dateCol.setPrefWidth(110);

        TableColumn<Expense,Void> actCol = new TableColumn<>(""); actCol.setPrefWidth(50);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button delBtn = new Button("🗑");
            {
                delBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc2626; -fx-cursor: hand; -fx-border-color: transparent; -fx-font-size: 13px;");
                delBtn.setOnAction(e -> {
                    Expense exp = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this expense?", ButtonType.YES, ButtonType.NO);
                    confirm.setHeaderText(null);
                    confirm.showAndWait().ifPresent(btn -> { if (btn == ButtonType.YES) { expenseDAO.delete(exp.getId()); loadData(); } });
                });
            }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v,empty); setGraphic(empty?null:delBtn); }
        });

        tv.getColumns().addAll(descCol, catCol, amtCol, payCol, rcptCol, dateCol, actCol);
        tv.setItems(data);
        return tv;
    }

    private VBox buildBreakdownPanel() {
        VBox panel = new VBox(16);

        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        Label title = new Label("By Category"); title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label subtitle = new Label("This month's breakdown"); subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        card.getChildren().addAll(title, subtitle);

        String[][] cats = {
            {"Payroll",      "320,000", "#2563eb",  "58%"},
            {"Rent",         "85,000",  "#16a34a",  "15%"},
            {"Transport",    "45,000",  "#d97706",  "8%"},
            {"Utilities",    "38,500",  "#7c3aed",  "7%"},
            {"Maintenance",  "22,000",  "#dc2626",  "4%"},
            {"Other",        "44,500",  "#94a3b8",  "8%"}
        };

        for (String[] cat : cats) {
            VBox row = new VBox(4);
            row.setPadding(new Insets(4, 0, 4, 0));
            HBox topRow = new HBox();
            Label name = new Label(cat[0]); name.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e293b;");
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            Label amount = new Label("KES " + cat[1]); amount.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
            Label pct = new Label(cat[3]); pct.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
            topRow.getChildren().addAll(name, sp, amount, new Label(" "), pct);
            ProgressBar pb = new ProgressBar(Double.parseDouble(cat[3].replace("%", "")) / 100.0);
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.setStyle("-fx-accent: " + cat[2] + "; -fx-pref-height: 6;");
            row.getChildren().addAll(topRow, pb);
            card.getChildren().add(row);
        }

        panel.getChildren().add(card);
        return panel;
    }

    private VBox statCard(String label, Label valueLabel, String note, String color) {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        Label lbl = new Label(label); lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        valueLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label noteL = new Label(note); noteL.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        card.getChildren().addAll(lbl, valueLabel, noteL);
        return card;
    }

    private void loadData() {
        List<Expense> expenses = expenseDAO.findAll();
        data.setAll(expenses);
        monthTotalLabel.setText(KESFormatter.format(expenseDAO.getMonthTotal()));
        totalLabel.setText(KESFormatter.formatShort(expenseDAO.getTotal()));
    }

    private void showAddDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Record New Expense");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(460);

        VBox form = new VBox(14); form.setPadding(new Insets(20));

        // Description
        form.getChildren().add(lbl("Description *"));
        TextField descField = field("e.g. Monthly rent payment", "");
        form.getChildren().add(descField);

        // Amount + Category row
        HBox amtCatRow = new HBox(16);
        VBox amtBox = new VBox(6);
        amtBox.getChildren().add(lbl("Amount (KES) *"));
        TextField amtField = field("0.00", "");
        amtBox.getChildren().add(amtField);
        HBox.setHgrow(amtBox, Priority.ALWAYS);

        VBox catBox = new VBox(6);
        catBox.getChildren().add(lbl("Category *"));
        ComboBox<String> catCombo = new ComboBox<>();
        catCombo.getItems().addAll(CATEGORIES);
        catCombo.setValue(CATEGORIES[0]);
        catCombo.setPrefWidth(200); catCombo.setPrefHeight(36);
        catBox.getChildren().add(catCombo);
        HBox.setHgrow(catBox, Priority.ALWAYS);

        amtCatRow.getChildren().addAll(amtBox, catBox);
        form.getChildren().add(amtCatRow);

        // Payment method + Date
        HBox payDateRow = new HBox(16);

        VBox payBox = new VBox(6);
        payBox.getChildren().add(lbl("Payment Method"));
        ComboBox<String> payCombo = new ComboBox<>();
        payCombo.getItems().addAll("Cash", "M-Pesa", "Bank Transfer", "Cheque");
        payCombo.setValue("Cash"); payCombo.setPrefWidth(180); payCombo.setPrefHeight(36);
        payBox.getChildren().add(payCombo);
        HBox.setHgrow(payBox, Priority.ALWAYS);

        VBox dateBox = new VBox(6);
        dateBox.getChildren().add(lbl("Date"));
        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(180); datePicker.setPrefHeight(36);
        dateBox.getChildren().add(datePicker);
        HBox.setHgrow(dateBox, Priority.ALWAYS);

        payDateRow.getChildren().addAll(payBox, dateBox);
        form.getChildren().add(payDateRow);

        // Receipt number
        form.getChildren().add(lbl("Receipt / Reference Number"));
        TextField rcptField = field("Optional receipt or reference number", "");
        form.getChildren().add(rcptField);

        // Notes
        form.getChildren().add(lbl("Notes"));
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Additional notes...");
        notesArea.setPrefRowCount(2);
        notesArea.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 8;");
        form.getChildren().add(notesArea);

        // Validation label
        Label validation = new Label(); validation.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
        form.getChildren().add(validation);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Wire validation before close
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (descField.getText().trim().isEmpty()) {
                validation.setText("Description is required."); event.consume(); return;
            }
            try { Double.parseDouble(amtField.getText().trim()); }
            catch (NumberFormatException e) { validation.setText("Amount must be a valid number."); event.consume(); }
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                Expense exp = new Expense();
                exp.setDescription(descField.getText().trim());
                exp.setCategory(catCombo.getValue());
                exp.setAmount(Double.parseDouble(amtField.getText().trim()));
                exp.setPaymentMethod(payCombo.getValue().toLowerCase());
                exp.setReceiptNumber(rcptField.getText().trim());
                exp.setNotes(notesArea.getText().trim());
                exp.setExpenseDate(datePicker.getValue());
                try { exp.setCreatedBy(SessionManager.getInstance().getCurrentUser().getId()); } catch (Exception ignored) { exp.setCreatedBy(1); }
                if (expenseDAO.save(exp)) loadData();
            }
        });
    }

    private TextField field(String prompt, String val) {
        TextField tf = new TextField(val); tf.setPromptText(prompt);
        tf.setStyle("-fx-pref-height: 36px; -fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 13px; -fx-padding: 0 10;");
        tf.setMaxWidth(Double.MAX_VALUE); return tf;
    }

    private Label lbl(String text) {
        Label l = new Label(text); l.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;"); return l;
    }
}
