package com.kaziflow.views;

import com.kaziflow.dao.SaccoDAO;
import com.kaziflow.utils.KESFormatter;
import com.kaziflow.utils.SceneManager;
import com.kaziflow.utils.Toast;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.List;

/**
 * SaccoView — SACCO / SHG management (Phase 10A): members, loans (reducing
 * balance), dividends, and merry-go-round rotating contributions.
 */
public class SaccoView {

    private final SaccoDAO dao = new SaccoDAO();
    private BorderPane root;

    public SaccoView() {
        dao.ensureTables();
        build();
    }

    public BorderPane getRoot() { return root; }

    private void build() {
        root = new BorderPane();
        root.setStyle("-fx-background-color:#f8fafc;");

        Label header = new Label("SACCO / SHG Management");
        header.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#1e293b;-fx-padding:20 20 0 20;");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            new Tab("Members", buildMembersPane()),
            new Tab("Loans", buildLoansPane()),
            new Tab("Dividends", buildDividendsPane()),
            new Tab("Merry-Go-Round", buildMgrPane())
        );

        VBox content = new VBox(10, header, tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.setCenter(content);
    }

    // ── Members tab ─────────────────────────────────────────────────────

    private VBox buildMembersPane() {
        VBox pane = new VBox(12);
        pane.setPadding(new Insets(16));

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Button addBtn = new Button("+ Register Member");
        addBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        Button depositBtn = new Button("💰 Record Deposit/Withdrawal");
        depositBtn.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:8;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        toolbar.getChildren().addAll(addBtn, depositBtn);

        TableView<String[]> table = new TableView<>();
        table.getColumns().add(col("Member #", 1, 130));
        table.getColumns().add(col("Name", 2, 160));
        table.getColumns().add(col("Phone", 3, 120));
        table.getColumns().add(col("Share Capital", 4, 120));
        table.getColumns().add(col("Total Savings", 5, 120));
        table.getColumns().add(col("Status", 6, 90));
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable refresh = () -> table.getItems().setAll(dao.findAllMembers());
        refresh.run();

        addBtn.setOnAction(e -> showRegisterMemberDialog(refresh));
        depositBtn.setOnAction(e -> showDepositDialog(table, refresh));

        pane.getChildren().addAll(toolbar, table);
        return pane;
    }

