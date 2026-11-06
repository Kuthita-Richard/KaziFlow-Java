// R1 sweep part 2: People (employees, attendance, leave) and Purchases (suppliers, orders) cards show real figures or "—".
// Renders the real tabs with an EMPTY database (no invented figure may appear) and with seeded rows (figures = the data).
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.User;
import com.kaziflow.modules.ModuleRegistry;
import com.kaziflow.utils.DateRange;
import com.kaziflow.utils.KESFormatter;
import com.kaziflow.utils.SessionManager;
import com.kaziflow.views.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class PeopleFiguresCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static <T> T onFx(Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(c.call()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.get(40, TimeUnit.SECONDS);
    }
    static void collect(Node n, List<String> out) {
        if (n instanceof Labeled l && l.getText() != null && !l.getText().isBlank()) out.add(l.getText());
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) collect(c, out);
    }
    static String text(Node root) { List<String> l = new ArrayList<>(); collect(root, l); return String.join(" | ", l); }
    static Node priv(Object view, String m) throws Exception { var mm = view.getClass().getDeclaredMethod(m); mm.setAccessible(true); return (Node) mm.invoke(view); }
    static void sql(String s) throws SQLException {
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement st = c.createStatement()) { st.execute(s); }
    }
    @SuppressWarnings("rawtypes")
    static void findTables(Node n, List<javafx.scene.control.TableView> out) {
        if (n instanceof javafx.scene.control.TableView tv) out.add(tv);
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) findTables(c, out);
    }
    static Map<String, String> screens() throws Exception {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("employees", onFx(() -> text(priv(new EmployeesView(), "buildEmployeeBody"))));
        m.put("attendance", onFx(() -> text(priv(new EmployeesView(), "buildAttendance"))));
        m.put("leave-tab", onFx(() -> text(priv(new EmployeesView(), "buildLeaveRequests"))));
        m.put("leave-screen", onFx(() -> text(new LeaveRequestView().getRoot())));
        m.put("suppliers", onFx(() -> text(priv(new PurchasesView(), "buildSuppliersBody"))));
        m.put("orders", onFx(() -> text(priv(new PurchasesView(), "buildPurchaseOrdersBody"))));
        return m;
    }

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        ModuleRegistry.getInstance().init();
        User u = new User(); u.setId(1); u.setName("Admin"); SessionManager.getInstance().login(u);
        Platform.startup(() -> {});
        String[] banned = {"81% rate", "93% active", "Industry standard", "30 days", "October 2024", "4 departments", "After 8:00 AM", "Annual 21d", "Sick 10d", "Casual 5d"};
        Map<String, String> empty = screens();
        for (var e : empty.entrySet()) {
            String bad = null; for (String b : banned) if (e.getValue().contains(b)) { bad = b; break; }
            check("empty DB, " + e.getKey() + ": no invented figure" + (bad == null ? "" : " (found '" + bad + "')"), bad == null);
        }
        check("empty DB: employees say 0 departments and that no attendance was recorded", empty.get("employees").contains("0 departments") && empty.get("employees").contains("No attendance recorded today"));
        check("empty DB: purchases show 'No active suppliers' and an em dash for the average order", empty.get("suppliers").contains("No active suppliers") && empty.get("orders").contains("—"));
        check("empty DB: purchase orders are labelled with the real current month", empty.get("orders").contains(DateRange.monthLabel(YearMonth.now())));

        // seed: 4 active employees in 2 departments (+1 with none), attendance, leave
        for (int i = 1; i <= 4; i++)
            sql("INSERT INTO employees (employee_number, name, department_id, salary, status) VALUES ('E-" + i + "','Emp " + i + "'," + (i == 4 ? "NULL" : (i == 3 ? "2" : "1")) + ",1000,'active')");
        // employees exist but nobody has applied for leave: the leave tab must be empty, not filled with example requests
        String leaveEmptyText = onFx(() -> text(priv(new EmployeesView(), "buildLeaveRequests")));
        int leaveRowsShown = onFx(() -> { List<javafx.scene.control.TableView> tv = new ArrayList<>(); findTables(priv(new EmployeesView(), "buildLeaveRequests"), tv); return tv.isEmpty() ? -1 : tv.get(0).getItems().size(); });
        check("employees but no leave requests: the leave table is empty (it used to show invented John Doe / Jane Smith requests)", leaveRowsShown == 0 && !leaveEmptyText.contains("Family vacation") && !leaveEmptyText.contains("John Doe"));
        String today = LocalDate.now().toString();
        String[] st = {"present", "present", "late", "absent"};
        for (int i = 0; i < 4; i++)
            sql("INSERT INTO attendance (employee_id, date, status) VALUES ((SELECT id FROM employees WHERE employee_number='E-" + (i + 1) + "'),'" + today + "','" + st[i] + "')");
        int y = Year.now().getValue();
        String eid = "(SELECT id FROM employees WHERE employee_number='E-1')";
        sql("INSERT INTO leave_requests (employee_id, leave_type, start_date, end_date, days_count, status) VALUES (" + eid + ",'Annual','" + LocalDate.now().minusDays(1) + "','" + LocalDate.now().plusDays(3) + "',5,'approved')");
        sql("INSERT INTO leave_requests (employee_id, leave_type, start_date, end_date, days_count, status) VALUES (" + eid + ",'Sick','" + y + "-01-10','" + y + "-01-11',2,'approved')");
        sql("INSERT INTO leave_requests (employee_id, leave_type, start_date, end_date, days_count, status) VALUES (" + eid + ",'Annual','" + y + "-02-10','" + y + "-02-12',3,'approved')");
        sql("INSERT INTO leave_requests (employee_id, leave_type, start_date, end_date, days_count, status) VALUES (" + eid + ",'Annual','" + (y - 1) + "-03-10','" + (y - 1) + "-03-12',3,'approved')");
        sql("INSERT INTO leave_requests (employee_id, leave_type, start_date, end_date, days_count, status) VALUES (" + eid + ",'Annual','" + y + "-05-10','" + y + "-05-12',3,'pending')");
        sql("INSERT INTO leave_requests (employee_id, leave_type, start_date, end_date, days_count, status) VALUES (" + eid + ",'Annual','" + y + "-06-10','" + y + "-06-12',3,'pending')");
        sql("INSERT INTO leave_requests (employee_id, leave_type, start_date, end_date, days_count, status) VALUES (" + eid + ",'Annual','" + y + "-07-10','" + y + "-07-12',3,'rejected')");
        // suppliers: 4 (3 active: terms 30/45/60; 1 inactive 90) and 3 orders
        sql("INSERT INTO suppliers (code, name, status, payment_terms) VALUES ('S1','A','active',30),('S2','B','active',45),('S3','C','active',60),('S4','D','inactive',90)");
        for (int i = 1; i <= 3; i++)
            sql("INSERT INTO purchases (purchase_number, supplier_id, subtotal, total_amount, balance, status, payment_status) VALUES ('P-" + i + "',(SELECT id FROM suppliers WHERE code='S1')," + i * 100 + "," + i * 100 + "," + i * 100 + ",'pending','unpaid')");
        Map<String, String> d = screens();
        check("data, employees: 2 departments (the employee with none is not a department)", d.get("employees").contains("2 departments"));
        check("data, employees: checked in today = present + late = 3 (75% of 4 active staff)", d.get("employees").contains("75% of active staff") && d.get("employees").contains("Checked In Today | 3"));
        check("data, employees: pending actions = 2 pending leave requests", d.get("employees").contains("Pending Actions | 2"));
        check("data, attendance tab: present 2, absent 1, late 1 (from the rows, not 'all staff')", d.get("attendance").contains("Present Today | 2") && d.get("attendance").contains("Absent Today | 1") && d.get("attendance").contains("Late Arrivals | 1"));
        check("data, attendance tab: on leave today = 1 approved leave covering today", d.get("attendance").contains("On Leave | 1"));
        check("data, leave tab: pending 2, approved this year 3 (last year's excluded), rejected 1, on leave now 1",
                d.get("leave-tab").contains("Pending Requests | 2") && d.get("leave-tab").contains("Approved | 3") && d.get("leave-tab").contains("Rejected | 1") && d.get("leave-tab").contains("Currently on Leave | 1"));
        check("data, leave screen: the request list loads from the table (7 rows; it threw 'no such column: days' before)",
                onFx(() -> { List<javafx.scene.control.TableView> tv = new ArrayList<>(); findTables(new LeaveRequestView().getRoot(), tv); return tv.isEmpty() ? -1 : tv.get(0).getItems().size(); }) == 7);
        check("data, leave screen: days approved this year = 5+2+3 = 10 (not typed policies)", d.get("leave-screen").contains("Days Approved | 10"));
        check("data, suppliers: 3 of 4 active = 75%, average terms of ACTIVE suppliers = 45 days",
                d.get("suppliers").contains("75% of suppliers") && d.get("suppliers").contains("45 days") && !d.get("suppliers").contains("No active suppliers"));
        check("data, orders: 3 orders, 3 this month, average order value KES 200",
                d.get("orders").contains("Total Orders | 3") && d.get("orders").contains("This Month | 3") && d.get("orders").contains(KESFormatter.formatShort(200)));
        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
