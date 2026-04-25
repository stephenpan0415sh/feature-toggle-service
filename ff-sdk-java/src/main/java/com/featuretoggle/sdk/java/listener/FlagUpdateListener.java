                          package com.featuretoggle.sdk.java.listener;

import com.featuretoggle.sdk.java.FeatureToggleClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis message listener for feature flag updates.
 * Listens to app-specific channel and triggers full sync when flags change.
 * 
 * Strategy: When receiving Pub/Sub notification, trigger full config pull from server.
 * This ensures L1 cache is always consistent with server state, including deletions.
 * 
 * Debouncing: Multiple notifications within 100ms window are coalesced into a single sync
 * to prevent overwhelming the server during batch updates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlagUpdateListener implements MessageListener {
    
    private final FeatureToggleClient featureToggleClient;
    private final ObjectMapper objectMapper;
    
    /**
     * Debouncing mechanism: prevent multiple concurrent full syncs
     */
    private final AtomicBoolean syncScheduled = new AtomicBoolean(false);
    
    /**
     * Scheduler for delayed sync execution
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "flag-sync-debouncer");
        t.setDaemon(true);
        return t;
    });
    
    /**
     * Debounce window in milliseconds
     */
    private static final long DEBOUNCE_DELAY_MS = 100;
    
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());
            
            log.debug("Received message from channel {}: {}", channel, body);
            
            // Parse message (for logging only, sync decision is based on notification presence)
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(body, Map.class);
            
            String flagKey = (String) event.get("flagKey");
            boolean deleted = Boolean.TRUE.equals(event.get("deleted"));
            
            log.info("Flag {} notification: {}, scheduling sync", 
                deleted ? "deleted" : "updated", flagKey);
            
            // Schedule sync with debouncing
            scheduleSync();
            
        } catch (Exception e) {
            log.error("Error processing flag update message", e);
        }
    }
    
    /**
     * Schedule a full sync with debouncing
     * Multiple calls within DEBOUNCE_DELAY_MS will be coalesced into one sync
     */
    private void scheduleSync() {
        // Try to set flag: only first call succeeds
        if (syncScheduled.compareAndSet(false, true)) {
            log.debug("Sync scheduled, will execute in {}ms", DEBOUNCE_DELAY_MS);
            
            scheduler.schedule(() -> {
                try {
                    log.info("Executing scheduled full sync");
                    featureToggleClient.triggerFullSync();
                    log.info("Full sync completed");
                } catch (Exception e) {
                    log.error("Failed to execute scheduled sync", e);
                } finally {
                    // Reset flag to allow next sync
                    syncScheduled.set(false);
                    log.debug("Sync flag reset, ready for next notification");
                }
            }, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
        } else {
            log.debug("Sync already scheduled, skipping duplicate notification");
        }
    }
}
