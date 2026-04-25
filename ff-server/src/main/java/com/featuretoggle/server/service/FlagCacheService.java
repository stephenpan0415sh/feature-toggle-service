package com.featuretoggle.server.service;

import com.featuretoggle.common.model.FeatureFlag;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis cache service for feature flags with multi-level caching strategy
 * 
 * Cache Structure:
 * - flag:{appKey}:{environment}:{flagKey} -> JSON (Individual flag)
 * - app:flags:{appKey}:{environment} -> JSON (All flags for an app)
 * - app:version:{appKey}:{environment} -> Long (Global version for incremental sync)
 * - flag:version:{appKey}:{environment}:{flagKey} -> Long (Individual flag version)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlagCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Cache key prefixes
    private static final String FLAG_CACHE_KEY_PREFIX = "flag:";
    private static final String APP_FLAGS_KEY_PREFIX = "app:flags:";
    private static final String APP_VERSION_KEY_PREFIX = "app:version:";
    private static final String FLAG_VERSION_KEY_PREFIX = "flag:version:";
    
    // TTL configuration
    private static final long CACHE_TTL_HOURS = 24;
    private static final long VERSION_TTL_HOURS = 48;
    private static final long NULL_CACHE_TTL_MINUTES = 5; // Cache null values for 5 minutes to prevent cache penetration

    /**
     * Get flag from Redis cache
     * Returns special marker object if null value is cached (cache penetration protection)
     */
    public FeatureFlag getFromCache(String appKey, String flagKey, String environment) {
        try {
            String cacheKey = buildCacheKey(appKey, flagKey, environment);
            String json = redisTemplate.opsForValue().get(cacheKey);
            
            if (json != null) {
                // Check if it's a cached null value
                if ("__NULL__".equals(json)) {
                    log.debug("Cache hit for null value: {}", cacheKey);
                    return null; // Return null but we know it was cached
                }
                
                log.debug("Cache hit for flag: {}", cacheKey);
                return objectMapper.readValue(json, FeatureFlag.class);
            }
            
            log.debug("Cache miss for flag: {}", cacheKey);
            return null;
        } catch (Exception e) {
            log.error("Error reading from cache", e);
            return null;
        }
    }

    /**
     * Save flag to Redis cache with version tracking
     */
    public void saveToCache(String appKey, FeatureFlag flag, String environment) {
        try {
            String cacheKey = buildCacheKey(appKey, flag.getFlagKey(), environment);
            String json = objectMapper.writeValueAsString(flag);
            
            // Save flag data
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
            
            // Update version info for incremental sync
            if (flag.getVersion() != null) {
                String versionKey = buildVersionKey(appKey, flag.getFlagKey(), environment);
                redisTemplate.opsForValue().set(versionKey, String.valueOf(flag.getVersion()), 
                    VERSION_TTL_HOURS, TimeUnit.HOURS);
            }
            
            log.debug("Saved flag to cache: {}, version={}", cacheKey, flag.getVersion());
        } catch (Exception e) {
            log.error("Error saving to cache", e);
        }
    }

    /**
     * Cache a null value to prevent cache penetration
     * Called when flag doesn't exist in database
     */
    public void saveNullToCache(String appKey, String flagKey, String environment) {
        try {
            String cacheKey = buildCacheKey(appKey, flagKey, environment);
            
            // Cache special marker for null value with short TTL
            redisTemplate.opsForValue().set(cacheKey, "__NULL__", 
                NULL_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            
            log.debug("Cached null value for flag: {} (TTL: {} min)", cacheKey, NULL_CACHE_TTL_MINUTES);
        } catch (Exception e) {
            log.error("Error caching null value", e);
        }
    }

    /**
     * Invalidate flag cache and update global version
     */
    public void invalidateCache(String appKey, String flagKey, String environment) {
        try {
            String cacheKey = buildCacheKey(appKey, flagKey, environment);
            String versionKey = buildVersionKey(appKey, flagKey, environment);
            
            redisTemplate.delete(cacheKey);
            redisTemplate.delete(versionKey);
            
            // Increment global version to signal change
            incrementGlobalVersion(appKey, environment);
            
            log.info("Invalidated cache for flag: {}", cacheKey);
        } catch (Exception e) {
            log.error("Error invalidating cache", e);
        }
    }

    /**
     * Get flag version from cache
     */
    public Long getFlagVersion(String appKey, String flagKey, String environment) {
        try {
            String versionKey = buildVersionKey(appKey, flagKey, environment);
            String version = redisTemplate.opsForValue().get(versionKey);
            return version != null ? Long.parseLong(version) : null;
        } catch (Exception e) {
            log.error("Error reading flag version from cache", e);
            return null;
        }
    }

    /**
     * Get all flag versions for an app (for incremental sync)
     * Returns map of flagKey -> version
     * Extracts versions from cached app flags list
     */
    public Map<String, Long> getAllFlagVersions(String appKey, String environment) {
        try {
            // Get all flags from cache
            List<FeatureFlag> flags = getAppFlagsFromCache(appKey, environment);
            
            if (flags == null || flags.isEmpty()) {
                return Map.of();
            }
            
            // Extract versions from flags
            Map<String, Long> versionMap = new java.util.HashMap<>();
            for (FeatureFlag flag : flags) {
                if (flag.getVersion() != null) {
                    versionMap.put(flag.getFlagKey(), flag.getVersion());
                }
            }
            
            log.debug("Retrieved {} flag versions for app: {}", versionMap.size(), appKey);
            return versionMap;
        } catch (Exception e) {
            log.error("Error reading flag versions from cache", e);
            return Map.of();
        }
    }

    /**
     * Get global version for an app and environment
     */
    public Long getGlobalVersion(String appKey, String environment) {
        try {
            String versionKey = buildGlobalVersionKey(appKey, environment);
            String version = redisTemplate.opsForValue().get(versionKey);
            return version != null ? Long.parseLong(version) : 0L;
        } catch (Exception e) {
            log.error("Error reading global version from cache", e);
            return 0L;
        }
    }

    /**
     * Increment global version when any flag changes
     */
    private void incrementGlobalVersion(String appKey, String environment) {
        try {
            String versionKey = buildGlobalVersionKey(appKey, environment);
            Long newVersion = redisTemplate.opsForValue().increment(versionKey);
            
            // Set TTL if key is new
            if (newVersion != null && newVersion == 1) {
                redisTemplate.expire(versionKey, VERSION_TTL_HOURS, TimeUnit.HOURS);
            }
            
            log.debug("Incremented global version for app: {}, new version: {}", appKey, newVersion);
        } catch (Exception e) {
            log.error("Error incrementing global version", e);
        }
    }

    /**
     * Get all flags for an app from cache
     */
    public List<FeatureFlag> getAppFlagsFromCache(String appKey, String environment) {
        try {
            String cacheKey = APP_FLAGS_KEY_PREFIX + appKey + ":" + environment;
            String json = redisTemplate.opsForValue().get(cacheKey);
            
            if (json != null) {
                log.debug("Cache hit for app flags: {}", cacheKey);
                return objectMapper.readValue(json, 
                    new com.fasterxml.jackson.core.type.TypeReference<List<FeatureFlag>>() {});
            }
            
            return null;
        } catch (Exception e) {
            log.error("Error reading app flags from cache", e);
            return null;
        }
    }

    /**
     * Save all flags for an app to cache with version tracking
     */
    public void saveAppFlagsToCache(String appKey, List<FeatureFlag> flags, String environment) {
        try {
            String cacheKey = APP_FLAGS_KEY_PREFIX + appKey + ":" + environment;
            String json = objectMapper.writeValueAsString(flags);
            
            // Save flags list
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
            
            // Update individual flag versions
            for (FeatureFlag flag : flags) {
                if (flag.getVersion() != null) {
                    String versionKey = buildVersionKey(appKey, flag.getFlagKey(), environment);
                    redisTemplate.opsForValue().set(versionKey, String.valueOf(flag.getVersion()), 
                        VERSION_TTL_HOURS, TimeUnit.HOURS);
                }
            }
            
            // Update global version
            Long maxVersion = flags.stream()
                .map(FeatureFlag::getVersion)
                .filter(v -> v != null)
                .max(Long::compareTo)
                .orElse(0L);
            
            if (maxVersion > 0) {
                String globalVersionKey = buildGlobalVersionKey(appKey, environment);
                redisTemplate.opsForValue().set(globalVersionKey, String.valueOf(maxVersion), 
                    VERSION_TTL_HOURS, TimeUnit.HOURS);
            }
            
            log.debug("Saved app flags to cache: {}, count={}, globalVersion={}", 
                cacheKey, flags.size(), maxVersion);
        } catch (Exception e) {
            log.error("Error saving app flags to cache", e);
        }
    }

    /**
     * Update app flags cache by refreshing a single flag in the cached list
     * Used when a flag changes - updates the app-level cache instead of deleting it
     */
    public void updateAppFlagsToCache(String appKey, FeatureFlag updatedFlag, String environment) {
        try {
            String cacheKey = APP_FLAGS_KEY_PREFIX + appKey + ":" + environment;
            String json = redisTemplate.opsForValue().get(cacheKey);
            
            List<FeatureFlag> flags;
            if (json != null) {
                // Load existing list and update the flag
                flags = objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<FeatureFlag>>() {});
                
                // Find and replace the flag
                boolean found = false;
                for (int i = 0; i < flags.size(); i++) {
                    if (flags.get(i).getFlagKey().equals(updatedFlag.getFlagKey())) {
                        flags.set(i, updatedFlag);
                        found = true;
                        break;
                    }
                }
                
                // If not found, add it (new flag)
                if (!found) {
                    flags.add(updatedFlag);
                }
            } else {
                // No cached list, load from DB will happen on next request
                log.warn("App flags cache not found, skipping update for app: {}", appKey);
                return;
            }
            
            // Save updated list
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(flags), 
                CACHE_TTL_HOURS, TimeUnit.HOURS);
            
            // Update individual flag version
            if (updatedFlag.getVersion() != null) {
                String versionKey = buildVersionKey(appKey, updatedFlag.getFlagKey(), environment);
                redisTemplate.opsForValue().set(versionKey, String.valueOf(updatedFlag.getVersion()), 
                    VERSION_TTL_HOURS, TimeUnit.HOURS);
            }
            
            // Update global version
            incrementGlobalVersion(appKey, environment);
            
            log.info("Updated app flags cache for app: {}, flag: {}", appKey, updatedFlag.getFlagKey());
        } catch (Exception e) {
            log.error("Error updating app flags cache", e);
        }
    }

    /**
     * Invalidate all flags cache for an app
     */
    public void invalidateAppCache(String appKey, String environment) {
        try {
            String cacheKey = APP_FLAGS_KEY_PREFIX + appKey + ":" + environment;
            redisTemplate.delete(cacheKey);
            
            // Note: Individual flag versions are kept for incremental sync
            // Global version will be incremented when flags are updated
            
            log.info("Invalidated app flags cache: {}", cacheKey);
        } catch (Exception e) {
            log.error("Error invalidating app cache", e);
        }
    }

    /**
     * Check if flag exists in cache
     */
    public boolean hasFlagInCache(String appKey, String flagKey, String environment) {
        try {
            String cacheKey = buildCacheKey(appKey, flagKey, environment);
            return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey));
        } catch (Exception e) {
            log.error("Error checking cache existence", e);
            return false;
        }
    }

    /**
     * Get cache statistics for monitoring
     */
    public Map<String, Object> getCacheStats(String appKey, String environment) {
        try {
            List<FeatureFlag> flags = getAppFlagsFromCache(appKey, environment);
            int flagCount = flags != null ? flags.size() : 0;
            
            return Map.of(
                "appKey", appKey,
                "environment", environment,
                "cachedFlags", flagCount,
                "trackedVersions", flagCount,
                "globalVersion", getGlobalVersion(appKey, environment)
            );
        } catch (Exception e) {
            log.error("Error getting cache stats", e);
            return Map.of("error", e.getMessage());
        }
    }

    // Helper methods for building cache keys
    private String buildCacheKey(String appKey, String flagKey, String environment) {
        return FLAG_CACHE_KEY_PREFIX + appKey + ":" + environment + ":" + flagKey;
    }

    private String buildVersionKey(String appKey, String flagKey, String environment) {
        return FLAG_VERSION_KEY_PREFIX + appKey + ":" + environment + ":" + flagKey;
    }

    private String buildGlobalVersionKey(String appKey, String environment) {
        return APP_VERSION_KEY_PREFIX + appKey + ":" + environment;
    }
}
