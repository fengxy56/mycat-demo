package cn.enjoy.masterslave;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;

@Slf4j
public class MasterSlaveConnectionProxy implements InvocationHandler {

    private MasterSlaveConnection masterSlaveConnection;

    public MasterSlaveConnectionProxy(MasterSlaveConnection masterSlaveConnection) {
        this.masterSlaveConnection = masterSlaveConnection;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if("prepareStatement".equalsIgnoreCase(method.getName())) {
            log.info("enjoy proxy sql：" + args[0]);
            PreparedStatement stmt = (PreparedStatement)method.invoke(masterSlaveConnection, args);
            stmt = PreparedStatementLogger.newInstance(stmt);
            return stmt;
        }
        return method.invoke(masterSlaveConnection,args);
    }
}
