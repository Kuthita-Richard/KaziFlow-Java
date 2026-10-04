package com.kaziflow.utils;

/**
 * AppPaths — where this installation keeps its data. ONE definition, used everywhere.
 *
 * <p><b>This deliberately does NOT follow the brand.</b> The folder and database names are part of every
 * existing installation (the database, backups, encryption salt, network settings, licence). Renaming the
 * product, the company or the logo must never move them — if it did, every customer would open the app to an
 * empty database. Change these only with a proper data-migration step.
 *
 * <p>Pure JDK (no JavaFX), safe to use from the database and service layers. Paths are computed on each call
 * (not cached) so tests that set {@code -Duser.home} to a temp folder keep working.
 */
public final class AppPaths {

    /** Folder under the user's home. Historical name — part of existing installs, not a brand string. */
    public static final String DATA_DIR_NAME = "KaziFlowERP";
    /** The SQLite file inside the data folder. Historical name — not a brand string. */
    public static final String DB_FILE_NAME = "kaziflow.db";

    private AppPaths() {}

    public static String dataDir() { return System.getProperty("user.home") + "/" + DATA_DIR_NAME; }

    public static String dbFile() { return dataDir() + "/" + DB_FILE_NAME; }
}
