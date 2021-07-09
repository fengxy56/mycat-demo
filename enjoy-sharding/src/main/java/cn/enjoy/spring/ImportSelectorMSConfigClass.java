package cn.enjoy.spring;

import cn.enjoy.configuration.EnjoyShardingConfiguration;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

public class ImportSelectorMSConfigClass implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[]{EnjoyShardingConfiguration.class.getName()};
    }
}
