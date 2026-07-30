package com.kaziflow.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * NetworkServer — the "LAN server mode" host side (Phase 10D).
 *
 * Runs on whichever PC is designated the host. Each accepted client
 * connection gets its own dedicated real JDBC Connection to the local
 * SQLite file (via DatabaseManager), held for the lifetime of that socket.
 * This is what preserves transaction correctness: when a client's DAO calls
 * setAutoCommit(false) ... commit(), those calls apply to that same
 * dedicated connection, exactly as they would locally.
 *
 * One line-delimited JSON message per request/response (Gson), over a plain
 * TCP socket — deliberately not HTTP, since every message here is a simple
 * one-shot RPC and a raw socket avoids pulling in an HTTP server dependency
 * or dealing with request/response framing overhead for what's a LAN-only,
 * trusted-network protocol.
 */
public class NetworkServer {

    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public NetworkServer(int port) { this.port = port; }

    public void start() {
        if (running) return;
        running = true;
        Thread acceptThread = new Thread(this::acceptLoop, "kaziflow-network-server");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }

    public boolean isRunning() { return running; }

    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[NetworkServer] Listening on port " + port);
            while (running) {
                Socket client = serverSocket.accept();
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "kaziflow-client-" + client.getRemoteSocketAddress());
                    t.setDaemon(true);
                    return t;
                }).submit(() -> handleClient(client));
            }
        } catch (IOException e) {
            if (running) System.err.println("[NetworkServer] Accept loop error: " + e.getMessage());
        }
    }

    private void handleClient(Socket socket) {
        // One dedicated real connection per client session — this is what makes
        // setAutoCommit/commit/rollback behave correctly across multiple requests.
        try (socket;
             Connection conn = com.kaziflow.database.DatabaseManager.getInstance().getLocalConnection();
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                NetProtocol.Request req = NetProtocol.Request.fromJson(line);
                NetProtocol.Response resp = handle(conn, req);
                out.println(resp.toJson());
                out.flush();
                if (NetProtocol.OP_CLOSE.equals(req.op)) break;
            }
        } catch (Exception e) {
            System.err.println("[NetworkServer] Client session ended: " + e.getMessage());
        }
    }

    private NetProtocol.Response handle(Connection conn, NetProtocol.Request req) {
        NetProtocol.Response resp = new NetProtocol.Response();
        try {
            switch (req.op) {
                case NetProtocol.OP_PING -> resp.ok = true;

                case NetProtocol.OP_SET_AUTOCOMMIT -> {
                    conn.setAutoCommit(req.autoCommit != null && req.autoCommit);
                    resp.ok = true;
                }
                case NetProtocol.OP_COMMIT -> { conn.commit(); resp.ok = true; }
                case NetProtocol.OP_ROLLBACK -> { conn.rollback(); resp.ok = true; }
                case NetProtocol.OP_CLOSE -> resp.ok = true;

                case NetProtocol.OP_EXECUTE_QUERY -> {
                    try (PreparedStatement ps = prepare(conn, req)) {
                        ResultSet rs = ps.executeQuery();
                        fillRows(rs, resp);
                        resp.ok = true;
                    }
                }
                case NetProtocol.OP_EXECUTE_UPDATE -> {
                    try (PreparedStatement ps = req.returnGeneratedKeys
                            ? conn.prepareStatement(req.sql, Statement.RETURN_GENERATED_KEYS)
                            : conn.prepareStatement(req.sql)) {
                        bindParams(ps, req.params);
                        resp.updateCount = ps.executeUpdate();
                        if (req.returnGeneratedKeys) {
                            ResultSet keys = ps.getGeneratedKeys();
                            if (keys.next()) resp.generatedKey = keys.getLong(1);
                        }
                        resp.ok = true;
                    }
                }
                case NetProtocol.OP_EXECUTE -> {
                    try (Statement s = conn.createStatement()) {
                        s.execute(req.sql);
                        resp.ok = true;
                    }
                }
                default -> { resp.ok = false; resp.error = "Unknown op: " + req.op; }
            }
        } catch (Exception e) {
            resp.ok = false;
            resp.error = e.getMessage();
        }
        return resp;
    }

    private PreparedStatement prepare(Connection conn, NetProtocol.Request req) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(req.sql);
        bindParams(ps, req.params);
        return ps;
    }

    private void bindParams(PreparedStatement ps, List<NetProtocol.Param> params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.size(); i++) {
            NetProtocol.Param p = params.get(i);
            int idx = i + 1;
            switch (p.t) {
                case "I" -> ps.setInt(idx, ((Number) p.v).intValue());
                case "S" -> ps.setString(idx, (String) p.v);
                case "D" -> ps.setDouble(idx, ((Number) p.v).doubleValue());
                case "N" -> ps.setNull(idx, ((Number) p.v).intValue());
                default -> ps.setObject(idx, p.v);
            }
        }
    }

    private void fillRows(ResultSet rs, NetProtocol.Response resp) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) columns.add(meta.getColumnLabel(i));
        resp.columns = columns;

        List<List<Object>> rows = new ArrayList<>();
        while (rs.next()) {
            List<Object> row = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) row.add(rs.getObject(i));
            rows.add(row);
        }
        resp.rows = rows;
    }
}
