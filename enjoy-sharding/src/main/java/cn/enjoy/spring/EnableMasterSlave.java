package cn.enjoy.spring;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(ImportMasterSlaveConfigClass.class)
public @interface EnableMasterSlave {
}
