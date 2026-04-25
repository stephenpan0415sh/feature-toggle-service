package com.featuretoggle.sdk.java.config;

import com.featuretoggle.common.model.FeatureFlag;
import com.featuretoggle.sdk.java.FeatureToggleClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FeatureFlagBeanScanner
 */
class FeatureFlagBeanScannerTest {
    
    private ApplicationContext applicationContext;
    private FeatureToggleClient featureToggleClient;
    private FeatureFlagBeanScanner scanner;
    
    @BeforeEach
    void setUp() {
        applicationContext = mock(ApplicationContext.class);
        featureToggleClient = mock(FeatureToggleClient.class);
        scanner = new FeatureFlagBeanScanner(applicationContext, featureToggleClient);
    }
    
    @Test
    void shouldScanAndRegisterFeatureFlagBeans() {
        // Given: FeatureFlag beans
        FeatureFlag flag1 = FeatureFlag.builder()
            .flagKey("flag_1")
            .defaultValue("true")
            .build();
        
        FeatureFlag flag2 = FeatureFlag.builder()
            .flagKey("flag_2")
            .defaultValue("false")
            .build();
        
        Map<String, FeatureFlag> beans = Map.of("flag1", flag1, "flag2", flag2);
        
        when(applicationContext.getBeansOfType(FeatureFlag.class)).thenReturn(beans);
        
        // When: Scanner runs
        scanner.scanAndRegister();
        
        // Then: Should register both flags
        verify(featureToggleClient, times(2)).registerFlag(any(FeatureFlag.class));
    }
    
    @Test
    void shouldSkipIfNoFeatureFlagBeans() {
        // Given: No FeatureFlag beans
        when(applicationContext.getBeansOfType(FeatureFlag.class)).thenReturn(Map.of());
        
        // When: Scanner runs
        scanner.scanAndRegister();
        
        // Then: Should not register any flags
        verify(featureToggleClient, never()).registerFlag(any(FeatureFlag.class));
    }
}
