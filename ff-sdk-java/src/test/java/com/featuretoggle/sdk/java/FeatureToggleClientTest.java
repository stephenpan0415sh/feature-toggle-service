package com.featuretoggle.sdk.java;

import com.featuretoggle.common.model.Condition;
import com.featuretoggle.common.model.EvaluationDetail;
import com.featuretoggle.common.model.FeatureFlag;
import com.featuretoggle.common.model.Rule;
import com.featuretoggle.common.model.UserContext;
import com.featuretoggle.sdk.java.config.SdkProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FeatureToggleClient
 * Tests cache management, sync operations, and flag evaluation
 */
class FeatureToggleClientTest {
    
    private SdkProperties properties;
    private FeatureToggleClient client;
    
    @BeforeEach
    void setUp() throws Exception {
        properties = new SdkProperties();
        properties.setAppKey("test-app");
        properties.setServerUrl("http://localhost:8080");
        properties.setEnvironment("prod");
        properties.setPullInterval(0); // Disable background sync for tests
        
        client = new FeatureToggleClient(properties);
        
        // Mock RestTemplate to avoid real HTTP calls during tests
        mockRestTemplate();
    }
    
    @Test
    void registerFlag_shouldAddToCache() {
        // Given
        FeatureFlag flag = FeatureFlag.builder()
            .flagKey("test_flag")
            .name("Test Flag")
            .defaultValue("false")
            .build();
        
        // When
        client.registerFlag(flag);
        
        // Then
        Map<String, FeatureFlag> cache = client.getFlagCache();
        assertTrue(cache.containsKey("test_flag"));
        assertEquals("Test Flag", cache.get("test_flag").getName());
    }
    
    @Test
    void registerFlag_shouldSkipDuplicate() {
        // Given
        FeatureFlag flag = FeatureFlag.builder()
            .flagKey("test_flag")
            .name("Test Flag")
            .defaultValue("false")
            .build();
        
        client.registerFlag(flag);
        int initialSize = client.getFlagCache().size();
        
        // When - register same flag again
        client.registerFlag(flag);
        
        // Then - cache size should not change
        assertEquals(initialSize, client.getFlagCache().size());
    }
    
    @Test
    void removeFlag_shouldRemoveFromCache() {
        // Given
        FeatureFlag flag = FeatureFlag.builder()
            .flagKey("test_flag")
            .name("Test Flag")
            .defaultValue("false")
            .build();
        
        client.registerFlag(flag);
        assertTrue(client.getFlagCache().containsKey("test_flag"));
        
        // When
        client.removeFlag("test_flag");
        
        // Then
        assertFalse(client.getFlagCache().containsKey("test_flag"));
    }
    
    @Test
    void evaluate_shouldReturnDefault_whenFlagNotFound() {
        // Given
        UserContext userContext = new UserContext("user123", Map.of("region", "cn-east"));
        
        // When
        EvaluationDetail detail = client.evaluate("nonexistent_flag", userContext);
        
        // Then
        assertFalse(detail.enabled());
        assertEquals("false", detail.value());
        assertEquals(EvaluationDetail.EvaluationReason.DEFAULT, detail.reason());
        assertEquals("nonexistent_flag", detail.flagKey());
    }
    
    @Test
    void evaluate_shouldUseRuleEvaluator_whenFlagExists() {
        // Given
        FeatureFlag flag = FeatureFlag.builder()
            .flagKey("test_flag")
            .name("Test Flag")
            .defaultValue("false")
            .status(1)
            .rules(List.of(
                Rule.builder()
                    .id("rule_1")
                    .priority(1)
                    .type(Rule.RuleType.TARGETING)
                    .actionValue("true")
                    .conditions(List.of(
                        new Condition("region", Condition.Operator.EQ, List.of("cn-east"))
                    ))
                    .build()
            ))
            .build();
        
        client.registerFlag(flag);
        UserContext userContext = new UserContext("user123", Map.of("region", "cn-east"));
        
        // When
        EvaluationDetail detail = client.evaluate("test_flag", userContext);
        
        // Then
        assertTrue(detail.enabled());
        assertEquals("true", detail.value());
        assertEquals(EvaluationDetail.EvaluationReason.MATCHED_RULE, detail.reason());
    }
    
    @Test
    void isEnabled_shouldReturnTrue_whenFlagEnabled() {
        // Given
        FeatureFlag flag = FeatureFlag.builder()
            .flagKey("enabled_flag")
            .name("Enabled Flag")
            .defaultValue("true")
            .status(1)
            .build();
        
        client.registerFlag(flag);
        UserContext userContext = new UserContext("user123", null);
        
        // When
        boolean enabled = client.isEnabled("enabled_flag", userContext);
        
        // Then
        assertTrue(enabled);
    }
    
    @Test
    void isEnabled_shouldReturnFalse_whenFlagDisabled() {
        // Given
        FeatureFlag flag = FeatureFlag.builder()
            .flagKey("disabled_flag")
            .name("Disabled Flag")
            .defaultValue("false")
            .status(0)
            .build();
        
        client.registerFlag(flag);
        UserContext userContext = new UserContext("user123", null);
        
        // When
        boolean enabled = client.isEnabled("disabled_flag", userContext);
        
        // Then
        assertFalse(enabled);
    }
    
