package com.featuretoggle.sdk.java.example;

import com.featuretoggle.common.model.UserContext;
import com.featuretoggle.sdk.java.annotation.ToggleMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Example service demonstrating annotation-driven feature toggles.
 * Design inspired by Sentinel's @SentinelResource annotation.
 * 
 * Feature flags are defined in the admin console, not in code.
 * This service only references flag keys for evaluation.
 * 
 * Usage patterns:
 * 1. With prepareContextMethod and fallback: 
 *    - prepareContextMethod builds UserContext from method parameters
 *    - fallbackMethod executes when flag is disabled
 * 2. Without fallback: returns default value when disabled
 */
@Slf4j
@Service
public class CheckoutService {
    
    /**
     * Method with prepareContextMethod and fallback.
     * When flag is disabled, fallbackCheckout() will be executed.
     * 
     * Flag configuration (in admin console):
     * - flagKey: new_checkout
     * - rules: TARGETING uid IN [100, 200] -> ruleDefaultEnabled: true
     * - defaultValue: false
     */
    @ToggleMethod(
        flagKey = "new_checkout", 
        prepareContextMethod = "prepareUserContext",
        fallbackMethod = "fallbackCheckout"
    )
    public String processCheckout(String orderId, int amount) {
        log.info("Processing with NEW checkout flow for order: {}", orderId);
        return "new_checkout_flow";
    }
    
    /**
     * Prepare UserContext from method parameters.
     * Must have same parameter signature as processCheckout().
     */
    private UserContext prepareUserContext(String orderId, int amount) {
        // Extract user info from business context (e.g., SecurityContext, Session)
        Long userId = getCurrentUserId(); // hypothetical method
        Integer vipLevel = getCurrentVipLevel(); // hypothetical method
        
        return new UserContext(
            String.valueOf(userId),
            Map.of("vipLevel", vipLevel, "orderId", orderId)
        );
    }
    
    /**
     * Fallback method for new_checkout flag.
     * Must have same parameter signature as the original method.
     */
    public String fallbackCheckout(String orderId, int amount) {
        log.info("Processing with DEFAULT checkout flow for order: {}", orderId);
        return "default_checkout_flow";
    }
    
    // Helper methods (hypothetical)
    private Long getCurrentUserId() {
        return 100L;
    }
    
    private Integer getCurrentVipLevel() {
        return 2;
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
    public String getTheme() {
        return "dark";
    }
}
