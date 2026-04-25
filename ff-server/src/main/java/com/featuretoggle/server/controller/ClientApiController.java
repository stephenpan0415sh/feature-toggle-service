package com.featuretoggle.server.controller;

import com.featuretoggle.common.dto.BatchEvaluationRequest;
import com.featuretoggle.common.dto.EvaluationEvent;
import com.featuretoggle.common.model.EvaluationDetail;
import com.featuretoggle.common.model.UserContext;
import com.featuretoggle.server.service.AuditLogService;
import com.featuretoggle.server.service.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Client API Controller for SDK flag evaluation
 */
@Slf4j
@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
@Tag(name = "Client API", description = "APIs for SDK flag evaluation and configuration retrieval")
public class ClientApiController {

    private final EvaluationService evaluationService;
    private final AuditLogService auditLogService;

    /**
     * Single flag evaluation
     * GET /api/client/flags/{key}?userId=123&region=cn-east
     */
    @GetMapping("/flags/{flagKey}")
    @Operation(
        summary = "Evaluate a single feature flag",
        description = "Evaluates a specific feature flag for a given user context and returns the evaluation result with explainability details"
    )
    public ResponseEntity<Map<String, Object>> evaluateFlag(
            @PathVariable String flagKey,
            @Parameter(description = "Application key", required = true, example = "ecommerce-web")
            @RequestParam String appKey,
            @Parameter(description = "User ID", required = true, example = "user_123")
            @RequestParam String userId,
            @Parameter(description = "User region", example = "cn-east")
            @RequestParam(required = false) String region,
            @Parameter(description = "Environment (default: prod)", example = "prod")
            @RequestParam(required = false) String environment) {
        
        try {
            // Build user context
            java.util.Map<String, Object> attributes = new java.util.HashMap<>();
            if (region != null) {
                attributes.put("region", region);
            }
            
            UserContext userContext = new UserContext(userId, attributes.isEmpty() ? null : attributes);
            
            // Evaluate
            long startTime = System.currentTimeMillis();
            EvaluationDetail detail = evaluationService.evaluateFlag(
                appKey, flagKey, userContext, environment != null ? environment : "prod");
            long latency = System.currentTimeMillis() - startTime;
            
            // Send audit log asynchronously
            sendAuditLog(appKey, environment != null ? environment : "prod", flagKey, userId, region, detail, latency, null, detail.releaseVersion());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", detail
            ));
            
        } catch (Exception e) {
            log.error("Error evaluating flag: {}", flagKey, e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Batch flag evaluation
     * POST /api/client/evaluate
     */
    @PostMapping("/evaluate")
    @Operation(
        summary = "Batch evaluate multiple feature flags",
        description = "Evaluates multiple feature flags in a single request for better performance. Recommended for SDK initialization or page loads requiring multiple flags."
    )
    public ResponseEntity<Map<String, Object>> batchEvaluate(
            @RequestBody BatchEvaluationRequest request) {
        
        try {
            // Validate request
            if (request == null || request.appKey() == null || request.userId() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Missing required fields: appKey, userId"
                ));
            }
            
            // Build user context
            UserContext userContext = new UserContext(
                request.userId(), 
                request.attributes()
            );
            
            // Batch evaluate (environment defaults to prod, can be added to request if needed)
            Map<String, EvaluationDetail> results = evaluationService.batchEvaluate(
                request.appKey(), request.flagKeys(), userContext, "prod");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", results
            ));
            
        } catch (Exception e) {
            log.error("Error in batch evaluation", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Get all flags for SDK initialization
     * GET /api/client/configs?appKey=xxx&lastKnownVersion=123
     * Supports incremental sync - returns only changed flags if lastKnownVersion is provided
     */
    @GetMapping("/configs")
    @Operation(
        summary = "Get feature flag configurations",
        description = "Retrieves feature flag configurations for SDK initialization. Supports incremental sync by providing lastKnownVersion to receive only changed flags."
    )
    public ResponseEntity<Map<String, Object>> getConfigs(
            @Parameter(description = "Application key", required = true, example = "ecommerce-web")
            @RequestParam String appKey,
            @Parameter(description = "Last known global version for incremental sync", example = "123")
            @RequestParam(required = false) Long lastKnownVersion,
            @Parameter(description = "Environment (default: prod)", example = "prod")
            @RequestParam(required = false) String environment) {
        
        try {
            String env = environment != null ? environment : "prod";
            
            // Get global version
            Long currentGlobalVersion = evaluationService.getFlagVersions(appKey, env).values().stream()
                .max(Long::compareTo)
                .orElse(0L);
            
            // Check if client already has latest version
            if (lastKnownVersion != null && currentGlobalVersion <= lastKnownVersion) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                        "globalVersion", currentGlobalVersion,
                        "flags", java.util.List.of(), // No changes
                        "hasChanges", false
                    )
                ));
            }
            
            // Get changed flags (or all flags if no lastKnownVersion)
            var flags = evaluationService.getAllFlagsIncremental(appKey, env, lastKnownVersion);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "globalVersion", currentGlobalVersion,
                    "flags", flags,
                    "hasChanges", !flags.isEmpty()
                )
            ));
            
        } catch (Exception e) {
            log.error("Error getting configs for app: {}", appKey, e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Get flag versions for incremental sync comparison
     * GET /api/client/versions?appKey=xxx
     */
    @GetMapping("/versions")
    @Operation(
        summary = "Get flag versions for change detection",
        description = "Returns the current global version and individual flag versions. Use this endpoint for lightweight polling to detect configuration changes before pulling full configs."
    )
    public ResponseEntity<Map<String, Object>> getFlagVersions(
            @Parameter(description = "Application key", required = true, example = "ecommerce-web")
            @RequestParam String appKey,
            @Parameter(description = "Environment (default: prod)", example = "prod")
            @RequestParam(required = false) String environment) {
        
        try {
            String env = environment != null ? environment : "prod";
            
            // Get all flag versions
            Map<String, Long> versions = evaluationService.getFlagVersions(appKey, env);
            
            // Calculate global version (max of all flag versions)
            Long globalVersion = versions.values().stream()
                .max(Long::compareTo)
                .orElse(0L);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "globalVersion", globalVersion,
                    "flagVersions", versions
                )
            ));
            
        } catch (Exception e) {
            log.error("Error getting flag versions for app: {}", appKey, e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Get cache statistics for monitoring
     * GET /api/client/cache-stats?appKey=xxx&environment=prod
     */
    @GetMapping("/cache-stats")
    @Operation(
        summary = "Get cache statistics",
        description = "Returns cache statistics including number of cached flags, tracked versions, and global version. Useful for monitoring and debugging."
    )
    public ResponseEntity<Map<String, Object>> getCacheStats(
            @Parameter(description = "Application key", required = true, example = "ecommerce-web")
            @RequestParam String appKey,
            @Parameter(description = "Environment (default: prod)", example = "prod")
            @RequestParam(required = false) String environment) {
        
        try {
            String env = environment != null ? environment : "prod";
            
            // Get cache stats
            Map<String, Object> stats = evaluationService.getCacheStats(appKey, env);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", stats
            ));
            
        } catch (Exception e) {
            log.error("Error getting cache stats for app: {}", appKey, e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Send evaluation event to audit log (async, non-blocking)
     */
    private void sendAuditLog(String appKey, String environment, String flagKey, 
                              String userId, String region, EvaluationDetail detail,
                              long latencyMs, String errorMessage, String releaseVersion) {
        try {
            EvaluationEvent event = EvaluationEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .appKey(appKey)
                .environment(environment)
                .flagKey(flagKey)
                .enabled(detail.enabled())
                .value(detail.value())
                .reason(detail.reason() != null ? detail.reason().name() : null)
                .matchedRuleId(detail.matchedRuleId())
                .traceId(detail.traceId())
                .userId(userId)
                .region(region)
                .releaseVersion(releaseVersion)
                .userContext(detail.userContextSnapshot())
                .matchedConditions(detail.matchedConditions())
                .sdkVersion("server-api") // SDK version would come from client headers
                .evaluationLatencyMs(latencyMs)
                .success(detail.isSuccess())
                .errorMessage(errorMessage)
                .build();
            
            // Async send to Kafka (simulated)
            auditLogService.logEvaluation(event);
            
        } catch (Exception e) {
            // Never let audit logging fail the main request
            log.warn("Failed to create audit log event", e);
        }
    }
}
