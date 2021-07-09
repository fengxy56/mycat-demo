package cn.enjoy.masterslave;

import javax.sql.DataSource;
import java.util.Map;

public class MasterSlaveDataSourceFactory {

    public static DataSource createDataSource(Map<String, DataSource> dataSourceMap,MasterSlaveProperties masterSlaveProperties) {
        return new MasterSlaveDataSource(dataSourceMap,masterSlaveProperties);
    }
}
