package com.xin.picturebackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnceTokenRequired {
    /**
     * 是否需要登录才能验证 token。默认 true。
     */
    boolean needLogin() default true;
}

