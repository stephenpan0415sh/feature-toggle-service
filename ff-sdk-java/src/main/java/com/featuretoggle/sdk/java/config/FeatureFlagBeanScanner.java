package com.featuretoggle.sdk.java.config;

import com.featuretoggle.common.model.FeatureFlag;
import com.featuretoggle.sdk.java.FeatureToggleClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Scans Spring context for FeatureFlag beans and registers them to the SDK client.
 * Supports programmatic flag registration via @Bean methods.
 * 
 * Example:
 * <pre>
 * &#64;Configuration
 * public class SharedFlagsConfig {
 *     
 *     &#64;Bean
 *     public FeatureFlag paymentTimeout() {
 *         return FeatureFlag.builder()
 *             .flagKey("payment_timeout")
 *             .defaultValue("30000")
 *             .build();
 *     }
 * }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeatureFlagBeanScanner {
    
    private final ApplicationContext applicationContext;
    private final FeatureToggleClient featureToggleClient;
    
    @EventListener(ApplicationReadyEvent.class)
    public void scanAndRegister() {
        log.info("Scanning for FeatureFlag beans...");
        
        Map<String, FeatureFlag> flags = applicationContext.getBeansOfType(FeatureFlag.class);
        
        for (Map.Entry<String, FeatureFlag> entry : flags.entrySet()) {
            FeatureFlag flag = entry.getValue();
            featureToggleClient.registerFlag(flag);
            log.info("Registered flag from bean: {} [bean: {}]", flag.getFlagKey(), entry.getKey());
        }
        
        log.info("Bean scanning completed, total flags: {}", 
            featureToggleClient.getFlagCache().size());
    }
}
