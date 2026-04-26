package com.featuretoggle.server.service;

import com.featuretoggle.server.entity.App;
import com.featuretoggle.server.repository.AppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Scheduled service to update cache metrics periodically.
 * This avoids side effects in read operations and provides consistent metric updates.
 * Note: Each environment has its own deployment, so this only updates metrics for the current environment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsUpdateService {
    
    private final EvaluationService evaluationService;
    private final AppRepository appRepository;
    
    @Value("${FEATURE_TOGGLE_ENVIRONMENT:prod}")
    private String currentEnvironment;
    
    /**
     * Update cache statistics gauge every 60 seconds.
     * This runs on each container instance to report local cache status to Prometheus.
     * Multiple containers reporting is expected - Prometheus distinguishes them by instance label.
     */
    @Scheduled(fixedRate = 60000, initialDelay = 30000)
    public void updateCacheMetrics() {
        try {
            // Only update metrics for active apps (limit query load)
            List<App> apps = appRepository.findAll();
            
            if (apps.isEmpty()) {
                log.debug("No apps found, skipping metrics update");
                return;
            }
            
            // Only update metrics for the current environment (each deployment is environment-specific)
            for (App app : apps) {
                updateMetricsForEnvironment(app.getAppKey(), currentEnvironment);
            }
            
            log.debug("Cache metrics updated for {} apps in environment: {}", apps.size(), currentEnvironment);
        } catch (Exception e) {
            log.error("Failed to update cache metrics", e);
        }
    }
    
    private void updateMetricsForEnvironment(String appKey, String environment) {
        try {
            Map<String, Object> stats = evaluationService.getCacheStats(appKey, environment);
            
            if (stats.containsKey("cachedFlags")) {
                int cachedFlags = ((Number) stats.get("cachedFlags")).intValue();
                // Note: The gauge update is now handled inside getCacheStats
                // We just call it to trigger the update
            }
        } catch (Exception e) {
            log.debug("Failed to update metrics for app={}, env={}", appKey, environment);
        }
    }
}
