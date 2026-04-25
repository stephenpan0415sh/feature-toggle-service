# Redis Cache Implementation Guide

## Overview

The Feature Toggle Service implements a multi-level caching strategy using Redis to achieve high throughput and low latency for feature flag evaluations. This document describes the cache architecture, key structures, and usage patterns.

## Architecture

### Cache Levels

| Level | Location | Technology | TTL | Purpose |
|-------|----------|------------|-----|---------|
| L1 | SDK Memory | ConcurrentHashMap/Caffeine | Permanent (until update) | Primary read path, zero network overhead |
| L1.5 | SDK Disk | File/SQLite/LocalStorage | Persistent | Fast startup, avoid cold start delay |
| L2 | Server Redis | Redis String/Hash | 24 hours | Secondary fallback, incremental sync |
| L3 | Database | MySQL | - | Source of Truth, write operations only |

### Cache Update Flow

1. **Active Push (Server-side SDK)**: Admin changes → Redis Pub/Sub → SDK updates L1 + persists to L1.5
2. **Passive Invalidation (Web/Mobile SDK)**: Periodic polling compares version → pulls only changed flags (incremental sync)
3. **Anti-loss Mechanism**: All SDKs perform full version comparison every 5 minutes to prevent inconsistency from message loss

## Redis Key Structure

### 1. Individual Flag Cache
```
Key: flag:{appKey}:{environment}:{flagKey}
Value: JSON serialized FeatureFlag object
TTL: 24 hours
Example: flag:ecommerce-web:prod:new_checkout_flow
```

### 2. App Flags List Cache
```
Key: app:flags:{appKey}:{environment}
Value: JSON array of FeatureFlag objects
TTL: 24 hours
Example: app:flags:ecommerce-web:prod
```

### 3. Individual Flag Version
```
Key: flag:version:{appKey}:{environment}:{flagKey}
Value: Long (version number)
TTL: 48 hours
Example: flag:version:ecommerce-web:prod:new_checkout_flow -> 15
```

### 4. Global Version Tracker
```
Key: app:version:{appKey}:{environment}
Value: Long (max version across all flags)
TTL: 48 hours
Example: app:version:ecommerce-web:prod -> 156
```

### 5. Pub/Sub Channel
```
Channel: feature_flag_changes (configurable)
Message Format: {"appKey":"xxx","flagKey":"yyy","environment":"prod","version":15}
```

## Core Components

### FlagCacheService

Main service for Redis cache operations:

```java
@Service
public class FlagCacheService {
    // Get individual flag from cache
    public FeatureFlag getFromCache(String appKey, String flagKey, String environment)
    
    // Save flag with version tracking
    public void saveToCache(String appKey, FeatureFlag flag, String environment)
    
    // Invalidate cache and increment global version
    public void invalidateCache(String appKey, String flagKey, String environment)
    
    // Get flag version for incremental sync
    public Long getFlagVersion(String appKey, String flagKey, String environment)
    
    // Get all flag versions for an app
    public Map<String, Long> getAllFlagVersions(String appKey, String environment)
    
    // Get global version
    public Long getGlobalVersion(String appKey, String environment)
    
    // Get/Savе all flags for an app
    public List<FeatureFlag> getAppFlagsFromCache(String appKey, String environment)
    public void saveAppFlagsToCache(String appKey, List<FeatureFlag> flags, String environment)
    
    // Cache statistics for monitoring
    public Map<String, Object> getCacheStats(String appKey, String environment)
}
```

### EvaluationService

Core evaluation logic with cache integration:

```java
@Service
public class EvaluationService {
    // Single flag evaluation with cache
    public EvaluationDetail evaluateFlag(String appKey, String flagKey, UserContext userContext, String environment)
    
    // Batch evaluation
    public Map<String, EvaluationDetail> batchEvaluate(String appKey, List<String> flagKeys, UserContext userContext, String environment)
    
    // Full sync - get all flags
    public List<FeatureFlag> getAllFlags(String appKey, String environment)
    
    // Incremental sync - get only changed flags
    public List<FeatureFlag> getAllFlagsIncremental(String appKey, String environment, Long lastKnownVersion)
    
    // Get flag versions for client comparison
    public Map<String, Long> getFlagVersions(String appKey, String environment)
    
    // Publish config change to Redis
    public void publishChange(String appKey, String flagKey, String environment, Long version)
    
    // Cache statistics
    public Map<String, Object> getCacheStats(String appKey, String environment)
}
```

