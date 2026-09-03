package com.kaziflow.views;

import com.kaziflow.dao.*;
import com.kaziflow.models.Product;
import com.kaziflow.models.Sale;
import com.kaziflow.utils.AsyncTask;
import com.kaziflow.utils.KESFormatter;
import com.kaziflow.utils.SceneManager;
import com.kaziflow.utils.Toast;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

import com.kaziflow.dao.DeniDAO;
import java.util.List;

public class DashboardView {

    private VBox root;
    private final SaleDAO saleDAO = new SaleDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final TransactionDAO transDAO = new TransactionDAO();

    public DashboardView() {
        buildUI();
    }

    public VBox getRoot() { return root; }

    private void buildUI() {
        root = new VBox(0);
        root.setStyle("-fx-background-color: #f8fafc;");

        // ── Top bar ──
        HBox topbar = buildTopBar();

        // ── Scrollable content ──
        VBox content = new VBox(20);
        content.setPadding(new Insets(24));

        // ── Header ──
        VBox header = new VBox(4);
        Label title = new Label("Overview");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label subtitle = new Label("Here's what's happening in your business today.");
        subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        header.getChildren().addAll(title, subtitle);

        // ── Stat Cards Row ──
        HBox statsRow = buildStatsRow();

        // ── Middle row: Chart + Payments ──
        HBox middleRow = new HBox(20);
        VBox chartCard = buildSalesChart();
        HBox.setHgrow(chartCard, Priority.ALWAYS);

        VBox paymentsCard = buildOutstandingPayments();
        paymentsCard.setPrefWidth(300);

        middleRow.getChildren().addAll(chartCard, paymentsCard);

        // ── Bottom row: Low Stock + Top Selling ──
        HBox bottomRow = new HBox(20);
        VBox lowStockCard  = buildLowStockCard();
        HBox.setHgrow(lowStockCard, Priority.ALWAYS);
        VBox topSellingCard = buildTopSellingCard();
        HBox.setHgrow(topSellingCard, Priority.ALWAYS);
        VBox analyticsCard  = buildAnalyticsCard();
        HBox.setHgrow(analyticsCard, Priority.ALWAYS);
        bottomRow.getChildren().addAll(lowStockCard, topSellingCard, analyticsCard);

        // ── New row: Top Customers + Cash Flow (Phase 10C) ──
        HBox reportsRow = new HBox(20);
        VBox topCustomersCard = buildTopCustomersCard();
        HBox.setHgrow(topCustomersCard, Priority.ALWAYS);
        VBox cashFlowCard = buildCashFlowCard();
        HBox.setHgrow(cashFlowCard, Priority.ALWAYS);
        reportsRow.getChildren().addAll(topCustomersCard, cashFlowCard);

        content.getChildren().addAll(header, statsRow, middleRow, bottomRow, reportsRow);
        root.getChildren().addAll(topbar, content);
    }

    private VBox buildTopCustomersCard() {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        card.setPrefWidth(420);

        Label title = new Label("Top 10 Customers — Last 30 Days");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        card.getChildren().add(title);

        java.time.LocalDate today = java.time.LocalDate.now();
        List<Object[]> topCustomers = saleDAO.getTopCustomers(today.minusDays(30).toString(), today.toString(), 10);

        if (topCustomers.isEmpty()) {
            Label empty = new Label("No named-customer sales in the last 30 days (walk-in sales aren't ranked here).");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            empty.setWrapText(true);
            card.getChildren().add(empty);
            return card;
        }

        int rank = 1;
        for (Object[] c : topCustomers) {
            String name = (String) c[0];
            int orders = (int) c[1];
            double spend = (double) c[2];

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 0, 6, 0));
            row.setStyle("-fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");

            Label rankLabel = new Label("#" + rank++);
            rankLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #94a3b8; -fx-min-width: 24;");

            VBox info = new VBox(2);
            Label nameLabel = new Label(name);
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
            Label ordersLabel = new Label(orders + " order" + (orders == 1 ? "" : "s"));
            ordersLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
            info.getChildren().addAll(nameLabel, ordersLabel);
            HBox.setHgrow(info, Priority.ALWAYS);

            Label spendLabel = new Label(KESFormatter.format(spend));
            spendLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");

