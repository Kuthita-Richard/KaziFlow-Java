package com.kaziflow.services;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Backup and restore service for the KaziFlow SQLite database.
 *
 * Features:
 *  - Manual backup to user-chosen directory
 *  - Scheduled auto-backup (daily at app startup)
 *  - Restore from .db backup file
 *  - Backup file naming: kaziflow_backup_YYYYMMDD_HHmmss.db
 */
public class BackupService {

    private static BackupService instance;
    private static final String DB_DIR = System.getProperty("user.home") + "/KaziFlowERP";
    private static final String DB_FILE = DB_DIR + "/kaziflow.db";
    private static final String BACKUP_DIR = DB_DIR + "/backups";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private BackupService() {
        // Create backup directory if it doesn't exist
        try { Files.createDirectories(Paths.get(BACKUP_DIR)); } catch (IOException ignored) {}
    }

    public static BackupService getInstance() {
        if (instance == null) instance = new BackupService();
        return instance;
    }

    // ─── Manual Backup ──────────────────────────────────────────────────────

    /** Opens a directory chooser and backs up the DB to the chosen folder. */
    public void backupToDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Backup Destination");
        chooser.setInitialDirectory(new File(BACKUP_DIR));

        File dest = chooser.showDialog(null);
        if (dest != null) {
            String filename = "kaziflow_backup_" + LocalDateTime.now().format(FMT) + ".db";
            File target = new File(dest, filename);
            try {
                Files.copy(Paths.get(DB_FILE), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                showSuccess("Backup saved to:\n" + target.getAbsolutePath());
            } catch (IOException e) {
                showError("Backup failed: " + e.getMessage());
            }
        }
    }

    /** Creates a backup in the default backup directory. Returns the backup file path or null. */
    public String autoBackup() {
        try {
            Files.createDirectories(Paths.get(BACKUP_DIR));
            String filename = "kaziflow_backup_" + LocalDateTime.now().format(FMT) + ".db";
            Path target = Paths.get(BACKUP_DIR, filename);
            Files.copy(Paths.get(DB_FILE), target, StandardCopyOption.REPLACE_EXISTING);
            pruneOldBackups(30); // keep last 30 backups
            System.out.println("[Backup] Auto-backup created: " + target);
            return target.toString();
        } catch (IOException e) {
            System.err.println("[Backup] Auto-backup failed: " + e.getMessage());
            return null;
        }
    }

    // ─── Restore ────────────────────────────────────────────────────────────

    /** Opens a file chooser to restore from a .db backup file. */
    public void restoreFromFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Backup File to Restore");
        chooser.setInitialDirectory(new File(BACKUP_DIR));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Database Files", "*.db"));

        File backupFile = chooser.showOpenDialog(null);
        if (backupFile != null) {
            // Confirm with user before overwriting
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Restore from:\n" + backupFile.getName() + "\n\nThis will overwrite all current data. Continue?",
                ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Confirm Restore");
            confirm.setHeaderText("⚠ This action cannot be undone.");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    try {
                        // Backup current DB before restoring (safety net)
                        autoBackup();
                        Files.copy(backupFile.toPath(), Paths.get(DB_FILE), StandardCopyOption.REPLACE_EXISTING);
                        showSuccess("Restore successful!\nPlease restart the application for changes to take effect.");
                    } catch (IOException e) {
                        showError("Restore failed: " + e.getMessage());
                    }
                }
            });
        }
    }

    // ─── Scheduled Auto-Backup ──────────────────────────────────────────────

    /** Schedules a daily backup at first run. Call on app startup. */
    public void scheduleDailyBackup() {
        Timer timer = new Timer(true); // daemon timer
        // First backup after 30 seconds, then every 24 hours
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                autoBackup();
            }
        }, 30_000L, 24L * 60 * 60 * 1000);
        System.out.println("[Backup] Daily auto-backup scheduled.");
    }

    // ─── List Backups ────────────────────────────────────────────────────────

    public File[] listBackups() {
        File dir = new File(BACKUP_DIR);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".db"));
        if (files == null) return new File[0];
        // Sort newest first
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    public String getLastBackupTime() {
        File[] backups = listBackups();
        if (backups.length == 0) return "Never";
        long lastMod = backups[0].lastModified();
        LocalDateTime time = LocalDateTime.ofEpochSecond(lastMod / 1000, 0, java.time.ZoneOffset.UTC);
        return time.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"));
    }

    public long getDatabaseSizeKB() {
        File db = new File(DB_FILE);
        return db.exists() ? db.length() / 1024 : 0;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Deletes old backups keeping only the most recent {@code keepCount} files. */
    private void pruneOldBackups(int keepCount) {
        File[] backups = listBackups();
        for (int i = keepCount; i < backups.length; i++) {
            backups[i].delete();
            System.out.println("[Backup] Pruned old backup: " + backups[i].getName());
        }
    }

    private void showSuccess(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
            a.setTitle("Backup"); a.setHeaderText(null); a.show();
        });
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
            a.setTitle("Backup Error"); a.setHeaderText(null); a.show();
        });
    }
}
