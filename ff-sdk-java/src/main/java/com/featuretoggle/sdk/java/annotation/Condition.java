package com.featuretoggle.sdk.java.annotation;

import com.featuretoggle.common.model.Condition.Operator;
import java.lang.annotation.*;

/**
 * Defines a condition within a rule.
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Condition {
    
    /**
     * User attribute to check
     */
    String attribute();
    
    /**
     * Comparison operator
     */
    Operator operator();
    
    /**
     * Values to compare against
     */
    String[] values();
}
