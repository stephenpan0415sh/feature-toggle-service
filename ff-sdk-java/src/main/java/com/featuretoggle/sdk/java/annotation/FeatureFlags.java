package com.featuretoggle.sdk.java.annotation;

import java.lang.annotation.*;

/**
 * Container for multiple @FeatureFlag annotations
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeatureFlags {
    FeatureFlag[] value();
}
