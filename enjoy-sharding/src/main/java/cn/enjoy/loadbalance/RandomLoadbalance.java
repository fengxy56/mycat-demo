package cn.enjoy.loadbalance;

import java.util.List;
import java.util.Random;

public class RandomLoadbalance implements LoadBalance {

    @Override
    public boolean support(String type) {
        return "random".equalsIgnoreCase(type);
    }

    @Override
    public String getDataSource(List<String> sources) {
        return sources.get(new Random().nextInt(sources.size()));
    }
}
