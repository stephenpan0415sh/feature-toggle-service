package com.featuretoggle.sdk.java.annotation;

import com.featuretoggle.common.model.Rule.RuleType;
import java.lang.annotation.*;

/**
 * Defines a rule for feature flag evaluation.
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Rule {
    
    /**
     * Rule ID (auto-generated if empty)
     */
    String id() default "";
    
    /**
     * Priority (lower number = higher priority)
     */
    int priority();
    
    /**
     * Rule type
     */
    RuleType type();
    
    /**
     * Value to return when this rule matches
     */
    String actionValue();
    
    /**
     * Percentage for PERCENTAGE_ROLLOUT type (0-100)
     */
    int percentage() default 0;
    
    /**
     * Hash attribute for PERCENTAGE_ROLLOUT type
     */
    String hashAttribute() default "";
    
    /**
     * Conditions for this rule
     */
    Condition[] conditions() default {};
}
