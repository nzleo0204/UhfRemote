package com.leo.remote.core.aop;

import com.flyjingfish.android_aop_annotation.anno.AndroidAopPointCut;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 网络检测注解 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@AndroidAopPointCut(CheckNetCut.class)
public @interface CheckNet {}