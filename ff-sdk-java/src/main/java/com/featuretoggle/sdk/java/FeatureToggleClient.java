package com.featuretoggle.sdk.java;

import com.featuretoggle.common.model.EvaluationDetail;
import com.featuretoggle.common.model.FeatureFlag;
import com.featuretoggle.common.model.UserContext;
import com.featuretoggle.sdk.core.evaluator.RuleEvaluator;
import com.featuretoggle.sdk.java.config.SdkProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main SDK client for feature toggle evaluation.
 * Supports local cache, remote sync, and annotation-driven evaluation.
 */
@Slf4j
public class FeatureToggleClient {
    
    private final SdkProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleEvaluator ruleEvaluator = new RuleEvaluator();
    
    /**
     * Local cache (L1) for feature flags
     */
    private final Map<String, FeatureFlag> flagCache = new ConcurrentHashMap<>();
    
    /**
     * Last known global version for incremental sync
     */
    private volatile Long lastKnownVersion = 0L;
    
    /**
     * Background scheduler for periodic sync
     */
    private ScheduledExecutorService scheduler;
    
    public FeatureToggleClient(SdkProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * Start the SDK client (initializes cache and starts background sync).
     * 
     * <p><b>Note:</b> This method is intended for <b>non-Spring</b> applications.
     * If you are using Spring Boot, the SDK is automatically started via 
     * {@link com.featuretoggle.sdk.java.config.FeatureToggleAutoConfiguration}.
     * Calling this method in Spring environment may cause duplicate sync threads.</p>
     */
    public void start() {
        log.info("Starting FeatureToggleClient for app: {}", properties.getAppKey());
        
        // Initial sync
        syncFromServer();
        
        // Start background sync
        if (properties.getPullInterval() > 0) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "feature-toggle-sync");
                t.setDaemon(true);
                return t;
            });
            
            scheduler.scheduleAtFixedRate(
                this::syncFromServer,
                properties.getPullInterval(),
                properties.getPullInterval(),
                TimeUnit.MILLISECONDS
            );
            
            log.info("Background sync scheduled every {}ms", properties.getPullInterval());
        }
    }
    
    /**
     * Stop the SDK client
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
    
    /**
     * Register a flag from annotation configuration.
     * Registers locally first, then posts to Dashboard for persistence.
     */
    public void registerFlag(FeatureFlag flag) {
        String flagKey = flag.getFlagKey();
        
        // Check if already registered
        if (flagCache.containsKey(flagKey)) {
            log.debug("Flag {} already registered, skipping", flagKey);
            return;
        }
        
        try {
            // Register locally first (to serve requests immediately)
            flagCache.put(flagKey, flag);
            log.debug("Registered flag locally: {}", flagKey);
            
            // Post to Dashboard for persistence
            publishFlagToDashboard(flag);
            
        } catch (Exception e) {
            log.error("Failed to register flag: {}", flagKey, e);
        }
    }
    
    /**
     * Publish flag to Dashboard server for persistence
     */
    private void publishFlagToDashboard(FeatureFlag flag) {
        try {
            String url = String.format("%s/api/client/register", properties.getServerUrl());
            
            Map<String, Object> request = Map.of(
                "appKey", properties.getAppKey(),
                "environment", properties.getEnvironment(),
                "flag", flag,
                "skipPublish", true  // Skip Redis Pub/Sub since we already cached locally
            );

            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                log.info("Published flag to Dashboard: {}", flag.getFlagKey());
            } else {
                log.warn("Dashboard rejected flag registration: {}", flag.getFlagKey());
            }
        } catch (Exception e) {
            log.error("Failed to publish flag to Dashboard: {}", flag.getFlagKey(), e);
        }
    }
    
    /**
     * Remove a flag from cache (when deleted)
     */
    public void removeFlag(String flagKey) {
        flagCache.remove(flagKey);
        log.debug("Removed flag from cache: {}", flagKey);
    }
    
    /**
     * Manually sync a specific flag from server.
     * 
     * <p><b>Usage Scenarios:</b>
     * <ul>
     *   <li>Non-Spring applications requiring manual flag synchronization</li>
     *   <li>Spring Beans needing on-demand sync for specific flags</li>
     *   <li>Debugging or testing individual flag updates</li>
     * </ul>
     * 
     * <p><b>Note:</b> For automatic synchronization, Spring Boot applications should rely on 
     * Pub/Sub notifications which trigger {@link #triggerFullSync()} automatically.
     * 
     * @param flagKey the flag key to sync
     */
    public void syncFlagFromServer(String flagKey) {
        try {
            String url = String.format("%s/api/client/flags/%s?appKey=%s&environment=%s",
                properties.getServerUrl(),
                flagKey,
                properties.getAppKey(),
                properties.getEnvironment());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                
                if (data != null) {
                    // Convert Map to FeatureFlag and cache it
                    FeatureFlag flag = objectMapper.convertValue(data, FeatureFlag.class);
                    flagCache.put(flagKey, flag);
                    log.debug("Synced and cached flag: {}", flagKey);
                }
            }
        } catch (Exception e) {
            log.error("Failed to sync flag from server: {}", flagKey, e);
        }
    }
    
    /**
     * Evaluate a flag for a user context
     */
    public EvaluationDetail evaluate(String flagKey, UserContext userContext) {
        FeatureFlag flag = flagCache.get(flagKey);
        
        if (flag == null) {
            log.warn("Flag not found in cache: {}, falling back to default", flagKey);
            return new EvaluationDetail(
                flagKey,
                false,
                "false",
                EvaluationDetail.EvaluationReason.DEFAULT,
                null,  // matchedRuleId
                java.util.UUID.randomUUID().toString(),  // traceId
                properties.getEnvironment(),
                userContext.getStringAttribute("region"),  // region from user context
                null,  // releaseVersion (unknown when flag not found)
                System.currentTimeMillis(),
                userContext.attributes(),  // userContextSnapshot
                null   // matchedConditions
            );
        }
        
        return ruleEvaluator.evaluate(flag, userContext);
    }
    
    /**
     * Check if a flag is enabled for a user
     */
    public boolean isEnabled(String flagKey, UserContext userContext) {
        return evaluate(flagKey, userContext).enabled();
    }
    
    /**
     * Trigger full sync from server (called when Pub/Sub message received)
     * Pulls all flags and replaces local cache completely
     */
    public void triggerFullSync() {
        try {
            log.info("Triggering full sync from server");
            
            // Reset version to 0 to force full sync
            lastKnownVersion = 0L;
            
            // Perform sync
            syncFromServer();
            
            log.info("Full sync completed, cached {} flags", flagCache.size());
        } catch (Exception e) {
            log.error("Failed to trigger full sync", e);
        }
    }
    
    /**
     * Sync flags from server (incremental or full)
     */
    private void syncFromServer() {
        try {
            String url = String.format("%s/api/client/configs?appKey=%s&lastKnownVersion=%s&environment=%s",
                properties.getServerUrl(),
                properties.getAppKey(),
                lastKnownVersion,
                properties.getEnvironment());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                
                if (data != null) {
                    Long globalVersion = ((Number) data.get("globalVersion")).longValue();
                    
                    if (globalVersion > lastKnownVersion) {
                        @SuppressWarnings("unchecked")
                        java.util.List<Map<String, Object>> flags = 
                            (java.util.List<Map<String, Object>>) data.get("flags");
                        
                        // Clear existing cache and replace with new data
                        flagCache.clear();
                        
                        if (flags != null && !flags.isEmpty()) {
                            for (Map<String, Object> flagMap : flags) {
                                FeatureFlag flag = objectMapper.convertValue(flagMap, FeatureFlag.class);
                                flagCache.put(flag.getFlagKey(), flag);
                                log.debug("Cached flag: {}", flag.getFlagKey());
                            }
                        }
                        
                        log.info("Full sync completed: {} flags cached", flagCache.size());
                        
                        lastKnownVersion = globalVersion;
                        log.info("Synced flags, new version: {}", globalVersion);
                    } else {
                        log.debug("No changes since version {}", lastKnownVersion);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to sync flags from server", e);
        }
    }
    
    /**
     * Get the flag cache (for testing/debugging)
     */
    public Map<String, FeatureFlag> getFlagCache() {
        return flagCache;
    }
}
