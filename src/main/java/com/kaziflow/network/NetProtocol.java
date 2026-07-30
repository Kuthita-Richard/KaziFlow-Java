package com.kaziflow.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wire protocol for LAN server mode (Phase 10D).
 *
 * Design: a client-side PreparedStatement buffers all its setX(...) calls
 * locally and sends exactly ONE request when execute() is finally called —
 * the sql text plus the full parameter list together. This means the server
 * needs zero per-statement state; the only state it keeps per client
 * connection is the one dedicated real JDBC Connection for that session
 * (so setAutoCommit/commit/rollback work correctly for multi-statement
 * transactions like Sale.save(), which insert a sale + several sale_items
 * under one atomic commit).
 *
 * Query results are sent as parallel columns[]/rows[][] arrays rather than
 * a JSON object-per-row, so column order is unambiguous regardless of how
 * a JSON parser might order object keys.
 *
 * Serialization is hand-written (via MiniJson) rather than Gson: this
 * sandbox's network allowlist doesn't reach Maven Central, and Gson isn't
 * published as a standalone jar via GitHub Releases either. Each class
 * below converts itself to/from a plain Map<String,Object>/List<Object>
 * tree that MiniJson can write/parse directly.
 */
public class NetProtocol {

    public static final String OP_EXECUTE_QUERY = "EXECUTE_QUERY";     // PreparedStatement.executeQuery() or Statement.executeQuery(sql)
    public static final String OP_EXECUTE_UPDATE = "EXECUTE_UPDATE";   // PreparedStatement.executeUpdate() or Statement.executeUpdate(sql)
    public static final String OP_EXECUTE = "EXECUTE";                 // Statement.execute(sql) — DDL/PRAGMA, no result rows expected
    public static final String OP_SET_AUTOCOMMIT = "SET_AUTOCOMMIT";
    public static final String OP_COMMIT = "COMMIT";
    public static final String OP_ROLLBACK = "ROLLBACK";
    public static final String OP_CLOSE = "CLOSE";
    public static final String OP_PING = "PING"; // connection test from Settings → Network

    /** One bound parameter. t = "I"(int) "S"(string) "D"(double) "N"(null, v holds the java.sql.Types code) "O"(object/passthrough). */
    public static class Param {
        public String t;
        public Object v;
        public Param() {}
        public Param(String t, Object v) { this.t = t; this.v = v; }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("t", t);
            m.put("v", v);
            return m;
        }

        static Param fromMap(Object o) {
            if (o == null) return null;
            Map<?, ?> m = (Map<?, ?>) o;
            Param p = new Param();
            p.t = (String) m.get("t");
            p.v = m.get("v");
            return p;
        }
    }

    public static class Request {
        public String op;
        public String sql;
        public List<Param> params;
        public boolean returnGeneratedKeys;
        public Boolean autoCommit; // only set for OP_SET_AUTOCOMMIT

        public String toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("op", op);
            m.put("sql", sql);
            if (params != null) {
                List<Object> plist = new ArrayList<>();
                for (Param p : params) plist.add(p == null ? null : p.toMap());
                m.put("params", plist);
            }
            m.put("returnGeneratedKeys", returnGeneratedKeys);
            if (autoCommit != null) m.put("autoCommit", autoCommit);
            return MiniJson.write(m);
        }

        @SuppressWarnings("unchecked")
        public static Request fromJson(String json) {
            Map<String, Object> m = (Map<String, Object>) MiniJson.parse(json);
            Request req = new Request();
            req.op = (String) m.get("op");
            req.sql = (String) m.get("sql");
            Object paramsObj = m.get("params");
            if (paramsObj != null) {
                req.params = new ArrayList<>();
                for (Object o : (List<Object>) paramsObj) req.params.add(Param.fromMap(o));
            }
            Object rgk = m.get("returnGeneratedKeys");
            req.returnGeneratedKeys = rgk != null && (Boolean) rgk;
            Object ac = m.get("autoCommit");
            req.autoCommit = ac == null ? null : (Boolean) ac;
            return req;
        }
    }

    public static class Response {
        public boolean ok;
        public String error;
        public List<String> columns;
        public List<List<Object>> rows;
        public int updateCount;
        public Long generatedKey; // null if none requested/available

        public String toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ok", ok);
            if (error != null) m.put("error", error);
            if (columns != null) m.put("columns", new ArrayList<Object>(columns));
            if (rows != null) {
                List<Object> rowsOut = new ArrayList<>();
                for (List<Object> row : rows) rowsOut.add(new ArrayList<Object>(row));
                m.put("rows", rowsOut);
            }
            m.put("updateCount", updateCount);
            if (generatedKey != null) m.put("generatedKey", generatedKey.doubleValue());
            return MiniJson.write(m);
        }

        @SuppressWarnings("unchecked")
        public static Response fromJson(String json) {
            Map<String, Object> m = (Map<String, Object>) MiniJson.parse(json);
            Response resp = new Response();
            resp.ok = Boolean.TRUE.equals(m.get("ok"));
            resp.error = (String) m.get("error");
            Object colsObj = m.get("columns");
            if (colsObj != null) {
                resp.columns = new ArrayList<>();
                for (Object o : (List<Object>) colsObj) resp.columns.add((String) o);
            }
            Object rowsObj = m.get("rows");
            if (rowsObj != null) {
                resp.rows = new ArrayList<>();
                for (Object rowObj : (List<Object>) rowsObj) {
                    List<Object> row = new ArrayList<>((List<Object>) rowObj);
                    resp.rows.add(row);
                }
            }
            Object uc = m.get("updateCount");
            resp.updateCount = uc == null ? 0 : ((Number) uc).intValue();
            Object gk = m.get("generatedKey");
            resp.generatedKey = gk == null ? null : ((Number) gk).longValue();
            return resp;
        }
    }
}
