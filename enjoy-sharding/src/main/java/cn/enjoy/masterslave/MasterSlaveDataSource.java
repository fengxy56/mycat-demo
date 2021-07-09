package cn.enjoy.masterslave;

import lombok.Getter;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.logging.Logger;

@Getter
public class MasterSlaveDataSource implements DataSource {

    private Map<String,DataSource> dataSourceMap;

    private MasterSlaveProperties masterSlaveProperties;

    public MasterSlaveDataSource(Map<String, DataSource> dataSourceMap, MasterSlaveProperties masterSlaveProperties) {
        this.dataSourceMap = dataSourceMap;
        this.masterSlaveProperties = masterSlaveProperties;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection proxCon = (Connection)Proxy.newProxyInstance(this.getClass().getClassLoader(),new Class<?>[]{Connection.class},
                new MasterSlaveConnectionProxy(new MasterSlaveConnection(this,dataSourceMap)));
        return proxCon;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return null;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {

    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {

    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }
}
