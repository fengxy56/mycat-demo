package cn.enjoy.configuration;

import cn.enjoy.masterslave.MasterSlaveDataSourceFactory;
import cn.enjoy.masterslave.MasterSlaveProperties;
import cn.enjoy.util.DataSourceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Configuration
//不能这么写
@EnableConfigurationProperties({MasterSlaveProperties.class})
@ConditionalOnProperty(prefix = "spring.enjoy", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class EnjoyShardingConfiguration implements EnvironmentAware {

    private Binder binder;

    private final MasterSlaveProperties masterSlaveProperties;

    private Map<String, DataSource> dataSourceMap = new HashMap<>();

    @Bean
    public DataSource dataSource() throws SQLException {
        if (null != masterSlaveProperties.getMasterSourceName()) {
            return MasterSlaveDataSourceFactory.createDataSource(dataSourceMap, masterSlaveProperties);
        }
        return null;
    }

    @Override
    public void setEnvironment(Environment environment) {
        binder = Binder.get(environment);
        String prefix = "spring.enjoy.masterslave.datasource.";
        String names = environment.getProperty(prefix + "names");
        for (String name : names.split(",")) {
            try {
                dataSourceMap.put(name,getDataSource(prefix,name));
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }
    }

    private DataSource getDataSource(final String prefix, final String dataSourceName) throws ReflectiveOperationException {
        Map<String, Object> dataSourceProps = binder.bind(prefix + dataSourceName, Map.class).get();
        return DataSourceUtil.getDataSource(dataSourceProps.get("type").toString(), dataSourceProps);
    }
}
