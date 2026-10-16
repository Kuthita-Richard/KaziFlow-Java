// The receipt must never present a locally generated number as a KRA/eTIMS number.
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.Sale;
import com.kaziflow.services.ETIMSService;
import com.kaziflow.services.ReceiptPrinter;

import java.lang.reflect.Method;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;

public class EtimsHonestyCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static String q(String sql) throws Exception { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { return r.next() ? r.getString(1) : null; } }
    static void x(String sql) throws Exception { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement s = c.createStatement()) { s.execute(sql); } }
    static int sale(String number) throws Exception { x("INSERT INTO sales (sale_number, subtotal, total_amount) VALUES ('" + number + "', 100, 116)"); return Integer.parseInt(q("SELECT id FROM sales WHERE sale_number='" + number + "'")); }
    static String receipt(int id, String number) {
        Sale s = new Sale(); s.setId(id); s.setSaleNumber(number); s.setSubtotal(100); s.setTotalAmount(116); s.setCreatedAt(LocalDateTime.now()); s.setItems(new ArrayList<>());
        return new ReceiptPrinter().generateReceipt(s);
    }

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        ETIMSService svc = ETIMSService.getInstance();

        int s1 = sale("INV-T1");
        String serial = svc.generateSerial(s1, "INV-T1");
        check("a newly generated number is labelled LOCAL-<year>-<seq>, never KRA-", serial != null && serial.matches("LOCAL-\\d{4}-\\d{6}") && !serial.contains("KRA"));
        check("it is recorded with status 'local'", "local".equals(q("SELECT status FROM etims_serials WHERE sale_id=" + s1)));
        check("the service states plainly that it does not transmit to KRA", !svc.isConnected());
        check("a local number is NOT reported as a transmitted eTIMS number", svc.getTransmittedSerial(s1) == null);

        String r1 = receipt(s1, "INV-T1");
        check("the receipt does not print 'ETIMS Serial'", !r1.contains("ETIMS Serial") && !r1.contains("eTIMS Invoice No"));
        check("...nor any KRA- number, nor the internal LOCAL- counter, nor 'PENDING'", !r1.contains("KRA-") && !r1.contains("LOCAL-") && !r1.contains("PENDING"));
        check("...but it is still a complete receipt (total present, not empty)", r1.contains("116") && r1.length() > 200);

        int s2 = sale("INV-T2"), s3 = sale("INV-T3");
        x("INSERT INTO etims_serials (serial, sale_id, sale_number, sequence, status) VALUES ('KRA-2025-000142', " + s2 + ", 'INV-T2', 142, 'local')");
        x("UPDATE sales SET etims_serial='KRA-2025-000142' WHERE id=" + s2);
        x("INSERT INTO etims_serials (serial, sale_id, sale_number, sequence, status) VALUES ('KRA-REAL-0001', " + s3 + ", 'INV-T3', 143, 'transmitted')");
        x("UPDATE sales SET etims_serial='KRA-REAL-0001' WHERE id=" + s3);
        Method m = ETIMSService.class.getDeclaredMethod("ensureTable"); m.setAccessible(true);
        m.invoke(svc);
        check("migration: an old local 'KRA-' serial is relabelled LOCAL- in etims_serials", "LOCAL-2025-000142".equals(q("SELECT serial FROM etims_serials WHERE sale_id=" + s2)));
        check("migration: ...and on the sale row", "LOCAL-2025-000142".equals(q("SELECT etims_serial FROM sales WHERE id=" + s2)));
        check("migration: a GENUINELY transmitted number is left untouched", "KRA-REAL-0001".equals(q("SELECT serial FROM etims_serials WHERE sale_id=" + s3)) && "KRA-REAL-0001".equals(q("SELECT etims_serial FROM sales WHERE id=" + s3)));
        m.invoke(svc);
        check("migration is idempotent (running again changes nothing)", "LOCAL-2025-000142".equals(q("SELECT serial FROM etims_serials WHERE sale_id=" + s2)) && "0".equals(q("SELECT COUNT(*) FROM etims_serials WHERE serial LIKE 'LOCAL-LOCAL%'")));
        String r2 = receipt(s2, "INV-T2");
        check("reprinting an OLD receipt no longer shows the fake KRA number", !r2.contains("KRA-") && !r2.contains("ETIMS Serial"));
        String r3 = receipt(s3, "INV-T3");
        check("an invoice KRA really received DOES print its eTIMS number (the slot for the real integration works)", r3.contains("eTIMS Invoice No:") && r3.contains("KRA-REAL-0001") && svc.getTransmittedSerial(s3).equals("KRA-REAL-0001"));

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
