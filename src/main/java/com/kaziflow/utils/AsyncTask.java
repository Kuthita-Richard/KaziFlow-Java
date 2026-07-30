package com.kaziflow.utils;

import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Utility for running heavy work off the JavaFX Application Thread.
 *
 * <pre>
 * AsyncTask.run(
 *     () -> productDAO.findAll(),      // runs on background thread
 *     products -> tableData.setAll(products),  // runs on FX thread with result
 *     err -> showAlert("Load failed: " + err)  // runs on FX thread on failure
 * );
 * </pre>
 */
public class AsyncTask {

    private static final ExecutorService POOL =
        Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "kaziflow-worker");
            t.setDaemon(true);
            return t;
        });

    /**
     * Run {@code work} on a background thread, then call {@code onSuccess} on the FX thread.
     *
     * @param work      Supplier that does the heavy lifting (DB queries, etc.)
     * @param onSuccess Consumer called on the FX thread with the result
     * @param onError   Consumer called on the FX thread if work throws
     */
    public static <T> void run(Supplier<T> work, Consumer<T> onSuccess, Consumer<String> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return work.get();
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> onSuccess.accept(task.getValue())));
        task.setOnFailed(e -> Platform.runLater(() -> {
            Throwable ex = task.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            if (onError != null) onError.accept(msg);
        }));
        POOL.submit(task);
    }

    // (Removed unused Runnable,Runnable,Consumer<String> overload — it caused
    //  ambiguous-method-reference errors with the Supplier<T> overload above,
    //  and no call site in the codebase actually needs the void-Runnable form.)

    // ─── Loading overlay factory ──────────────────────────────────────────────

    /**
     * Build a full-pane loading overlay. Wrap your content pane in a StackPane
     * and call showLoading()/hideLoading() to toggle it.
     *
     * <pre>
     * StackPane wrapper = new StackPane(content);
     * StackPane overlay = AsyncTask.buildLoadingOverlay();
     * wrapper.getChildren().add(overlay);
     * overlay.setVisible(false);
     *
     * // On load:
     * overlay.setVisible(true);
     * AsyncTask.run(() -> dao.findAll(), data -> {
     *     table.setItems(FXCollections.observableArrayList(data));
     *     overlay.setVisible(false);
     * }, null);
     * </pre>
     */
    public static StackPane buildLoadingOverlay() {
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(248,250,252,0.85);");
        overlay.setMouseTransparent(false);

        VBox content = new VBox(12);
        content.setAlignment(Pos.CENTER);

        // Spinner using a rotating label (Unicode circle arc)
        Label spinner = new Label("◌");
        spinner.setStyle("-fx-font-size: 36px; -fx-text-fill: #2563eb;");

        RotateTransition rotate = new RotateTransition(Duration.millis(800), spinner);
        rotate.setByAngle(360);
        rotate.setCycleCount(RotateTransition.INDEFINITE);
        rotate.play();

        Label loadingText = new Label("Loading...");
        loadingText.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");

        content.getChildren().addAll(spinner, loadingText);
        overlay.getChildren().add(content);
        overlay.setVisible(false);
        return overlay;
    }

    /** Create a small inline loading label for buttons */
    public static Label buildInlineSpinner() {
        Label lbl = new Label("⟳ Loading...");
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #2563eb;");
        RotateTransition r = new RotateTransition(Duration.millis(600), lbl);
        r.setByAngle(360);
        r.setCycleCount(RotateTransition.INDEFINITE);
        r.play();
        return lbl;
    }

    /** Shut down the thread pool gracefully (call on app exit). */
    public static void shutdown() {
        POOL.shutdown();
    }
}
