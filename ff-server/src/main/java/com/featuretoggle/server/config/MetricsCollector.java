package com.featuretoggle.server.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collector for feature flag evaluation.
 * Exposes Prometheus metrics for monitoring and alerting.
 */
@Component
@RequiredArgsConstructor
public class MetricsCollector {

    private final MeterRegistry meterRegistry;
    
    // Counters
    private final Counter evaluationTotalCounter;
    private final Counter evaluationSuccessCounter;
    private final Counter evaluationErrorCounter;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    
    // Timers
    private final Timer evaluationTimer;
    
    // Gauges for cache stats
    private final ConcurrentHashMap<String, AtomicLong> cachedFlagsGauge = new ConcurrentHashMap<>();

    public MetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        this.evaluationTotalCounter = Counter.builder("feature_flag_evaluation_total")
            .description("Total number of feature flag evaluations")
            .register(meterRegistry);
            
        this.evaluationSuccessCounter = Counter.builder("feature_flag_evaluation_success_total")
            .description("Number of successful feature flag evaluations")
            .register(meterRegistry);
            
        this.evaluationErrorCounter = Counter.builder("feature_flag_evaluation_error_total")
            .description("Number of failed feature flag evaluations")
            .register(meterRegistry);
            
        this.cacheHitCounter = Counter.builder("feature_flag_cache_hit_total")
            .description("Number of cache hits")
            .register(meterRegistry);
            
        this.cacheMissCounter = Counter.builder("feature_flag_cache_miss_total")
            .description("Number of cache misses")
            .register(meterRegistry);
        
        // Initialize timer
        this.evaluationTimer = Timer.builder("feature_flag_evaluation_duration")
            .description("Feature flag evaluation duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
    }

    /**
     * Record a flag evaluation
     */
    public void recordEvaluation(String appKey, String flagKey, boolean success, long durationMs) {
        evaluationTotalCounter.increment();
        
        if (success) {
            evaluationSuccessCounter.increment();
        } else {
            evaluationErrorCounter.increment();
        }
        
        evaluationTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Record cache hit
     */
    public void recordCacheHit(String appKey, String environment) {
        cacheHitCounter.increment();
    }

    /**
     * Record cache miss
     */
    public void recordCacheMiss(String appKey, String environment) {
        cacheMissCounter.increment();
    }

    /**
     * Update cached flags count gauge
     */
    public void updateCachedFlagsGauge(String appKey, String environment, int count) {
        String key = appKey + ":" + environment;
        AtomicLong gauge = cachedFlagsGauge.computeIfAbsent(key, k -> {
            AtomicLong atomicLong = new AtomicLong(count);
            io.micrometer.core.instrument.Gauge.builder("feature_flag_cached_flags", atomicLong, AtomicLong::get)
                .tag("app_key", appKey)
                .tag("environment", environment)
                .description("Number of cached flags for app")
                .register(meterRegistry);
            return atomicLong;
        });
        gauge.set(count);
    }

    /**
     * Get cache hit rate
     */
    public double getCacheHitRate() {
        long hits = (long) cacheHitCounter.count();
        long misses = (long) cacheMissCounter.count();
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }
}
