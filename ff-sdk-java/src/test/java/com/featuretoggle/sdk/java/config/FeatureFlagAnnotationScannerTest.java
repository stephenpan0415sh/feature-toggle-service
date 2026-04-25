package com.featuretoggle.sdk.java.config;

import com.featuretoggle.common.model.FeatureFlag;
import com.featuretoggle.sdk.java.FeatureToggleClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FeatureFlagAnnotationScanner
 */
class FeatureFlagAnnotationScannerTest {
    
    private ApplicationContext applicationContext;
    private FeatureToggleClient featureToggleClient;
    private FeatureFlagAnnotationScanner scanner;
    
    @BeforeEach
    void setUp() {
        applicationContext = mock(ApplicationContext.class);
        featureToggleClient = mock(FeatureToggleClient.class);
        scanner = new FeatureFlagAnnotationScanner();
        
        // Inject mocks via reflection (since fields are @Autowired)
        try {
            var appField = FeatureFlagAnnotationScanner.class.getDeclaredField("applicationContext");
            appField.setAccessible(true);
            appField.set(scanner, applicationContext);
            
            var clientField = FeatureFlagAnnotationScanner.class.getDeclaredField("featureToggleClient");
            clientField.setAccessible(true);
            clientField.set(scanner, featureToggleClient);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Test
    void shouldScanAndRegisterAnnotatedBeans() {
        // Given: A bean with @FeatureFlag annotation
        TestService testService = new TestService();
        Map<String, Object> beans = Map.of("testService", testService);
        
        when(applicationContext.getBeansWithAnnotation(com.featuretoggle.sdk.java.annotation.FeatureFlag.class))
            .thenReturn(beans);
        
        // When: Scanner runs
        scanner.scanAndRegister();
        
        // Then: Should register the flag
        verify(featureToggleClient, times(1)).registerFlag(any(FeatureFlag.class));
    }
    
    @Test
    void shouldSkipIfNoAnnotatedBeans() {
        // Given: No beans with annotation
        when(applicationContext.getBeansWithAnnotation(com.featuretoggle.sdk.java.annotation.FeatureFlag.class))
            .thenReturn(Map.of());
        
        // When: Scanner runs
        scanner.scanAndRegister();
        
        // Then: Should not register any flags
        verify(featureToggleClient, never()).registerFlag(any(FeatureFlag.class));
    }
    
    /**
     * Test service with @FeatureFlag annotation
     */
    @com.featuretoggle.sdk.java.annotation.FeatureFlag(
        flagKey = "test_flag",
        name = "Test Flag",
        defaultValue = "false"
    )
    static class TestService {
    }
}
