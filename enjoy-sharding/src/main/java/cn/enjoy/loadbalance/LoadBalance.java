package cn.enjoy.loadbalance;

import java.util.List;

public interface LoadBalance {

    boolean support(String type);

    String getDataSource(List<String> sources);
}