### FeatureFlagChangeListener

Redis Pub/Sub message listener:

```java
@Component
public class FeatureFlagChangeListener implements MessageListener {
    @Override
    public void onMessage(Message message, byte[] pattern) {
        // Parse change notification
        // Log and trigger additional processing
        // (Cache invalidation already done by EvaluationService.publishChange)
    }
}
```

### CacheWarmingService

Preloads frequently accessed data on startup:

```java
@Service
public class CacheWarmingService {
    // Automatically runs after application ready
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmUpCache() {
        // Load all apps' flags into Redis cache
    }
    
    // Manual trigger for specific app
    public void warmUpAppCacheManual(String appKey, String environment)
}
```

## API Endpoints

### 1. Single Flag Evaluation
```http
GET /api/client/flags/{flagKey}?appKey=xxx&userId=123&region=cn-east&environment=prod
```

**Response:**
```json
{
  "success": true,
  "data": {
    "flagKey": "new_checkout",
    "enabled": true,
    "value": "v2",
    "reason": "MATCHED_RULE",
    "matchedRuleId": "rule_001",
    "environment": "prod",
    "traceId": "trace_abc123"
  }
}
```

### 2. Batch Evaluation
```http
POST /api/client/evaluate
Content-Type: application/json

{
  "appKey": "ecommerce-web",
  "userId": "123",
  "attributes": {
    "region": "cn-east",
    "deviceType": "ios",
    "vipLevel": 2
  },
  "flagKeys": ["new_checkout", "free_shipping", "dark_mode"]
}
```

### 3. Full Configuration Pull (SDK Initialization)
```http
GET /api/client/configs?appKey=xxx&lastKnownVersion=123&environment=prod
```

**Response (with changes):**
```json
{
  "success": true,
  "data": {
    "globalVersion": 456,
    "flags": [
      {
        "flagKey": "new_checkout",
        "version": 12,
        "status": 1,
        "defaultValue": "false",
        "rules": [...]
      }
    ],
    "deletedFlags": [],
    "hasChanges": true
  }
}
```

**Response (no changes):**
```json
{
  "success": true,
  "data": {
    "globalVersion": 456,
    "flags": [],
    "deletedFlags": [],
    "hasChanges": false
  }
}
```

### 4. Version Check (Lightweight Polling)
```http
GET /api/client/versions?appKey=xxx&environment=prod
```

**Response:**
```json
{
  "success": true,
  "data": {
    "globalVersion": 456,
    "flagVersions": {
      "new_checkout": 12,
      "free_shipping": 8,
      "dark_mode": 15
    }
  }
}
```

### 5. Cache Statistics (Monitoring)
```http
GET /api/client/cache-stats?appKey=xxx&environment=prod
```

**Response:**
```json
{
  "success": true,
  "data": {
    "appKey": "ecommerce-web",
    "environment": "prod",
    "cachedFlags": 45,
    "trackedVersions": 45,
    "globalVersion": 456
  }
}
```

## Incremental Sync Strategy

### Client-Side Flow (Web/Mobile SDK)

1. **Initialization**:
   ```javascript
   // Load from local storage first
   const cachedConfig = localStorage.getItem('ff_config');
   const lastVersion = cachedConfig?.globalVersion || 0;
   
   // Check for updates
   const response = await fetch(`/api/client/configs?appKey=xxx&lastKnownVersion=${lastVersion}`);
   const data = await response.json();
   
   if (data.data.hasChanges) {
     // Merge changes with local cache
     updateLocalCache(data.data.flags);
   }
   ```

2. **Periodic Polling** (every 30s-5min):
   - Call `/api/client/versions` to check `globalVersion`
   - If version changed, call `/api/client/configs?lastKnownVersion={currentVersion}`
   - Only download changed flags (minimal bandwidth)

3. **Full Comparison** (every 5 minutes):
   - Compare all flag versions locally vs server
   - Detect any missed updates
   - Pull missing flags

### Server-Side Optimization

- **Version Tracking**: Each flag has a `version` field that increments on every change
- **Global Version**: Max version across all flags for quick change detection
- **Selective Loading**: Only load changed flags from database when `lastKnownVersion` is provided
- **Cache Efficiency**: Unchanged flags served from Redis without DB query

## Performance Characteristics

