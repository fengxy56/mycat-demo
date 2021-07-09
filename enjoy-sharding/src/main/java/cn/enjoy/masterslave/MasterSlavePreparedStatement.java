package cn.enjoy.masterslave;

import cn.enjoy.loadbalance.LoadBalance;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.sql.Date;
import java.util.*;

@Slf4j
@Getter
public class MasterSlavePreparedStatement implements PreparedStatement {

    private MasterSlaveConnection connection;

    private final Collection<PreparedStatement> routedStatements = new LinkedList<>();

    public MasterSlavePreparedStatement(MasterSlaveConnection connection, String sql) {
        this.connection = connection;
        MasterSlaveProperties masterSlaveProperties = connection.getMasterSlaveDataSource().getMasterSlaveProperties();
        if (isRead(sql)) {
            createPrepareStatement(masterSlaveProperties.getSlaveSourceNames(), routedStatements, sql);
        } else {
            createPrepareStatement(Collections.singletonList(masterSlaveProperties.getMasterSourceName()), routedStatements, sql);
        }
    }

    private void createPrepareStatement(List<String> sources, Collection<PreparedStatement> routedStatements, String sql) {
        ServiceLoader<LoadBalance> load = ServiceLoader.load(LoadBalance.class);
        MasterSlaveProperties masterSlaveProperties = connection.getMasterSlaveDataSource().getMasterSlaveProperties();
        for (LoadBalance loadBalance : load) {
            if(loadBalance.support(masterSlaveProperties.getLoadbalance())) {
                String dataSource = loadBalance.getDataSource(sources);
                log.info("enjoy sharding choice datasource is : " + dataSource);
                PreparedStatement preparedStatement = null;
                try {
                    preparedStatement = connection.getConnection(dataSource).prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
                routedStatements.add(preparedStatement);
            }
        }
    }


    private boolean isRead(String sql) {
        return (sql.contains("select") || sql.contains("SELECT")) ? true : false;
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        return routedStatements.iterator().next().executeQuery();
    }

    @Override
    public int executeUpdate() throws SQLException {
        int result = 0;
        for (PreparedStatement each : routedStatements) {
            result += each.executeUpdate();
        }
        return result;
    }

    @Override
    public final void setNull(final int parameterIndex, final int sqlType) throws SQLException {
        getTargetPreparedStatement().setNull(parameterIndex, sqlType);
    }

    @Override
    public final void setNull(final int parameterIndex, final int sqlType, final String typeName) throws SQLException {
        getTargetPreparedStatement().setNull(parameterIndex, sqlType, typeName);
    }

    @Override
    public final void setBoolean(final int parameterIndex, final boolean x) throws SQLException {
        getTargetPreparedStatement().setBoolean(parameterIndex, x);
    }

    @Override
    public final void setByte(final int parameterIndex, final byte x) throws SQLException {
        getTargetPreparedStatement().setByte(parameterIndex, x);
    }

    @Override
    public final void setShort(final int parameterIndex, final short x) throws SQLException {
        getTargetPreparedStatement().setShort(parameterIndex, x);
    }

    @Override
    public final void setInt(final int parameterIndex, final int x) throws SQLException {
        getTargetPreparedStatement().setInt(parameterIndex, x);
    }

    @Override
    public final void setLong(final int parameterIndex, final long x) throws SQLException {
        getTargetPreparedStatement().setLong(parameterIndex, x);
    }

    @Override
    public final void setFloat(final int parameterIndex, final float x) throws SQLException {
        getTargetPreparedStatement().setFloat(parameterIndex, x);
    }

    @Override
    public final void setDouble(final int parameterIndex, final double x) throws SQLException {
        getTargetPreparedStatement().setDouble(parameterIndex, x);
    }

    @Override
    public final void setString(final int parameterIndex, final String x) throws SQLException {
        getTargetPreparedStatement().setString(parameterIndex, x);
    }

    @Override
    public final void setBigDecimal(final int parameterIndex, final BigDecimal x) throws SQLException {
        getTargetPreparedStatement().setBigDecimal(parameterIndex, x);
    }

    @Override
    public final void setDate(final int parameterIndex, final Date x) throws SQLException {
        getTargetPreparedStatement().setDate(parameterIndex, x);
    }

    @Override
    public final void setDate(final int parameterIndex, final Date x, final Calendar cal) throws SQLException {
        getTargetPreparedStatement().setDate(parameterIndex, x, cal);
    }

    @Override
    public final void setTime(final int parameterIndex, final Time x) throws SQLException {
        getTargetPreparedStatement().setTime(parameterIndex, x);
    }

    @Override
    public final void setTime(final int parameterIndex, final Time x, final Calendar cal) throws SQLException {
        getTargetPreparedStatement().setTime(parameterIndex, x, cal);
    }

    @Override
    public final void setTimestamp(final int parameterIndex, final Timestamp x) throws SQLException {
        getTargetPreparedStatement().setTimestamp(parameterIndex, x);
    }

    @Override
    public final void setTimestamp(final int parameterIndex, final Timestamp x, final Calendar cal) throws SQLException {
        getTargetPreparedStatement().setTimestamp(parameterIndex, x, cal);
    }

    @Override
    public final void setBytes(final int parameterIndex, final byte[] x) throws SQLException {
        getTargetPreparedStatement().setBytes(parameterIndex, x);
    }

    @Override
    public final void setBlob(final int parameterIndex, final Blob x) throws SQLException {
        getTargetPreparedStatement().setBlob(parameterIndex, x);
    }

    @Override
    public final void setBlob(final int parameterIndex, final InputStream x) throws SQLException {
        getTargetPreparedStatement().setBlob(parameterIndex, x);
    }

    @Override
    public final void setBlob(final int parameterIndex, final InputStream x, final long length) throws SQLException {
        getTargetPreparedStatement().setBlob(parameterIndex, x, length);
    }

    @Override
    public final void setClob(final int parameterIndex, final Clob x) throws SQLException {
        getTargetPreparedStatement().setClob(parameterIndex, x);
    }

    @Override
    public final void setClob(final int parameterIndex, final Reader x) throws SQLException {
        getTargetPreparedStatement().setClob(parameterIndex, x);
    }

    @Override
    public final void setClob(final int parameterIndex, final Reader x, final long length) throws SQLException {
        getTargetPreparedStatement().setClob(parameterIndex, x, length);
    }

    @Override
    public final void setAsciiStream(final int parameterIndex, final InputStream x) throws SQLException {
        getTargetPreparedStatement().setAsciiStream(parameterIndex, x);
    }

    @Override
    public final void setAsciiStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
        getTargetPreparedStatement().setAsciiStream(parameterIndex, x, length);
    }

