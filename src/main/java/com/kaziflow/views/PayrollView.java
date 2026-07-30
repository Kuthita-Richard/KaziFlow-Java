package com.kaziflow.views;

import com.kaziflow.dao.PayrollDAO;
import com.kaziflow.services.AuditLog;
import com.kaziflow.utils.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.List;

public class PayrollView {

    private BorderPane root;
    private final PayrollDAO dao = new PayrollDAO();
    private ObservableList<String[]> runData = FXCollections.observableArrayList();

    public PayrollView() {
        dao.ensureTables();
        buildUI();
        loadRuns();
    }

    public BorderPane getRoot() { return root; }

    private void buildUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color:#f8fafc;");
        root.setTop(buildHeader());
        root.setCenter(buildContent());
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 24, 16, 24));
        header.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent " +
            "#e2e8f0 transparent;-fx-border-width:0 0 1 0;");

        VBox titleBlock = new VBox(2);
        Label title = new Label("💵  Payroll");
        title.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label sub = new Label("Kenya statutory deductions — PAYE · NSSF · NHIF · NITA");
        sub.setStyle("-fx-font-size:12px;-fx-text-fill:#64748b;");
        titleBlock.getChildren().addAll(title, sub);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        // Month/Year pickers
        ComboBox<String> monthBox = new ComboBox<>();
        monthBox.getItems().addAll("January","February","March","April","May","June",
            "July","August","September","October","November","December");
        monthBox.setValue(monthBox.getItems().get(LocalDate.now().getMonthValue() - 1));
        monthBox.setPrefWidth(130);

        ComboBox<Integer> yearBox = new ComboBox<>();
        int curYear = LocalDate.now().getYear();
        for (int y = curYear; y >= curYear - 3; y--) yearBox.getItems().add(y);
        yearBox.setValue(curYear);
        yearBox.setPrefWidth(90);

        Button runBtn = new Button("▶ Run Payroll");
        runBtn.setStyle("-fx-background-color:#16a34a;-fx-text-fill:white;-fx-font-weight:bold;" +
            "-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        runBtn.setOnAction(e -> {
            int month = monthBox.getSelectionModel().getSelectedIndex() + 1;
            int year  = yearBox.getValue();
            runPayroll(month, year);
        });

        header.getChildren().addAll(titleBlock, sp, monthBox, yearBox, runBtn);
        return header;
    }

    // ── Content ─────────────────────────────────────────────────────────────

    private VBox buildContent() {
        VBox content = new VBox(0);
        VBox.setVgrow(content, Priority.ALWAYS);

        // Payroll runs table
        TableView<String[]> tv = new TableView<>(runData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;");
        VBox.setVgrow(tv, Priority.ALWAYS);

        TableColumn<String[], String> runCol   = col("Run #",       1, 100);
        TableColumn<String[], String> monthCol = col("Period",      2, 110);
        TableColumn<String[], String> empCol   = col("Employees",   3, 100);

        TableColumn<String[], String> grossCol = amtCol("Gross (KES)", 4);
        TableColumn<String[], String> netCol   = amtCol("Net Pay (KES)", 5);
        TableColumn<String[], String> payeCol  = amtCol("PAYE (KES)", 6);

        // Status badge
        TableColumn<String[], String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(110);
        statusCol.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(d.getValue()[7]));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setGraphic(null); return; }
                Label badge = new Label(v.toUpperCase());
                String color = switch (v) {
                    case "paid"     -> "-fx-background-color:#dcfce7;-fx-text-fill:#16a34a;";
                    case "approved" -> "-fx-background-color:#dbeafe;-fx-text-fill:#2563eb;";
                    default         -> "-fx-background-color:#fef3c7;-fx-text-fill:#d97706;";
                };
                badge.setStyle(color + "-fx-font-size:10px;-fx-font-weight:bold;" +
                    "-fx-background-radius:20;-fx-padding:3 10;");
                setGraphic(badge);
            }
        });

        TableColumn<String[], String> dateCol = col("Created", 8, 130);

        // Actions
        TableColumn<String[], Void> actCol = new TableColumn<>("");
        actCol.setPrefWidth(260);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button viewBtn    = btn("👁 Payslips",  "#2563eb");
            private final Button approveBtn = btn("✔ Approve",    "#16a34a");
            private final Button paidBtn    = btn("💰 Mark Paid", "#7c3aed");
            private final HBox box = new HBox(6, viewBtn, approveBtn, paidBtn);

            {
                viewBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    showPayslips(Integer.parseInt(row[0]), row[1], row[2]);
                });
                approveBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    if (dao.approveRun(Integer.parseInt(row[0]))) {
                        AuditLog.log("PAYROLL_APPROVED", "Payroll " + row[1] + " approved", "payroll", null);
                        loadRuns();
                        Toast.success(SceneManager.getInstance().getStage(),
                            "Approved", "Payroll " + row[1] + " approved");
                    }
                });
                paidBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    if (dao.markPaid(Integer.parseInt(row[0]))) {
                        AuditLog.log("PAYROLL_PAID", "Payroll " + row[1] + " marked paid", "payroll", null);
                        loadRuns();
                        Toast.success(SceneManager.getInstance().getStage(),
                            "Paid", "Payroll " + row[1] + " marked as paid");
                    }
                });
            }

            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                String[] row = getTableView().getItems().get(getIndex());
                approveBtn.setDisable(!"draft".equals(row[7]));
                paidBtn.setDisable("paid".equals(row[7]));
                setGraphic(box);
            }
        });

        tv.getColumns().addAll(runCol, monthCol, empCol, grossCol, netCol, payeCol,
            statusCol, dateCol, actCol);

        // KRA remittance reminder
        Label reminder = new Label(
            "📅  KRA PAYE deadline: 9th of following month  |  " +
            "NSSF deadline: 9th of following month  |  " +
            "NHIF deadline: 9th of following month");
        reminder.setStyle("-fx-background-color:#fef3c7;-fx-text-fill:#92400e;-fx-font-size:12px;" +
            "-fx-padding:10 24;-fx-font-weight:bold;");
        reminder.setMaxWidth(Double.MAX_VALUE);

        content.getChildren().addAll(tv, reminder);
        return content;
    }

    // ── Payslips Dialog ────────────────────────────────────────────────────

    private void showPayslips(int runId, String runNum, String period) {
        String[] run = dao.getRunById(runId);
        List<String[]> entries = dao.getEntries(runId);
        if (entries.isEmpty()) {
            Toast.error(SceneManager.getInstance().getStage(),
                "No entries", "No employees found in this payroll run.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Payslips — " + runNum + " (" + period + ")");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color:white;");
        dialog.getDialogPane().setPrefWidth(860);
        dialog.getDialogPane().setPrefHeight(640);

        VBox layout = new VBox(0);

        // Summary banner
        if (run != null) {
            HBox summary = new HBox(40);
            summary.setPadding(new Insets(14, 24, 14, 24));
            summary.setStyle("-fx-background-color:#f8fafc;-fx-border-color:transparent transparent " +
                "#e2e8f0 transparent;-fx-border-width:0 0 1 0;");
            summary.getChildren().addAll(
                summaryItem("Employees",  run[3]),
                summaryItem("Total Gross","KES " + KESFormatter.format(Double.parseDouble(run[4]))),
                summaryItem("Total PAYE", "KES " + KESFormatter.format(Double.parseDouble(run[7]))),
                summaryItem("Total NSSF", "KES " + KESFormatter.format(Double.parseDouble(run[5]))),
                summaryItem("Total NHIF", "KES " + KESFormatter.format(Double.parseDouble(run[6]))),
                summaryItem("Total Net",  "KES " + KESFormatter.format(Double.parseDouble(run[9])))
            );
            layout.getChildren().add(summary);
        }

        // Payslips table
        TableView<String[]> tv = new TableView<>(
            FXCollections.observableArrayList(entries));
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;");
        VBox.setVgrow(tv, Priority.ALWAYS);

        String[] headers = {"#", "Name", "Dept", "Gross", "NSSF", "NHIF", "PAYE", "NITA", "Deductions", "Net Pay"};
        int[] idxs = {1, 2, 3, 5, 6, 7, 8, 9, 10, 11};
        for (int i = 0; i < headers.length; i++) {
            final int ci = idxs[i];
            TableColumn<String[], String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                    ci < d.getValue().length ? d.getValue()[ci] : ""));
            if (i == 9) col.setStyle("-fx-font-weight:bold;");
            tv.getColumns().add(col);
        }

        // Print individual payslip button
        TableColumn<String[], Void> printCol = new TableColumn<>("");
        printCol.setPrefWidth(90);
        printCol.setCellFactory(c -> new TableCell<>() {
            private final Button pb = btn("🖨 Payslip", "#475569");
            { pb.setOnAction(e -> printPayslip(
                getTableView().getItems().get(getIndex()), runNum, period)); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty); setGraphic(empty ? null : pb);
            }
        });
        tv.getColumns().add(printCol);

        layout.getChildren().add(tv);
        dialog.getDialogPane().setContent(layout);
        dialog.getDialogPane().getButtonTypes().addAll(
            new ButtonType("Export CSV", ButtonBar.ButtonData.LEFT),
            ButtonType.CLOSE);

        dialog.showAndWait().ifPresent(r -> {
            if (r.getButtonData() == ButtonBar.ButtonData.LEFT) exportCSV(entries, runNum);
        });
    }

    private VBox summaryItem(String label, String value) {
        VBox item = new VBox(2);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;-fx-font-weight:bold;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        item.getChildren().addAll(lbl, val);
        return item;
    }

    private void printPayslip(String[] entry, String runNum, String period) {
        StringBuilder sb = new StringBuilder();
        sb.append("================================================\n");
        sb.append("                     PAYSLIP\n");
        sb.append("================================================\n");
        sb.append("Pay Run  : ").append(runNum).append("\n");
        sb.append("Period   : ").append(period).append("\n");
        sb.append("Date     : ").append(LocalDate.now()).append("\n");
        sb.append("------------------------------------------------\n");
        sb.append("Employee : ").append(entry[2]).append("\n");
        sb.append("Number   : ").append(entry[1]).append("\n");
        sb.append("Dept     : ").append(entry[3]).append("\n");
        sb.append("Position : ").append(entry[4]).append("\n");
        sb.append("------------------------------------------------\n");
        sb.append(String.format("%-28s KES %10s\n", "Gross Salary",     entry[5]));
        sb.append("                      DEDUCTIONS\n");
        sb.append(String.format("%-28s KES %10s\n", "NSSF (Employee)",  entry[6]));
        sb.append(String.format("%-28s KES %10s\n", "NHIF",            entry[7]));
        sb.append(String.format("%-28s KES %10s\n", "PAYE",            entry[8]));
        sb.append(String.format("%-28s KES %10s\n", "NITA",            entry[9]));
        sb.append("------------------------------------------------\n");
        sb.append(String.format("%-28s KES %10s\n", "Total Deductions", entry[10]));
        sb.append(String.format("%-28s KES %10s\n", "NET PAY",         entry[11]));
        sb.append("================================================\n");
        sb.append("This is a computer generated payslip.\n");

        TextArea ta = new TextArea(sb.toString());
        ta.setFont(javafx.scene.text.Font.font("Courier New", 12));
        ta.setEditable(false); ta.setPrefSize(460, 400);

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Payslip — " + entry[2]);
        d.getDialogPane().setContent(ta);
        d.getDialogPane().getButtonTypes().addAll(
            new ButtonType("Print", ButtonBar.ButtonData.LEFT), ButtonType.CLOSE);
        d.showAndWait().ifPresent(r -> {
            if (r.getButtonData() == ButtonBar.ButtonData.LEFT) {
                try {
                    javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
                    if (job != null && job.showPrintDialog(null)) {
                        job.printPage(ta); job.endJob();
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void exportCSV(List<String[]> entries, String runNum) {
        StringBuilder csv = new StringBuilder();
        csv.append("Emp#,Name,Department,Position,Gross,NSSF,NHIF,PAYE,NITA,Deductions,NetPay\n");
        for (String[] e : entries) {
            csv.append(String.join(",", e[1], e[2], e[3], e[4],
                e[5], e[6], e[7], e[8], e[9], e[10], e[11])).append("\n");
        }
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Save Payroll CSV");
        fc.setInitialFileName("Payroll_" + runNum + ".csv");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV", "*.csv"));
        java.io.File f = fc.showSaveDialog(SceneManager.getInstance().getStage());
        if (f != null) {
            try (java.io.FileWriter fw = new java.io.FileWriter(f)) {
                fw.write(csv.toString());
                Toast.success(SceneManager.getInstance().getStage(),
                    "Exported", "Saved to " + f.getName());
            } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    // ── Actions ────────────────────────────────────────────────────────────

    private void runPayroll(int month, int year) {
        String monthStr = String.format("%04d-%02d", year, month);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Run payroll for " + monthStr + "?\n" +
            "This will calculate salaries for all active employees\n" +
            "with PAYE, NSSF, NHIF, and NITA deductions.",
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Payroll Run");
        confirm.showAndWait().ifPresent(r -> {
            if (r != ButtonType.YES) return;
            int userId = 1;
            try { userId = SessionManager.getInstance().getCurrentUser().getId(); }
            catch (Exception ignored) {}
            final int uid = userId;
            AsyncTask.run(() -> dao.createRun(month, year, uid), runId -> {
                if (runId > 0) {
                    AuditLog.log("PAYROLL_RUN",
                        "Payroll run created for " + monthStr, "payroll", runId);
                    loadRuns();
                    Toast.success(SceneManager.getInstance().getStage(),
                        "Payroll run complete", "Period: " + monthStr);
                } else {
                    Toast.error(SceneManager.getInstance().getStage(),
                        "Failed", "Could not create payroll run. Check employee salaries.");
                }
            }, err -> Toast.error(SceneManager.getInstance().getStage(), "Error", err));
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void loadRuns() {
        AsyncTask.run(dao::findAllRuns, runData::setAll, err -> {});
    }

    private TableColumn<String[], String> col(String h, int idx, double w) {
        TableColumn<String[], String> c = new TableColumn<>(h);
        c.setPrefWidth(w);
        c.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            idx < d.getValue().length ? d.getValue()[idx] : ""));
        return c;
    }

    private TableColumn<String[], String> amtCol(String h, int idx) {
        TableColumn<String[], String> c = new TableColumn<>(h);
        c.setPrefWidth(140);
        c.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            idx < d.getValue().length ? d.getValue()[idx] : "0.00"));
        c.setCellFactory(cc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); return; }
                try { setText("KES " + KESFormatter.format(Double.parseDouble(v))); }
                catch (Exception ex) { setText(v); }
                setStyle("-fx-font-weight:bold;-fx-text-fill:#1e293b;");
            }
        });
        return c;
    }

    private Button btn(String label, String color) {
        Button b = new Button(label);
        b.setStyle("-fx-background-color:white;-fx-border-color:" + color +
            ";-fx-border-radius:5;-fx-background-radius:5;-fx-font-size:11px;" +
            "-fx-text-fill:" + color + ";-fx-cursor:hand;-fx-padding:3 8;");
        return b;
    }
}
