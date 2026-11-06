// R1 step D, part 2: payment methods as master data — the service, the till, the filters, the pickers and the Settings dialog.
import com.kaziflow.dao.SaleDAO;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.*;
import com.kaziflow.modules.ModuleRegistry;
import com.kaziflow.services.PaymentMethods;
import com.kaziflow.services.PaymentMethods.Kind;
import com.kaziflow.services.PaymentMethods.Method;
import com.kaziflow.services.PaymentMethods.Scope;
import com.kaziflow.utils.PaymentMethodCombo;
import com.kaziflow.utils.SessionManager;
import com.kaziflow.views.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class PaymentMethodsCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static <T> T onFx(Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(c.call()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.get(40, TimeUnit.SECONDS);
    }
    static <T extends Node> List<T> all(Node n, Class<T> k) { List<T> out = new ArrayList<>(); walk(n, k, out); return out; }
    static <T extends Node> void walk(Node n, Class<T> k, List<T> out) { if (k.isInstance(n)) out.add(k.cast(n)); if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) walk(c, k, out); }
    static void sql(String s) throws SQLException { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement st = c.createStatement()) { st.execute(s); } }
    static long n(String q) throws SQLException { try (Connection c = DatabaseManager.getInstance().getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(q)) { return rs.next() ? rs.getLong(1) : 0; } }
    static List<String> codes(List<Method> l) { return l.stream().map(Method::code).collect(Collectors.toList()); }
    static boolean throwsIAE(Runnable r, String contains) { try { r.run(); return false; } catch (IllegalArgumentException e) { return contains == null || e.getMessage().toLowerCase().contains(contains.toLowerCase()); } }
    static boolean waitFor(Callable<Boolean> cond) throws Exception { for (int i = 0; i < 40; i++) { if (cond.call()) return true; Thread.sleep(100); } return false; }
    static Object field(Object o, String name) throws Exception { var f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
    static Sale sale(int pid, String method) {
        Sale s = new Sale(); s.setCustomerName("T"); s.setSubtotal(100); s.setTotalAmount(100); s.setVatAmount(0); s.setDiscountAmount(0); s.setAmountPaid(100); s.setChangeAmount(0);
        s.setPaymentMethod(method); s.setStatus("completed"); s.setServedBy(1); s.setServedByName("Admin");
        SaleItem it = new SaleItem(); it.setProductId(pid); it.setProductName("P"); it.setQuantity(1); it.setUnitPrice(100); it.setCostPrice(60); it.setLineTotal(100); it.setDiscount(0);
        s.setItems(new ArrayList<>(List.of(it))); return s;
    }

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        com.kaziflow.database.SchemaMigrations.runAll();
        com.kaziflow.services.ETIMSService.getInstance();
        ModuleRegistry.getInstance().init();
        User u = new User(); u.setId(1); u.setName("Admin"); SessionManager.getInstance().login(u);
        Platform.startup(() -> {});

        // ── the defaults ──
        check("a new database has the six default methods, in order", codes(PaymentMethods.all()).equals(List.of("cash", "mpesa", "card", "bank", "cheque", "credit")));
        check("the till offers cash, M-Pesa, card, bank; credit and cheque are not for sales", codes(PaymentMethods.list(Scope.SALES, true)).equals(List.of("cash", "mpesa", "card", "bank")));
        check("purchases offer cash, M-Pesa, bank, cheque, credit; expenses offer cash, M-Pesa, card, bank, cheque",
                codes(PaymentMethods.list(Scope.PURCHASES, true)).equals(List.of("cash", "mpesa", "bank", "cheque", "credit")) && codes(PaymentMethods.list(Scope.EXPENSES, true)).equals(List.of("cash", "mpesa", "card", "bank", "cheque")));
        check("the new sales table has no CHECK on payment_method", PaymentMethods.list(Scope.SALES, false).size() == 4 && n("SELECT COUNT(*) FROM sqlite_master WHERE name='sales' AND sql LIKE '%CHECK(payment_method%'") == 0);

        // ── reading a stored value ──
        check("find/label read every spelling the old code stored: mpesa, M-Pesa, m-pesa, MPESA → M-Pesa", Stream("mpesa", "M-Pesa", "m-pesa", "MPESA").allMatch(x -> "M-Pesa".equals(PaymentMethods.label(x))));
        check("…'bank transfer', 'Bank Transfer', 'bank' → Bank transfer; 'Cash' → Cash; 'cheque' → Cheque", Stream("bank transfer", "Bank Transfer", "bank").allMatch(x -> "Bank transfer".equals(PaymentMethods.label(x))) && "Cash".equals(PaymentMethods.label("Cash")) && "Cheque".equals(PaymentMethods.label("cheque")));
        check("an unknown stored value is shown as it is, null as empty", "Barter".equals(PaymentMethods.label("Barter")) && "".equals(PaymentMethods.label(null)) && PaymentMethods.find("Barter") == null);
        check("isCash: cash/Cash yes, mpesa no; isMpesa: mpesa/m-pesa/M-Pesa yes, card no", PaymentMethods.isCash("cash") && PaymentMethods.isCash("Cash") && !PaymentMethods.isCash("mpesa")
                && PaymentMethods.isMpesa("mpesa") && PaymentMethods.isMpesa("m-pesa") && PaymentMethods.isMpesa("M-Pesa") && !PaymentMethods.isMpesa("card") && !PaymentMethods.isMpesa(null));

        // ── adding ──
        check("add rejects a bad code, a missing or too long name, no scope, a duplicate code, and a duplicate name in other spelling",
                throwsIAE(() -> PaymentMethods.add("A", "X", Kind.OTHER, true, false, false), "code") && throwsIAE(() -> PaymentMethods.add("9x", "X", Kind.OTHER, true, false, false), "code")
                && throwsIAE(() -> PaymentMethods.add("has space", "X", Kind.OTHER, true, false, false), "code") && throwsIAE(() -> PaymentMethods.add("ok_code", " ", Kind.OTHER, true, false, false), "name")
                && throwsIAE(() -> PaymentMethods.add("ok_code", "x".repeat(31), Kind.OTHER, true, false, false), "name") && throwsIAE(() -> PaymentMethods.add("ok_code", "Fine", Kind.OTHER, false, false, false), "where")
                && throwsIAE(() -> PaymentMethods.add("cash", "Other cash", Kind.OTHER, true, false, false), "already") && throwsIAE(() -> PaymentMethods.add("mp2", "m pesa", Kind.OTHER, true, false, false), "already"));
        Method airtel = PaymentMethods.add("Airtel_Money", "Airtel Money", Kind.MOBILE_MONEY, true, true, false);
        check("add: Airtel Money is stored (code lower-cased), active, last in order, offered for sales and purchases but not expenses",
                airtel != null && airtel.code().equals("airtel_money") && airtel.active() && airtel.kind() == Kind.MOBILE_MONEY && airtel.integration() == null && !airtel.system()
                && codes(PaymentMethods.list(Scope.SALES, true)).equals(List.of("cash", "mpesa", "card", "bank", "airtel_money")) && !codes(PaymentMethods.list(Scope.EXPENSES, true)).contains("airtel_money")
                && PaymentMethods.isMpesa("airtel_money") == false && PaymentMethods.isCash("airtel_money") == false);
        check("add is written to the audit log (it is written on a background thread, so wait for it)", waitFor(() -> n("SELECT COUNT(*) FROM audit_log WHERE action='PAYMENT_METHOD_ADDED' AND description LIKE '%airtel_money%'") >= 1));
        PaymentMethods.setSalesReadyCheck(() -> false);
        check("while the sales-table upgrade is unfinished, a method for sales is refused with a clear reason, but one for purchases/expenses is fine",
                throwsIAE(() -> PaymentMethods.add("sacco", "SACCO account", Kind.BANK, true, false, false), "upgrade") && PaymentMethods.add("sacco", "SACCO account", Kind.BANK, false, true, true) != null);
        PaymentMethods.setSalesReadyCheck(null);

        // ── rename / reorder / switch off ──
        PaymentMethods.update("bank", "Bank / RTGS", 15, true, true, true);
        check("update: the name and the position change (Bank / RTGS now sits between Cash and M-Pesa), the code does not",
                "Bank / RTGS".equals(PaymentMethods.label("bank")) && codes(PaymentMethods.list(Scope.SALES, true)).subList(0, 3).equals(List.of("cash", "bank", "mpesa")) && PaymentMethods.find("bank") != null);
        check("old free-text values still resolve after a rename ('bank transfer', 'Bank Transfer' → the bank method, now called Bank / RTGS)", "Bank / RTGS".equals(PaymentMethods.label("bank transfer")) && "Bank / RTGS".equals(PaymentMethods.label("Bank Transfer")));
        check("update rejects an existing name, an empty selection of scopes, and taking the system method out of a scope",
                throwsIAE(() -> PaymentMethods.update("card", "M-Pesa", 30, true, false, true), "already") && throwsIAE(() -> PaymentMethods.update("card", "Card", 30, false, false, false), "where")
                && throwsIAE(() -> PaymentMethods.update("cash", "Cash", 10, true, false, true), "system"));
        PaymentMethods.update("bank", "Bank transfer", 40, true, true, true);
        PaymentMethods.setActive("card", false);
        check("switching a method off removes it from what is offered but not from the records: label still resolves, filters still list it",
                !codes(PaymentMethods.list(Scope.SALES, true)).contains("card") && codes(PaymentMethods.all()).contains("card") && "Card".equals(PaymentMethods.label("card"))
                && PaymentMethodCombo.filter("All").getItems().contains("card") && !PaymentMethodCombo.create(Scope.SALES, "cash").getItems().contains("card"));
        check("the system method cannot be switched off", throwsIAE(() -> PaymentMethods.setActive("cash", false), "system"));
        PaymentMethods.setActive("card", true);
        check("…and it can be switched back on", codes(PaymentMethods.list(Scope.SALES, true)).contains("card"));

        // ── the combos ──
        var buy = PaymentMethodCombo.create(Scope.PURCHASES, "credit"); var exp = PaymentMethodCombo.create(Scope.EXPENSES, "cash"); var flt = PaymentMethodCombo.filter("All Payment Methods");
        check("pickers: values are codes, the display is the label, the default is honoured (credit for purchases, cash for expenses)",
                "credit".equals(buy.getValue()) && "Credit (pay later)".equals(buy.getConverter().toString("credit")) && "cash".equals(exp.getValue()) && "M-Pesa".equals(exp.getConverter().toString("mpesa"))
                && buy.getItems().equals(codes(PaymentMethods.list(Scope.PURCHASES, true))));
        check("pickers: a default that is not offered falls back to the first method", PaymentMethodCombo.create(Scope.SALES, "credit").getValue().equals("cash"));
        check("filter box: 'All' first (shown with its label), then every method", PaymentMethodCombo.ALL.equals(flt.getValue()) && "All Payment Methods".equals(flt.getConverter().toString(PaymentMethodCombo.ALL)) && flt.getItems().size() == 1 + PaymentMethods.all().size());

        // ── documents ──
        sql("INSERT INTO products (sku, name, selling_price, cost_price, stock_quantity, status) VALUES ('PM1','P',100,60,100000,'active')");
        int pid = (int) n("SELECT id FROM products WHERE sku='PM1'");
        SaleDAO sd = new SaleDAO(); boolean allOk = true;
        for (String m : List.of("cash", "mpesa", "card", "bank", "airtel_money")) allOk &= sd.save(sale(pid, m)) != null;
        check("sales save with every offered method, including one added in Settings (airtel_money) — a CHECK failure before", allOk);
        check("a method that is not in the master data cannot be stored (foreign key)", sd.save(sale(pid, "bitcoin")) == null);
        PaymentMethods.setActive("card", false);
        check("a sale with a method that has since been switched off still saves (refunds, imports, other tills)", sd.save(sale(pid, "card")) != null);
        PaymentMethods.setActive("card", true);

        // ── the till ──
        Parent till = onFx(() -> new SalesView().getRoot());
        SalesView[] sv = new SalesView[1];
        onFx(() -> { sv[0] = new SalesView(); return null; });
        List<ToggleButton> btns = onFx(() -> all(sv[0].getRoot(), ToggleButton.class).stream().filter(b -> PaymentMethods.list(Scope.SALES, true).stream().anyMatch(m -> m.label().equals(b.getText()))).toList());
        check("the till's payment buttons are exactly the active sales methods, in order, by their labels",
                btns.stream().map(ToggleButton::getText).toList().equals(PaymentMethods.list(Scope.SALES, true).stream().map(Method::label).toList()) && btns.size() == 5);
        ToggleButton mp = btns.stream().filter(b -> b.getText().equals("M-Pesa")).findFirst().orElseThrow();
        onFx(() -> { mp.setSelected(true); return null; });
        check("selecting the M-Pesa button sets the code 'mpesa' (it set 'm-pesa': the STK push never ran and the sale broke the CHECK)", "mpesa".equals(onFx(() -> field(sv[0], "selectedPayment"))));
        check("…the cash-tendered row is hidden for M-Pesa and shown for Cash", !((javafx.scene.layout.HBox) onFx(() -> field(sv[0], "cashTenderedRow"))).isVisible() && onFx(() -> {
            btns.stream().filter(b -> b.getText().equals("Cash")).findFirst().get().setSelected(true);
            return ((javafx.scene.layout.HBox) field(sv[0], "cashTenderedRow")).isVisible() && "cash".equals(field(sv[0], "selectedPayment")); }));
        ToggleButton airtelBtn = btns.stream().filter(b -> b.getText().equals("Airtel Money")).findFirst().orElseThrow();
        check("a method added in Settings appears at the till and selects its own code", onFx(() -> { airtelBtn.setSelected(true); return "airtel_money".equals(field(sv[0], "selectedPayment")) && !PaymentMethods.isMpesa("airtel_money"); }));
        PaymentMethods.setActive("card", false);
        List<String> afterOff = onFx(() -> { SalesView v2 = new SalesView(); return all(v2.getRoot(), ToggleButton.class).stream().map(ToggleButton::getText).filter(t -> PaymentMethods.find(t) != null).toList(); });
        check("a switched-off method disappears from the till", !afterOff.contains("Card") && afterOff.contains("Cash"));
        PaymentMethods.setActive("card", true);

        // ── sales history: filter and badges ──
        SalesHistoryView hv = onFx(() -> new SalesHistoryView());
        ComboBox<String> hf = onFx(() -> all(hv.getRoot(), ComboBox.class).stream().filter(c -> c.getItems().contains(PaymentMethodCombo.ALL)).map(c -> (ComboBox<String>) c).findFirst().orElseThrow());
        TableView<Sale> ht = onFx(() -> all(hv.getRoot(), TableView.class).get(0));
        long mpesaSales = n("SELECT COUNT(*) FROM sales WHERE payment_method='mpesa'");
        onFx(() -> { hf.setValue("mpesa"); hf.getOnAction().handle(new javafx.event.ActionEvent()); return null; });
        check("history filter 'M-Pesa' finds the M-Pesa sales (its label never matched the stored code 'mpesa')", mpesaSales >= 1 && onFx(() -> ht.getItems().size()) == mpesaSales && onFx(() -> ht.getItems().stream().allMatch(s -> "mpesa".equals(s.getPaymentMethod()))));
        onFx(() -> { hf.setValue(PaymentMethodCombo.ALL); hf.getOnAction().handle(new javafx.event.ActionEvent()); return null; });
        check("history filter back to 'all' shows every sale", onFx(() -> ht.getItems().size()) == n("SELECT COUNT(*) FROM sales"));
        check("history badges show labels (M-PESA, AIRTEL MONEY, CASH), not raw codes", onFx(() -> {
            javafx.stage.Stage stg = new javafx.stage.Stage(); stg.setScene(new javafx.scene.Scene(hv.getRoot(), 1300, 1000)); stg.show();
            hv.getRoot().applyCss(); hv.getRoot().layout(); ht.refresh(); hv.getRoot().applyCss(); hv.getRoot().layout();
            return all(hv.getRoot(), Label.class).stream().map(Label::getText).collect(Collectors.toSet()); }).containsAll(Set.of("M-PESA", "AIRTEL MONEY", "CASH")));

        // ── Settings ──
        SettingsView st = onFx(() -> new SettingsView());
        check("Settings has a Payment Methods card", onFx(() -> all(st.getRoot(), Label.class).stream().anyMatch(l -> "Payment Methods".equals(l.getText()))));
        var m = SettingsView.class.getDeclaredMethod("buildPaymentMethodsDialog"); m.setAccessible(true);
        Dialog<?> dlg = onFx(() -> (Dialog<?>) m.invoke(st));
        TableView<Method> tv = onFx(() -> all(dlg.getDialogPane().getContent(), TableView.class).get(0));
        Button toggle = onFx(() -> all(dlg.getDialogPane().getContent(), Button.class).stream().filter(b -> b.getText().startsWith("Switch")).findFirst().orElseThrow());
        check("the dialog lists every payment method with its type and uses", onFx(() -> tv.getItems().size()) == PaymentMethods.all().size());
        Method cardRow = onFx(() -> tv.getItems().stream().filter(x -> x.code().equals("card")).findFirst().orElseThrow());
        onFx(() -> { tv.getSelectionModel().select(cardRow); toggle.fire(); return null; });
        check("'Switch on / off' on Card switches it off (and the table shows it)", !PaymentMethods.find("card").active() && !onFx(() -> tv.getItems().stream().filter(x -> x.code().equals("card")).findFirst().get().active()));
        onFx(() -> { toggle.fire(); return null; });
        check("…and on again", PaymentMethods.find("card").active());
        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
    static java.util.stream.Stream<String> Stream(String... s) { return java.util.Arrays.stream(s); }
}
