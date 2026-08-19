package com.leo.uhf.core.aop;

import com.flyjingfish.android_aop_annotation.anno.AndroidAopPointCut;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Debug 日志注解 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@AndroidAopPointCut(LogCut.class)
public @interface Log {

    String value() default "APPLog";
}