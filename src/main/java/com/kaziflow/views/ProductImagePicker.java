package com.kaziflow.views;

import com.kaziflow.dao.ProductImageDAO;
import com.kaziflow.utils.AsyncTask;
import com.kaziflow.utils.ImageProcessor;
import com.kaziflow.utils.ProductImages;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;

/**
 * ProductImagePicker — the "photo" control for the add/edit product dialog: choose a file (or drag one onto the
 * preview), see it immediately, remove it. Nothing is written until the dialog's Save: the picker just stages the
 * change, and {@link #applyTo(int)} commits it once the product has an id (new products don't until saved).
 *
 * <p>Picking runs {@link ImageProcessor} off the UI thread (a big camera photo takes a moment) and shows its
 * plain-language error if the file isn't usable, so a bad file is rejected at selection, not at Save.
 */
public final class ProductImagePicker {

    private static final String BTN = "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-pref-height: 32px; -fx-padding: 0 12; -fx-cursor: hand; -fx-font-size: 12px;";
    private static final double PREVIEW = 96;

    private final ProductImageDAO dao = new ProductImageDAO();
    private final HBox root = new HBox(14);
    private final StackPane preview = new StackPane();
    private final Label status = new Label();
    private final Button choose = new Button("Choose photo…");
    private final Button remove = new Button("Remove photo");

    private ImageProcessor.Processed pending;   // chosen but not yet saved
    private boolean removeRequested;            // user asked to delete the stored picture
    private boolean hasPicture;                 // is something currently shown?

    /** @param productId the product being edited, or null for a new one. */
    public ProductImagePicker(Integer productId) {
        Image current = productId == null ? null : ProductImages.full(productId);
        hasPicture = current != null;

        preview.setPrefSize(PREVIEW, PREVIEW);
        preview.setMinSize(PREVIEW, PREVIEW);
        preview.setMaxSize(PREVIEW, PREVIEW);
        preview.setId("picker-preview");
        showImage(current);

        choose.setStyle(BTN);
        choose.setId("picker-choose");
        choose.setOnAction(e -> chooseFile());
        remove.setStyle(BTN);
        remove.setId("picker-remove");
        remove.setOnAction(e -> {
            pending = null;
            removeRequested = hasPicture || removeRequested;
            hasPicture = false;
            showImage(null);
            status.setText("The photo will be removed when you save.");
        });
        Label hint = new Label("JPG, PNG, GIF or BMP. It is resized and compressed automatically.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        status.setWrapText(true);
        status.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        status.setId("picker-status");

        HBox buttons = new HBox(8, choose, remove);
        VBox side = new VBox(6, buttons, hint, status);
        side.setMaxWidth(300);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(0, 0, 4, 0));
        root.getChildren().addAll(preview, side);

        // drag & drop a picture straight onto the preview
        preview.setOnDragOver((DragEvent ev) -> {
            if (ev.getDragboard().hasFiles()) ev.acceptTransferModes(TransferMode.COPY);
            ev.consume();
        });
        preview.setOnDragDropped((DragEvent ev) -> {
            var files = ev.getDragboard().getFiles();
            if (files != null && !files.isEmpty()) load(files.get(0));
            ev.setDropCompleted(files != null && !files.isEmpty());
            ev.consume();
        });
        syncButtons();
    }

    public Node getNode() { return root; }

    /** True if saving should change the stored picture (a new one was chosen, or removal was requested). */
    public boolean hasChange() { return pending != null || removeRequested; }

    /**
     * Commits the staged change to the database for this product.
     * @return null on success (or nothing to do), otherwise a message to show the user.
     */
    public String applyTo(int productId) {
        if (pending != null) {
            if (!dao.save(productId, pending)) return "The photo could not be saved.";
        } else if (removeRequested) {
            dao.delete(productId);
        }
        ProductImages.invalidate(productId);
        pending = null;
        removeRequested = false;
        return null;
    }

    // ── internals ─────────────────────────────────────────────────────────

    private void chooseFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose a product photo");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pictures", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp"));
        File f = fc.showOpenDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (f != null) load(f);
    }

    /** Reads and processes a file in the background; shows the result or the reason it was refused. */
    public void load(File file) {
        status.setText("Processing…");
        choose.setDisable(true);
        AsyncTask.run(() -> {
            try {
                if (file.length() > ImageProcessor.MAX_FILE_BYTES) throw new ImageProcessor.ImageException("That picture is too large. Pick one under 20 MB.");
                return (Object) ImageProcessor.process(Files.readAllBytes(file.toPath()));
            } catch (ImageProcessor.ImageException e) {
                return (Object) e.getMessage();
            } catch (Exception e) {
                return (Object) ("Could not read the file: " + e.getMessage());
            }
        }, result -> {
            choose.setDisable(false);
            if (result instanceof ImageProcessor.Processed p) {
                pending = p;
                removeRequested = false;
                hasPicture = true;
                showImage(new Image(new ByteArrayInputStream(p.thumb())));
                status.setText("New photo ready (" + p.width() + " × " + p.height() + "). It is saved with the product.");
            } else {
                status.setText("⚠ " + result);
                status.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 11px;");
            }
            syncButtons();
        }, err -> {
            choose.setDisable(false);
            status.setText("⚠ Could not process the picture: " + err);
            status.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 11px;");
        });
    }

    private void showImage(Image img) {
        preview.getChildren().clear();
        if (img == null) {
            preview.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 12; -fx-border-color: #cbd5e1; -fx-border-style: dashed; -fx-border-radius: 12;");
            Label l = new Label("No photo\n(drop one here)");
            l.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-text-alignment: center;");
            preview.getChildren().add(l);
        } else {
            preview.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12;");
            ImageView v = new ImageView(img);
            v.setFitWidth(PREVIEW - 4);
            v.setFitHeight(PREVIEW - 4);
            v.setPreserveRatio(true);
            v.setSmooth(true);
            preview.getChildren().add(v);
        }
        status.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        syncButtons();
    }

    private void syncButtons() { remove.setDisable(!hasPicture); }
}
