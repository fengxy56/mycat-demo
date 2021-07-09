package cn.enjoy.masterslave;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@ConfigurationProperties(prefix = "spring.enjoy.masterslave")
public class MasterSlaveProperties {
    private String loadbalance;

    private String masterSourceName;

    List<String> slaveSourceNames = new ArrayList<>();
}
