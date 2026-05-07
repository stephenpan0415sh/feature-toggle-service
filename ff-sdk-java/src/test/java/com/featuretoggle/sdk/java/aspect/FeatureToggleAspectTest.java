package com.featuretoggle.sdk.java.aspect;

import com.featuretoggle.common.model.UserContext;
import com.featuretoggle.sdk.java.FeatureToggleClient;
import com.featuretoggle.sdk.java.annotation.ToggleMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FeatureToggleAspect
 */
@ExtendWith(MockitoExtension.class)
class FeatureToggleAspectTest {

    @Mock
    private FeatureToggleClient featureToggleClient;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private ToggleMethod toggleMethod;

    @InjectMocks
    private FeatureToggleAspect aspect;

    private TestService testService;
    private Object[] args;
    private Class<?>[] parameterTypes;

    @BeforeEach
    void setUp() {
        testService = new TestService();
        args = new Object[]{new UserContext("user123", Map.of("region", "cn-east"))};
        parameterTypes = new Class<?>[]{UserContext.class};
    }

    @Test
    void evaluateToggle_shouldProceed_whenFlagEnabled() throws Throwable {
        // Given
        when(toggleMethod.flagKey()).thenReturn("test-flag");
        when(toggleMethod.fallbackMethod()).thenReturn("");
        when(toggleMethod.defaultValue()).thenReturn("false");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(TestService.class.getMethod("enabledMethod", UserContext.class));
        when(featureToggleClient.isEnabled(eq("test-flag"), nullable(UserContext.class))).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("original-result");

        // When
        Object result = aspect.evaluateToggle(joinPoint, toggleMethod);

        // Then
        assertEquals("original-result", result);
        verify(joinPoint, times(1)).proceed();
        verify(joinPoint, never()).getTarget();
    }

    @Test
    void evaluateToggle_shouldReturnDefaultValue_whenFlagDisabledAndNoFallback() throws Throwable {
        // Given
        when(toggleMethod.flagKey()).thenReturn("test-flag");
        when(toggleMethod.fallbackMethod()).thenReturn("");
        when(toggleMethod.defaultValue()).thenReturn("true");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(TestService.class.getMethod("enabledMethod", UserContext.class));
        when(featureToggleClient.isEnabled(eq("test-flag"), nullable(UserContext.class))).thenReturn(false);

        // When
        Object result = aspect.evaluateToggle(joinPoint, toggleMethod);

        // Then - enabledMethod returns String, so default value is returned as String
        assertEquals("true", result);
        verify(joinPoint, never()).proceed();
    }

    @Test
    void evaluateToggle_shouldExecuteFallback_whenFlagDisabled() throws Throwable {
        // Given
        when(toggleMethod.flagKey()).thenReturn("test-flag");
        when(toggleMethod.fallbackMethod()).thenReturn("fallbackMethod");
        when(toggleMethod.defaultValue()).thenReturn("false");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(TestService.class.getMethod("enabledMethod", UserContext.class));
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.getTarget()).thenReturn(testService);
        when(featureToggleClient.isEnabled(eq("test-flag"), nullable(UserContext.class))).thenReturn(false);

        // When
        Object result = aspect.evaluateToggle(joinPoint, toggleMethod);

        // Then
        assertEquals("fallback-result", result);
        verify(joinPoint, never()).proceed();
    }

    @Test
    void evaluateToggle_shouldProceed_whenNoUserContext() throws Throwable {
        // Given
        when(toggleMethod.flagKey()).thenReturn("test-flag");
        when(toggleMethod.fallbackMethod()).thenReturn("");
        when(toggleMethod.defaultValue()).thenReturn("false");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(TestService.class.getMethod("enabledMethod", UserContext.class));
        when(joinPoint.proceed()).thenReturn("proceeded");
        when(featureToggleClient.isEnabled(eq("test-flag"), nullable(UserContext.class))).thenReturn(true);

        // When
        Object result = aspect.evaluateToggle(joinPoint, toggleMethod);

        // Then - isEnabled is called with null userContext and returns true, so proceed
        assertEquals("proceeded", result);
        verify(featureToggleClient, times(1)).isEnabled(eq("test-flag"), nullable(UserContext.class));
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void parseDefaultValue_shouldParseBoolean() {
        assertTrue((Boolean) aspect.parseDefaultValue("true", boolean.class));
        assertFalse((Boolean) aspect.parseDefaultValue("false", Boolean.class));
    }

    @Test
    void parseDefaultValue_shouldParseInteger() {
        assertEquals(42, aspect.parseDefaultValue("42", int.class));
        assertEquals(0, aspect.parseDefaultValue("invalid", Integer.class));
    }

    @Test
    void parseDefaultValue_shouldParseLong() {
        assertEquals(100L, aspect.parseDefaultValue("100", long.class));
        assertEquals(0L, aspect.parseDefaultValue("invalid", Long.class));
    }

    @Test
    void parseDefaultValue_shouldParseDouble() {
        assertEquals(3.14, aspect.parseDefaultValue("3.14", double.class));
        assertEquals(0.0, aspect.parseDefaultValue("invalid", Double.class));
    }

    @Test
    void parseDefaultValue_shouldParseString() {
        assertEquals("hello", aspect.parseDefaultValue("hello", String.class));
    }

    // Test service class
    static class TestService {
        public String enabledMethod(UserContext context) {
            return "original";
        }

        public String fallbackMethod(UserContext context) {
            return "fallback-result";
        }
    }
}