    private void showRegisterMemberDialog(Runnable onDone) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Register SACCO Member");
        GridPane f = new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));
        TextField nameF = new TextField(); nameF.setPromptText("Full name");
        TextField phoneF = new TextField(); phoneF.setPromptText("0712345678");
        TextField idF = new TextField(); idF.setPromptText("National ID");
        TextField shareF = new TextField(); shareF.setPromptText("0"); shareF.setText("0");
        f.addRow(0, new Label("Name"), nameF);
        f.addRow(1, new Label("Phone"), phoneF);
        f.addRow(2, new Label("ID Number"), idF);
        f.addRow(3, new Label("Initial Share Capital (KES)"), shareF);
        d.getDialogPane().setContent(f);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            if (nameF.getText().isBlank()) { Toast.error(stage(), "Name Required", "Please enter the member's name."); return; }
            double share;
            try { share = Double.parseDouble(shareF.getText().isBlank() ? "0" : shareF.getText()); }
            catch (NumberFormatException ex) { Toast.error(stage(), "Invalid Amount", "Share capital must be a number."); return; }
            int id = dao.addMember(nameF.getText().trim(), phoneF.getText().trim(), idF.getText().trim(), share);
            if (id > 0) { Toast.success(stage(), "Registered", nameF.getText() + " added as a member."); onDone.run(); }
            else Toast.error(stage(), "Failed", "Could not register member.");
        });
    }

    private void showDepositDialog(TableView<String[]> table, Runnable onDone) {
        String[] selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { Toast.error(stage(), "No Member Selected", "Select a member from the table first."); return; }
        int memberId = Integer.parseInt(selected[0]);

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Savings Transaction — " + selected[2]);
        GridPane f = new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));
        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList("deposit", "withdrawal", "share_capital"));
        typeBox.setValue("deposit");
        TextField amountF = new TextField(); amountF.setPromptText("Amount (KES)");
        TextField notesF = new TextField(); notesF.setPromptText("Optional note");
        f.addRow(0, new Label("Type"), typeBox);
        f.addRow(1, new Label("Amount"), amountF);
        f.addRow(2, new Label("Notes"), notesF);
        d.getDialogPane().setContent(f);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            try {
                double amount = Double.parseDouble(amountF.getText());
                if (amount <= 0) throw new NumberFormatException();
                dao.recordSavingsTransaction(memberId, typeBox.getValue(), amount, notesF.getText());
                Toast.success(stage(), "Recorded", typeBox.getValue() + " of " + KESFormatter.format(amount) + " recorded.");
                onDone.run();
            } catch (NumberFormatException ex) {
                Toast.error(stage(), "Invalid Amount", "Please enter a positive number.");
            }
        });
    }

    // ── Loans tab ───────────────────────────────────────────────────────

    private VBox buildLoansPane() {
        VBox pane = new VBox(12);
        pane.setPadding(new Insets(16));

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Button applyBtn = new Button("+ New Loan Application");
        applyBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        Button approveBtn = new Button("✓ Approve");
        approveBtn.setStyle("-fx-background-color:white;-fx-border-color:#16a34a;-fx-text-fill:#16a34a;-fx-border-radius:8;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        Button rejectBtn = new Button("✕ Reject");
        rejectBtn.setStyle("-fx-background-color:white;-fx-border-color:#dc2626;-fx-text-fill:#dc2626;-fx-border-radius:8;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        Button disburseBtn = new Button("💸 Disburse");
        disburseBtn.setStyle("-fx-background-color:white;-fx-border-color:#d97706;-fx-text-fill:#d97706;-fx-border-radius:8;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        Button scheduleBtn = new Button("📋 View Schedule / Repay");
        scheduleBtn.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:8;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        toolbar.getChildren().addAll(applyBtn, approveBtn, rejectBtn, disburseBtn, scheduleBtn);

        TableView<String[]> table = new TableView<>();
        table.getColumns().add(col("Loan #", 1, 130));
        table.getColumns().add(col("Member", 2, 140));
        table.getColumns().add(col("Principal", 3, 110));
        table.getColumns().add(col("Rate % p.a.", 4, 90));
        table.getColumns().add(col("Term (mo)", 5, 80));
        table.getColumns().add(col("Status", 6, 100));
        table.getColumns().add(col("Purpose", 7, 160));
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable refresh = () -> table.getItems().setAll(dao.findAllLoans());
        refresh.run();

        applyBtn.setOnAction(e -> showLoanApplicationDialog(refresh));
        approveBtn.setOnAction(e -> withSelectedLoan(table, "pending", loan -> {
            dao.updateLoanStatus(Integer.parseInt(loan[0]), "approved");
            Toast.success(stage(), "Approved", "Loan " + loan[1] + " approved — ready to disburse.");
            refresh.run();
        }));
        rejectBtn.setOnAction(e -> withSelectedLoan(table, "pending", loan -> {
            dao.updateLoanStatus(Integer.parseInt(loan[0]), "rejected");
            Toast.success(stage(), "Rejected", "Loan " + loan[1] + " rejected.");
            refresh.run();
        }));
        disburseBtn.setOnAction(e -> withSelectedLoan(table, "approved", loan -> {
            boolean ok = dao.disburseLoan(Integer.parseInt(loan[0]));
            if (ok) Toast.success(stage(), "Disbursed", "Loan " + loan[1] + " disbursed — repayment schedule generated.");
            else Toast.error(stage(), "Failed", "Could not disburse this loan.");
            refresh.run();
        }));
        scheduleBtn.setOnAction(e -> {
            String[] selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Toast.error(stage(), "No Loan Selected", "Select a loan first."); return; }
            showScheduleDialog(Integer.parseInt(selected[0]), selected[1]);
        });

        pane.getChildren().addAll(toolbar, table);
        return pane;
    }

    private void withSelectedLoan(TableView<String[]> table, String requiredStatus, java.util.function.Consumer<String[]> action) {
        String[] selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { Toast.error(stage(), "No Loan Selected", "Select a loan from the table first."); return; }
        if (!requiredStatus.equals(selected[6])) {
            Toast.error(stage(), "Wrong Status", "This loan must be '" + requiredStatus + "' for that action (currently '" + selected[6] + "').");
            return;
        }
        action.accept(selected);
    }

    private void showLoanApplicationDialog(Runnable onDone) {
        List<String[]> members = dao.findAllMembers();
        if (members.isEmpty()) { Toast.error(stage(), "No Members", "Register a SACCO member before applying for a loan."); return; }

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("New Loan Application");
        GridPane f = new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));

        ComboBox<String> memberBox = new ComboBox<>();
        java.util.Map<String, Integer> nameToId = new java.util.HashMap<>();
        for (String[] m : members) { nameToId.put(m[2] + " (" + m[1] + ")", Integer.parseInt(m[0])); memberBox.getItems().add(m[2] + " (" + m[1] + ")"); }
        memberBox.setValue(memberBox.getItems().get(0));

        TextField principalF = new TextField(); principalF.setPromptText("e.g. 50000");
        TextField rateF = new TextField(); rateF.setPromptText("Annual %, e.g. 12"); rateF.setText("12");
        TextField termF = new TextField(); termF.setPromptText("Months, e.g. 12"); termF.setText("12");
        TextField purposeF = new TextField(); purposeF.setPromptText("e.g. School fees, business stock");

        Label note = new Label("Interest is calculated on a reducing balance (standard for Kenyan SACCOs) — "
            + "you set the annual rate, the system amortizes it into equal monthly installments once disbursed.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill:#64748b;-fx-font-size:11px;");

        f.addRow(0, note);
        f.addRow(1, new Label("Member"), memberBox);
        f.addRow(2, new Label("Principal (KES)"), principalF);
        f.addRow(3, new Label("Annual Rate (%)"), rateF);
        f.addRow(4, new Label("Term (months)"), termF);
        f.addRow(5, new Label("Purpose"), purposeF);
        d.getDialogPane().setContent(f);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            try {
                double principal = Double.parseDouble(principalF.getText());
                double rate = Double.parseDouble(rateF.getText());
                int term = Integer.parseInt(termF.getText());
                if (principal <= 0 || rate < 0 || term <= 0) throw new NumberFormatException();
                int memberId = nameToId.get(memberBox.getValue());
                int loanId = dao.applyForLoan(memberId, principal, rate, term, purposeF.getText());
                if (loanId > 0) { Toast.success(stage(), "Application Submitted", "Loan pending approval."); onDone.run(); }
                else Toast.error(stage(), "Failed", "Could not submit loan application.");
            } catch (NumberFormatException ex) {
                Toast.error(stage(), "Invalid Input", "Principal, rate, and term must be valid positive numbers.");
            }
        });
    }

    private void showScheduleDialog(int loanId, String loanNumber) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Repayment Schedule — " + loanNumber);
        d.getDialogPane().setPrefWidth(680);

        TableView<Object[]> table = new TableView<>();
        table.getColumns().add(objCol("#", 1, 40));
        table.getColumns().add(objCol("Due Date", 2, 100));
        table.getColumns().add(moneyCol("Principal", 3, 100));
        table.getColumns().add(moneyCol("Interest", 4, 100));
        table.getColumns().add(moneyCol("Total Due", 5, 100));
        table.getColumns().add(moneyCol("Paid", 6, 100));
        table.getColumns().add(objCol("Status", 7, 80));
        table.setPrefHeight(360);

        Runnable refresh = () -> table.getItems().setAll(dao.getRepaymentSchedule(loanId));
        refresh.run();

        Button payBtn = new Button("Record Payment for Selected Installment");
        payBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-background-radius:6;-fx-cursor:hand;-fx-pref-height:32px;");
        payBtn.setOnAction(e -> {
            Object[] selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Toast.error(stage(), "No Installment Selected", "Select an installment row first."); return; }
            TextInputDialog amtDialog = new TextInputDialog(String.valueOf(selected[5]));
            amtDialog.setTitle("Record Payment");
            amtDialog.setHeaderText("Total due: " + KESFormatter.format((double) selected[5]));
            amtDialog.setContentText("Amount paid:");
            amtDialog.showAndWait().ifPresent(input -> {
                try {
                    double amt = Double.parseDouble(input);
                    dao.recordRepayment((int) selected[0], amt);
                    Toast.success(stage(), "Payment Recorded", KESFormatter.format(amt) + " recorded.");
                    refresh.run();
                } catch (NumberFormatException ex) {
                    Toast.error(stage(), "Invalid Amount", "Please enter a valid number.");
                }
            });
        });

        VBox content = new VBox(10, table, payBtn);
        content.setPadding(new Insets(16));
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.showAndWait();
    }

    // ── Dividends tab ───────────────────────────────────────────────────

    private VBox buildDividendsPane() {
        VBox pane = new VBox(12);
        pane.setPadding(new Insets(16));

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        TextField yearF = new TextField(String.valueOf(LocalDate.now().getYear()));
        yearF.setPrefWidth(80);
        TextField rateF = new TextField(); rateF.setPromptText("Dividend rate %"); rateF.setPrefWidth(120);
        Button declareBtn = new Button("Declare Dividends");
        declareBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        toolbar.getChildren().addAll(new Label("Year:"), yearF, new Label("Rate %:"), rateF, declareBtn);

        TableView<String[]> table = new TableView<>();
        table.getColumns().add(col("Member", 0, 160));
        table.getColumns().add(col("Share Capital", 1, 130));
        table.getColumns().add(col("Rate %", 2, 90));
        table.getColumns().add(col("Dividend Amount", 3, 140));
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable refresh = () -> {
            try { table.getItems().setAll(dao.getDividendHistory(Integer.parseInt(yearF.getText()))); }
            catch (NumberFormatException ignored) {}
        };
        refresh.run();

        declareBtn.setOnAction(e -> {
            try {
                int year = Integer.parseInt(yearF.getText());
                double rate = Double.parseDouble(rateF.getText());
                if (rate <= 0) throw new NumberFormatException();
                int count = dao.declareDividends(year, rate);
                Toast.success(stage(), "Dividends Declared", count + " member(s) received a " + rate + "% dividend for " + year + ".");
                refresh.run();
            } catch (NumberFormatException ex) {
                Toast.error(stage(), "Invalid Input", "Year and rate must be valid numbers.");
            }
        });

        pane.getChildren().addAll(toolbar, table);
        return pane;
    }

    // ── Merry-go-round tab ──────────────────────────────────────────────

    private VBox buildMgrPane() {
        VBox pane = new VBox(12);
        pane.setPadding(new Insets(16));

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        Button newGroupBtn = new Button("+ New Group");
        newGroupBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        Button viewBtn = new Button("View Current Payout");
        viewBtn.setStyle("-fx-background-color:white;-fx-border-color:#e2e8f0;-fx-border-radius:8;-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        toolbar.getChildren().addAll(newGroupBtn, viewBtn);

        TableView<String[]> table = new TableView<>();
        table.getColumns().add(col("Group", 1, 160));
        table.getColumns().add(col("Contribution", 2, 110));
        table.getColumns().add(col("Frequency", 3, 100));
        table.getColumns().add(col("Current Cycle", 4, 100));
        table.getColumns().add(col("Members", 5, 80));
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable refresh = () -> table.getItems().setAll(dao.findAllMgrGroups());
        refresh.run();

        newGroupBtn.setOnAction(e -> {
            Dialog<ButtonType> d = new Dialog<>();
            d.setTitle("New Merry-Go-Round Group");
            GridPane f = new GridPane(); f.setHgap(12); f.setVgap(12); f.setPadding(new Insets(20));
            TextField nameF = new TextField(); nameF.setPromptText("Group name");
            TextField amountF = new TextField(); amountF.setPromptText("Contribution per member, e.g. 1000");
            ComboBox<String> freqBox = new ComboBox<>(FXCollections.observableArrayList("monthly", "weekly"));
            freqBox.setValue("monthly");
            f.addRow(0, new Label("Name"), nameF);
            f.addRow(1, new Label("Contribution (KES)"), amountF);
            f.addRow(2, new Label("Frequency"), freqBox);
            d.getDialogPane().setContent(f);
            d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            d.showAndWait().ifPresent(btn -> {
                if (btn != ButtonType.OK) return;
                try {
                    double amount = Double.parseDouble(amountF.getText());
                    int groupId = dao.createMgrGroup(nameF.getText(), amount, freqBox.getValue());
                    if (groupId > 0) {
                        Toast.success(stage(), "Group Created", "Add members to \"" + nameF.getText() + "\" from the Members tab, then assign payout order here.");
                        refresh.run();
                    }
                } catch (NumberFormatException ex) {
                    Toast.error(stage(), "Invalid Amount", "Contribution must be a number.");
                }
            });
        });

        viewBtn.setOnAction(e -> {
            String[] selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Toast.error(stage(), "No Group Selected", "Select a group first."); return; }
            String[] recipient = dao.getCurrentPayoutRecipient(Integer.parseInt(selected[0]));
            if (recipient == null) {
                Toast.error(stage(), "No Members", "This group has no members assigned a payout position yet.");
            } else {
                Toast.success(stage(), "Current Cycle Payout", recipient[1] + " is due to receive this cycle's payout for \"" + selected[1] + "\".");
            }
        });

        pane.getChildren().addAll(toolbar, table);
        return pane;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private TableColumn<String[], String> col(String label, int index, double width) {
        TableColumn<String[], String> c = new TableColumn<>(label);
        c.setPrefWidth(width);
        c.setCellValueFactory(d -> new SimpleStringProperty(index < d.getValue().length ? d.getValue()[index] : "—"));
        return c;
    }

    private TableColumn<Object[], String> objCol(String label, int index, double width) {
        TableColumn<Object[], String> c = new TableColumn<>(label);
        c.setPrefWidth(width);
        c.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue()[index])));
        return c;
    }

    private TableColumn<Object[], String> moneyCol(String label, int index, double width) {
        TableColumn<Object[], String> c = new TableColumn<>(label);
        c.setPrefWidth(width);
        c.setCellValueFactory(d -> new SimpleStringProperty(KESFormatter.format((double) d.getValue()[index])));
        return c;
    }

    private javafx.stage.Stage stage() { return SceneManager.getInstance().getStage(); }
}
