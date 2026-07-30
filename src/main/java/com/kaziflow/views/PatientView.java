package com.kaziflow.views;

import com.kaziflow.dao.PatientDAO;
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

public class PatientView {

    private BorderPane root;
    private final PatientDAO dao = new PatientDAO();
    private ObservableList<String[]> patientData = FXCollections.observableArrayList();
    private Label totalLbl, todayLbl, pendingLabLbl;

    public PatientView() {
        dao.ensureTables();
        buildUI();
        loadPatients(null);
    }

    public BorderPane getRoot() { return root; }

    private void buildUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color:#f8fafc;");
        root.setTop(buildHeader());
        root.setCenter(buildContent());
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private VBox buildHeader() {
        VBox header = new VBox(0);

        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 24));
        bar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");

        VBox tb = new VBox(2);
        Label t = new Label("🏥  Patient Records");
        t.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        Label s = new Label("Medical records · Encounters · Prescriptions · Lab results");
        s.setStyle("-fx-font-size:12px;-fx-text-fill:#64748b;");
        tb.getChildren().addAll(t, s);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        totalLbl      = statVal(String.valueOf(dao.getTotalPatients()));
        todayLbl      = statVal(String.valueOf(dao.getTodayEncounters()));
        pendingLabLbl = statVal(String.valueOf(dao.getPendingLabCount()));

        Button newBtn = new Button("+ Register Patient");
        newBtn.setStyle("-fx-background-color:#2563eb;-fx-text-fill:white;-fx-font-weight:bold;" +
            "-fx-background-radius:8;-fx-pref-height:38px;-fx-padding:0 18;-fx-cursor:hand;-fx-font-size:13px;");
        newBtn.setOnAction(e -> showRegisterDialog());

        bar.getChildren().addAll(tb, sp,
            statCard("Total Patients", totalLbl),
            statCard("Today's Visits",  todayLbl),
            statCard("Pending Labs",    pendingLabLbl),
            newBtn);
        header.getChildren().add(bar);
        return header;
    }

    private Label statVal(String val) {
        Label l = new Label(val);
        l.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#1e293b;");
        return l;
    }

    private VBox statCard(String label, Label valLbl) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:11px;-fx-font-weight:bold;");
        VBox card = new VBox(2, lbl, valLbl);
        card.setStyle("-fx-background-color:white;-fx-background-radius:10;" +
            "-fx-border-color:#e2e8f0;-fx-border-radius:10;-fx-border-width:1;" +
            "-fx-padding:10 20;-fx-min-width:110px;");
        return card;
    }

    // ── Content ─────────────────────────────────────────────────────────────

    private VBox buildContent() {
        VBox content = new VBox(0);
        VBox.setVgrow(content, Priority.ALWAYS);

        // Search bar
        HBox searchBar = new HBox(10);
        searchBar.setPadding(new Insets(12, 24, 12, 24));
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setStyle("-fx-background-color:white;-fx-border-color:transparent transparent #e2e8f0 transparent;-fx-border-width:0 0 1 0;");

        TextField searchField = new TextField();
        searchField.setPromptText("Search by name, MRN, or phone...");
        searchField.setPrefWidth(320);
        searchField.setStyle("-fx-pref-height:36px;-fx-background-color:#f8fafc;" +
            "-fx-border-color:#e2e8f0;-fx-border-radius:8;-fx-background-radius:8;" +
            "-fx-font-size:13px;-fx-padding:0 12;");
        searchField.textProperty().addListener((obs, old, val) ->
            AsyncTask.run(() -> val.isBlank() ? dao.findAll() : dao.search(val),
                patientData::setAll, err -> {}));

        searchBar.getChildren().add(searchField);

        // Table
        TableView<String[]> tv = buildTable();
        VBox.setVgrow(tv, Priority.ALWAYS);

        content.getChildren().addAll(searchBar, tv);
        return content;
    }

    private TableView<String[]> buildTable() {
        TableView<String[]> tv = new TableView<>(patientData);
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setStyle("-fx-background-color:white;");

        // [0]=id [1]=mrn [2]=full_name [3]=gender [4]=phone [5]=blood_group
        // [6]=allergies [7]=species [8]=visit_count [9]=created_at

        TableColumn<String[], String> mrnCol  = col("MRN",     1, 100);
        TableColumn<String[], String> nameCol = new TableColumn<>("Patient");
        nameCol.setPrefWidth(180);
        nameCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[2]));
        nameCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                String[] row = getTableView().getItems().get(getIndex());
                VBox cell = new VBox(2);
                Label name = new Label(row[2]);
                name.setStyle("-fx-font-weight:bold;-fx-text-fill:#1e293b;");
                Label info = new Label(row[3] + (row[7].equals("Human") ? "" : " · " + row[7]));
                info.setStyle("-fx-font-size:11px;-fx-text-fill:#94a3b8;");
                cell.getChildren().addAll(name, info);
                setGraphic(cell);
            }
        });

        TableColumn<String[], String> phoneCol   = col("Phone",       4, 120);
        TableColumn<String[], String> bloodCol   = col("Blood Group", 5, 90);
        TableColumn<String[], String> allergyCol = new TableColumn<>("Allergies");
        allergyCol.setPrefWidth(140);
        allergyCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[6]));
        allergyCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null || v.equals("None")) { setText(v); setStyle(""); return; }
                setText(v);
                setStyle("-fx-text-fill:#dc2626;-fx-font-weight:bold;");
            }
        });

        TableColumn<String[], String> visitsCol = col("Visits", 8, 60);
        TableColumn<String[], String> dateCol   = col("Registered", 9, 120);

        // Actions
        TableColumn<String[], Void> actCol = new TableColumn<>("");
        actCol.setPrefWidth(200);
        actCol.setCellFactory(c -> new TableCell<>() {
            private final Button viewBtn  = btn("📋 Records", "#2563eb");
            private final Button visitBtn = btn("+ Visit",   "#16a34a");
            private final HBox box = new HBox(6, viewBtn, visitBtn);
            {
                viewBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    showPatientRecords(Integer.parseInt(row[0]), row[2], row[1]);
                });
                visitBtn.setOnAction(e -> {
                    String[] row = getTableView().getItems().get(getIndex());
                    showNewEncounterDialog(Integer.parseInt(row[0]), row[2]);
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty); setGraphic(empty ? null : box);
            }
        });

        tv.getColumns().addAll(mrnCol, nameCol, phoneCol, bloodCol, allergyCol, visitsCol, dateCol, actCol);
        return tv;
    }

    // ── Register Patient Dialog ────────────────────────────────────────────

    private void showRegisterDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Register New Patient");
        dialog.getDialogPane().setStyle("-fx-background-color:white;");
        dialog.getDialogPane().setPrefWidth(520);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Personal info tab
        GridPane personal = new GridPane(); personal.setHgap(12); personal.setVgap(12); personal.setPadding(new Insets(16));
        TextField firstNameF  = fld("First Name *", "");
        TextField lastNameF   = fld("Last Name *",  "");
        TextField dobF        = fld("Date of Birth (YYYY-MM-DD)", "");
        ComboBox<String> genderBox = new ComboBox<>();
        genderBox.getItems().addAll("Male","Female","Other"); genderBox.setValue("Male"); genderBox.setPrefWidth(280);
        TextField phoneF      = fld("Phone", "");
        TextField emailF      = fld("Email", "");
        TextField addressF    = fld("Address", "");
        TextField bloodF      = fld("Blood Group (A+/B-/O+ etc)", "");
        TextField allergiesF  = fld("Allergies (comma-separated)", "None");

        personal.addRow(0, lbl("First Name"), firstNameF, lbl("Last Name"), lastNameF);
        personal.addRow(1, lbl("Date of Birth"), dobF, lbl("Gender"), genderBox);
        personal.addRow(2, lbl("Phone"), phoneF, lbl("Email"), emailF);
        personal.addRow(3, lbl("Address"), addressF);
        personal.addRow(4, lbl("Blood Group"), bloodF, lbl("Allergies"), allergiesF);

        // Medical / Vet tab
        GridPane medical = new GridPane(); medical.setHgap(12); medical.setVgap(12); medical.setPadding(new Insets(16));
        TextField insNoF      = fld("Insurance / NHIF No", "");
        TextField insProvF    = fld("Insurance Provider", "");
        TextField speciesF    = fld("Species (leave blank for human)", "");
        TextField breedF      = fld("Breed", "");
        TextField ownerF      = fld("Owner Name (for vet)", "");
        TextArea notesArea    = new TextArea(); notesArea.setPromptText("Additional notes"); notesArea.setPrefRowCount(3);
        medical.addRow(0, lbl("Insurance No"), insNoF, lbl("Provider"), insProvF);
        medical.addRow(1, lbl("Species"), speciesF, lbl("Breed"), breedF);
        medical.addRow(2, lbl("Owner Name"), ownerF);
        medical.addRow(3, lbl("Notes"), notesArea);

        tabs.getTabs().addAll(
            new Tab("Personal Info", personal),
            new Tab("Medical / Vet", medical)
        );

        dialog.getDialogPane().setContent(tabs);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK) return;
            if (firstNameF.getText().isBlank() || lastNameF.getText().isBlank()) {
                Toast.error(SceneManager.getInstance().getStage(), "Required", "First and last name are required.");
                return;
            }
            int id = dao.savePatient(
                firstNameF.getText().trim(), lastNameF.getText().trim(),
                dobF.getText().trim(), genderBox.getValue(),
                phoneF.getText().trim(), emailF.getText().trim(),
                addressF.getText().trim(), bloodF.getText().trim(),
                allergiesF.getText().trim(), insNoF.getText().trim(),
                insProvF.getText().trim(),
                speciesF.getText().isBlank() ? "Human" : speciesF.getText().trim(),
                breedF.getText().trim(), ownerF.getText().trim(),
                notesArea.getText().trim()
            );
            if (id > 0) {
                AuditLog.log("PATIENT_REGISTERED", firstNameF.getText() + " " + lastNameF.getText(), "patients", id);
                loadPatients(null);
                refreshStats();
                Toast.success(SceneManager.getInstance().getStage(), "Patient registered",
                    firstNameF.getText() + " " + lastNameF.getText());
            }
        });
    }

    // ── Patient Records Dialog ────────────────────────────────────────────

    private void showPatientRecords(int patientId, String patientName, String mrn) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Records — " + patientName + " (" + mrn + ")");
        dialog.getDialogPane().setStyle("-fx-background-color:white;");
        dialog.getDialogPane().setPrefWidth(800);
        dialog.getDialogPane().setPrefHeight(600);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // ── Encounters Tab
        List<String[]> encounters = dao.getEncounters(patientId);
        TableView<String[]> encTv = new TableView<>(FXCollections.observableArrayList(encounters));
        encTv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] encHdrs = {"Enc #","Date","Type","Complaint","Diagnosis","Doctor","Follow-up","Amount","Status"};
        int[]    encIdxs = {1,2,3,4,5,6,7,8,9};
        for (int i=0;i<encHdrs.length;i++) {
            final int ci=encIdxs[i];
            TableColumn<String[],String> tc = new TableColumn<>(encHdrs[i]);
            tc.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(ci<d.getValue().length?d.getValue()[ci]:""));
            encTv.getColumns().add(tc);
        }
        Button newEncBtn = new Button("+ New Visit");
        newEncBtn.setStyle("-fx-background-color:#16a34a;-fx-text-fill:white;-fx-font-weight:bold;" +
            "-fx-background-radius:8;-fx-pref-height:34px;-fx-padding:0 14;-fx-cursor:hand;");
        newEncBtn.setOnAction(e -> { dialog.close(); showNewEncounterDialog(patientId, patientName); });
        VBox encView = new VBox(0);
        HBox encBar = new HBox(newEncBtn); encBar.setPadding(new Insets(10,16,10,16));
        encView.getChildren().addAll(encBar, encTv);
        VBox.setVgrow(encTv, Priority.ALWAYS);

        // ── Lab Results Tab
        List<String[]> labs = dao.getLabResults(patientId);
        TableView<String[]> labTv = new TableView<>(FXCollections.observableArrayList(labs));
        labTv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String[] labHdrs = {"Test","Result","Normal Range","Unit","Status","Date"};
        int[]    labIdxs = {1,2,3,4,5,6};
        for (int i=0;i<labHdrs.length;i++) {
            final int ci=labIdxs[i];
            TableColumn<String[],String> tc = new TableColumn<>(labHdrs[i]);
            tc.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(ci<d.getValue().length?d.getValue()[ci]:""));
            labTv.getColumns().add(tc);
        }

        tabs.getTabs().addAll(
            new Tab("Visits (" + encounters.size() + ")", encView),
            new Tab("Lab Results (" + labs.size() + ")", labTv)
        );

        dialog.getDialogPane().setContent(tabs);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    // ── New Encounter Dialog ───────────────────────────────────────────────

    private void showNewEncounterDialog(int patientId, String patientName) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Visit — " + patientName);
        dialog.getDialogPane().setStyle("-fx-background-color:white;");
        dialog.getDialogPane().setPrefWidth(540);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Consultation tab
        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(10); form.setPadding(new Insets(14));
        TextField dateF       = fld("YYYY-MM-DD", LocalDate.now().toString());
        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("OPD","IPD","Emergency","Follow-up","Procedure","Vaccination");
        typeBox.setValue("OPD"); typeBox.setPrefWidth(280);
        TextField complaintF  = fld("Chief complaint", "");
        TextField diagnosisF  = fld("Diagnosis", "");
        TextField icd10F      = fld("ICD-10 Code (optional)", "");
        TextArea  treatmentA  = new TextArea(); treatmentA.setPromptText("Treatment / Notes"); treatmentA.setPrefRowCount(3);
        TextField doctorF     = fld("Doctor / Provider", "");
        TextField followUpF   = fld("Follow-up Date (YYYY-MM-DD)", "");
        TextField amountF     = fld("Amount Billed (KES)", "500");

        form.addRow(0, lbl("Date"),        dateF,      lbl("Type"),      typeBox);
        form.addRow(1, lbl("Complaint"),   complaintF, lbl("Diagnosis"), diagnosisF);
        form.addRow(2, lbl("ICD-10"),      icd10F,     lbl("Doctor"),    doctorF);
        form.addRow(3, lbl("Treatment"),   treatmentA);
        form.addRow(4, lbl("Follow-up"),   followUpF,  lbl("Amount"),    amountF);

        // Vitals tab
        GridPane vitals = new GridPane(); vitals.setHgap(12); vitals.setVgap(10); vitals.setPadding(new Insets(14));
        TextField bpF     = fld("e.g. 120/80", "");
        TextField tempF   = fld("e.g. 37.2°C", "");
        TextField weightF = fld("e.g. 72kg", "");
        TextField pulseF  = fld("e.g. 78 bpm", "");
        vitals.addRow(0, lbl("Blood Pressure"), bpF, lbl("Temperature"), tempF);
        vitals.addRow(1, lbl("Weight"),         weightF, lbl("Pulse"),   pulseF);

        // Prescriptions tab
        GridPane rxForm = new GridPane(); rxForm.setHgap(12); rxForm.setVgap(10); rxForm.setPadding(new Insets(14));
        TextField medF  = fld("Medicine Name", "");
        TextField dosF  = fld("Dosage e.g. 500mg", "");
        TextField freqF = fld("Frequency e.g. TDS", "");
        TextField durF  = fld("Duration e.g. 7 days", "");
        TextField insF  = fld("Instructions", "Take after meals");
        rxForm.addRow(0, lbl("Medicine"),    medF,  lbl("Dosage"),    dosF);
        rxForm.addRow(1, lbl("Frequency"),   freqF, lbl("Duration"),  durF);
        rxForm.addRow(2, lbl("Instructions"), insF);

        tabs.getTabs().addAll(
            new Tab("Consultation", form),
            new Tab("Vitals", vitals),
            new Tab("Prescription", rxForm)
        );

        dialog.getDialogPane().setContent(tabs);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK) return;
            int userId = 1;
            try { userId = SessionManager.getInstance().getCurrentUser().getId(); } catch (Exception ignored){}
            double amount = 0;
            try { amount = Double.parseDouble(amountF.getText().trim()); } catch (Exception ignored){}

            int encId = dao.createEncounter(patientId, dateF.getText().trim(),
                typeBox.getValue(), complaintF.getText().trim(),
                diagnosisF.getText().trim(), icd10F.getText().trim(),
                treatmentA.getText().trim(), bpF.getText().trim(),
                tempF.getText().trim(), weightF.getText().trim(),
                pulseF.getText().trim(), doctorF.getText().trim(),
                followUpF.getText().trim(), amount, userId);

            if (encId > 0) {
                // Save prescription if medicine was entered
                if (!medF.getText().isBlank()) {
                    dao.addPrescription(encId, patientId, medF.getText().trim(),
                        dosF.getText().trim(), freqF.getText().trim(),
                        durF.getText().trim(), insF.getText().trim());
                }
                AuditLog.log("ENCOUNTER_CREATED", patientName + " — " + typeBox.getValue(), "patients", encId);
                refreshStats();
                Toast.success(SceneManager.getInstance().getStage(), "Visit recorded", patientName);
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void loadPatients(String query) {
        AsyncTask.run(() -> query == null ? dao.findAll() : dao.search(query),
            patientData::setAll, err -> {});
    }

    private void refreshStats() {
        totalLbl.setText(String.valueOf(dao.getTotalPatients()));
        todayLbl.setText(String.valueOf(dao.getTodayEncounters()));
        pendingLabLbl.setText(String.valueOf(dao.getPendingLabCount()));
    }

    private TableColumn<String[], String> col(String h, int idx, double w) {
        TableColumn<String[], String> c = new TableColumn<>(h); c.setPrefWidth(w);
        c.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(idx<d.getValue().length?d.getValue()[idx]:""));
        return c;
    }

    private Button btn(String t, String color) {
        Button b = new Button(t);
        b.setStyle("-fx-background-color:white;-fx-border-color:"+color+";-fx-border-radius:5;" +
            "-fx-background-radius:5;-fx-font-size:11px;-fx-text-fill:"+color+";-fx-cursor:hand;-fx-padding:3 8;");
        return b;
    }

    private TextField fld(String prompt, String val) {
        TextField tf = new TextField(val); tf.setPromptText(prompt); tf.setPrefWidth(240);
        tf.setStyle("-fx-pref-height:34px;-fx-background-color:white;-fx-border-color:#e2e8f0;" +
            "-fx-border-radius:6;-fx-background-radius:6;-fx-font-size:13px;-fx-padding:0 8;");
        return tf;
    }

    private Label lbl(String t) {
        Label l = new Label(t); l.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#475569;"); return l;
    }
}