    @Test
    void syncFlagFromServer_shouldCacheFlag_whenSuccess() throws Exception {
        // Given
        String flagKey = "synced_flag";
        
        // Mock HTTP response using reflection
        Map<String, Object> mockResponse = Map.of(
            "success", true,
            "data", Map.of(
                "flagKey", flagKey,
                "name", "Synced Flag",
                "defaultValue", "true",
                "status", 1
            )
        );
        
        mockRestTemplateResponse(flagKey, mockResponse);
        
        // When
        client.syncFlagFromServer(flagKey);
        
        // Then
        Map<String, FeatureFlag> cache = client.getFlagCache();
        assertTrue(cache.containsKey(flagKey));
        assertEquals("Synced Flag", cache.get(flagKey).getName());
    }
    
    @Test
    void syncFlagFromServer_shouldHandleError_gracefully() {
        // Given
        String flagKey = "error_flag";
        
        // When & Then - should not throw exception even if server is down
        assertDoesNotThrow(() -> client.syncFlagFromServer(flagKey));
        assertFalse(client.getFlagCache().containsKey(flagKey));
    }
    
    @Test
    void triggerFullSync_shouldClearAndReloadCache() throws Exception {
        // Given
        // Add some initial flags
        client.registerFlag(FeatureFlag.builder()
            .flagKey("old_flag")
            .name("Old Flag")
            .defaultValue("false")
            .build());
        
        assertEquals(1, client.getFlagCache().size());
        
        // Mock server response with new flags
        Map<String, Object> mockResponse = Map.of(
            "success", true,
            "data", Map.of(
                "globalVersion", 100,
                "flags", List.of(
                    Map.of("flagKey", "new_flag_1", "name", "New Flag 1", "defaultValue", "true", "status", 1),
                    Map.of("flagKey", "new_flag_2", "name", "New Flag 2", "defaultValue", "false", "status", 1)
                )
            )
        );
        
        mockRestTemplateResponseForSync(mockResponse);
        
        // When
        client.triggerFullSync();
        
        // Then - old flag should be removed, new flags should be cached
        Map<String, FeatureFlag> cache = client.getFlagCache();
        assertFalse(cache.containsKey("old_flag"));
        assertTrue(cache.containsKey("new_flag_1"));
        assertTrue(cache.containsKey("new_flag_2"));
        assertEquals(2, cache.size());
    }
    
    @Test
    void stop_shouldShutdownScheduler() throws Exception {
        // Given - create client with background sync enabled
        properties.setPullInterval(5000);
        FeatureToggleClient clientWithSync = new FeatureToggleClient(properties);
        
        // Mock RestTemplate for this new client instance to avoid real HTTP calls
        RestTemplate mockRestTemplate = new RestTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T getForObject(String url, Class<T> responseType, Object... uriVariables) {
                return (T) Map.of("success", false, "message", "Mocked - no server available");
            }
            
            @Override
            @SuppressWarnings("unchecked")
            public <T> T postForObject(String url, Object request, Class<T> responseType, Object... uriVariables) {
                return (T) Map.of("success", true, "message", "Mocked");
            }
        };
        
        Field field = FeatureToggleClient.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        field.set(clientWithSync, mockRestTemplate);
        
        clientWithSync.start();
        
        // When
        clientWithSync.stop();
        
        // Then - scheduler should be shutdown (no exception means success)
        assertDoesNotThrow(() -> clientWithSync.stop());
    }
    
    /**
     * Helper method to mock RestTemplate to avoid real HTTP calls
     */
    private void mockRestTemplate() throws Exception {
        RestTemplate mockRestTemplate = new RestTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T getForObject(String url, Class<T> responseType, Object... uriVariables) {
                // Return empty response for GET requests (sync operations)
                return (T) Map.of("success", false, "message", "Mocked - no server available");
            }
            
            @Override
            @SuppressWarnings("unchecked")
            public <T> T postForObject(String url, Object request, Class<T> responseType, Object... uriVariables) {
                // Return success response for POST requests (publish operations)
                return (T) Map.of("success", true, "message", "Mocked - publish skipped");
            }
        };
        
        Field field = FeatureToggleClient.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        field.set(client, mockRestTemplate);
    }
    
    /**
     * Helper method to mock RestTemplate response using reflection
     */
    private void mockRestTemplateResponse(String flagKey, Map<String, Object> response) throws Exception {
        RestTemplate mockRestTemplate = new RestTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T getForObject(String url, Class<T> responseType, Object... uriVariables) {
                return (T) response;
            }
            
            @Override
            @SuppressWarnings("unchecked")
            public <T> T postForObject(String url, Object request, Class<T> responseType, Object... uriVariables) {
                return (T) Map.of("success", true, "message", "Mocked");
            }
        };
        
        Field field = FeatureToggleClient.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        field.set(client, mockRestTemplate);
    }
    
    /**
     * Helper method to mock RestTemplate response for sync operations
     */
    private void mockRestTemplateResponseForSync(Map<String, Object> response) throws Exception {
        RestTemplate mockRestTemplate = new RestTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T getForObject(String url, Class<T> responseType, Object... uriVariables) {
                return (T) response;
            }
            
            @Override
            @SuppressWarnings("unchecked")
            public <T> T postForObject(String url, Object request, Class<T> responseType, Object... uriVariables) {
                return (T) Map.of("success", true, "message", "Mocked");
            }
        };
        
        Field field = FeatureToggleClient.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        field.set(client, mockRestTemplate);
    }
}
