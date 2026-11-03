// UI check for the R1 "trust" fixes: login makes no false claims and has no dead Google button; the Reports stock pie shows
// real data or an honest empty state. Renders the real screens with the real stylesheet under a (virtual) display.
import com.kaziflow.brand.Brand;
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.User;
import com.kaziflow.modules.ModuleRegistry;
import com.kaziflow.utils.SessionManager;
import com.kaziflow.views.LoginView;
import com.kaziflow.views.ReportsView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class TrustFixesUiCheck {
    static int pass, fail;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (ok) pass++; else fail++; }
    static <T> T onFx(Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(c.call()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.get(40, TimeUnit.SECONDS);
    }
    static <T extends Node> void collect(Node n, Class<T> k, List<T> out) {
        if (k.isInstance(n)) out.add(k.cast(n));
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) collect(c, k, out);
    }
    static List<String> texts(Node root) {
        List<Labeled> ls = new ArrayList<>(); collect(root, Labeled.class, ls);
        List<String> out = new ArrayList<>();
        for (Labeled l : ls) if (l.getText() != null && !l.getText().isBlank()) out.add(l.getText());
        return out;
    }
    static void sql(String s) throws SQLException {
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement st = c.createStatement()) { st.execute(s); }
    }
    static double scalar(String s) throws SQLException {
        try (Connection c = DatabaseManager.getInstance().getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(s)) { return rs.next() ? rs.getDouble(1) : 0; }
    }
    static Scene scene(Parent root) {
        Scene sc = new Scene(root, 1200, 760);
        sc.getStylesheets().add(TrustFixesUiCheck.class.getResource("/styles/main.css").toExternalForm());
        Stage s = new Stage(); s.setScene(sc); s.show();
        return sc;
    }
    static PieChart pieOf(Parent root) {
        List<PieChart> l = new ArrayList<>(); collect(root, PieChart.class, l);
        return l.isEmpty() ? null : l.get(0);
    }

    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        ModuleRegistry.getInstance().init();
        User u = new User(); u.setId(1); u.setName("Admin"); SessionManager.getInstance().login(u);
        Platform.startup(() -> {});

        // ── Login ──
        List<String> lt = onFx(() -> texts(scene(new LoginView().getRoot()).getRoot()));
        String all = String.join(" | ", lt).toLowerCase();
        check("login: no 'Trusted by' / customer-count claim", !all.contains("trusted by") && !all.contains("500+") && !all.contains("businesses in nairobi"));
        check("login: no Google sign-in button or divider text", !all.contains("google") && !all.contains("or continue with") && !all.matches(".*\\bor\\b\\s*\\|.*"));
        check("login: shows the product tagline", !Brand.tagline().isBlank() && lt.contains(Brand.tagline()));
        check("login: shows the Tihems lineage", lt.stream().anyMatch(t -> t.contains(Brand.lineage())));
        check("login: sign-in button is present", lt.stream().anyMatch(t -> t.toLowerCase().contains("sign in") || t.toLowerCase().contains("log in") || t.toLowerCase().contains("login")));

        // ── Reports pie: empty state ──
        sql("DELETE FROM products");
        final Parent[] rv = new Parent[1];
        onFx(() -> { rv[0] = new ReportsView().getRoot(); scene(rv[0]); return null; });
        check("pie (no stock): no PieChart is drawn", onFx(() -> pieOf(rv[0])) == null);
        check("pie (no stock): says so honestly", onFx(() -> texts(rv[0])).stream().anyMatch(t -> t.contains("No stock on hand yet")));
        check("pie (no stock): none of the old invented slice names appear", onFx(() -> texts(rv[0])).stream().noneMatch(t -> t.equals("Cement") || t.equals("Steel") || t.equals("Paint")));

        // ── Reports pie: real data, incl. a product with no category and more than 5 categories ──
        for (int i = 1; i <= 7; i++) sql("INSERT INTO categories (name) VALUES ('Cat" + i + "')");
        for (int i = 1; i <= 7; i++)
            sql("INSERT INTO products (name, sku, selling_price, cost_price, stock_quantity, category_id) VALUES ('P" + i + "','SKU-" + i + "'," + (100 + i) + "," + (10 * i) + "," + (3 + i) + ", (SELECT id FROM categories WHERE name='Cat" + i + "'))");
        sql("INSERT INTO products (name, sku, selling_price, cost_price, stock_quantity, category_id) VALUES ('NoCat','SKU-N',500,200,50,NULL)");
        sql("INSERT INTO products (name, sku, selling_price, cost_price, stock_quantity, category_id) VALUES ('Zero','SKU-Z',9,9,0,(SELECT id FROM categories WHERE name='Cat1'))");
        double expected = scalar("SELECT SUM(stock_quantity*cost_price) FROM products");
        onFx(() -> { rv[0] = new ReportsView().getRoot(); scene(rv[0]); return null; });
        PieChart pie = onFx(() -> pieOf(rv[0]));
        check("pie (data): a PieChart is drawn", pie != null);
        List<PieChart.Data> d = pie == null ? List.of() : new ArrayList<>(pie.getData());
        double sum = d.stream().mapToDouble(PieChart.Data::getPieValue).sum();
        check("pie (data): slices sum to the real stock value (SUM(stock*cost)) = " + expected, Math.abs(sum - expected) < 0.005);
        check("pie (data): at most 6 slices, including 'Other' for the tail", d.size() <= 6 && d.stream().anyMatch(x -> x.getName().equals("Other")));
        check("pie (data): the uncategorised product is counted, not dropped", d.stream().anyMatch(x -> x.getName().equals("Uncategorised")));
        check("pie (data): a zero-stock product adds no slice of its own", d.stream().noneMatch(x -> x.getName().equals("Cat1") && x.getPieValue() == 0));
        check("pie (data): no empty-state message while data exists", onFx(() -> texts(rv[0])).stream().noneMatch(t -> t.contains("No stock on hand yet")));

        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
