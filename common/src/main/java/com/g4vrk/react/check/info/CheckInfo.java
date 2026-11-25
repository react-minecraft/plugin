package com.g4vrk.react.check.info;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares display and configuration metadata for a {@link com.g4vrk.react.check.Check} implementation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckInfo {

    @NotNull String name();

    @NotNull String configId();

    boolean experimental() default false;

}
