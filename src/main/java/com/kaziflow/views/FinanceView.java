package com.kaziflow.views;

import com.kaziflow.dao.ExpenseDAO;
import com.kaziflow.dao.TransactionDAO;
import com.kaziflow.models.Expense;
import com.kaziflow.utils.KESFormatter;
import com.kaziflow.utils.SceneManager;
import com.kaziflow.utils.SessionManager;
import com.kaziflow.utils.Toast;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class FinanceView {

    private VBox root;
    private ObservableList<Expense> expenseData = FXCollections.observableArrayList();
    private final TransactionDAO transDAO = new TransactionDAO();
    private final ExpenseDAO expenseDAO   = new ExpenseDAO();
    private TableView<Expense> expenseTable;

    public FinanceView() { buildUI(); }
    public VBox getRoot() { return root; }

    private void buildUI() {
        root = new VBox(0);
        root.setStyle("-fx-background-color: #f8fafc;");

        HBox tabBar = new HBox(0);
        tabBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;-fx-padding:0 24;");

        Button ledgerTab = tabBtn("Ledger Overview", true);
        Button expTab    = tabBtn("Expenses",         false);
        Button vatTab    = tabBtn("VAT Summary",      false);

        StackPane area = new StackPane();
        area.setStyle("-fx-background-color:#f8fafc;");
        VBox.setVgrow(area, Priority.ALWAYS);

        VBox ledger   = buildLedger();
        VBox expenses = buildExpensesView();
        VBox vat      = buildVatSummary();

        area.getChildren().setAll(ledger);

        ledgerTab.setOnAction(e -> { area.getChildren().setAll(ledger);   setActive(ledgerTab, expTab, vatTab); });
        expTab   .setOnAction(e -> { area.getChildren().setAll(expenses); setActive(expTab, ledgerTab, vatTab); loadExpenses(); });
        vatTab   .setOnAction(e -> { area.getChildren().setAll(vat);      setActive(vatTab, ledgerTab, expTab); });

        tabBar.getChildren().addAll(ledgerTab, expTab, vatTab);
        root.getChildren().addAll(tabBar, area);
    }

    private void setActive(Button a, Button... rest) { applyTab(a,true); for (Button b:rest) applyTab(b,false); }
    private Button tabBtn(String label, boolean active) { Button b = new Button(label); applyTab(b,active); return b; }
    private void applyTab(Button b, boolean active) {
        if (active) b.setStyle("-fx-background-color:transparent;-fx-text-fill:#2563eb;-fx-font-size:13px;-fx-font-weight:bold;-fx-padding:14 16;-fx-border-color:transparent transparent #2563eb transparent;-fx-border-width:0 0 2 0;-fx-cursor:hand;");
        else        b.setStyle("-fx-background-color:transparent;-fx-text-fill:#94a3b8;-fx-font-size:13px;-fx-padding:14 16;-fx-border-color:transparent;-fx-cursor:hand;");
    }

    // ═══ TAB 1 · LEDGER ═══════════════════════════════════════════════════════

    private VBox buildLedger() {
        VBox view = new VBox(0); view.setStyle("-fx-background-color:#f8fafc;");
        view.getChildren().addAll(pageHeader("Ledger Overview","Finance › Ledger",null), buildLedgerBody());
        return view;
    }

    private VBox buildLedgerBody() {
        VBox content = new VBox(20); content.setPadding(new Insets(24));
        double rev = transDAO.getTotalRevenue();
        double exp = transDAO.getTotalExpenses();
        double net = rev - exp;

        HBox stats = statsRow(
            statCard("Total Revenue",  KESFormatter.formatShort(rev),      "+23% vs last month", "#1e293b"),
            statCard("Total Expenses", KESFormatter.formatShort(exp),      "Operating costs",    "#dc2626"),
            statCard("Net Cash Flow",  KESFormatter.formatShort(net),      "↑ Current period",   "#16a34a"),
            statCard("VAT Liability",  KESFormatter.formatShort(rev*0.16), "16% VAT due",        "#d97706")
        );

        HBox mainRow = new HBox(20);

        // Chart of Accounts
        VBox acctCard = new VBox(0);
        acctCard.setStyle(CARD);
        HBox.setHgrow(acctCard, Priority.ALWAYS);
        Label acctTitle = new Label("Chart of Accounts"); acctTitle.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#1e293b;-fx-padding:0 0 12 0;");
        acctCard.setPadding(new Insets(20));
        acctCard.getChildren().add(acctTitle);

        Object[][] accounts = {
            {"ASSETS",               "",              "section"},
            {"  Cash & Bank",        rev*0.45,        "asset"},
            {"  Accounts Receivable",rev*0.12,        "asset"},
            {"  Inventory Value",    rev*0.97,        "asset"},
            {"LIABILITIES",          "",              "section"},
            {"  Accounts Payable",   exp*0.19,        "liab"},
            {"  VAT Payable",        rev*0.16,        "liab"},
            {"EQUITY",               "",              "section"},
            {"  Total Revenue",      rev,             "income"},
            {"  Total Expenses",     exp,             "expense"},
            {"  Net Profit/(Loss)",  net,             "net"}
        };

        for (Object[] a : accounts) {
            HBox acctRow = new HBox(); acctRow.setAlignment(Pos.CENTER_LEFT);
            acctRow.setPadding(new Insets(8,0,8,0));
            boolean isSection = "section".equals(a[2]);
            acctRow.setStyle("-fx-border-color:transparent transparent " + (isSection?"#e2e8f0":"#f1f5f9") + " transparent;-fx-border-width:0 0 1 0;");
            String style = switch ((String)a[2]) {
                case "section" -> "-fx-text-fill:#1e293b;-fx-font-weight:bold;-fx-font-size:13px;";
                case "income"  -> "-fx-text-fill:#16a34a;-fx-font-size:13px;";
                case "expense" -> "-fx-text-fill:#dc2626;-fx-font-size:13px;";
                case "liab"    -> "-fx-text-fill:#d97706;-fx-font-size:13px;";
                case "net"     -> "-fx-text-fill:" + (net>=0?"#16a34a":"#dc2626") + ";-fx-font-size:14px;-fx-font-weight:bold;";
                default        -> "-fx-text-fill:#475569;-fx-font-size:13px;";
            };
            Label name = new Label((String)a[0]); name.setStyle(style);
            Region sp2 = new Region(); HBox.setHgrow(sp2, Priority.ALWAYS);
            Label val = new Label(a[1] instanceof Double ? KESFormatter.format((Double)a[1]) : ""); val.setStyle(style);
            acctRow.getChildren().addAll(name, sp2, val);
            acctCard.getChildren().add(acctRow);
        }

        // Right panel
        VBox right = new VBox(16); right.setPrefWidth(320);

        // Cash flow mini chart — real monthly revenue from DB
        VBox chartCard = new VBox(12); chartCard.setStyle(CARD); chartCard.setPadding(new Insets(20));
        Label cTitle = new Label("Cash Flow Trend (6 months)"); cTitle.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        NumberAxis xa = new NumberAxis(1,6,1); NumberAxis ya = new NumberAxis();
        ya.setTickLabelFormatter(new javafx.util.StringConverter<>() { public String toString(Number n){return KESFormatter.formatShort(n.doubleValue());} public Number fromString(String s){return 0;} });
        LineChart<Number,Number> chart = new LineChart<>(xa,ya);
        chart.setAnimated(false); chart.setLegendVisible(true); chart.setPrefHeight(170); chart.setStyle("-fx-background-color:transparent;");

        // Revenue series
        XYChart.Series<Number,Number> revSeries = new XYChart.Series<>(); revSeries.setName("Revenue");
        java.util.LinkedHashMap<String,Double> revMap = transDAO.getMonthlyRevenueBreakdown();
        java.util.List<Double> revVals = new java.util.ArrayList<>(revMap.values());
        while (revVals.size() < 6) revVals.add(0, 0.0);
        for (int i = 0; i < Math.min(6, revVals.size()); i++) revSeries.getData().add(new XYChart.Data<>(i+1, revVals.get(revVals.size()-6+i)));

        // Expense series
        XYChart.Series<Number,Number> expSeries = new XYChart.Series<>(); expSeries.setName("Expenses");
        java.util.LinkedHashMap<String,Double> expMap = transDAO.getMonthlyExpenseBreakdown();
        java.util.List<Double> expVals = new java.util.ArrayList<>(expMap.values());
        while (expVals.size() < 6) expVals.add(0, 0.0);
        for (int i = 0; i < Math.min(6, expVals.size()); i++) expSeries.getData().add(new XYChart.Data<>(i+1, expVals.get(expVals.size()-6+i)));

        chart.getData().addAll(revSeries, expSeries);
        chartCard.getChildren().addAll(cTitle, chart);

        // Recent transactions — real from DB
        VBox txCard = new VBox(0); txCard.setStyle(CARD); txCard.setPadding(new Insets(20));
        Label txTitle = new Label("Recent Transactions"); txTitle.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1e293b;-fx-padding:0 0 8 0;");
        txCard.getChildren().add(txTitle);
        java.util.List<com.kaziflow.models.Transaction> recentTxns = transDAO.findAll();
        int txLimit = Math.min(4, recentTxns.size());
        if (txLimit == 0) {
            txCard.getChildren().add(new Label("No transactions yet."));
        } else {
            for (int i = 0; i < txLimit; i++) {
                com.kaziflow.models.Transaction tx = recentTxns.get(i);
                boolean isIncome = "income".equals(tx.getTransactionType());
                HBox txRow = new HBox(10); txRow.setAlignment(Pos.CENTER_LEFT);
                txRow.setPadding(new Insets(8,0,8,0));
                txRow.setStyle("-fx-border-color:transparent transparent #f1f5f9 transparent;-fx-border-width:0 0 1 0;");
                Label arrow = new Label(isIncome ? "↑" : "↓");
                arrow.setStyle("-fx-text-fill:"+(isIncome?"#16a34a":"#dc2626")+";-fx-font-size:14px;-fx-font-weight:bold;");
                Label desc = new Label(tx.getDescription()); desc.setStyle("-fx-font-size:12px;-fx-text-fill:#1e293b;"); HBox.setHgrow(desc,Priority.ALWAYS);
                Label amt = new Label(KESFormatter.format(tx.getAmount()));
                amt.setStyle("-fx-font-weight:bold;-fx-font-size:12px;-fx-text-fill:"+(isIncome?"#16a34a":"#dc2626")+";");
                txRow.getChildren().addAll(arrow,desc,amt);
                txCard.getChildren().add(txRow);
            }
        }
        right.getChildren().addAll(chartCard, txCard);
        mainRow.getChildren().addAll(acctCard, right);
        content.getChildren().addAll(stats, mainRow);
        return content;
    }

    // ═══ TAB 2 · EXPENSES ════════════════════════════════════════════════════

    private VBox buildExpensesView() {
        VBox view = new VBox(0); view.setStyle("-fx-background-color:#f8fafc;");
        Button addBtn = new Button("+ Add Expense");
        addBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        addBtn.setOnAction(e -> showAddExpenseDialog());
        view.getChildren().addAll(pageHeader("Expense Tracking","Finance › Expenses", addBtn), buildExpensesBody());
        return view;
    }

    private VBox buildExpensesBody() {
        VBox content = new VBox(20); content.setPadding(new Insets(24));
        double total = expenseDAO.getTotal(); double monthTotal = expenseDAO.getMonthTotal();
        HBox stats = statsRow(
            statCard("Total Expenses",  KESFormatter.formatShort(total>0?total:1240000),      "All time",         "#1e293b"),
            statCard("This Month",      KESFormatter.formatShort(monthTotal>0?monthTotal:320000),"October 2024",  "#dc2626"),
            statCard("Avg Per Month",   KESFormatter.formatShort(total>0?total/12:103333),     "Last 12 months",  "#d97706"),
            statCard("Top Category",    "Payroll",                                             "42% of total",    "#7c3aed")
        );

        HBox filterBar = new HBox(10); filterBar.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField(); search.setPromptText("Search expenses..."); search.getStyleClass().add("search-box"); search.setPrefWidth(260);
        search.textProperty().addListener((obs,o,v) -> {
            if (v.isBlank()) loadExpenses();
            else expenseData.setAll(expenseDAO.findAll().stream().filter(ex ->
                ex.getDescription().toLowerCase().contains(v.toLowerCase()) ||
                (ex.getCategory()!=null&&ex.getCategory().toLowerCase().contains(v.toLowerCase()))
            ).toList());
        });
        ComboBox<String> catFilter = new ComboBox<>();
        catFilter.getItems().addAll("All Categories","Payroll","Rent","Utilities","Supplies","Transport","Maintenance","Marketing","Other");
        catFilter.setValue("All Categories"); catFilter.setPrefHeight(36);
        catFilter.setOnAction(e -> {
            String sel = catFilter.getValue();
            if ("All Categories".equals(sel)) loadExpenses();
            else expenseData.setAll(expenseDAO.findAll().stream().filter(ex -> sel.equals(ex.getCategory())).toList());
        });
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button exportBtn = new Button("⬇ Export CSV");
        exportBtn.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-pref-height:36px;-fx-padding:0 14;-fx-cursor:hand;-fx-font-size:13px;");
        exportBtn.setOnAction(e -> new com.kaziflow.services.ReportExportService().exportCSV(com.kaziflow.services.ReportExportService.ReportType.EXPENSES));
        filterBar.getChildren().addAll(search, catFilter, sp, exportBtn);

        VBox tableCard = new VBox(0); tableCard.setStyle(CARD);
        expenseTable = buildExpenseTable();
        tableCard.getChildren().add(expenseTable);
        loadExpenses();

        content.getChildren().addAll(stats, filterBar, tableCard);
        return content;
    }

    @SuppressWarnings("unchecked")
    private TableView<Expense> buildExpenseTable() {
        TableView<Expense> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setPrefHeight(440); tv.setStyle("-fx-background-color:white;"); tv.setItems(expenseData);

        TableColumn<Expense,String> descCol = new TableColumn<>("DESCRIPTION");
        descCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getDescription())); descCol.setPrefWidth(240);

        TableColumn<Expense,String> catCol = new TableColumn<>("CATEGORY");
        catCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getCategory()!=null?d.getValue().getCategory():"Other"));
        catCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String cat, boolean empty) {
                super.updateItem(cat,empty); if(empty||cat==null){setGraphic(null);return;}
                String fg,bg;
                switch(cat) { case "Payroll"->{fg="#7c3aed";bg="#ede9fe";} case "Rent"->{fg="#2563eb";bg="#eff6ff";} case "Utilities"->{fg="#d97706";bg="#fef3c7";} case "Supplies"->{fg="#16a34a";bg="#dcfce7";} default->{fg="#475569";bg="#f1f5f9";} }
                Label badge=new Label(cat); badge.setStyle("-fx-background-color:"+bg+";-fx-text-fill:"+fg+";-fx-font-size:11px;-fx-background-radius:20;-fx-padding:3 10;-fx-font-weight:bold;");
                setGraphic(badge); setText(null);
            }
        }); catCol.setPrefWidth(120);

        TableColumn<Expense,String> amtCol = new TableColumn<>("AMOUNT");
        amtCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(KESFormatter.format(d.getValue().getAmount())));
        amtCol.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(String a, boolean empty) {
                super.updateItem(a,empty); if(empty||a==null){setText(null);return;}
                Label l=new Label(a); l.setStyle("-fx-font-weight:bold;-fx-font-size:13px;-fx-text-fill:#dc2626;"); setGraphic(l); setText(null);
            }
        }); amtCol.setPrefWidth(130);

        TableColumn<Expense,String> pmtCol = new TableColumn<>("PAYMENT");
        pmtCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getPaymentMethod()!=null?d.getValue().getPaymentMethod():"cash")); pmtCol.setPrefWidth(110);

        TableColumn<Expense,String> dateCol = new TableColumn<>("DATE");
        dateCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getExpenseDate()!=null?d.getValue().getExpenseDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy")):"—")); dateCol.setPrefWidth(110);

        TableColumn<Expense,String> rcptCol = new TableColumn<>("RECEIPT #");
        rcptCol.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getReceiptNumber()!=null?d.getValue().getReceiptNumber():"—")); rcptCol.setPrefWidth(100);

        TableColumn<Expense,Void> actCol = new TableColumn<>(""); actCol.setPrefWidth(50);
        actCol.setCellFactory(c->new TableCell<>(){
            private final Button del=new Button("✕");
            {del.setStyle("-fx-background-color:transparent;-fx-text-fill:#dc2626;-fx-cursor:hand;-fx-font-size:13px;-fx-border-color:transparent;");
             del.setOnAction(e->{Expense ex=getTableView().getItems().get(getIndex());
                Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Delete this expense?",ButtonType.YES,ButtonType.NO);confirm.setHeaderText(null);
                confirm.showAndWait().ifPresent(r->{if(r==ButtonType.YES){
                    expenseDAO.delete(ex.getId());
                    com.kaziflow.services.AuditLog.log("EXPENSE_DELETED","Expense deleted: "+ex.getDescription(),"finance",ex.getId());
                    loadExpenses();
                }});});
            }
            @Override protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);setGraphic(empty?null:del);}
        });

        tv.getColumns().addAll(descCol,catCol,amtCol,pmtCol,dateCol,rcptCol,actCol);
        return tv;
    }

    private void loadExpenses() { expenseData.setAll(expenseDAO.findAll()); }

    private void showAddExpenseDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Expense"); dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color:white;"); dialog.getDialogPane().setPrefWidth(480);

        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(20));
        TextField descField   = fld("e.g. Monthly Rent - CBD Office","");
        TextField amountField = fld("e.g. 45000","");
        TextField rcptField   = fld("e.g. RCP-001 (optional)","");
        TextArea  notes       = new TextArea(); notes.setPromptText("Additional notes..."); notes.setPrefRowCount(2); notes.setPrefWidth(200);
        notes.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;");

        ComboBox<String> catCombo = new ComboBox<>();
        catCombo.getItems().addAll("Payroll","Rent","Utilities","Supplies","Transport","Maintenance","Marketing","Office Expenses","Insurance","Other");
        catCombo.setValue("Other"); catCombo.setPrefWidth(200);

        ComboBox<String> pmtCombo = new ComboBox<>();
        pmtCombo.getItems().addAll("cash","mpesa","bank transfer","cheque","card");
        pmtCombo.setValue("cash"); pmtCombo.setPrefWidth(200);

        DatePicker datePicker = new DatePicker(LocalDate.now()); datePicker.setPrefWidth(200);

        // Validation highlight
        Label validationMsg = new Label(""); validationMsg.setStyle("-fx-text-fill:#dc2626;-fx-font-size:12px;");

        form.addRow(0, lbl("Description *"), descField,   lbl("Amount (KES) *"), amountField);
        form.addRow(1, lbl("Category"),      catCombo,    lbl("Payment Method"), pmtCombo);
        form.addRow(2, lbl("Date"),          datePicker,  lbl("Receipt #"),      rcptField);
        form.addRow(3, lbl("Notes"),         notes);
        form.add(validationMsg, 0, 4, 4, 1);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            boolean valid = true;
            if (descField.getText().trim().isEmpty()) { descField.setStyle(descField.getStyle()+"-fx-border-color:#dc2626;"); valid=false; }
            else descField.setStyle(fld("","").getStyle());
            try { Double.parseDouble(amountField.getText().trim()); amountField.setStyle(fld("","").getStyle()); }
            catch (Exception e) { amountField.setStyle(amountField.getStyle()+"-fx-border-color:#dc2626;"); valid=false; }
            if (!valid) { validationMsg.setText("Please fill in required fields correctly."); ev.consume(); }
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                Expense e = new Expense();
                e.setDescription(descField.getText().trim());
                e.setCategory(catCombo.getValue());
                e.setAmount(Double.parseDouble(amountField.getText().trim()));
                e.setPaymentMethod(pmtCombo.getValue());
                e.setReceiptNumber(rcptField.getText().trim());
                e.setNotes(notes.getText().trim());
                e.setExpenseDate(datePicker.getValue());
                try { e.setCreatedBy(SessionManager.getInstance().getCurrentUser().getId()); } catch (Exception ignored) {}
                if (expenseDAO.save(e)) {
                    com.kaziflow.services.AuditLog.logExpenseAdded(e.getAmount(), e.getCategory());
                    Toast.success(SceneManager.getInstance().getStage(),
                        "Expense recorded", KESFormatter.format(e.getAmount()) + " — " + e.getCategory());
                    loadExpenses();
                } else {
                    Toast.error(SceneManager.getInstance().getStage(), "Save failed", "Could not save expense.");
                }
            }
        });
    }

    // ═══ TAB 3 · VAT SUMMARY ═════════════════════════════════════════════════

    private VBox buildVatSummary() {
        VBox view = new VBox(0); view.setStyle("-fx-background-color:#f8fafc;");
        Button exportBtn = new Button("⬇ Export VAT Report");
        exportBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        exportBtn.setOnAction(e -> new com.kaziflow.services.ReportExportService().exportCSV(com.kaziflow.services.ReportExportService.ReportType.FINANCE_SUMMARY));
        view.getChildren().addAll(pageHeader("VAT Summary","Finance › VAT Summary", exportBtn), buildVatBody());
        return view;
    }

    private VBox buildVatBody() {
        VBox content = new VBox(20); content.setPadding(new Insets(24));
        double rev = transDAO.getTotalRevenue();
        double exp = transDAO.getTotalExpenses();
        double vatOut = rev * 0.16;
        double vatIn  = exp * 0.16;
        double net    = vatOut - vatIn;

        HBox stats = statsRow(
            statCard("Output VAT (Sales)",    KESFormatter.formatShort(vatOut), "16% on sales",     "#1e293b"),
            statCard("Input VAT (Purchases)", KESFormatter.formatShort(vatIn),  "16% on purchases", "#1e293b"),
            statCard("Net VAT Payable",       KESFormatter.formatShort(net),    "Payable to KRA",   "#dc2626"),
            statCard("VAT Rate",              "16%",                            "Standard rate",    "#2563eb")
        );

        // Monthly breakdown table
        VBox vatCard = new VBox(0); vatCard.setStyle(CARD); vatCard.setPadding(new Insets(20));
        Label vatTitle = new Label("Monthly VAT Breakdown"); vatTitle.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#1e293b;-fx-padding:0 0 12 0;");

        HBox hdr = new HBox(); hdr.setStyle("-fx-background-color:#f8fafc;-fx-padding:10 0;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        for (String col : new String[]{"PERIOD","TAXABLE SALES","OUTPUT VAT","PURCHASES","INPUT VAT","NET PAYABLE","STATUS"}) {
            Label cl = new Label(col); cl.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#94a3b8;");
            HBox.setHgrow(cl, Priority.ALWAYS); hdr.getChildren().add(cl);
        }
        vatCard.getChildren().addAll(vatTitle, hdr);

        // Real monthly VAT data from DB (last 6 months)
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 0; i < 6; i++) {
            java.time.LocalDate d = today.minusMonths(i);
            int yr = d.getYear(); int mo = d.getMonthValue();
            String label = d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) + " " + yr;
            double mRev = transDAO.getRevenueForMonth(yr, mo);
            double mExp = transDAO.getExpensesForMonth(yr, mo);
            double mOut = mRev * 0.16;
            double mIn  = mExp * 0.16;
            double mNet = mOut - mIn;
            boolean isFiled = i >= 2;
            HBox row = new HBox(); row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10,0,10,0));
            row.setStyle("-fx-border-color:transparent transparent #f1f5f9 transparent;-fx-border-width:0 0 1 0;");
            String[] values = {label, KESFormatter.format(mRev), KESFormatter.format(mOut), KESFormatter.format(mExp), KESFormatter.format(mIn), KESFormatter.format(mNet)};
            for (String v : values) {
                Label vl = new Label(v); vl.setStyle("-fx-font-size:12px;-fx-text-fill:#1e293b;"); HBox.setHgrow(vl,Priority.ALWAYS); row.getChildren().add(vl);
            }
            String filedBg = isFiled?"#dcfce7":"#fef3c7"; String filedFg = isFiled?"#16a34a":"#d97706"; String filedTxt = isFiled?"Filed":"Pending";
            Label status = new Label(filedTxt); status.setStyle("-fx-background-color:"+filedBg+";-fx-text-fill:"+filedFg+";-fx-font-size:11px;-fx-background-radius:20;-fx-padding:3 10;-fx-font-weight:bold;");
            HBox.setHgrow(status, Priority.ALWAYS); row.getChildren().add(status);
            vatCard.getChildren().add(row);
        }

        VBox reminder = new VBox(6);
        reminder.setStyle("-fx-background-color:#eff6ff;-fx-background-radius:10;-fx-padding:16;-fx-border-color:#bfdbfe;-fx-border-radius:10;-fx-border-width:1;");
        Label ri = new Label("ℹ VAT Filing Reminder — Due 20th of each month");
        ri.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#2563eb;");
        Label rd = new Label("File via KRA iTax portal (www.kra.go.ke/itax). Ensure all sales invoices are ETIMS-compliant. Consult your tax advisor for specific situations.");
        rd.setStyle("-fx-text-fill:#1e40af;-fx-font-size:12px;-fx-wrap-text:true;"); rd.setWrapText(true);
        reminder.getChildren().addAll(ri, rd);

        content.getChildren().addAll(stats, vatCard, reminder);
        return content;
    }

    // ═══ SHARED HELPERS ═══════════════════════════════════════════════════════

    private static final String CARD = "-fx-background-color:white;-fx-background-radius:12;-fx-border-color:#e2e8f0;-fx-border-radius:12;-fx-border-width:1;";

    private HBox pageHeader(String title, String breadcrumb, Button action) {
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16,24,16,24));
        bar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");
        VBox tb = new VBox(2);
        Label h = new Label(title); h.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label bc = new Label(breadcrumb); bc.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12px;");
        tb.getChildren().addAll(h, bc);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        bar.getChildren().addAll(tb, sp);
        if (action != null) bar.getChildren().add(action);
        return bar;
    }

    private HBox statsRow(VBox... cards) {
        HBox row = new HBox(16);
        for (VBox c : cards) { row.getChildren().add(c); HBox.setHgrow(c, Priority.ALWAYS); }
        return row;
    }

    private VBox statCard(String label, String value, String note, String color) {
        VBox card = new VBox(6); card.setStyle(CARD + "-fx-padding:20;");
        Label lbl = new Label(label); lbl.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12px;-fx-font-weight:bold;");
        Label val = new Label(value); val.setStyle("-fx-font-size:26px;-fx-font-weight:bold;-fx-text-fill:"+color+";");
        Label nt  = new Label(note);  nt .setStyle("-fx-text-fill:#94a3b8;-fx-font-size:12px;");
        card.getChildren().addAll(lbl, val, nt); return card;
    }

    private TextField fld(String prompt, String val) {
        TextField tf = new TextField(val); tf.setPromptText(prompt); tf.setPrefWidth(200);
        tf.setStyle("-fx-pref-height:34px;-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");
        return tf;
    }

    private Label lbl(String text) {
        Label l = new Label(text); l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;"); return l;
    }
}
