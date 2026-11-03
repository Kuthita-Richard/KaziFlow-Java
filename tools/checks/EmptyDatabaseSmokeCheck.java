// New databases are empty (no demo company). Every screen must still build on an empty database without throwing —
// no screen may assume a supplier, product, employee or transaction exists.
import com.kaziflow.database.DatabaseManager;
import com.kaziflow.models.User;
import com.kaziflow.modules.ModuleRegistry;
import com.kaziflow.utils.SessionManager;
import javafx.application.Platform;
import javafx.scene.Node;

import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

public class EmptyDatabaseSmokeCheck {
    static int pass, fail, skipped;
    public static void main(String[] a) throws Exception {
        DatabaseManager.getInstance().initialize();
        ModuleRegistry.getInstance().init();
        User u = new User(); u.setId(1); u.setName("Admin"); SessionManager.getInstance().login(u);
        Platform.startup(() -> {});
        File dir = new File(a.length > 0 ? a[0] : "/tmp/kaziflow-verify/scratch/out/com/kaziflow/views");
        String[] names = dir.list((d, n) -> n.endsWith("View.class") && !n.contains("$"));
        Arrays.sort(names);
        for (String f : names) {
            String cn = "com.kaziflow.views." + f.replace(".class", "");
            Class<?> k = Class.forName(cn);
            Constructor<?> ctor = null;
            try { ctor = k.getConstructor(); } catch (NoSuchMethodException e) { /* needs arguments */ }
            Method root = null;
            for (String m : new String[]{"getRoot", "getView"}) { try { root = k.getMethod(m); break; } catch (NoSuchMethodException e) { } }
            if (ctor == null || root == null || Modifier.isAbstract(k.getModifiers())) { skipped++; System.out.println("SKIP " + k.getSimpleName() + " (needs arguments or has no getRoot)"); continue; }
            final Constructor<?> c = ctor; final Method r = root;
            CompletableFuture<Object> fut = new CompletableFuture<>();
            Platform.runLater(() -> { try { Object v = c.newInstance(); fut.complete(r.invoke(v)); } catch (Throwable t) { fut.completeExceptionally(t); } });
            try {
                Object node = fut.get(40, TimeUnit.SECONDS);
                boolean ok = node instanceof Node;
                System.out.println((ok ? "PASS " : "FAIL ") + k.getSimpleName() + " builds on an empty database");
                if (ok) pass++; else fail++;
            } catch (ExecutionException e) {
                Throwable t = e.getCause() instanceof InvocationTargetException ? e.getCause().getCause() : e.getCause();
                System.out.println("FAIL " + k.getSimpleName() + " throws on an empty database: " + t);
                if (t != null) { StackTraceElement[] st = t.getStackTrace(); for (int i = 0; i < Math.min(4, st.length); i++) System.out.println("       at " + st[i]); }
                fail++;
            } catch (TimeoutException e) { System.out.println("FAIL " + k.getSimpleName() + " did not finish"); fail++; }
        }
        System.out.println(pass + " passed, " + fail + " failed (" + skipped + " skipped: need arguments)");
        System.exit(fail == 0 ? 0 : 1);
    }
}
