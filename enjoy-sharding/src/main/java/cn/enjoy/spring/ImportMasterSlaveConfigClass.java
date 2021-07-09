package cn.enjoy.spring;

import cn.enjoy.configuration.EnjoyShardingConfiguration;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

public class ImportMasterSlaveConfigClass implements ImportBeanDefinitionRegistrar {
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        BeanDefinitionBuilder shardingBuilder = BeanDefinitionBuilder.genericBeanDefinition(EnjoyShardingConfiguration.class);
        registry.registerBeanDefinition("cn.enjoy.configuration.enjoyShardingConfiguration",shardingBuilder.getBeanDefinition());
    }
}
