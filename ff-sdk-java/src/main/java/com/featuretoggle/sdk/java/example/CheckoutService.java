package com.featuretoggle.sdk.java.example;

import com.featuretoggle.common.model.UserContext;
import com.featuretoggle.sdk.java.annotation.ToggleMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Example service demonstrating annotation-driven feature toggles.
 * Design inspired by Sentinel's @SentinelResource annotation.
 * 
 * Feature flags are defined in the admin console, not in code.
 * This service only references flag keys for evaluation.
 * 
 * Usage patterns:
 * 1. With fallback method: @ToggleMethod(flagKey = "xxx", fallbackMethod = "fallbackXxx")
 * 2. Without fallback: @ToggleMethod(flagKey = "xxx") - returns default value when disabled
 */
@Slf4j
@Service
public class CheckoutService {
    
    /**
     * Method with fallback - similar to Sentinel's blockHandler.
     * When flag is disabled, fallbackCheckout() will be executed.
     * 
     * Flag configuration (in admin console):
     * - flagKey: new_checkout
     * - rules: TARGETING uid IN [123, 456] -> actionValue: true
     * - defaultValue: false
     */
    @ToggleMethod(flagKey = "new_checkout", fallbackMethod = "fallbackCheckout")
    public String processCheckout(UserContext userContext) {
        log.info("Processing with NEW checkout flow for user: {}", userContext.userId());
        return "new_checkout_flow";
    }
    
    /**
     * Fallback method for new_checkout flag.
     * Must have same parameter signature as the original method.
     */
    public String fallbackCheckout(UserContext userContext) {
        log.info("Processing with DEFAULT checkout flow for user: {}", userContext.userId());
        return "default_checkout_flow";
    }
    
    /**
     * Simple flag check without fallback.
     * Returns defaultValue when flag is disabled.
     * 
     * Flag configuration (in admin console):
     * - flagKey: dark_mode
     * - defaultValue: true
     */
    @ToggleMethod(flagKey = "dark_mode", defaultValue = "true")
    public String getTheme(UserContext userContext) {
        return "dark";
    }
}
