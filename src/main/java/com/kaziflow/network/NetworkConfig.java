package com.kaziflow.network;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * NetworkConfig — multi-computer mode settings (Phase 10D).
 *
 * Deliberately stored in a flat properties file (~/KaziFlowERP/network.properties)
 * rather than the settings table inside the SQLite database itself: in CLIENT
 * mode, there IS no meaningful local database to query yet — the whole point
 * of client mode is that DatabaseManager.getConnection() needs to know
 * "go connect to another PC" before it can do anything database-shaped at
 * all. Reading that decision from a DB row would be a chicken-and-egg
 * problem. A tiny local file sidesteps it entirely.
 */
public class NetworkConfig {

    public enum Mode { STANDALONE, SERVER, CLIENT, SHARED }

    private static final String CONFIG_DIR = com.kaziflow.utils.AppPaths.dataDir();
    private static final String CONFIG_PATH = CONFIG_DIR + "/network.properties";

    private static NetworkConfig instance;
    public static synchronized NetworkConfig getInstance() {
        if (instance == null) instance = new NetworkConfig();
        return instance;
    }

    private Mode mode = Mode.STANDALONE;
    private String host = "";
    private int port = 8765;
    private String sharedDbPath = "";

    private NetworkConfig() { load(); }

    private void load() {
        Path path = Path.of(CONFIG_PATH);
        if (!Files.exists(path)) return;
        try (FileInputStream in = new FileInputStream(CONFIG_PATH)) {
            Properties p = new Properties();
            p.load(in);
            try { mode = Mode.valueOf(p.getProperty("mode", "STANDALONE")); } catch (IllegalArgumentException e) { mode = Mode.STANDALONE; }
            host = p.getProperty("host", "");
            try { port = Integer.parseInt(p.getProperty("port", "8765")); } catch (NumberFormatException ignored) {}
            sharedDbPath = p.getProperty("sharedDbPath", "");
        } catch (IOException e) {
            System.err.println("[NetworkConfig] Could not read " + CONFIG_PATH + ": " + e.getMessage());
        }
    }

    public void save(Mode mode, String host, int port, String sharedDbPath) {
        this.mode = mode;
        this.host = host;
        this.port = port;
        this.sharedDbPath = sharedDbPath;
        try {
            Files.createDirectories(Path.of(CONFIG_DIR));
            try (FileOutputStream out = new FileOutputStream(CONFIG_PATH)) {
                Properties p = new Properties();
                p.setProperty("mode", mode.name());
                p.setProperty("host", host != null ? host : "");
                p.setProperty("port", String.valueOf(port));
                p.setProperty("sharedDbPath", sharedDbPath != null ? sharedDbPath : "");
                p.store(out, com.kaziflow.brand.Brand.productFullName()+" — multi-computer network configuration. Restart the app after changing this.");
            }
        } catch (IOException e) {
            System.err.println("[NetworkConfig] Could not write " + CONFIG_PATH + ": " + e.getMessage());
        }
    }

    public Mode getMode() { return mode; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getSharedDbPath() { return sharedDbPath; }
}
