package com.kaziflow.dao;

import com.kaziflow.database.DatabaseManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * PatientDAO — patient/animal records for Clinic, Hospital, Vet Clinic.
 *
 * Tables: patients, patient_encounters, patient_prescriptions, lab_results
 */
public class PatientDAO {

    private Connection conn() throws SQLException {
        return DatabaseManager.getInstance().getConnection();
    }

    public void ensureTables() {
        try (Connection c = conn(); Statement s = c.createStatement()) {

            s.execute("""
                CREATE TABLE IF NOT EXISTS patients (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    mrn             TEXT NOT NULL UNIQUE,
                    first_name      TEXT NOT NULL,
                    last_name       TEXT NOT NULL,
                    date_of_birth   DATE,
                    gender          TEXT CHECK(gender IN ('Male','Female','Other')),
                    phone           TEXT,
                    email           TEXT,
                    address         TEXT,
                    blood_group     TEXT,
                    allergies       TEXT,
                    insurance_no    TEXT,
                    insurance_provider TEXT,
                    species         TEXT,
                    breed           TEXT,
                    owner_name      TEXT,
                    notes           TEXT,
                    status          TEXT DEFAULT 'active',
                    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            s.execute("""
                CREATE TABLE IF NOT EXISTS patient_encounters (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    patient_id      INTEGER NOT NULL REFERENCES patients(id),
                    encounter_no    TEXT NOT NULL UNIQUE,
                    encounter_date  DATE NOT NULL,
                    encounter_type  TEXT DEFAULT 'OPD',
                    chief_complaint TEXT,
                    diagnosis       TEXT,
                    icd10_code      TEXT,
                    treatment_notes TEXT,
                    vitals_bp       TEXT,
                    vitals_temp     TEXT,
                    vitals_weight   TEXT,
                    vitals_pulse    TEXT,
                    doctor_name     TEXT,
                    follow_up_date  DATE,
                    amount_billed   REAL DEFAULT 0,
                    payment_status  TEXT DEFAULT 'unpaid',
                    created_by      INTEGER REFERENCES users(id),
                    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            s.execute("""
                CREATE TABLE IF NOT EXISTS patient_prescriptions (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    encounter_id    INTEGER NOT NULL REFERENCES patient_encounters(id),
                    patient_id      INTEGER NOT NULL REFERENCES patients(id),
                    medicine_name   TEXT NOT NULL,
                    dosage          TEXT,
                    frequency       TEXT,
                    duration        TEXT,
                    instructions    TEXT,
                    dispensed       INTEGER DEFAULT 0,
                    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            s.execute("""
                CREATE TABLE IF NOT EXISTS lab_results (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    encounter_id    INTEGER REFERENCES patient_encounters(id),
                    patient_id      INTEGER NOT NULL REFERENCES patients(id),
                    test_name       TEXT NOT NULL,
                    result          TEXT,
                    normal_range    TEXT,
                    unit            TEXT,
                    status          TEXT DEFAULT 'pending' CHECK(status IN ('pending','completed','critical')),
                    lab_date        DATE,
                    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Patient CRUD ──────────────────────────────────────────────────────

    public String generateMRN() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*)+1 FROM patients");
            if (rs.next()) return String.format("MRN-%06d", rs.getInt(1));
        } catch (Exception e) { e.printStackTrace(); }
        return "MRN-000001";
    }

    public int savePatient(String firstName, String lastName, String dob, String gender,
                            String phone, String email, String address, String bloodGroup,
                            String allergies, String insuranceNo, String insuranceProvider,
                            String species, String breed, String ownerName, String notes) {
        String mrn = generateMRN();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("""
            INSERT INTO patients (mrn,first_name,last_name,date_of_birth,gender,phone,email,
            address,blood_group,allergies,insurance_no,insurance_provider,
            species,breed,owner_name,notes)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, mrn);         ps.setString(2, firstName);
            ps.setString(3, lastName);    ps.setString(4, dob.isBlank() ? null : dob);
            ps.setString(5, gender);      ps.setString(6, phone);
            ps.setString(7, email);       ps.setString(8, address);
            ps.setString(9, bloodGroup);  ps.setString(10, allergies);
            ps.setString(11, insuranceNo); ps.setString(12, insuranceProvider);
            ps.setString(13, species);    ps.setString(14, breed);
            ps.setString(15, ownerName);  ps.setString(16, notes);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    public List<String[]> findAll() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("""
                SELECT p.id, p.mrn, p.first_name||' '||p.last_name AS full_name,
                       p.gender, p.phone, p.blood_group, p.insurance_no,
                       p.allergies, p.species, p.created_at,
                       COUNT(e.id) AS visit_count
                FROM patients p
                LEFT JOIN patient_encounters e ON e.patient_id = p.id
                WHERE p.status='active'
                GROUP BY p.id ORDER BY p.created_at DESC LIMIT 200
            """);
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("mrn"),
                    rs.getString("full_name"),
                    rs.getString("gender") != null ? rs.getString("gender") : "—",
                    rs.getString("phone")  != null ? rs.getString("phone")  : "—",
                    rs.getString("blood_group") != null ? rs.getString("blood_group") : "—",
                    rs.getString("allergies") != null ? rs.getString("allergies") : "None",
                    rs.getString("species") != null ? rs.getString("species") : "Human",
                    String.valueOf(rs.getInt("visit_count")),
                    rs.getString("created_at")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public List<String[]> search(String query) {
        List<String[]> list = new ArrayList<>();
        String q = "%" + query + "%";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("""
            SELECT id, mrn, first_name||' '||last_name AS full_name,
                   gender, phone, blood_group, allergies, species, '0', created_at
            FROM patients
            WHERE status='active' AND (first_name LIKE ? OR last_name LIKE ? OR mrn LIKE ? OR phone LIKE ?)
            ORDER BY first_name LIMIT 50
        """)) {
            ps.setString(1,q); ps.setString(2,q); ps.setString(3,q); ps.setString(4,q);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")), rs.getString("mrn"),
                    rs.getString("full_name"),
                    rs.getString("gender") != null ? rs.getString("gender") : "—",
                    rs.getString("phone")  != null ? rs.getString("phone")  : "—",
                    rs.getString("blood_group") != null ? rs.getString("blood_group") : "—",
                    rs.getString("allergies") != null ? rs.getString("allergies") : "None",
                    rs.getString("species") != null ? rs.getString("species") : "Human",
                    "0", rs.getString("created_at")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // ── Encounters ────────────────────────────────────────────────────────

    public int createEncounter(int patientId, String date, String type,
                                String complaint, String diagnosis, String icd10,
                                String treatment, String vitalsBP, String vitalsTemp,
                                String vitalsWeight, String vitalsPulse,
                                String doctorName, String followUpDate,
                                double amountBilled, int createdBy) {
        try (Connection c = conn()) {
            int count = 0;
            try (Statement s = c.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT COUNT(*)+1 FROM patient_encounters");
                if (rs.next()) count = rs.getInt(1);
            }
            String encNo = String.format("ENC-%06d", count);
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO patient_encounters
                (patient_id,encounter_no,encounter_date,encounter_type,chief_complaint,
                 diagnosis,icd10_code,treatment_notes,vitals_bp,vitals_temp,
                 vitals_weight,vitals_pulse,doctor_name,follow_up_date,amount_billed,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, patientId);       ps.setString(2, encNo);
                ps.setString(3, date);         ps.setString(4, type);
                ps.setString(5, complaint);    ps.setString(6, diagnosis);
                ps.setString(7, icd10);        ps.setString(8, treatment);
                ps.setString(9, vitalsBP);     ps.setString(10, vitalsTemp);
                ps.setString(11, vitalsWeight); ps.setString(12, vitalsPulse);
                ps.setString(13, doctorName);
                ps.setString(14, followUpDate.isBlank() ? null : followUpDate);
                ps.setDouble(15, amountBilled); ps.setInt(16, createdBy);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    public List<String[]> getEncounters(int patientId) {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("""
            SELECT id,encounter_no,encounter_date,encounter_type,chief_complaint,
                   diagnosis,doctor_name,follow_up_date,amount_billed,payment_status
            FROM patient_encounters WHERE patient_id=? ORDER BY encounter_date DESC
        """)) {
            ps.setInt(1, patientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("encounter_no"),
                    rs.getString("encounter_date"),
                    rs.getString("encounter_type"),
                    rs.getString("chief_complaint") != null ? rs.getString("chief_complaint") : "—",
                    rs.getString("diagnosis") != null ? rs.getString("diagnosis") : "—",
                    rs.getString("doctor_name") != null ? rs.getString("doctor_name") : "—",
                    rs.getString("follow_up_date") != null ? rs.getString("follow_up_date") : "—",
                    String.format("%.2f", rs.getDouble("amount_billed")),
                    rs.getString("payment_status")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // ── Prescriptions ─────────────────────────────────────────────────────

    public boolean addPrescription(int encounterId, int patientId, String medicine,
                                    String dosage, String frequency, String duration, String instructions) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("""
            INSERT INTO patient_prescriptions
            (encounter_id,patient_id,medicine_name,dosage,frequency,duration,instructions)
            VALUES (?,?,?,?,?,?,?)
        """)) {
            ps.setInt(1, encounterId); ps.setInt(2, patientId);
            ps.setString(3, medicine); ps.setString(4, dosage);
            ps.setString(5, frequency); ps.setString(6, duration);
            ps.setString(7, instructions);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public List<String[]> getPrescriptions(int encounterId) {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM patient_prescriptions WHERE encounter_id=?")) {
            ps.setInt(1, encounterId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("medicine_name"),
                    rs.getString("dosage") != null ? rs.getString("dosage") : "—",
                    rs.getString("frequency") != null ? rs.getString("frequency") : "—",
                    rs.getString("duration") != null ? rs.getString("duration") : "—",
                    rs.getString("instructions") != null ? rs.getString("instructions") : "—",
                    rs.getInt("dispensed") == 1 ? "Yes" : "No"
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // ── Lab Results ───────────────────────────────────────────────────────

    public boolean addLabResult(int encounterId, int patientId, String testName,
                                 String result, String normalRange, String unit, String labDate) {
        String status = (result == null || result.isBlank()) ? "pending" : "completed";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO lab_results (encounter_id,patient_id,test_name,result,normal_range,unit,lab_date,status) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setInt(1, encounterId); ps.setInt(2, patientId);
            ps.setString(3, testName); ps.setString(4, result.isBlank() ? null : result);
            ps.setString(5, normalRange); ps.setString(6, unit);
            ps.setString(7, labDate.isBlank() ? null : labDate);
            ps.setString(8, status);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    public List<String[]> getLabResults(int patientId) {
        List<String[]> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("""
            SELECT l.id, l.test_name, l.result, l.normal_range, l.unit, l.status, l.lab_date,
                   e.encounter_date
            FROM lab_results l
            LEFT JOIN patient_encounters e ON e.id = l.encounter_id
            WHERE l.patient_id=? ORDER BY l.created_at DESC LIMIT 50
        """)) {
            ps.setInt(1, patientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("test_name"),
                    rs.getString("result") != null ? rs.getString("result") : "Pending",
                    rs.getString("normal_range") != null ? rs.getString("normal_range") : "—",
                    rs.getString("unit") != null ? rs.getString("unit") : "—",
                    rs.getString("status"),
                    rs.getString("lab_date") != null ? rs.getString("lab_date") : "—",
                    rs.getString("encounter_date") != null ? rs.getString("encounter_date") : "—"
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    public int getTotalPatients() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM patients WHERE status='active'");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    public int getTodayEncounters() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT COUNT(*) FROM patient_encounters WHERE encounter_date=date('now')");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }

    public int getPendingLabCount() {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT COUNT(*) FROM lab_results WHERE status='pending'");
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) { return 0; }
    }
}
