package com.kaziflow.network;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * RemoteResultSet — wraps the columns[]/rows[][] a NetworkServer response
 * returned, implementing only the subset of java.sql.ResultSet that this
 * codebase's DAOs actually call (verified by grepping every DAO before
 * writing this): next(), getInt/getString/getDouble/getObject by column
 * name, plus their by-index forms since 3 call sites use those.
 *
 * Every other ResultSet method throws UnsupportedOperationException with a
 * clear message — a loud failure here is much better than a silent wrong
 * result if some DAO is later written using a method this class doesn't cover.
 */
public class RemoteResultSet implements ResultSet {

    private final List<String> columns;
    private final List<List<Object>> rows;
    private int cursor = -1;
    private boolean wasNullFlag = false;

    public RemoteResultSet(List<String> columns, List<List<Object>> rows) {
        this.columns = columns != null ? columns : List.of();
        this.rows = rows != null ? rows : List.of();
    }

    private int colIndex(String label) {
        int idx = columns.indexOf(label);
        if (idx < 0) throw new RuntimeException("Unknown column in remote result set: " + label);
        return idx;
    }

    private Object raw(int oneBasedIdx) {
        Object v = rows.get(cursor).get(oneBasedIdx - 1);
        wasNullFlag = (v == null);
        return v;
    }

    @Override public boolean next() { cursor++; return cursor < rows.size(); }
    @Override public void close() {}

    @Override public boolean wasNull() { return wasNullFlag; }

    @Override public String getString(String columnLabel) { Object v = raw(colIndex(columnLabel) + 1); return v == null ? null : String.valueOf(v); }
    @Override public String getString(int columnIndex) { Object v = raw(columnIndex); return v == null ? null : String.valueOf(v); }

    @Override public int getInt(String columnLabel) { Object v = raw(colIndex(columnLabel) + 1); return v == null ? 0 : ((Number) v).intValue(); }
    @Override public int getInt(int columnIndex) { Object v = raw(columnIndex); return v == null ? 0 : ((Number) v).intValue(); }

    @Override public double getDouble(String columnLabel) { Object v = raw(colIndex(columnLabel) + 1); return v == null ? 0 : ((Number) v).doubleValue(); }
    @Override public double getDouble(int columnIndex) { Object v = raw(columnIndex); return v == null ? 0 : ((Number) v).doubleValue(); }

    @Override public Object getObject(String columnLabel) { return raw(colIndex(columnLabel) + 1); }
    @Override public Object getObject(int columnIndex) { return raw(columnIndex); }

    @Override public long getLong(String columnLabel) { Object v = raw(colIndex(columnLabel) + 1); return v == null ? 0 : ((Number) v).longValue(); }
    @Override public long getLong(int columnIndex) { Object v = raw(columnIndex); return v == null ? 0 : ((Number) v).longValue(); }

    @Override public boolean getBoolean(String columnLabel) { Object v = raw(colIndex(columnLabel) + 1); return v != null && ((Number) v).intValue() != 0; }
    @Override public boolean getBoolean(int columnIndex) { Object v = raw(columnIndex); return v != null && ((Number) v).intValue() != 0; }

    @Override public ResultSetMetaData getMetaData() { return new RemoteResultSetMetaData(columns); }

