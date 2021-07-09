package cn.enjoy.masterslave;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.ArrayUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class PreparedStatementLogger implements InvocationHandler {

    protected static final Set<String> SET_METHODS;
    protected static final Set<String> EXECUTE_METHODS = new HashSet<>();

    private final Map<Object, Object> columnMap = new HashMap<>();

    private final List<Object> columnNames = new ArrayList<>();
    private final List<Object> columnValues = new ArrayList<>();

    static {
        SET_METHODS = Arrays.stream(PreparedStatement.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("set"))
                .filter(method -> method.getParameterCount() > 1)
                .map(Method::getName)
                .collect(Collectors.toSet());

        EXECUTE_METHODS.add("execute");
        EXECUTE_METHODS.add("executeUpdate");
        EXECUTE_METHODS.add("executeQuery");
        EXECUTE_METHODS.add("addBatch");
    }

    private PreparedStatement statement;

    public PreparedStatementLogger(PreparedStatement statement) {
        this.statement = statement;
    }

    public static PreparedStatement newInstance(PreparedStatement stmt) {
        InvocationHandler prepareStatementProxy = new PreparedStatementLogger(stmt);
        ClassLoader cl = PreparedStatement.class.getClassLoader();
        return (PreparedStatement) Proxy.newProxyInstance(cl, new Class[]{PreparedStatement.class, CallableStatement.class}, prepareStatementProxy);
    }

    protected void setColumn(Object key, Object value) {
        columnMap.put(key, value);
        columnNames.add(key);
        columnValues.add(value);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if (EXECUTE_METHODS.contains(method.getName())) {
            log.info("enjoy sharding Parameters: " + getParameterValueString(), true);
            return method.invoke(statement, args);
        }
        //1、收集入参   ps.setString ps.setInteger
        else if (SET_METHODS.contains(method.getName())) {
            if ("setNull".equals(method.getName())) {
                setColumn(args[0], null);
            } else {
                setColumn(args[0], args[1]);
            }
            return method.invoke(statement, args);
        }
        return method.invoke(statement, args);
    }

    protected String getParameterValueString() {
        List<Object> typeList = new ArrayList<>(columnValues.size());
        for (Object value : columnValues) {
            if (value == null) {
                typeList.add("null");
            } else {
                typeList.add(objectValueString(value) + "(" + value.getClass().getSimpleName() + ")");
            }
        }
        final String parameters = typeList.toString();
        return parameters.substring(1, parameters.length() - 1);
    }

    protected String objectValueString(Object value) {
        if (value instanceof Array) {
            try {
                return ArrayUtil.toString(((Array) value).getArray());
            } catch (SQLException e) {
                return value.toString();
            }
        }
        return value.toString();
    }
}
