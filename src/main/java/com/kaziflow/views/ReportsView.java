package com.kaziflow.views;

import com.kaziflow.dao.SaleDAO;
import com.kaziflow.dao.TransactionDAO;
import com.kaziflow.dao.ProductDAO;
import com.kaziflow.utils.KESFormatter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ReportsView {

    private VBox root;
    private VBox content;
    private HBox statsRow;
    private com.kaziflow.utils.DateRangePicker picker;
    private com.kaziflow.services.ReportExportService exporter = new com.kaziflow.services.ReportExportService();
    private final SaleDAO saleDAO = new SaleDAO();
    private final TransactionDAO transDAO = new TransactionDAO();
    private final ProductDAO productDAO = new ProductDAO();

    public ReportsView() { buildUI(); }
    public VBox getRoot() { return root; }

    private void buildUI() {
        root = new VBox(0);
        root.setStyle("-fx-background-color: #f8fafc;");

        HBox topbar = buildTopBar();
        content = new VBox(20);
        content.setPadding(new Insets(24));

        HBox periodStrip = buildPeriodStrip();
        statsRow = buildStatsRow(picker.getRange());
        HBox chartsRow = buildChartsRow();
        VBox browseSection = buildBrowseSection();

        content.getChildren().addAll(periodStrip, statsRow, chartsRow, browseSection);
        root.getChildren().addAll(topbar, content);
    }

    private Button exportSales, exportFinance;

    private HBox buildTopBar() {
        HBox bar = new HBox(); bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #e2e8f0 transparent; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(2);
        Label title = new Label("Reports & Analytics"); title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label bc = new Label("Dashboard › Reports"); bc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        titleBox.getChildren().addAll(title, bc);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        // The period lives in the strip under this bar (DateRangePicker); the exports below use it.
        // (There used to be a branch box and a date box here that filtered nothing, an "Export PDF" button that wrote a CSV,
        // and a "Schedule Report" button for a feature that does not exist. Sales/expenses/purchases/products do not record
        // a branch, so a branch filter cannot be real until they do.)
        exportSales = new Button("⬇ Sales CSV");
        exportSales.setOnAction(e -> { if (picker.isValid()) exporter.exportCSV(com.kaziflow.services.ReportExportService.ReportType.SALES, picker.getRange()); });
        exportFinance = new Button("⬇ Finance summary CSV");
        exportFinance.setOnAction(e -> { if (picker.isValid()) exporter.exportCSV(com.kaziflow.services.ReportExportService.ReportType.FINANCE_SUMMARY, picker.getRange()); });
        for (Button b : new Button[]{exportSales, exportFinance})
            b.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 34px; -fx-padding: 0 12;");

        bar.getChildren().addAll(titleBox, sp, exportSales, exportFinance);
        bar.setSpacing(10);
        return bar;
    }

    /** "Period" strip: the shared DateRangePicker; the KPI cards below follow it, and so do the two export buttons. */
    private HBox buildPeriodStrip() {
        picker = new com.kaziflow.utils.DateRangePicker();
        HBox strip = new HBox(16); strip.setAlignment(Pos.TOP_LEFT);
        strip.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-padding: 14 20;");
        Label lbl = new Label("PERIOD"); lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;"); lbl.setPadding(new Insets(8, 0, 0, 0)); lbl.setMinWidth(Region.USE_PREF_SIZE);
        picker.setPrefWidth(340);
        Label note = new Label("Revenue and expenses follow the period. Inventory value and the stock chart are as of today; the trend chart is always the last 6 months.");
        note.setWrapText(true); note.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;"); note.setPadding(new Insets(8, 0, 0, 0));
        HBox.setHgrow(note, Priority.ALWAYS);
        strip.getChildren().addAll(lbl, picker, note);
        picker.rangeProperty().addListener((o, a, r) -> {
            exportSales.setDisable(r == null); exportFinance.setDisable(r == null);
            if (r != null && statsRow != null) { statsRow = buildStatsRow(r); content.getChildren().set(1, statsRow); }   // invalid choice: keep the last cards
        });
        exportSales.setDisable(!picker.isValid()); exportFinance.setDisable(!picker.isValid());
        return strip;
    }

    private HBox buildStatsRow(com.kaziflow.utils.DateRange range) {
        HBox row = new HBox(16);
        double revenue = transDAO.getRevenue(range);
        double expenses = transDAO.getExpenses(range);
        double netProfit = revenue - expenses;
        double margin = revenue > 0 ? (netProfit / revenue * 100) : 0;
        double stockValue = productDAO.getTotalStockValue();
        // vs the period before (a calendar month vs the month before, otherwise the same number of days) — only when there is something to compare
        com.kaziflow.utils.DateRange prev = range.previousPeriod();
        double prevRevenue = prev.isAll() ? 0 : transDAO.getRevenue(prev);
        String revenueNote = prevRevenue > 0
            ? String.format(java.util.Locale.ROOT, "%+.0f%% vs previous period", (revenue - prevRevenue) / prevRevenue * 100)
            : range.label();

        row.getChildren().addAll(
            statCard("REVENUE", KESFormatter.formatShort(revenue), revenueNote, "#1e293b"),
            statCard("NET PROFIT", KESFormatter.formatShort(netProfit), revenue > 0 ? String.format(java.util.Locale.ROOT, "%.0f%% of revenue", margin) : "No sales in this period", "#1e293b"),
            statCard("EXPENSES", KESFormatter.formatShort(expenses), "Recorded in this period", "#1e293b"),
            statCard("INVENTORY VALUE", KESFormatter.formatShort(stockValue), productDAO.getTotalCount() + " active products, at selling price, as of today", "#1e293b")
        );
        for (var c : row.getChildren()) HBox.setHgrow((Region)c, Priority.ALWAYS);
        return row;
    }

    private VBox statCard(String label, String value, String note, String color) {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        Label lbl = new Label(label); lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        Label val = new Label(value); val.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label noteL = new Label(note); noteL.setStyle("-fx-text-fill: #2563eb; -fx-font-size: 12px;");
        card.getChildren().addAll(lbl, val, noteL);
        return card;
    }

    private HBox buildChartsRow() {
        HBox row = new HBox(20);

        // Revenue vs Expenses trend — real 6-month data from DB
        VBox trendCard = new VBox(12);
        trendCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        HBox.setHgrow(trendCard, Priority.ALWAYS);

        Label trendTitle = new Label("Revenue vs Expenses — Last 6 Months");
        trendTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label trendSub = new Label("Monthly totals from sales and expense records");
        trendSub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setTickLabelFormatter(new javafx.util.StringConverter<Number>() {
            @Override public String toString(Number n) { return KESFormatter.formatShort(n.doubleValue()); }
            @Override public Number fromString(String s) { return 0; }
        });
        LineChart<String,Number> trendChart = new LineChart<>(xAxis, yAxis);
        trendChart.setAnimated(false);
        trendChart.setLegendVisible(true);
        trendChart.setPrefHeight(220);
        trendChart.setStyle("-fx-background-color: transparent;");

        XYChart.Series<String,Number> revSeries = new XYChart.Series<>();
        revSeries.setName("Revenue");
        XYChart.Series<String,Number> expSeries = new XYChart.Series<>();
        expSeries.setName("Expenses");
        // one query, one set of month keys: the last 6 calendar months, each month's revenue and expenses together
        for (var e : transDAO.getMonthlyTotals(6, java.time.LocalDate.now()).entrySet()) {
            String m = e.getKey().getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) + " " + String.format("%02d", e.getKey().getYear() % 100);
            revSeries.getData().add(new XYChart.Data<>(m, e.getValue()[0]));
            expSeries.getData().add(new XYChart.Data<>(m, e.getValue()[1]));
        }

        trendChart.getData().addAll(revSeries, expSeries);
        trendCard.getChildren().addAll(trendTitle, trendSub, trendChart);

        // Sales by category pie — real counts from products in each category
        VBox pieCard = new VBox(12);
        pieCard.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20;");
        pieCard.setPrefWidth(300);

        Label pieTitle = new Label("Stock Value by Category");
        pieTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label pieSub = new Label("Stock on hand at cost price, by category");
        pieSub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        // Stock value (at cost) per category, from the database — and only ever from the database: when there is
        // nothing to show we say so rather than draw sample slices (an earlier version fell back to invented
        // "Cement 40 / Steel 30 / Paints 20 / Hardware 10", which a new business would read as its own data).
        javafx.collections.ObservableList<PieChart.Data> pieData = javafx.collections.FXCollections.observableArrayList();
        String pieProblem = null;
        try (java.sql.Connection conn = com.kaziflow.database.DatabaseManager.getInstance().getConnection();
             java.sql.Statement st = conn.createStatement();
             // LEFT JOIN so products with no category are counted (as "Uncategorised"), not silently dropped
             java.sql.ResultSet rs = st.executeQuery(
                "SELECT COALESCE(c.name, 'Uncategorised') AS name, COALESCE(SUM(p.stock_quantity * p.cost_price),0) AS val " +
                "FROM products p LEFT JOIN categories c ON p.category_id = c.id " +
                "GROUP BY COALESCE(c.name, 'Uncategorised') HAVING val > 0 ORDER BY val DESC")) {
            double other = 0;
            int shown = 0;
            while (rs.next()) {
                double val = rs.getDouble("val");
                if (shown < 5) { pieData.add(new PieChart.Data(rs.getString("name"), val)); shown++; }
                else other += val;                       // every remaining category, instead of dropping them
            }
            if (other > 0) pieData.add(new PieChart.Data("Other", other));
        } catch (Exception e) {
            pieProblem = "Could not read stock values: " + e.getMessage();
        }

        javafx.scene.Node pieNode;
        if (!pieData.isEmpty()) {
            PieChart pie = new PieChart(pieData);
            pie.setAnimated(false);
            pie.setLegendVisible(true);
            pie.setPrefHeight(220);
            pie.setStyle("-fx-background-color: transparent;");
            pieNode = pie;
        } else {
            Label empty = new Label(pieProblem != null ? pieProblem
                    : "No stock on hand yet. Add products with a cost price and stock quantity to see how your stock value is split.");
            empty.setWrapText(true);
            empty.setStyle("-fx-text-fill: " + (pieProblem != null ? "#dc2626" : "#94a3b8") + "; -fx-font-size: 12px;");
            empty.setPrefHeight(220);
            empty.setAlignment(javafx.geometry.Pos.CENTER);
            pieNode = empty;
        }

        pieCard.getChildren().addAll(pieTitle, pieSub, pieNode);
        row.getChildren().addAll(trendCard, pieCard);
        return row;
    }

    private VBox buildBrowseSection() {
        VBox section = new VBox(16);
        Label title = new Label("Browse Reports");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        HBox reportCards = new HBox(16);
        String[][] reports = {
            {"⊙", "Sales & POS",             "Daily sales, shift reports, payment methods",   "#eff6ff", "#2563eb", "SALES"},
            {"◈", "Inventory",               "Stock levels, valuation, low stock alerts",      "#ede9fe", "#7c3aed", "INVENTORY"},
            {"▤", "Profit & Loss",           "Net income, gross margins, COGS analysis",       "#dcfce7", "#16a34a", "FINANCE_SUMMARY"},
            {"⊞", "Expenses",               "Operational costs, payroll summaries, petty cash","#fef3c7", "#d97706", "EXPENSES"},
            {"◉", "Purchases & Suppliers",   "Purchase orders, supplier aging, AP",            "#fff7ed", "#ea580c", "PURCHASES"},
            {"◎", "Human Resources",         "Attendance logs, payroll reports, performance",  "#fce7f3", "#db2777", "EMPLOYEES"}
        };

        for (String[] r : reports) {
            VBox card = new VBox(10);
            card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20; -fx-cursor: hand;");
            HBox.setHgrow(card, Priority.ALWAYS);

            StackPane iconBox = new StackPane();
            iconBox.setStyle("-fx-background-color: " + r[3] + "; -fx-background-radius: 10; -fx-padding: 12;");
            iconBox.setPrefSize(44, 44); iconBox.setMaxSize(44, 44);
            Label icon = new Label(r[0]); icon.setStyle("-fx-text-fill: " + r[4] + "; -fx-font-size: 18px;");
            iconBox.getChildren().add(icon);

            Label name = new Label(r[1]); name.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
            Label desc = new Label(r[2]); desc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-wrap-text: true;"); desc.setWrapText(true);

            // Wire export on click
            final String exportType = r[5];
            card.setOnMouseClicked(ev -> {
                com.kaziflow.services.ReportExportService.ReportType type =
                    com.kaziflow.services.ReportExportService.ReportType.valueOf(exportType);
                new com.kaziflow.services.ReportExportService().exportCSV(type);
            });

            card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 12; -fx-border-color: #2563eb; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20; -fx-cursor: hand;"));
            card.setOnMouseExited(e -> card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-border-width: 1; -fx-padding: 20; -fx-cursor: hand;"));

            card.getChildren().addAll(iconBox, name, desc);
            reportCards.getChildren().add(card);
        }
        section.getChildren().addAll(title, reportCards);
        return section;
    }
}
