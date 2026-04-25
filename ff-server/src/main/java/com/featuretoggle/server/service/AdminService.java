package com.featuretoggle.server.service;

import com.featuretoggle.common.model.FeatureFlag;
import com.featuretoggle.common.model.Rule;
import com.featuretoggle.server.entity.App;
import com.featuretoggle.server.entity.FeatureFlagEntity;
import com.featuretoggle.server.entity.FlagRuleEntity;
import com.featuretoggle.server.repository.AppRepository;
import com.featuretoggle.server.repository.FeatureFlagRepository;
import com.featuretoggle.server.repository.FlagRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Admin service for managing feature flags via dashboard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {
    
    private final FeatureFlagRepository flagRepository;
    private final FlagRuleRepository ruleRepository;
    private final AppRepository appRepository;
    private final EvaluationService evaluationService;
    private final FlagCacheService flagCacheService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * Create a new feature flag with rules
     */
    @Transactional
    public FeatureFlag createFlag(String appKey, String environment, FeatureFlag flagRequest) {
        // Get app
        App app = appRepository.findByAppKey(appKey)
            .orElseThrow(() -> new RuntimeException("App not found: " + appKey));
        
        // Check if flag already exists
        Optional<FeatureFlagEntity> existing = flagRepository
            .findByAppIdAndEnvironmentAndFlagKey(app.getId(), environment, flagRequest.getFlagKey());
        
        if (existing.isPresent()) {
            throw new RuntimeException("Flag already exists: " + flagRequest.getFlagKey());
        }
        
        // Create flag entity
        FeatureFlagEntity flagEntity = FeatureFlagEntity.builder()
            .appId(app.getId())
            .flagKey(flagRequest.getFlagKey())
            .name(flagRequest.getName())
            .environment(environment)
            .status(1)
            .defaultValue(flagRequest.getDefaultValue())
            .version(1L)
            .build();
        
        flagEntity = flagRepository.save(flagEntity);
        
        // Remove null cache if exists (in case someone queried this flag before it was created)
        String cacheKey = "flag:" + appKey + ":" + environment + ":" + flagRequest.getFlagKey();
        redisTemplate.delete(cacheKey);
        log.debug("Removed null cache for newly created flag: {}", flagRequest.getFlagKey());
        
        // Save rules
        if (flagRequest.getRules() != null && !flagRequest.getRules().isEmpty()) {
            int priority = 1;
            for (Rule rule : flagRequest.getRules()) {
                try {
                    FlagRuleEntity ruleEntity = FlagRuleEntity.builder()
                        .flagId(flagEntity.getId())
                        .priority(priority++)
                        .conditions(objectMapper.writeValueAsString(rule))
                        .actionValue(rule.getActionValue())
                        .description("")
                        .build();
                    
                    ruleRepository.save(ruleEntity);
                } catch (Exception e) {
                    log.error("Failed to save rule", e);
                    throw new RuntimeException("Failed to save rule", e);
                }
            }
        }
        
        // Publish change to Redis
        evaluationService.publishChange(appKey, flagRequest.getFlagKey(), environment, flagEntity.getVersion());
        
        log.info("Created flag: {} for app: {}", flagRequest.getFlagKey(), appKey);
        
        return loadFlagWithRules(flagEntity);
    }
    
    /**
     * Update an existing feature flag
     */
    @Transactional
    public FeatureFlag updateFlag(String appKey, String environment, String flagKey, FeatureFlag flagRequest) {
        // Get app
        App app = appRepository.findByAppKey(appKey)
            .orElseThrow(() -> new RuntimeException("App not found: " + appKey));
        
        // Get flag
        FeatureFlagEntity flagEntity = flagRepository
            .findByAppIdAndEnvironmentAndFlagKey(app.getId(), environment, flagKey)
            .orElseThrow(() -> new RuntimeException("Flag not found: " + flagKey));
        
        // Update flag fields
        flagEntity.setName(flagRequest.getName());
        flagEntity.setDefaultValue(flagRequest.getDefaultValue());
        flagEntity.setStatus(flagRequest.getStatus());
        flagEntity.setVersion(flagEntity.getVersion() + 1);
        
        flagEntity = flagRepository.save(flagEntity);
        
        // Delete old rules
        ruleRepository.deleteByFlagId(flagEntity.getId());
        
        // Save new rules
        if (flagRequest.getRules() != null && !flagRequest.getRules().isEmpty()) {
            int priority = 1;
            for (Rule rule : flagRequest.getRules()) {
                try {
                    FlagRuleEntity ruleEntity = FlagRuleEntity.builder()
                        .flagId(flagEntity.getId())
                        .priority(priority++)
                        .conditions(objectMapper.writeValueAsString(rule))
                        .actionValue(rule.getActionValue())
                        .description("")
                        .build();
                    
                    ruleRepository.save(ruleEntity);
                } catch (Exception e) {
                    log.error("Failed to save rule", e);
                    throw new RuntimeException("Failed to save rule", e);
                }
            }
        }
        
        // Publish change to Redis
        evaluationService.publishChange(appKey, flagKey, environment, flagEntity.getVersion());
        
        log.info("Updated flag: {} for app: {}", flagKey, appKey);
        
        return loadFlagWithRules(flagEntity);
    }
    
    /**
     * Delete a feature flag (hard delete)
     */
    @Transactional
    public void deleteFlag(String appKey, String environment, String flagKey) {
        // Get app
        App app = appRepository.findByAppKey(appKey)
            .orElseThrow(() -> new RuntimeException("App not found: " + appKey));
        
        // Get flag
        FeatureFlagEntity flagEntity = flagRepository
            .findByAppIdAndEnvironmentAndFlagKey(app.getId(), environment, flagKey)
            .orElseThrow(() -> new RuntimeException("Flag not found: " + flagKey));
        
        Long version = flagEntity.getVersion();
        
        // Delete rules first
        ruleRepository.deleteByFlagId(flagEntity.getId());
        
        // Delete flag
        flagRepository.delete(flagEntity);
        
        // Publish deletion to Redis (optimized - no extra DB query)
        evaluationService.publishDelete(appKey, flagKey, environment, version);
        
        log.info("Deleted flag: {} for app: {}", flagKey, appKey);
    }
    
    /**
     * Get a feature flag by key
     */
    public FeatureFlag getFlag(String appKey, String environment, String flagKey) {
        // Try Redis cache first
        FeatureFlag cachedFlag = flagCacheService.getFromCache(appKey, flagKey, environment);
        
        // Check if we have a cached value (including null marker)
        if (flagCacheService.hasFlagInCache(appKey, flagKey, environment)) {
            log.debug("Returning from cache (may be null): {}", flagKey);
            return cachedFlag; // Could be null if cached as null
        }
        
        // Cache miss, load from database
        // Get app
        App app = appRepository.findByAppKey(appKey)
            .orElseThrow(() -> new RuntimeException("App not found: " + appKey));
        
        // Get flag
        Optional<FeatureFlagEntity> flagEntityOpt = flagRepository
            .findByAppIdAndEnvironmentAndFlagKey(app.getId(), environment, flagKey);
        
        if (flagEntityOpt.isEmpty()) {
            // Flag doesn't exist - cache null to prevent cache penetration
            flagCacheService.saveNullToCache(appKey, flagKey, environment);
            log.debug("Flag not found, cached null value: {}", flagKey);
            return null;
        }
        
        FeatureFlag flag = loadFlagWithRules(flagEntityOpt.get());
        
        // Save to cache
        flagCacheService.saveToCache(appKey, flag, environment);
        
        return flag;
    }
    
    /**
     * List all flags for an app and environment
     */
    public List<FeatureFlag> listFlags(String appKey, String environment) {
        // Try Redis cache first
        List<FeatureFlag> cachedFlags = flagCacheService.getAppFlagsFromCache(appKey, environment);
        
        if (cachedFlags != null && !cachedFlags.isEmpty()) {
            log.debug("Returning {} flags from cache for app: {}, environment: {}", 
                cachedFlags.size(), appKey, environment);
            return cachedFlags;
        }
        
        // Cache miss, load from database
        // Get app
        App app = appRepository.findByAppKey(appKey)
            .orElseThrow(() -> new RuntimeException("App not found: " + appKey));
        
        // Get all flags
        List<FeatureFlagEntity> flagEntities = flagRepository.findByAppIdAndEnvironment(app.getId(), environment);
        
        // Convert to FeatureFlag with rules
        List<FeatureFlag> flags = new ArrayList<>();
        for (FeatureFlagEntity entity : flagEntities) {
            flags.add(loadFlagWithRules(entity));
        }
        
        // Save to cache
        if (!flags.isEmpty()) {
            flagCacheService.saveAppFlagsToCache(appKey, flags, environment);
        }
        
        return flags;
    }
    
    /**
     * Load flag with its rules from database
     */
    private FeatureFlag loadFlagWithRules(FeatureFlagEntity entity) {
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
}
