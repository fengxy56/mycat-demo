package com.xiangxue.jack.dynamicDataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class XXX {

    @Autowired
    private Environment environment;

    @PostConstruct
    public void xx() {
        BeanDefinitionRegistry beanDefinitionRegistry = BeanDifiTest.getBeanDefinitionRegistry();
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(beanDefinitionRegistry);
//        scanner.addIncludeFilter(new AnnotationTypeFilter(MyService.class));
        int scan = scanner.scan("com.xiangxue.jack");
    }
}
