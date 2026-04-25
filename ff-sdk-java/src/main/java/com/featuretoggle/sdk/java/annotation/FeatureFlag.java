package com.featuretoggle.sdk.java.annotation;

import com.featuretoggle.common.model.Rule.RuleType;
import java.lang.annotation.*;

/**
 * Defines a feature flag with its rules.
 * Can be placed on a class to register flags at startup.
 * 
 * Example:
 * <pre>
 * &#64;Service
 * &#64;FeatureFlag(
 *     flagKey = "new_checkout",
 *     defaultValue = "false",
 *     rules = {
 *         &#64;Rule(type = RuleType.TARGETING, priority = 1, actionValue = "true",
 *             conditions = &#64;Condition(attribute = "uid", operator = Condition.Operator.IN, values = {"123"}))
 *     }
 * )
 * public class CheckoutService { }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(FeatureFlags.class)
public @interface FeatureFlag {
    
    /**
     * Unique flag key
     */
    String flagKey();
    
    /**
     * Human-readable name
     */
    String name() default "";
    
    /**
     * Default value when no rules match ("true" or "false")
     */
    String defaultValue() default "false";
    
    /**
     * Rules to evaluate (optional, can be empty for simple on/off flag)
     */
    Rule[] rules() default {};
}