| Metric | Target | Actual |
|--------|--------|--------|
| Cache Hit Rate (L2) | > 95% | TBD |
| Single Flag Eval Latency | < 5ms | TBD |
| Batch Eval (10 flags) | < 20ms | TBD |
| Config Change Propagation | < 1s | TBD |
| Incremental Sync Bandwidth | < 1KB per check | TBD |
| Cold Start Time | < 100ms (with cache warming) | TBD |

## Monitoring & Observability

### Key Metrics to Track

1. **Cache Hit Rate**: 
   - Monitor via `getCacheStats()` endpoint
   - Alert if hit rate drops below 90%

2. **Cache Size**:
   - Number of cached flags per app
   - Total Redis memory usage

3. **Version Drift**:
   - Difference between global version and individual flag versions
   - Indicates potential consistency issues

4. **Pub/Sub Message Frequency**:
   - Config change rate per minute
   - Alert on unusual spikes (possible misconfiguration)

5. **Incremental Sync Efficiency**:
   - Average number of flags returned per sync
   - Should be small (< 5% of total flags)

### Cache Stats Endpoint Usage

```bash
# Check cache health
curl http://localhost:8080/api/client/cache-stats?appKey=ecommerce-web&environment=prod

# Response
{
  "success": true,
  "data": {
    "appKey": "ecommerce-web",
    "environment": "prod",
    "cachedFlags": 45,
    "trackedVersions": 45,
    "globalVersion": 456
  }
}
```

## Best Practices

### 1. Cache Invalidation
- Always use `invalidateCache()` which also increments global version
- Never manually delete Redis keys (bypasses version tracking)
- Invalidate both individual flag and app-level cache on changes

### 2. Version Management
- Use optimistic locking (`@Version` annotation) in database
- Increment version on every flag update
- Clients should always compare versions before pulling data

### 3. Error Handling
- Cache failures should not block flag evaluation
- Fall back to database on cache miss
- Log errors but continue operation (fail-safe principle)

### 4. Cache Warming
- Enable automatic cache warming on startup
- Manually warm cache after major deployments
- Monitor warming duration and adjust async thread pool if needed

### 5. TTL Configuration
- Flag cache: 24 hours (refreshed on change or access)
- Version tracking: 48 hours (longer to support incremental sync)
- Adjust based on your update frequency and consistency requirements

## Troubleshooting

### Issue: High Cache Miss Rate

**Symptoms**: Slow evaluation times, high database load

**Solutions**:
1. Check if cache warming is enabled and completing successfully
2. Verify TTL settings are appropriate for your traffic pattern
3. Monitor Redis memory usage - may need to increase maxmemory
4. Check for frequent cache invalidations (too many config changes)

### Issue: Stale Configuration

**Symptoms**: Clients seeing old flag values

**Solutions**:
1. Verify Redis Pub/Sub is working (check listener logs)
2. Ensure `publishChange()` is called after every config update
3. Check client-side version comparison logic
4. Force full sync by clearing client's `lastKnownVersion`

### Issue: Version Inconsistency

**Symptoms**: Global version lower than some individual flag versions

**Solutions**:
1. This can happen during concurrent updates - usually self-correcting
2. Run manual cache warming to resync
3. Check for race conditions in admin API updates
4. Monitor `getCacheStats()` for anomalies

## Configuration

### application.yml Settings

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 5000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5

feature-toggle:
  # Redis Pub/Sub Channel
  redis-channel: feature_flag_changes
  # SDK Config Pull Interval (milliseconds)
  sdk-pull-interval: 300000
```

### Environment Variables

- `REDIS_HOST`: Redis server hostname (default: localhost)
- `REDIS_PORT`: Redis server port (default: 6379)
- `REDIS_PASSWORD`: Redis authentication password (optional)

## Future Enhancements

1. **Redis Cluster Support**: Add sharding for horizontal scaling
2. **Cache Compression**: Compress large flag configurations
3. **Distributed Locking**: Prevent thundering herd on cache misses
4. **Cache Analytics**: Track hit/miss patterns per flag
5. **Automatic Eviction**: LRU/LFU policies for memory-constrained environments
6. **Multi-Region Replication**: Cross-region cache synchronization

## References

- [Design Document V1](../设计文档V1.md) - Complete system architecture
- [README](../README.md) - Project overview and module structure
- [Spring Data Redis Documentation](https://docs.spring.io/spring-data/redis/docs/current/reference/html/)