            row.getChildren().addAll(rankLabel, info, spendLabel);
            card.getChildren().add(row);
        }
        return card;
    }

    private VBox buildCashFlowCard() {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");

        Label title = new Label("Cash Flow — Last 14 Days");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("KES");
        javafx.scene.chart.LineChart<String, Number> chart = new javafx.scene.chart.LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setPrefHeight(240);
        chart.setCreateSymbols(false);
        chart.setStyle("-fx-background-color: transparent;");

        java.time.LocalDate end = java.time.LocalDate.now();
        java.time.LocalDate start = end.minusDays(13);
        java.util.LinkedHashMap<String, double[]> flow = transDAO.getDailyCashFlowForRange(start, end);

        XYChart.Series<String, Number> incomeSeries = new XYChart.Series<>();
        incomeSeries.setName("Income");
        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("Expenses");

        for (java.time.LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            double[] pair = flow.getOrDefault(d.toString(), new double[]{0, 0});
            String label = d.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM"));
            incomeSeries.getData().add(new XYChart.Data<>(label, pair[0]));
            expenseSeries.getData().add(new XYChart.Data<>(label, pair[1]));
        }
        chart.getData().addAll(incomeSeries, expenseSeries);

        card.getChildren().addAll(title, chart);
        return card;
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 24, 0, 24));
        bar.setPrefHeight(60);
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        TextField search = new TextField();
        search.setPromptText("Search products, orders...");
        search.getStyleClass().add("search-box");
        search.setPrefWidth(300);
        HBox.setHgrow(search, Priority.NEVER);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Toggle buttons — previously purely decorative (no listener at all was attached to any
        // of them). Wired up as quick-filter shortcuts for the sales chart built in v11, since
        // that already has real date-range filtering infrastructure to reuse.
        ToggleGroup tg = new ToggleGroup();
        ToggleButton daily = new ToggleButton("Daily"); daily.setToggleGroup(tg); daily.setSelected(true);
        ToggleButton weekly = new ToggleButton("Weekly"); weekly.setToggleGroup(tg);
        ToggleButton monthly = new ToggleButton("Monthly"); monthly.setToggleGroup(tg);
        for (ToggleButton tb : new ToggleButton[]{daily, weekly, monthly}) {
            tb.setStyle("-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 12px; -fx-pref-height: 32px; -fx-padding: 0 12;");
        }
        daily.setOnAction(e -> { chartRangeStart = java.time.LocalDate.now(); chartRangeEnd = java.time.LocalDate.now(); if (salesChart != null) refreshSalesChart(); });
        weekly.setOnAction(e -> { chartRangeStart = java.time.LocalDate.now().minusDays(6); chartRangeEnd = java.time.LocalDate.now(); if (salesChart != null) refreshSalesChart(); });
        monthly.setOnAction(e -> { chartRangeStart = java.time.LocalDate.now().minusDays(29); chartRangeEnd = java.time.LocalDate.now(); if (salesChart != null) refreshSalesChart(); });
        // Same deselection footgun found and fixed elsewhere this session (ProfileSelector,
        // SalesView, Network/Language dialogs) — never allow this group to end up with nothing selected.
        tg.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) tg.selectToggle(oldToggle);
        });

        Button addSaleBtn = new Button("+ Add Sale");
        addSaleBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-size: 13px; -fx-background-radius: 8; -fx-pref-height: 36px; -fx-padding: 0 16; -fx-cursor: hand;");
        addSaleBtn.setOnAction(e -> SceneManager.getInstance().navigateTo("sales"));

        Button addPurchBtn = new Button("Add Purchase");
        addPurchBtn.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-size: 13px; -fx-pref-height: 36px; -fx-padding: 0 14; -fx-cursor: hand;");
        addPurchBtn.setOnAction(e -> SceneManager.getInstance().navigateTo("purchases"));

        bar.getChildren().addAll(search, spacer, daily, weekly, monthly, addPurchBtn, addSaleBtn);
        return bar;
    }

    private HBox buildStatsRow() {
        HBox row = new HBox(16);

        double todayRevenue = saleDAO.getTodayRevenue();
        int todayCount      = saleDAO.getTodayCount();
        int lowStock        = productDAO.getLowStockCount();

        // Expiry alert count — best-effort, silently 0 if batches table not yet created
        int expiringSoon = 0;
        try {
            com.kaziflow.dao.BatchDAO batchDAO = new com.kaziflow.dao.BatchDAO();
            batchDAO.updateExpiredBatches();
            expiringSoon = batchDAO.getExpiringSoonCount(batchDAO.getAlertDays());
        } catch (Exception ignored) {}

        row.getChildren().addAll(
            statCard("Today's Revenue",   KESFormatter.format(todayRevenue), null, "Total sales today", "#2563eb"),
            statCard("Today's Sales",     String.valueOf(todayCount),        null, "Transactions", "#16a34a"),
            statCard("Low Stock",         lowStock + " Items",               null, "Below minimum level", "#dc2626"),
            statCard("Expiring Soon",     expiringSoon + " Batches",         null, "Within alert window", "#d97706")
        );

        for (var child : row.getChildren()) {
            HBox.setHgrow((Region) child, Priority.ALWAYS);
        }
        return row;
    }

    private VBox statCard(String label, String value, String change, String note, String accentColor) {
        VBox card = new VBox(6);
        card.getStyleClass().add("stat-card");
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 8, 0, 0, 2);");

        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label val = new Label(value);
        val.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + accentColor + ";");
        val.setWrapText(true);

        HBox bottomRow = new HBox(4);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        if (change != null) {
            Label chg = new Label(change);
            chg.setStyle("-fx-text-fill: #16a34a; -fx-font-size: 12px; -fx-font-weight: bold;");
            bottomRow.getChildren().add(chg);
        }
        if (note != null) {
            Label noteLabel = new Label(note);
            noteLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            bottomRow.getChildren().add(noteLabel);
        }

        card.getChildren().addAll(lbl, val, bottomRow);
        return card;
    }

    // Chart is rebuilt on date-range changes; kept as a field-in-method so buildSalesChart()
    // itself can stay small — the actual (re)population lives in refreshSalesChart().
    private BarChart<String, Number> salesChart;
    private Label rangeSummaryLabel;
    private java.time.LocalDate chartRangeStart = java.time.LocalDate.now().minusDays(6);
    private java.time.LocalDate chartRangeEnd = java.time.LocalDate.now();

    private VBox buildSalesChart() {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox titleBox = new VBox(2);
        Label title = new Label("Sales Analytics");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        rangeSummaryLabel = new Label();
        rangeSummaryLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        titleBox.getChildren().addAll(title, rangeSummaryLabel);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        // Date-range filter — defaults to the last 7 days, but is fully user-driven from here on.
        DatePicker fromPicker = new DatePicker(chartRangeStart);
        DatePicker toPicker = new DatePicker(chartRangeEnd);
        fromPicker.setPrefWidth(130);
        toPicker.setPrefWidth(130);
        Button applyBtn = new Button("Apply");
        applyBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-size: 12px; -fx-background-radius: 6; -fx-pref-height: 30px; -fx-cursor: hand;");
        applyBtn.setOnAction(e -> {
            if (fromPicker.getValue() == null || toPicker.getValue() == null) return;
            if (fromPicker.getValue().isAfter(toPicker.getValue())) {
                Toast.error(SceneManager.getInstance().getStage(), "Invalid Range", "Start date must be before end date.");
                return;
            }
            long days = java.time.temporal.ChronoUnit.DAYS.between(fromPicker.getValue(), toPicker.getValue());
            if (days > 366) {
                Toast.error(SceneManager.getInstance().getStage(), "Range Too Large",
                    "Please pick a range of 366 days or less — a wider range would render a bar per day and make the chart unusable.");
                return;
            }
            chartRangeStart = fromPicker.getValue();
            chartRangeEnd = toPicker.getValue();
            refreshSalesChart();
        });

        header.getChildren().addAll(titleBox, fromPicker, toPicker, applyBtn);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("KES");
        salesChart = new BarChart<>(xAxis, yAxis);
        salesChart.setLegendVisible(false);
        salesChart.setAnimated(false);
        salesChart.setPrefHeight(240);
        salesChart.setStyle("-fx-background-color: transparent;");
        salesChart.setBarGap(2);
        salesChart.setCategoryGap(10);

        card.getChildren().addAll(header, salesChart);
        refreshSalesChart();
        return card;
    }

    /** (Re)loads the sales chart from real per-day DB revenue for [chartRangeStart, chartRangeEnd]. */
    private void refreshSalesChart() {
        String startStr = chartRangeStart.toString();
        String endStr = chartRangeEnd.toString();

        java.util.LinkedHashMap<String, Double> daily = saleDAO.getDailyRevenueForRange(startStr, endStr);
        double[] totals = saleDAO.getRevenueAndCountForRange(startStr, endStr);
        rangeSummaryLabel.setText(KESFormatter.format(totals[0]) + " across " + (int) totals[1] + " sales ("
            + chartRangeStart + " to " + chartRangeEnd + ")");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        // Walk every day in the range (not just days with sales) so gaps show as zero, not as missing bars.
        for (java.time.LocalDate d = chartRangeStart; !d.isAfter(chartRangeEnd); d = d.plusDays(1)) {
            String key = d.toString();
            double revenue = daily.getOrDefault(key, 0.0);
            String label = d.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM"));
            XYChart.Data<String, Number> point = new XYChart.Data<>(label, revenue);
            final String dateForDrillThrough = key;
            point.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode == null) return;
                newNode.setStyle("-fx-bar-fill: #2563eb; -fx-cursor: hand;");
                newNode.setOnMouseClicked(e -> showDayDrillThrough(dateForDrillThrough));
                javafx.scene.control.Tooltip.install(newNode,
                    new javafx.scene.control.Tooltip(label + ": " + KESFormatter.format(revenue)));
            });
            series.getData().add(point);
        }
        salesChart.getData().setAll(series);
    }

    /** Drill-through: clicking a bar shows every sale made that day. */
    private void showDayDrillThrough(String date) {
        List<Sale> daySales = saleDAO.findByDateRange(date, date);
        Dialog<Void> dialog = new Dialog<>();
        com.kaziflow.utils.DialogUtils.makeResizable(dialog);
        dialog.setTitle("Sales on " + date);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(480);

        VBox content = new VBox(6);
        content.setPadding(new Insets(10));
        if (daySales.isEmpty()) {
            content.getChildren().add(new Label("No completed sales on this day."));
        } else {
            for (Sale s : daySales) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                Label num = new Label(s.getSaleNumber());
                num.setStyle("-fx-font-weight: bold; -fx-min-width: 90;");
                Label cust = new Label(s.getCustomerName());
                HBox.setHgrow(cust, Priority.ALWAYS);
                Label amt = new Label(KESFormatter.format(s.getTotalAmount()));
                amt.setStyle("-fx-font-weight: bold;");
                row.getChildren().addAll(num, cust, amt);
                content.getChildren().add(row);
            }
        }
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(320);
        dialog.getDialogPane().setContent(scroll);
        dialog.initOwner(SceneManager.getInstance().getStage());
        dialog.showAndWait();
    }

    private VBox buildOutstandingPayments() {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");

        Label title = new Label("Outstanding Payments");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        card.getChildren().add(title);

        // Real data from the Deni Book — unpaid/partial balances, largest first.
        DeniDAO deniDAO = new DeniDAO();
        List<String[]> outstanding = deniDAO.findAll("unpaid_and_partial");

        if (outstanding.isEmpty()) {
            Label empty = new Label("No outstanding balances.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            card.getChildren().add(empty);
            return card;
        }

        int shown = 0;
        for (String[] entry : outstanding) {
            if (shown++ >= 4) break; // card is a compact summary — full list lives in the Deni Book module
            String name = entry[1];
            double balance = Double.parseDouble(entry[6]);
            String daysAgoText = entry[9]; // e.g. "12 days ago"
            int daysAgo = 0;
            try { daysAgo = Integer.parseInt(daysAgoText.split(" ")[0]); } catch (Exception ignored) {}

            String status;
            String color;
            if (daysAgo > 30) { status = "OVERDUE"; color = "#dc2626"; }
            else if (daysAgo > 14) { status = "DUE SOON"; color = "#d97706"; }
            else { status = "PENDING"; color = "#94a3b8"; }

            VBox row = new VBox(4);
            row.setPadding(new Insets(10, 0, 10, 0));
            row.setStyle("-fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");

            HBox topRow = new HBox();
            Label nameLabel = new Label(name);
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
            HBox.setHgrow(nameLabel, Priority.ALWAYS);
            Label statusLabel = new Label(status);
            statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 11px; -fx-font-weight: bold;");
            topRow.getChildren().addAll(nameLabel, statusLabel);

            Label amtLabel = new Label("Amount Due");
            amtLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
            Label amount = new Label(KESFormatter.format(balance));
            amount.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1e293b;");

            row.getChildren().addAll(topRow, amtLabel, amount);
            card.getChildren().add(row);
        }

        Hyperlink viewAll = new Hyperlink("View All Payables");
        viewAll.setStyle("-fx-text-fill: #2563eb; -fx-font-size: 13px;");
        viewAll.setOnAction(e -> SceneManager.getInstance().navigateTo("purchases"));
        card.getChildren().add(viewAll);

        return card;
    }

    private VBox buildLowStockCard() {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");

        HBox header = new HBox();
        Label title = new Label("Low Stock Alerts");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        HBox.setHgrow(title, Priority.ALWAYS);
        Hyperlink viewAll = new Hyperlink("View All");
        viewAll.setStyle("-fx-text-fill: #2563eb; -fx-font-size: 13px;");
        viewAll.setOnAction(e -> SceneManager.getInstance().navigateTo("inventory"));
        header.getChildren().addAll(title, viewAll);

        List<Product> lowStock = productDAO.findLowStock();
        if (lowStock.isEmpty()) {
            Label ok = new Label("All stock levels are healthy ✓");
            ok.setStyle("-fx-text-fill: #16a34a; -fx-font-size: 13px;");
            card.getChildren().addAll(header, ok);
            return card;
        }

        card.getChildren().add(header);
        int limit = Math.min(4, lowStock.size());
        for (Product p : lowStock.subList(0, limit)) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 0, 6, 0));
            row.setStyle("-fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");

            StackPane dot = new StackPane();
            dot.setStyle("-fx-background-color: #fee2e2; -fx-background-radius: 8; -fx-padding: 8;");
            Label dotL = new Label("▲");
            dotL.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 11px;");
            dot.getChildren().add(dotL);

            VBox info = new VBox(2);
            Label name = new Label(p.getName());
            name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
            Label cat = new Label(p.getCategoryName() != null ? p.getCategoryName() : "—");
            cat.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
            info.getChildren().addAll(name, cat);
            HBox.setHgrow(info, Priority.ALWAYS);

            String qtyColor = p.isOutOfStock() ? "#dc2626" : "#d97706";
            Label qty = new Label((int) p.getStockQuantity() + " " + (p.getUnit() != null ? p.getUnit() : "pcs"));
            qty.setStyle("-fx-text-fill: " + qtyColor + "; -fx-font-weight: bold; -fx-font-size: 12px;");

            row.getChildren().addAll(dot, info, qty);
            card.getChildren().add(row);
        }
        return card;
    }

    private VBox buildTopSellingCard() {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");

        HBox header = new HBox();
        Label title = new Label("Top Selling");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        HBox.setHgrow(title, Priority.ALWAYS);
        Label period = new Label("Last 30 Days");
        period.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        header.getChildren().addAll(title, period);
        card.getChildren().add(header);

        // Real data — top products by revenue over the last 30 days, not sample rows.
        java.time.LocalDate today = java.time.LocalDate.now();
        String start = today.minusDays(30).toString();
        String end = today.toString();
        List<Object[]> topProducts = saleDAO.getTopProducts(start, end, 5);

        if (topProducts.isEmpty()) {
            Label empty = new Label("No sales in the last 30 days yet.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
            card.getChildren().add(empty);
            return card;
        }

        int rank = 1;
        for (Object[] p : topProducts) {
            String name = (String) p[0];
            double qty = (double) p[1];
            double revenue = (double) p[2];

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 0, 8, 0));
            row.setStyle("-fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");

            Label rankLabel = new Label("#" + rank++);
            rankLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #94a3b8; -fx-min-width: 24;");

            StackPane imgPlaceholder = new StackPane();
            imgPlaceholder.setStyle("-fx-background-color: #eff6ff; -fx-background-radius: 6; -fx-min-width: 36; -fx-min-height: 36;");
            Label imgL = new Label("⊞");
            imgL.setStyle("-fx-font-size: 14px; -fx-text-fill: #2563eb;");
            imgPlaceholder.getChildren().add(imgL);

            VBox info = new VBox(2);
            Label nameLabel = new Label(name);
            nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
            Label salesLabel = new Label((int) qty + " sold");
            salesLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
            info.getChildren().addAll(nameLabel, salesLabel);
            HBox.setHgrow(info, Priority.ALWAYS);

            Label revenueLabel = new Label(KESFormatter.format(revenue));
            revenueLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");

            row.getChildren().addAll(rankLabel, imgPlaceholder, info, revenueLabel);
            card.getChildren().add(row);
        }
        return card;
    }

    // ── Predictive Analytics Card ─────────────────────────────────────────

    private VBox buildAnalyticsCard() {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; " +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");

        HBox header = new HBox();
        Label title = new Label("✦ AI Insights");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        HBox.setHgrow(title, Priority.ALWAYS);
        Label badge = new Label("LIVE");
        badge.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #16a34a; " +
            "-fx-font-size: 9px; -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 2 8;");
        header.getChildren().addAll(title, badge);
        card.getChildren().add(header);

        // Generate insights from live DAO data asynchronously
        VBox insightsBox = new VBox(8);
        Label loading = new Label("Analysing business data...");
        loading.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        insightsBox.getChildren().add(loading);
        card.getChildren().add(insightsBox);

        AsyncTask.run(() -> generateInsights(), insights -> {
            insightsBox.getChildren().clear();
            for (String[] insight : insights) {
                insightsBox.getChildren().add(insightRow(insight[0], insight[1], insight[2]));
            }
        }, err -> {
            insightsBox.getChildren().clear();
            insightsBox.getChildren().add(new Label("Could not load insights."));
        });

        return card;
    }

    private List<String[]> generateInsights() {
        List<String[]> insights = new java.util.ArrayList<>();
        try {
            // Insight 1: Stock forecast
            List<Product> lowStock = productDAO.findLowStock();
            if (!lowStock.isEmpty()) {
                insights.add(new String[]{"⚠️", lowStock.size() + " products below reorder level",
                    lowStock.stream().limit(2).map(Product::getName)
                        .collect(java.util.stream.Collectors.joining(", "))});
            }

            // Insight 2: Revenue trend
            double todayRev = saleDAO.getTodayRevenue();
            double weekRev  = saleDAO.getWeekRevenue();
            double avgDaily = weekRev / 7;
            if (todayRev > 0 && avgDaily > 0) {
                double pct = ((todayRev - avgDaily) / avgDaily) * 100;
                String dir = pct >= 0 ? "📈" : "📉";
                insights.add(new String[]{dir,
                    String.format("Today %.1f%% vs 7-day average", Math.abs(pct)),
                    String.format("Today KES %.0f | Avg KES %.0f", todayRev, avgDaily)});
            }

            // Insight 3: Deni outstanding
            try {
                DeniDAO deniDAO = new DeniDAO();
                deniDAO.ensureTables();
                double outstanding = deniDAO.getTotalOutstanding();
                int debtors        = deniDAO.getDebtorCount();
                int overdue        = deniDAO.getOverdueCount();
                if (outstanding > 0) {
                    insights.add(new String[]{"💰",
                        String.format("KES %.0f credit outstanding", outstanding),
                        debtors + " debtors" + (overdue > 0 ? " · " + overdue + " overdue" : "")});
                }
            } catch (Exception ignored) {}

            // Insight 4: Slow movers (products with stock but no recent sales)
            List<Product> all = productDAO.findAll();
            long overstocked = all.stream()
                .filter(p -> p.getStockQuantity() > p.getMinStockLevel() * 3)
                .count();
            if (overstocked > 0) {
                insights.add(new String[]{"🔄",
                    overstocked + " products may be overstocked",
                    "Consider promotions or reducing reorder quantities"});
            }

            if (insights.isEmpty()) {
                insights.add(new String[]{"✅", "All systems normal", "No anomalies detected today"});
            }
        } catch (Exception e) {
            insights.add(new String[]{"ℹ️", "Insights unavailable", e.getMessage()});
        }
        return insights;
    }

    private HBox insightRow(String icon, String main, String sub) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(6, 0, 6, 0));
        row.setStyle("-fx-border-color: transparent transparent #f1f5f9 transparent; -fx-border-width: 0 0 1 0;");

        Label ic = new Label(icon);
        ic.setStyle("-fx-font-size: 16px; -fx-min-width: 24;");

        VBox text = new VBox(2);
        Label mainLbl = new Label(main);
        mainLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1e293b;");
        mainLbl.setWrapText(true);
        Label subLbl = new Label(sub);
        subLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        subLbl.setWrapText(true);
        text.getChildren().addAll(mainLbl, subLbl);
        HBox.setHgrow(text, Priority.ALWAYS);

        row.getChildren().addAll(ic, text);
        return row;
    }
}

