package com.toir.audit;

import com.toir.enums.AuditModule;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AuditedResource {
    AuditModule[] module() default {};

    String entityType() default "";

    String[] redactedFields() default {};

    String[] ignoredFields() default {};
}