    @Override
    public final void setAsciiStream(final int parameterIndex, final InputStream x, final long length) throws SQLException {
        getTargetPreparedStatement().setAsciiStream(parameterIndex, x, length);
    }

    @SuppressWarnings("deprecation")
    @Override
    public final void setUnicodeStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
        getTargetPreparedStatement().setUnicodeStream(parameterIndex, x, length);
    }

    @Override
    public final void setBinaryStream(final int parameterIndex, final InputStream x) throws SQLException {
        getTargetPreparedStatement().setBinaryStream(parameterIndex, x);
    }

    @Override
    public final void setBinaryStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
        getTargetPreparedStatement().setBinaryStream(parameterIndex, x, length);
    }

    @Override
    public final void setBinaryStream(final int parameterIndex, final InputStream x, final long length) throws SQLException {
        getTargetPreparedStatement().setBinaryStream(parameterIndex, x, length);
    }

    @Override
    public final void setCharacterStream(final int parameterIndex, final Reader x) throws SQLException {
        getTargetPreparedStatement().setCharacterStream(parameterIndex, x);
    }

    @Override
    public final void setCharacterStream(final int parameterIndex, final Reader x, final int length) throws SQLException {
        getTargetPreparedStatement().setCharacterStream(parameterIndex, x, length);
    }

    @Override
    public final void setCharacterStream(final int parameterIndex, final Reader x, final long length) throws SQLException {
        getTargetPreparedStatement().setCharacterStream(parameterIndex, x, length);
    }

    @Override
    public final void setSQLXML(final int parameterIndex, final SQLXML x) throws SQLException {
        getTargetPreparedStatement().setSQLXML(parameterIndex, x);
    }

    @Override
    public final void setURL(final int parameterIndex, final URL x) throws SQLException {
        getTargetPreparedStatement().setURL(parameterIndex, x);
    }

    @Override
    public final void setObject(final int parameterIndex, final Object x) throws SQLException {
        getTargetPreparedStatement().setObject(parameterIndex, x);
    }

    @Override
    public final void setObject(final int parameterIndex, final Object x, final int targetSqlType) throws SQLException {
        getTargetPreparedStatement().setObject(parameterIndex, x, targetSqlType);
    }

    @Override
    public final void setObject(final int parameterIndex, final Object x, final int targetSqlType, final int scaleOrLength) throws SQLException {
        getTargetPreparedStatement().setObject(parameterIndex, x, targetSqlType, scaleOrLength);
    }

    @Override
    public final void clearParameters() throws SQLException {
        getTargetPreparedStatement().clearParameters();
    }

    private PreparedStatement getTargetPreparedStatement() {
        return (PreparedStatement) getRoutedStatements().iterator().next();
    }

    @Override
    public boolean execute() throws SQLException {
        boolean result = false;
        for (PreparedStatement each : routedStatements) {
            result = each.execute();
        }
        return result;
    }

    @Override
    public void addBatch() throws SQLException {
        routedStatements.iterator().next().addBatch();
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {

    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {

    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return routedStatements.iterator().next().getMetaData();
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return routedStatements.iterator().next().getParameterMetaData();
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {

    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {

    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {

    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {

    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {

    }


    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {

    }


    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {

    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        return routedStatements.iterator().next().executeQuery();
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        return routedStatements.iterator().next().executeUpdate();
    }

    @Override
    public void close() throws SQLException {

    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return 0;
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {

    }

    @Override
    public int getMaxRows() throws SQLException {
        return 0;
    }

    @Override
    public void setMaxRows(int max) throws SQLException {

    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {

    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return 0;
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {

    }

    @Override
    public void cancel() throws SQLException {

    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {

    }

    @Override
    public void setCursorName(String name) throws SQLException {

    }

    @Override
    public boolean execute(String sql) throws SQLException {
        return routedStatements.iterator().next().execute(sql);
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return routedStatements.iterator().next().getResultSet();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return 0;
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return false;
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {

    }

    @Override
    public int getFetchDirection() throws SQLException {
        return 0;
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {

    }

    @Override
    public int getFetchSize() throws SQLException {
        return 0;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return 0;
    }

    @Override
    public int getResultSetType() throws SQLException {
        return 0;
    }

    @Override
    public void addBatch(String sql) throws SQLException {

    }

    @Override
    public void clearBatch() throws SQLException {

    }

    @Override
    public int[] executeBatch() throws SQLException {
        return new int[0];
    }

    @Override
    public Connection getConnection() throws SQLException {
        return null;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return false;
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return null;
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return 0;
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return 0;
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return 0;
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return false;
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return false;
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return false;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return 0;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return false;
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {

    }

    @Override
    public boolean isPoolable() throws SQLException {
        return false;
    }

    @Override
    public void closeOnCompletion() throws SQLException {

    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }
}
