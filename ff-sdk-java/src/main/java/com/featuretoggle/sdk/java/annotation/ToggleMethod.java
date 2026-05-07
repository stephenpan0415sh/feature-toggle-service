package com.featuretoggle.sdk.java.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to be intercepted for feature toggle evaluation.
 * Similar to @SentinelResource, combines flag evaluation and fallback in one annotation.
 * 
 * Usage:
 * <pre>
 * &#64;ToggleMethod(
 *     flagKey = "new_checkout",
 *     prepareContextMethod = "prepareUserContext",
 *     fallbackMethod = "checkoutFallback"
 * )
 * public CheckoutResult checkout(String orderId, int amount) {
 *     // New feature logic
 * }
 * 
 * // Prepare UserContext - must have same parameters as original method
 * private UserContext prepareUserContext(String orderId, int amount) {
 *     return new UserContext(getCurrentUserId(), Map.of("orderId", orderId));
 * }
 * 
 * // Fallback method - must have same parameters as original method
 * public CheckoutResult checkoutFallback(String orderId, int amount) {
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
     * Method name to prepare UserContext from original method parameters.
     * The method must be declared in the same class and return UserContext.
     * If not specified, an empty UserContext will be used.
     * 
     * @return method name, empty string means use empty UserContext
     */
    String prepareContextMethod() default "";
    
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
