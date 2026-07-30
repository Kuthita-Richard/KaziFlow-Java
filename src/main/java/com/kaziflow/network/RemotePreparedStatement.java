package com.kaziflow.network;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * RemotePreparedStatement — buffers every setX(index, value) call locally
 * (no network round trip yet), then sends exactly ONE request to the server
 * when executeQuery()/executeUpdate() is finally called: the SQL text plus
 * every bound parameter together. This is what keeps the server completely
 * stateless per-statement — it only ever sees "run this SQL with these
 * params," never a multi-step prepare/bind/execute conversation.
 */
public class RemotePreparedStatement implements PreparedStatement {

    private final RemoteConnection conn;
    private final String sql;
    private final boolean returnGeneratedKeys;
    private final List<NetProtocol.Param> params = new ArrayList<>();
    private Long lastGeneratedKey;

    public RemotePreparedStatement(RemoteConnection conn, String sql, boolean returnGeneratedKeys) {
        this.conn = conn;
        this.sql = sql;
        this.returnGeneratedKeys = returnGeneratedKeys;
    }

    private void put(int index, String type, Object value) {
        while (params.size() < index) params.add(null);
        params.set(index - 1, new NetProtocol.Param(type, value));
    }

    @Override public void setInt(int i, int x) { put(i, "I", x); }
    @Override public void setString(int i, String x) { put(i, "S", x); }
    @Override public void setDouble(int i, double x) { put(i, "D", x); }
    @Override public void setNull(int i, int sqlType) { put(i, "N", sqlType); }
    @Override public void setNull(int i, int sqlType, String typeName) { put(i, "N", sqlType); }
    @Override public void setObject(int i, Object x) { put(i, x == null ? "N" : "O", x == null ? Types.NULL : x); }
    @Override public void setBoolean(int i, boolean x) { put(i, "I", x ? 1 : 0); }
    @Override public void setLong(int i, long x) { put(i, "O", x); }
    @Override public void setFloat(int i, float x) { put(i, "D", (double) x); }

    @Override
    public ResultSet executeQuery() throws SQLException {
        NetProtocol.Request req = buildRequest(NetProtocol.OP_EXECUTE_QUERY);
        NetProtocol.Response resp = conn.send(req);
        return new RemoteResultSet(resp.columns, resp.rows);
    }

    @Override
    public int executeUpdate() throws SQLException {
        NetProtocol.Request req = buildRequest(NetProtocol.OP_EXECUTE_UPDATE);
        NetProtocol.Response resp = conn.send(req);
        lastGeneratedKey = resp.generatedKey;
        return resp.updateCount;
    }

    @Override
    public boolean execute() throws SQLException {
        executeUpdate();
        return false;
    }

    private NetProtocol.Request buildRequest(String op) {
        NetProtocol.Request req = new NetProtocol.Request();
        req.op = op;
        req.sql = sql;
        req.params = params;
        req.returnGeneratedKeys = returnGeneratedKeys;
        return req;
    }

    @Override
    public ResultSet getGeneratedKeys() {
        if (lastGeneratedKey == null) return new RemoteResultSet(List.of("id"), List.of());
        return new RemoteResultSet(List.of("id"), List.of(List.of(lastGeneratedKey)));
    }