    // ── Everything below is deliberately unsupported — not used by any DAO in this codebase ──

    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException("RemoteResultSet." + method + "() is not implemented — "
            + "this codebase's DAOs don't use it. If a new DAO needs it, add support in RemoteResultSet.");
    }

    @Override public boolean isBeforeFirst() { throw unsupported("isBeforeFirst"); }
    @Override public boolean isAfterLast() { throw unsupported("isAfterLast"); }
    @Override public boolean isFirst() { throw unsupported("isFirst"); }
    @Override public boolean isLast() { throw unsupported("isLast"); }
    @Override public void beforeFirst() { throw unsupported("beforeFirst"); }
    @Override public void afterLast() { throw unsupported("afterLast"); }
    @Override public boolean first() { throw unsupported("first"); }
    @Override public boolean last() { throw unsupported("last"); }
    @Override public int getRow() { throw unsupported("getRow"); }
    @Override public boolean absolute(int row) { throw unsupported("absolute"); }
    @Override public boolean relative(int rows) { throw unsupported("relative"); }
    @Override public boolean previous() { throw unsupported("previous"); }
    @Override public void setFetchDirection(int direction) { throw unsupported("setFetchDirection"); }
    @Override public int getFetchDirection() { throw unsupported("getFetchDirection"); }
    @Override public void setFetchSize(int rows) { throw unsupported("setFetchSize"); }
    @Override public int getFetchSize() { throw unsupported("getFetchSize"); }
    @Override public int getType() { throw unsupported("getType"); }
    @Override public int getConcurrency() { throw unsupported("getConcurrency"); }
    @Override public boolean rowUpdated() { throw unsupported("rowUpdated"); }
    @Override public boolean rowInserted() { throw unsupported("rowInserted"); }
    @Override public boolean rowDeleted() { throw unsupported("rowDeleted"); }
    @Override public java.sql.SQLWarning getWarnings() { return null; }
    @Override public void clearWarnings() {}
    @Override public String getCursorName() { throw unsupported("getCursorName"); }
    @Override public byte getByte(String s) { throw unsupported("getByte"); }
    @Override public byte getByte(int i) { throw unsupported("getByte"); }
    @Override public short getShort(String s) { throw unsupported("getShort"); }
    @Override public short getShort(int i) { throw unsupported("getShort"); }
    @Override public float getFloat(String s) { Object v = raw(colIndex(s) + 1); return v == null ? 0 : ((Number) v).floatValue(); }
    @Override public float getFloat(int i) { Object v = raw(i); return v == null ? 0 : ((Number) v).floatValue(); }
    @Override public byte[] getBytes(String s) { throw unsupported("getBytes"); }
    @Override public byte[] getBytes(int i) { throw unsupported("getBytes"); }
    @Override public java.sql.Date getDate(String s) { throw unsupported("getDate"); }
    @Override public java.sql.Date getDate(int i) { throw unsupported("getDate"); }
    @Override public java.sql.Date getDate(String s, java.util.Calendar cal) { throw unsupported("getDate"); }
    @Override public java.sql.Date getDate(int i, java.util.Calendar cal) { throw unsupported("getDate"); }
    @Override public java.sql.Time getTime(String s) { throw unsupported("getTime"); }
    @Override public java.sql.Time getTime(int i) { throw unsupported("getTime"); }
    @Override public java.sql.Time getTime(String s, java.util.Calendar cal) { throw unsupported("getTime"); }
    @Override public java.sql.Time getTime(int i, java.util.Calendar cal) { throw unsupported("getTime"); }
    @Override public java.sql.Timestamp getTimestamp(String s) { throw unsupported("getTimestamp"); }
    @Override public java.sql.Timestamp getTimestamp(int i) { throw unsupported("getTimestamp"); }
    @Override public java.sql.Timestamp getTimestamp(String s, java.util.Calendar cal) { throw unsupported("getTimestamp"); }
    @Override public java.sql.Timestamp getTimestamp(int i, java.util.Calendar cal) { throw unsupported("getTimestamp"); }
    @Override public java.io.InputStream getAsciiStream(String s) { throw unsupported("getAsciiStream"); }
    @Override public java.io.InputStream getAsciiStream(int i) { throw unsupported("getAsciiStream"); }
    @Override public java.io.InputStream getUnicodeStream(String s) { throw unsupported("getUnicodeStream"); }
    @Override public java.io.InputStream getUnicodeStream(int i) { throw unsupported("getUnicodeStream"); }
    @Override public java.io.InputStream getBinaryStream(String s) { throw unsupported("getBinaryStream"); }
    @Override public java.io.InputStream getBinaryStream(int i) { throw unsupported("getBinaryStream"); }
    @Override public java.math.BigDecimal getBigDecimal(String s) { throw unsupported("getBigDecimal"); }
    @Override public java.math.BigDecimal getBigDecimal(int i) { throw unsupported("getBigDecimal"); }
    @Override public java.math.BigDecimal getBigDecimal(String s, int scale) { throw unsupported("getBigDecimal"); }
    @Override public java.math.BigDecimal getBigDecimal(int i, int scale) { throw unsupported("getBigDecimal"); }
    @Override public java.sql.Ref getRef(String s) { throw unsupported("getRef"); }
    @Override public java.sql.Ref getRef(int i) { throw unsupported("getRef"); }
    @Override public java.sql.Blob getBlob(String s) { throw unsupported("getBlob"); }
    @Override public java.sql.Blob getBlob(int i) { throw unsupported("getBlob"); }
    @Override public java.sql.Clob getClob(String s) { throw unsupported("getClob"); }
    @Override public java.sql.Clob getClob(int i) { throw unsupported("getClob"); }
    @Override public java.sql.Array getArray(String s) { throw unsupported("getArray"); }
    @Override public java.sql.Array getArray(int i) { throw unsupported("getArray"); }
    @Override public Object getObject(String s, java.util.Map<String, Class<?>> map) { throw unsupported("getObject"); }
    @Override public Object getObject(int i, java.util.Map<String, Class<?>> map) { throw unsupported("getObject"); }
    @Override public <T> T getObject(String s, Class<T> type) { throw unsupported("getObject"); }
    @Override public <T> T getObject(int i, Class<T> type) { throw unsupported("getObject"); }
    @Override public int findColumn(String columnLabel) { return colIndex(columnLabel) + 1; }
    @Override public java.sql.RowId getRowId(String s) { throw unsupported("getRowId"); }
    @Override public java.sql.RowId getRowId(int i) { throw unsupported("getRowId"); }
    @Override public java.net.URL getURL(String s) { throw unsupported("getURL"); }
    @Override public java.net.URL getURL(int i) { throw unsupported("getURL"); }
    @Override public java.sql.NClob getNClob(String s) { throw unsupported("getNClob"); }
    @Override public java.sql.NClob getNClob(int i) { throw unsupported("getNClob"); }
    @Override public java.sql.SQLXML getSQLXML(String s) { throw unsupported("getSQLXML"); }
    @Override public java.sql.SQLXML getSQLXML(int i) { throw unsupported("getSQLXML"); }
    @Override public String getNString(String s) { throw unsupported("getNString"); }
    @Override public String getNString(int i) { throw unsupported("getNString"); }
    @Override public java.io.Reader getNCharacterStream(String s) { throw unsupported("getNCharacterStream"); }
    @Override public java.io.Reader getNCharacterStream(int i) { throw unsupported("getNCharacterStream"); }
    @Override public java.io.Reader getCharacterStream(String s) { throw unsupported("getCharacterStream"); }
    @Override public java.io.Reader getCharacterStream(int i) { throw unsupported("getCharacterStream"); }
    @Override public boolean isClosed() { return false; }
    @Override public java.sql.Statement getStatement() { return null; }
    @Override public int getHoldability() { throw unsupported("getHoldability"); }

    // Update* methods — this ResultSet is read-only (queries go through DAOs' own INSERT/UPDATE statements, never ResultSet.updateRow()).
    @Override public void updateNull(String s) { throw unsupported("updateNull"); }
    @Override public void updateNull(int i) { throw unsupported("updateNull"); }
    @Override public void updateRow() { throw unsupported("updateRow"); }
    @Override public void deleteRow() { throw unsupported("deleteRow"); }
    @Override public void insertRow() { throw unsupported("insertRow"); }
    @Override public void refreshRow() { throw unsupported("refreshRow"); }
    @Override public void cancelRowUpdates() { throw unsupported("cancelRowUpdates"); }
    @Override public void moveToInsertRow() { throw unsupported("moveToInsertRow"); }
    @Override public void moveToCurrentRow() { throw unsupported("moveToCurrentRow"); }
    @Override public <T> T unwrap(Class<T> iface) { throw unsupported("unwrap"); }
    @Override public boolean isWrapperFor(Class<?> iface) { return false; }

    // Bulk of update* setters follow the same "not used" pattern — omitted individually is not possible
    // since the interface requires overrides, so each throws with the same clear message.
    @Override public void updateString(String s, String x) { throw unsupported("updateString"); }
    @Override public void updateString(int i, String x) { throw unsupported("updateString"); }
    @Override public void updateInt(String s, int x) { throw unsupported("updateInt"); }
    @Override public void updateInt(int i, int x) { throw unsupported("updateInt"); }
    @Override public void updateDouble(String s, double x) { throw unsupported("updateDouble"); }
    @Override public void updateDouble(int i, double x) { throw unsupported("updateDouble"); }
    @Override public void updateBoolean(String s, boolean x) { throw unsupported("updateBoolean"); }
    @Override public void updateBoolean(int i, boolean x) { throw unsupported("updateBoolean"); }
    @Override public void updateByte(String s, byte x) { throw unsupported("updateByte"); }
    @Override public void updateByte(int i, byte x) { throw unsupported("updateByte"); }
    @Override public void updateShort(String s, short x) { throw unsupported("updateShort"); }
    @Override public void updateShort(int i, short x) { throw unsupported("updateShort"); }
    @Override public void updateLong(String s, long x) { throw unsupported("updateLong"); }
    @Override public void updateLong(int i, long x) { throw unsupported("updateLong"); }
    @Override public void updateFloat(String s, float x) { throw unsupported("updateFloat"); }
    @Override public void updateFloat(int i, float x) { throw unsupported("updateFloat"); }
    @Override public void updateBytes(String s, byte[] x) { throw unsupported("updateBytes"); }
    @Override public void updateBytes(int i, byte[] x) { throw unsupported("updateBytes"); }
    @Override public void updateBigDecimal(String s, java.math.BigDecimal x) { throw unsupported("updateBigDecimal"); }
    @Override public void updateBigDecimal(int i, java.math.BigDecimal x) { throw unsupported("updateBigDecimal"); }
    @Override public void updateDate(String s, java.sql.Date x) { throw unsupported("updateDate"); }
    @Override public void updateDate(int i, java.sql.Date x) { throw unsupported("updateDate"); }
    @Override public void updateTime(String s, java.sql.Time x) { throw unsupported("updateTime"); }
    @Override public void updateTime(int i, java.sql.Time x) { throw unsupported("updateTime"); }
    @Override public void updateTimestamp(String s, java.sql.Timestamp x) { throw unsupported("updateTimestamp"); }
    @Override public void updateTimestamp(int i, java.sql.Timestamp x) { throw unsupported("updateTimestamp"); }
    @Override public void updateAsciiStream(String s, java.io.InputStream x, int len) { throw unsupported("updateAsciiStream"); }
    @Override public void updateAsciiStream(int i, java.io.InputStream x, int len) { throw unsupported("updateAsciiStream"); }
    @Override public void updateAsciiStream(String s, java.io.InputStream x, long len) { throw unsupported("updateAsciiStream"); }
    @Override public void updateAsciiStream(int i, java.io.InputStream x, long len) { throw unsupported("updateAsciiStream"); }
    @Override public void updateAsciiStream(String s, java.io.InputStream x) { throw unsupported("updateAsciiStream"); }
    @Override public void updateAsciiStream(int i, java.io.InputStream x) { throw unsupported("updateAsciiStream"); }
    @Override public void updateBinaryStream(String s, java.io.InputStream x, int len) { throw unsupported("updateBinaryStream"); }
    @Override public void updateBinaryStream(int i, java.io.InputStream x, int len) { throw unsupported("updateBinaryStream"); }
    @Override public void updateBinaryStream(String s, java.io.InputStream x, long len) { throw unsupported("updateBinaryStream"); }
    @Override public void updateBinaryStream(int i, java.io.InputStream x, long len) { throw unsupported("updateBinaryStream"); }
    @Override public void updateBinaryStream(String s, java.io.InputStream x) { throw unsupported("updateBinaryStream"); }
    @Override public void updateBinaryStream(int i, java.io.InputStream x) { throw unsupported("updateBinaryStream"); }
    @Override public void updateCharacterStream(String s, java.io.Reader x, int len) { throw unsupported("updateCharacterStream"); }
    @Override public void updateCharacterStream(int i, java.io.Reader x, int len) { throw unsupported("updateCharacterStream"); }
    @Override public void updateCharacterStream(String s, java.io.Reader x, long len) { throw unsupported("updateCharacterStream"); }
    @Override public void updateCharacterStream(int i, java.io.Reader x, long len) { throw unsupported("updateCharacterStream"); }
    @Override public void updateCharacterStream(String s, java.io.Reader x) { throw unsupported("updateCharacterStream"); }
    @Override public void updateCharacterStream(int i, java.io.Reader x) { throw unsupported("updateCharacterStream"); }
    @Override public void updateObject(String s, Object x, int scale) { throw unsupported("updateObject"); }
    @Override public void updateObject(int i, Object x, int scale) { throw unsupported("updateObject"); }
    @Override public void updateObject(String s, Object x) { throw unsupported("updateObject"); }
    @Override public void updateObject(int i, Object x) { throw unsupported("updateObject"); }
    @Override public void updateRef(String s, java.sql.Ref x) { throw unsupported("updateRef"); }
    @Override public void updateRef(int i, java.sql.Ref x) { throw unsupported("updateRef"); }
    @Override public void updateBlob(String s, java.sql.Blob x) { throw unsupported("updateBlob"); }
    @Override public void updateBlob(int i, java.sql.Blob x) { throw unsupported("updateBlob"); }
    @Override public void updateBlob(String s, java.io.InputStream x, long len) { throw unsupported("updateBlob"); }
    @Override public void updateBlob(int i, java.io.InputStream x, long len) { throw unsupported("updateBlob"); }
    @Override public void updateBlob(String s, java.io.InputStream x) { throw unsupported("updateBlob"); }
    @Override public void updateBlob(int i, java.io.InputStream x) { throw unsupported("updateBlob"); }
    @Override public void updateClob(String s, java.sql.Clob x) { throw unsupported("updateClob"); }
    @Override public void updateClob(int i, java.sql.Clob x) { throw unsupported("updateClob"); }
    @Override public void updateClob(String s, java.io.Reader x, long len) { throw unsupported("updateClob"); }
    @Override public void updateClob(int i, java.io.Reader x, long len) { throw unsupported("updateClob"); }
    @Override public void updateClob(String s, java.io.Reader x) { throw unsupported("updateClob"); }
    @Override public void updateClob(int i, java.io.Reader x) { throw unsupported("updateClob"); }
    @Override public void updateArray(String s, java.sql.Array x) { throw unsupported("updateArray"); }
    @Override public void updateArray(int i, java.sql.Array x) { throw unsupported("updateArray"); }
    @Override public void updateRowId(String s, java.sql.RowId x) { throw unsupported("updateRowId"); }
    @Override public void updateRowId(int i, java.sql.RowId x) { throw unsupported("updateRowId"); }
    @Override public void updateNString(String s, String x) { throw unsupported("updateNString"); }
    @Override public void updateNString(int i, String x) { throw unsupported("updateNString"); }
    @Override public void updateNClob(String s, java.sql.NClob x) { throw unsupported("updateNClob"); }
    @Override public void updateNClob(int i, java.sql.NClob x) { throw unsupported("updateNClob"); }
    @Override public void updateNClob(String s, java.io.Reader x, long len) { throw unsupported("updateNClob"); }
    @Override public void updateNClob(int i, java.io.Reader x, long len) { throw unsupported("updateNClob"); }
    @Override public void updateNClob(String s, java.io.Reader x) { throw unsupported("updateNClob"); }
    @Override public void updateNClob(int i, java.io.Reader x) { throw unsupported("updateNClob"); }
    @Override public void updateSQLXML(String s, java.sql.SQLXML x) { throw unsupported("updateSQLXML"); }
    @Override public void updateSQLXML(int i, java.sql.SQLXML x) { throw unsupported("updateSQLXML"); }
    @Override public void updateNCharacterStream(String s, java.io.Reader x, long len) { throw unsupported("updateNCharacterStream"); }
    @Override public void updateNCharacterStream(int i, java.io.Reader x, long len) { throw unsupported("updateNCharacterStream"); }
    @Override public void updateNCharacterStream(String s, java.io.Reader x) { throw unsupported("updateNCharacterStream"); }
    @Override public void updateNCharacterStream(int i, java.io.Reader x) { throw unsupported("updateNCharacterStream"); }
}
