package com.featuretoggle.server.service;

import com.featuretoggle.common.model.EvaluationDetail;
import com.featuretoggle.common.model.FeatureFlag;
import com.featuretoggle.common.model.Rule;
import com.featuretoggle.common.model.UserContext;
import com.featuretoggle.sdk.core.evaluator.RuleEvaluator;
import com.featuretoggle.server.entity.App;
import com.featuretoggle.server.entity.FeatureFlagEntity;
import com.featuretoggle.server.entity.FlagRuleEntity;
import com.featuretoggle.server.repository.AppRepository;
import com.featuretoggle.server.repository.FeatureFlagRepository;
import com.featuretoggle.server.repository.FlagRuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core service for feature flag evaluation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final FeatureFlagRepository flagRepository;
    private final FlagRuleRepository ruleRepository;
    private final AppRepository appRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final FlagCacheService flagCacheService;
    private final com.featuretoggle.server.config.MetricsCollector metricsCollector;
    private final RuleEvaluator ruleEvaluator = new RuleEvaluator();

    /**
     * Evaluate a single feature flag
     */
    public EvaluationDetail evaluateFlag(String appKey, String flagKey, UserContext userContext, String environment) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        
        try {
            // Try Redis cache first
            FeatureFlag flag = flagCacheService.getFromCache(appKey, flagKey, environment);
            
            // Check if we have a cached value (including null marker)
            boolean hasCache = flagCacheService.hasFlagInCache(appKey, flagKey, environment);
            
            if (!hasCache) {
                // Cache miss, load from database
                metricsCollector.recordCacheMiss(appKey, environment);
                flag = loadFlagWithRules(appKey, flagKey, environment);
                
                if (flag != null) {
                    // Save to cache
                    flagCacheService.saveToCache(appKey, flag, environment);
                } else {
                    // Flag doesn't exist - cache null to prevent cache penetration
                    flagCacheService.saveNullToCache(appKey, flagKey, environment);
                }
            } else if (flag == null) {
                // Cached null value - flag doesn't exist
                log.debug("Using cached null value for flag: {}", flagKey);
                metricsCollector.recordCacheHit(appKey, environment);
            } else {
                metricsCollector.recordCacheHit(appKey, environment);
            }
            
            if (flag == null) {
                log.warn("Flag not found: appKey={}, flagKey={}, environment={}", appKey, flagKey, environment);
                return buildNotFoundDetail(flagKey, environment);
            }

            // Evaluate
            EvaluationDetail detail = ruleEvaluator.evaluate(flag, userContext);
            success = detail.isSuccess();
            return detail;
            
        } catch (Exception e) {
            log.error("Error evaluating flag: {}", flagKey, e);
            return buildErrorDetail(flagKey, environment);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.recordEvaluation(appKey, flagKey, success, duration);
        }
    }

    /**
     * Batch evaluate multiple feature flags
     */
    public Map<String, EvaluationDetail> batchEvaluate(
            String appKey, List<String> flagKeys, UserContext userContext, String environment) {
        
        Map<String, EvaluationDetail> results = new java.util.HashMap<>();
        
        for (String flagKey : flagKeys) {
            try {
                EvaluationDetail detail = evaluateFlag(appKey, flagKey, userContext, environment);
                results.put(flagKey, detail);
            } catch (Exception e) {
                log.error("Error evaluating flag in batch: {}", flagKey, e);
                results.put(flagKey, buildErrorDetail(flagKey, environment));
            }
        }
        
        return results;
    }

    /**
     * Get all flags for SDK initialization (with version info)
     * Supports incremental sync based on lastKnownVersion
     */
    public List<FeatureFlag> getAllFlags(String appKey, String environment) {
        return getAllFlagsIncremental(appKey, environment, null);
    }

    /**
     * Get changed flags since last known version (incremental sync)
     * Returns only flags that have been updated since the specified version
     */
    public List<FeatureFlag> getAllFlagsIncremental(String appKey, String environment, Long lastKnownVersion) {
        try {
            // Try Redis cache first
            List<FeatureFlag> flags = flagCacheService.getAppFlagsFromCache(appKey, environment);
            
            if (flags != null && lastKnownVersion == null) {
                // Full sync from cache
                return flags;
            }
            
            // Check global version for incremental sync
            Long currentGlobalVersion = flagCacheService.getGlobalVersion(appKey, environment);
            
            if (lastKnownVersion != null && currentGlobalVersion <= lastKnownVersion) {
                // No changes since last known version
                log.debug("No changes since version {}, returning empty list", lastKnownVersion);
                return List.of();
            }
            
            // Cache miss or changes detected, load from database
            // Get app by appKey
            App app = appRepository.findByAppKey(appKey)
                .orElseThrow(() -> new RuntimeException("App not found: " + appKey));
            
            // Get all flags for this app and environment
            List<FeatureFlagEntity> flagEntities = flagRepository.findByAppIdAndEnvironment(
                app.getId(), environment);
            
            // Convert to FeatureFlag with rules
            flags = new ArrayList<>();
            for (FeatureFlagEntity entity : flagEntities) {
                // For incremental sync, only include changed flags
                if (lastKnownVersion == null || entity.getVersion() > lastKnownVersion) {
                    FeatureFlag flag = convertToFeatureFlag(entity);
                    flags.add(flag);
                }
            }
            
            // Save to cache
            if (!flags.isEmpty()) {
                flagCacheService.saveAppFlagsToCache(appKey, flags, environment);
            }
            
            log.info("Retrieved {} flags for app: {}, environment: {}, lastKnownVersion: {}", 
                flags.size(), appKey, environment, lastKnownVersion);
            
            return flags;
        } catch (Exception e) {
            log.error("Error getting all flags for app: {}", appKey, e);
            return List.of();
        }
    }

    /**
     * Get flag versions map for incremental sync comparison
     * Returns map of flagKey -> version
     */
    public Map<String, Long> getFlagVersions(String appKey, String environment) {
        try {
            // Try cache first
            Map<String, Long> cachedVersions = flagCacheService.getAllFlagVersions(appKey, environment);
            if (!cachedVersions.isEmpty()) {
                return cachedVersions;
            }
            
            // Load from database
            App app = appRepository.findByAppKey(appKey)
                .orElseThrow(() -> new RuntimeException("App not found: " + appKey));
            
            List<FeatureFlagEntity> flagEntities = flagRepository.findByAppIdAndEnvironment(
                app.getId(), environment);
            
            Map<String, Long> versionMap = new java.util.HashMap<>();
            for (FeatureFlagEntity entity : flagEntities) {
                versionMap.put(entity.getFlagKey(), entity.getVersion());
            }
            
            return versionMap;
        } catch (Exception e) {
            log.error("Error getting flag versions for app: {}", appKey, e);
            return Map.of();
        }
    }

    /**
     * Get cache statistics for monitoring and observability
     */
    public Map<String, Object> getCacheStats(String appKey, String environment) {
        Map<String, Object> stats = flagCacheService.getCacheStats(appKey, environment);
        
        // Update metrics gauge
        if (stats.containsKey("cachedFlags")) {
            int cachedFlags = ((Number) stats.get("cachedFlags")).intValue();
            metricsCollector.updateCachedFlagsGauge(appKey, environment, cachedFlags);
        }
        
        return stats;
    }

    /**
     * Publish configuration change to Redis for SDK clients to subscribe
     * Called by admin API after saving flag to database
     * Refreshes Redis cache and notifies clients to update their local memory
     */
    public void publishChange(String appKey, String flagKey, String environment, Long version) {
        try {
            // 1. Load latest data from DB (already saved by admin API)
            FeatureFlag updatedFlag = loadFlagWithRules(appKey, flagKey, environment);
            
            if (updatedFlag != null) {
                // Update or create flag
                flagCacheService.saveToCache(appKey, updatedFlag, environment);
                flagCacheService.updateAppFlagsToCache(appKey, updatedFlag, environment);
                log.info("Refreshed Redis cache for flag: {}", flagKey);
            } else {
                // Flag deleted - remove from cache
                flagCacheService.invalidateCache(appKey, flagKey, environment);
                log.info("Removed deleted flag from cache: {}", flagKey);
            }
            
            // 2. Publish message to app-specific channel for SDK clients
            String channel = "feature_flag_changes:" + appKey;
            String message = String.format(
                "{\"flagKey\":\"%s\",\"environment\":\"%s\",\"version\":%d,\"deleted\":%s}",
                flagKey, environment, version, updatedFlag == null);
            
            redisTemplate.convertAndSend(channel, message);
            log.info("Published config change to channel {}: {}", channel, message);
        } catch (Exception e) {
            log.error("Failed to publish config change", e);
        }
    }

    /**
     * Publish flag deletion to Redis for SDK clients to subscribe
     * Called by admin API after deleting flag from database
     * Removes flag from Redis cache and notifies clients to remove from their local memory
     */
    public void publishDelete(String appKey, String flagKey, String environment, Long version) {
        try {
            // 1. Remove from Redis cache directly (no DB query needed)
            flagCacheService.invalidateCache(appKey, flagKey, environment);
            log.info("Removed deleted flag from cache: {}", flagKey);
            
            // 2. Publish deletion message to app-specific channel for SDK clients
            String channel = "feature_flag_changes:" + appKey;
            String message = String.format(
                "{\"flagKey\":\"%s\",\"environment\":\"%s\",\"version\":%d,\"deleted\":true}",
                flagKey, environment, version);
            
            redisTemplate.convertAndSend(channel, message);
            log.info("Published flag deletion to channel {}: {}", channel, message);
        } catch (Exception e) {
            log.error("Failed to publish flag deletion", e);
        }
    }

    /**
     * Load feature flag with its rules from database
     */
    private FeatureFlag loadFlagWithRules(String appKey, String flagKey, String environment) {
        try {
            // Get app by appKey
            App app = appRepository.findByAppKey(appKey)
                .orElseThrow(() -> new RuntimeException("App not found: " + appKey));
            
            // Get flag
            FeatureFlagEntity entity = flagRepository
                .findByAppIdAndEnvironmentAndFlagKey(app.getId(), environment, flagKey)
                .orElse(null);
            
            if (entity == null) {
                return null;
            }
            
            return convertToFeatureFlag(entity);
        } catch (Exception e) {
            log.error("Error loading flag: appKey={}, flagKey={}", appKey, flagKey, e);
            return null;
        }
    }

    /**
     * Convert FeatureFlagEntity to FeatureFlag with rules
     */
    private FeatureFlag convertToFeatureFlag(FeatureFlagEntity entity) {
        try {
            // Load rules
            List<FlagRuleEntity> ruleEntities = ruleRepository.findByFlagIdOrderByPriorityAsc(entity.getId());
            
            // Convert rules
            List<Rule> rules = new ArrayList<>();
            for (FlagRuleEntity ruleEntity : ruleEntities) {
                Rule rule = objectMapper.readValue(ruleEntity.getConditions(), Rule.class);
                rule.setId("rule_" + ruleEntity.getId());
                rule.setPriority(ruleEntity.getPriority());
                rule.setActionValue(ruleEntity.getActionValue());
                rule.setDescription(ruleEntity.getDescription());
                rules.add(rule);
            }
            
            // Build FeatureFlag
            return FeatureFlag.builder()
                .id(entity.getId())
                .appId(entity.getAppId())
                .flagKey(entity.getFlagKey())
                .name(entity.getName())
                .environment(entity.getEnvironment())
                .status(entity.getStatus())
                .defaultValue(entity.getDefaultValue())
                .version(entity.getVersion())
                .rules(rules)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
        } catch (Exception e) {
            log.error("Error converting flag entity: {}", entity.getId(), e);
            return FeatureFlag.builder()
                .id(entity.getId())
                .flagKey(entity.getFlagKey())
                .build();
        }
    }

    /**
     * Build EvaluationDetail for not found flag
     */
    private EvaluationDetail buildNotFoundDetail(String flagKey, String environment) {
        return new EvaluationDetail(
            flagKey,
            false,
            "false",
            EvaluationDetail.EvaluationReason.DEFAULT,
            null,
            java.util.UUID.randomUUID().toString(),
            environment,
            null,  // region
            null,  // releaseVersion
            System.currentTimeMillis(),
            null,
            null
        );
    }

    /**
     * Build EvaluationDetail for error case
     */
    private EvaluationDetail buildErrorDetail(String flagKey, String environment) {
        return new EvaluationDetail(
            flagKey,
            false,
            "false",
            EvaluationDetail.EvaluationReason.ERROR,
            null,
            java.util.UUID.randomUUID().toString(),
            environment,
            null,  // region
            null,  // releaseVersion
            System.currentTimeMillis(),
            null,
            null
        );
    }
}
