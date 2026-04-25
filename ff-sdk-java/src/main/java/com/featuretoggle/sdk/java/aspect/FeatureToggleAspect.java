package com.featuretoggle.sdk.java.aspect;

import com.featuretoggle.common.model.UserContext;
import com.featuretoggle.sdk.java.FeatureToggleClient;
import com.featuretoggle.sdk.java.annotation.ToggleMethod;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * AOP Aspect for intercepting @ToggleMethod annotated methods.
 * Evaluates the feature flag and decides whether to proceed or execute fallback.
 * 
 * Design inspired by Sentinel's @SentinelResource annotation.
 */
@Slf4j
@Aspect
@Component
public class FeatureToggleAspect {
    
    @Autowired
    private FeatureToggleClient featureToggleClient;
    
    @Around("@annotation(toggleMethod)")
    public Object evaluateToggle(ProceedingJoinPoint joinPoint, ToggleMethod toggleMethod) throws Throwable {
        String flagKey = toggleMethod.flagKey();
        String fallbackMethodName = toggleMethod.fallbackMethod();
        String defaultValue = toggleMethod.defaultValue();
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // Extract UserContext from method parameters
        UserContext userContext = extractUserContext(joinPoint.getArgs(), signature.getParameterTypes());
        
        if (userContext == null) {
            log.warn("No UserContext found in method parameters for flag: {}, proceeding with original method", flagKey);
            return joinPoint.proceed();
        }
        
        // Evaluate flag
        boolean enabled = featureToggleClient.isEnabled(flagKey, userContext);
        log.debug("Flag {} evaluated: {} for user {}", flagKey, enabled, userContext.userId());
        
        if (enabled) {
            // Flag enabled, execute original method
            return joinPoint.proceed();
        } else {
            // Flag disabled, execute fallback if configured
            if (fallbackMethodName != null && !fallbackMethodName.isEmpty()) {
                return executeFallback(joinPoint, fallbackMethodName, flagKey);
            } else {
                log.warn("Flag {} is disabled and no fallback method configured, returning default value: {}", 
                    flagKey, defaultValue);
                return parseDefaultValue(defaultValue, method.getReturnType());
            }
        }
    }
    
    /**
     * Extract UserContext from method arguments
     */
    private UserContext extractUserContext(Object[] args, Class<?>[] parameterTypes) {
        if (args == null || parameterTypes == null) {
            return null;
        }
        
        for (int i = 0; i < parameterTypes.length; i++) {
            if (UserContext.class.isAssignableFrom(parameterTypes[i])) {
                return (UserContext) args[i];
            }
        }
        
        return null;
    }
    
    /**
     * Execute fallback method when flag is disabled
     */
    private Object executeFallback(ProceedingJoinPoint joinPoint, String fallbackMethodName, 
                                   String flagKey) throws Throwable {
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();
        
        try {
            // Find fallback method by name with compatible parameters
            Method fallbackMethod = findFallbackMethod(target.getClass(), fallbackMethodName, args);
            
            if (fallbackMethod == null) {
                log.error("Fallback method '{}' not found or has incompatible parameters for flag: {}", 
                    fallbackMethodName, flagKey);
                return null;
            }
            
            log.info("Executing fallback method '{}' for disabled flag {}", fallbackMethodName, flagKey);
            return fallbackMethod.invoke(target, args);
            
        } catch (Exception e) {
            log.error("Error executing fallback method '{}' for flag: {}", fallbackMethodName, flagKey, e);
            throw e;
        }
    }
    
    /**
     * Find fallback method by name with compatible parameter types
     */
    private Method findFallbackMethod(Class<?> clazz, String methodName, Object[] args) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                // Check parameter count and types compatibility
                if (method.getParameterCount() == args.length) {
                    return method;
                }
            }
        }
        return null;
    }
    
    /**
     * Parse default value based on return type
     */
    private Object parseDefaultValue(String defaultValue, Class<?> returnType) {
        if (returnType == boolean.class || returnType == Boolean.class) {
            return Boolean.parseBoolean(defaultValue);
        } else if (returnType == int.class || returnType == Integer.class) {
            try {
                return Integer.parseInt(defaultValue);
            } catch (NumberFormatException e) {
                return 0;
            }
        } else if (returnType == long.class || returnType == Long.class) {
            try {
                return Long.parseLong(defaultValue);
            } catch (NumberFormatException e) {
                return 0L;
            }
        } else if (returnType == double.class || returnType == Double.class) {
            try {
                return Double.parseDouble(defaultValue);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        } else if (returnType == String.class) {
            return defaultValue;
        }
        // For complex types, return null
        return null;
    }
}
