package com.featuretoggle.sdk.java.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to be intercepted for feature toggle evaluation.
 * Similar to @SentinelResource, combines flag evaluation and fallback in one annotation.
 * 
 * Usage:
 * <pre>
 * &#64;ToggleMethod(flagKey = "new_checkout", fallbackMethod = "checkoutFallback")
 * public CheckoutResult checkout(UserContext user) {
 *     // New feature logic
 * }
 * 
 * // Fallback method in the same class
 * public CheckoutResult checkoutFallback(UserContext user) {
 *     // Legacy logic when flag is disabled
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToggleMethod {
    
    /**
     * Flag key to evaluate
     */
    String flagKey();
    
    /**
     * Fallback method name to execute when flag is disabled.
     * The fallback method must be declared in the same class with compatible parameters.
     * 
     * @return method name, empty string means no fallback (returns null or default value)
     */
    String fallbackMethod() default "";
    
    /**
     * Default value to return when flag not found, evaluation error, or no fallback method.
     * 
     * @return "true" or "false", default is "false"
     */
    String defaultValue() default "false";
}
