package com.kaziflow.network;

import java.sql.ResultSetMetaData;
import java.util.List;

/**
 * Minimal ResultSetMetaData backing RemoteResultSet.getMetaData() — only
 * getColumnCount()/getColumnLabel() are used anywhere in this codebase
 * (via ResultSetMetaData in NetworkServer.fillRows() on the server side;
 * client-side DAOs never call getMetaData() directly, this exists purely
 * so RemoteResultSet.getMetaData() has something valid to return).
 */
public class RemoteResultSetMetaData implements ResultSetMetaData {

    private final List<String> columns;

    public RemoteResultSetMetaData(List<String> columns) {
        this.columns = columns != null ? columns : List.of();
    }

    @Override public int getColumnCount() { return columns.size(); }
    @Override public String getColumnLabel(int column) { return columns.get(column - 1); }
    @Override public String getColumnName(int column) { return columns.get(column - 1); }

    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException("RemoteResultSetMetaData." + method + "() is not implemented.");
    }

    @Override public boolean isAutoIncrement(int column) { throw unsupported("isAutoIncrement"); }
    @Override public boolean isCaseSensitive(int column) { return true; }
    @Override public boolean isSearchable(int column) { return true; }
    @Override public boolean isCurrency(int column) { return false; }
    @Override public int isNullable(int column) { return columnNullableUnknown; }
    @Override public boolean isSigned(int column) { return true; }
    @Override public int getColumnDisplaySize(int column) { return 0; }
    @Override public String getSchemaName(int column) { return ""; }
    @Override public int getPrecision(int column) { return 0; }
    @Override public int getScale(int column) { return 0; }
    @Override public String getTableName(int column) { return ""; }
    @Override public String getCatalogName(int column) { return ""; }
    @Override public int getColumnType(int column) { return java.sql.Types.OTHER; }
    @Override public String getColumnTypeName(int column) { return "OTHER"; }
    @Override public boolean isReadOnly(int column) { return true; }
    @Override public boolean isWritable(int column) { return false; }
    @Override public boolean isDefinitelyWritable(int column) { return false; }
    @Override public String getColumnClassName(int column) { return Object.class.getName(); }
    @Override public <T> T unwrap(Class<T> iface) { throw unsupported("unwrap"); }
    @Override public boolean isWrapperFor(Class<?> iface) { return false; }
}