    @Override public void close() {}
    @Override public java.sql.Connection getConnection() { return conn; }

    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException("RemotePreparedStatement." + method + "() is not implemented — not used by this codebase's DAOs.");
    }

    // ── Statement-inherited methods (executeQuery(sql) etc. with an explicit SQL arg are never called on a PreparedStatement in this codebase) ──
    @Override public ResultSet executeQuery(String sql) { throw unsupported("executeQuery(String)"); }
    @Override public int executeUpdate(String sql) { throw unsupported("executeUpdate(String)"); }
    @Override public boolean execute(String sql) { throw unsupported("execute(String)"); }
    @Override public int getMaxFieldSize() { return 0; }
    @Override public void setMaxFieldSize(int max) { }
    @Override public int getMaxRows() { return 0; }
    @Override public void setMaxRows(int max) { }
    @Override public void setEscapeProcessing(boolean enable) { }
    @Override public int getQueryTimeout() { return 0; }
    @Override public void setQueryTimeout(int seconds) { }
    @Override public void cancel() { throw unsupported("cancel"); }
    @Override public SQLWarning getWarnings() { return null; }
    @Override public void clearWarnings() { }
    @Override public void setCursorName(String name) { throw unsupported("setCursorName"); }
    @Override public ResultSet getResultSet() { throw unsupported("getResultSet"); }
    @Override public int getUpdateCount() { throw unsupported("getUpdateCount"); }
    @Override public boolean getMoreResults() { return false; }
    @Override public void setFetchDirection(int direction) { }
    @Override public int getFetchDirection() { return ResultSet.FETCH_FORWARD; }
    @Override public void setFetchSize(int rows) { }
    @Override public int getFetchSize() { return 0; }
    @Override public int getResultSetConcurrency() { return ResultSet.CONCUR_READ_ONLY; }
    @Override public int getResultSetType() { return ResultSet.TYPE_FORWARD_ONLY; }
    @Override public void addBatch(String sql) { throw unsupported("addBatch"); }
    @Override public void clearBatch() { throw unsupported("clearBatch"); }
    @Override public int[] executeBatch() { throw unsupported("executeBatch"); }
    @Override public boolean getMoreResults(int current) { return false; }
    @Override public int executeUpdate(String sql, int autoGeneratedKeys) { throw unsupported("executeUpdate"); }
    @Override public int executeUpdate(String sql, int[] columnIndexes) { throw unsupported("executeUpdate"); }
    @Override public int executeUpdate(String sql, String[] columnNames) { throw unsupported("executeUpdate"); }
    @Override public boolean execute(String sql, int autoGeneratedKeys) { throw unsupported("execute"); }
    @Override public boolean execute(String sql, int[] columnIndexes) { throw unsupported("execute"); }
    @Override public boolean execute(String sql, String[] columnNames) { throw unsupported("execute"); }
    @Override public int getResultSetHoldability() { throw unsupported("getResultSetHoldability"); }
    @Override public boolean isClosed() { return false; }
    @Override public void setPoolable(boolean poolable) { }
    @Override public boolean isPoolable() { return false; }
    @Override public void closeOnCompletion() { }
    @Override public boolean isCloseOnCompletion() { return false; }
    @Override public <T> T unwrap(Class<T> iface) { throw unsupported("unwrap"); }
    @Override public boolean isWrapperFor(Class<?> iface) { return false; }

    // ── PreparedStatement setters not used by this codebase's DAOs ──
    @Override public void setByte(int i, byte x) { throw unsupported("setByte"); }
    @Override public void setShort(int i, short x) { throw unsupported("setShort"); }
    @Override public void setBigDecimal(int i, BigDecimal x) { throw unsupported("setBigDecimal"); }
    @Override public void setBytes(int i, byte[] x) { throw unsupported("setBytes"); }
    @Override public void setDate(int i, Date x) { throw unsupported("setDate"); }
    @Override public void setDate(int i, Date x, Calendar cal) { throw unsupported("setDate"); }
    @Override public void setTime(int i, Time x) { throw unsupported("setTime"); }
    @Override public void setTime(int i, Time x, Calendar cal) { throw unsupported("setTime"); }
    @Override public void setTimestamp(int i, Timestamp x) { throw unsupported("setTimestamp"); }
    @Override public void setTimestamp(int i, Timestamp x, Calendar cal) { throw unsupported("setTimestamp"); }
    @Override public void setAsciiStream(int i, InputStream x, int len) { throw unsupported("setAsciiStream"); }
    @Override public void setAsciiStream(int i, InputStream x, long len) { throw unsupported("setAsciiStream"); }
    @Override public void setAsciiStream(int i, InputStream x) { throw unsupported("setAsciiStream"); }
    @Override @Deprecated public void setUnicodeStream(int i, InputStream x, int len) { throw unsupported("setUnicodeStream"); }
    @Override public void setBinaryStream(int i, InputStream x, int len) { throw unsupported("setBinaryStream"); }
    @Override public void setBinaryStream(int i, InputStream x, long len) { throw unsupported("setBinaryStream"); }
    @Override public void setBinaryStream(int i, InputStream x) { throw unsupported("setBinaryStream"); }
    @Override public void clearParameters() { params.clear(); }
    @Override public void setObject(int i, Object x, int targetSqlType) { setObject(i, x); }
    @Override public void setObject(int i, Object x, int targetSqlType, int scaleOrLength) { setObject(i, x); }
    @Override public void addBatch() { throw unsupported("addBatch"); }
    @Override public void setCharacterStream(int i, Reader reader, int length) { throw unsupported("setCharacterStream"); }
    @Override public void setCharacterStream(int i, Reader reader, long length) { throw unsupported("setCharacterStream"); }
    @Override public void setCharacterStream(int i, Reader reader) { throw unsupported("setCharacterStream"); }
    @Override public void setRef(int i, Ref x) { throw unsupported("setRef"); }
    @Override public void setBlob(int i, Blob x) { throw unsupported("setBlob"); }
    @Override public void setBlob(int i, InputStream inputStream, long length) { throw unsupported("setBlob"); }
    @Override public void setBlob(int i, InputStream inputStream) { throw unsupported("setBlob"); }
    @Override public void setClob(int i, Clob x) { throw unsupported("setClob"); }
    @Override public void setClob(int i, Reader reader, long length) { throw unsupported("setClob"); }
    @Override public void setClob(int i, Reader reader) { throw unsupported("setClob"); }
    @Override public void setArray(int i, Array x) { throw unsupported("setArray"); }
    @Override public ResultSetMetaData getMetaData() { throw unsupported("getMetaData"); }
    @Override public void setURL(int i, URL x) { throw unsupported("setURL"); }
    @Override public ParameterMetaData getParameterMetaData() { throw unsupported("getParameterMetaData"); }
    @Override public void setRowId(int i, RowId x) { throw unsupported("setRowId"); }
    @Override public void setNString(int i, String value) { throw unsupported("setNString"); }
    @Override public void setNCharacterStream(int i, Reader value, long length) { throw unsupported("setNCharacterStream"); }
    @Override public void setNCharacterStream(int i, Reader value) { throw unsupported("setNCharacterStream"); }
    @Override public void setNClob(int i, NClob value) { throw unsupported("setNClob"); }
    @Override public void setNClob(int i, Reader reader, long length) { throw unsupported("setNClob"); }
    @Override public void setNClob(int i, Reader reader) { throw unsupported("setNClob"); }
    @Override public void setSQLXML(int i, SQLXML xmlObject) { throw unsupported("setSQLXML"); }
}
