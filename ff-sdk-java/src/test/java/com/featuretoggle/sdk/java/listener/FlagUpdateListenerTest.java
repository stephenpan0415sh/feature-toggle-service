package com.featuretoggle.sdk.java.listener;

import com.featuretoggle.sdk.java.FeatureToggleClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FlagUpdateListener with debouncing
 * Verifies that multiple notifications are coalesced into single sync
 */
@ExtendWith(MockitoExtension.class)
class FlagUpdateListenerTest {
    
    @Mock
    private FeatureToggleClient featureToggleClient;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @Mock
    private Message message;
    
    private FlagUpdateListener listener;
    
    @BeforeEach
    void setUp() throws Exception {
        listener = new FlagUpdateListener(featureToggleClient, objectMapper);
        
        // Mock message
        when(message.getChannel()).thenReturn("feature_flag_changes:test-app".getBytes());
        
        // Mock objectMapper to return valid event
        java.util.Map<String, Object> event = new java.util.HashMap<>();
        event.put("flagKey", "test_flag");
        event.put("deleted", false);
        event.put("version", 100);
        when(objectMapper.readValue(anyString(), eq(java.util.Map.class))).thenReturn(event);
    }
    
    @Test
    void onMessage_ShouldTriggerFullSync_WhenFlagDeleted() throws Exception {
        // Given
        java.util.Map<String, Object> event = new java.util.HashMap<>();
        event.put("flagKey", "old_feature");
        event.put("deleted", true);
        event.put("version", 100);
        when(objectMapper.readValue(anyString(), eq(java.util.Map.class))).thenReturn(event);
        
        // When
        listener.onMessage(message, null);
        
        // Then - sync is scheduled (will execute after 100ms)
        Thread.sleep(150); // Wait for debounced sync
        
        verify(featureToggleClient, times(1)).triggerFullSync();
    }
    
    @Test
    void onMessage_ShouldTriggerFullSync_WhenFlagUpdated() throws Exception {
        // When
        listener.onMessage(message, null);
        
        // Then - sync is scheduled
        Thread.sleep(150); // Wait for debounced sync
        
        verify(featureToggleClient, times(1)).triggerFullSync();
    }
    
    @Test
    void onMessage_ShouldDebounce_MultipleNotifications() throws Exception {
        // Given: Send 10 notifications rapidly
        for (int i = 0; i < 10; i++) {
            listener.onMessage(message, null);
            Thread.sleep(10); // 10ms between notifications (within 100ms window)
        }
        
        // When: Wait for debounce window
        Thread.sleep(200);
        
        // Then: Should only trigger 1 sync (not 10)
        verify(featureToggleClient, times(1)).triggerFullSync();
    }
    
    @Test
    void onMessage_ShouldAllowSync_AfterDebounceWindow() throws Exception {
        // Given: First notification
        listener.onMessage(message, null);
        Thread.sleep(200); // Wait for first sync to complete
        
        verify(featureToggleClient, times(1)).triggerFullSync();
        
        // When: Second notification after debounce window
        listener.onMessage(message, null);
        Thread.sleep(200);
        
        // Then: Should trigger another sync
        verify(featureToggleClient, times(2)).triggerFullSync();
    }
    
    @Test
    void onMessage_ShouldHandleException_Gracefully() throws Exception {
        // Given
        when(objectMapper.readValue(anyString(), eq(java.util.Map.class)))
            .thenThrow(new RuntimeException("JSON parse error"));
        
        // When & Then
        assertDoesNotThrow(() -> listener.onMessage(message, null));
        
        // Should not trigger sync on error
        Thread.sleep(150);
        verify(featureToggleClient, never()).triggerFullSync();
    }
}
