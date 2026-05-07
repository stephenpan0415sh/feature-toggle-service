package com.featuretoggle.server.controller;

import com.featuretoggle.common.dto.BatchEvaluationRequest;
import com.featuretoggle.common.dto.EvaluationEvent;
import com.featuretoggle.common.model.EvaluationDetail;
import com.featuretoggle.common.model.UserContext;
import com.featuretoggle.server.service.AuditLogService;
import com.featuretoggle.server.service.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Client-facing API for SDK consumption.
 * This controller provides endpoints for flag evaluation and configuration retrieval.
 * Note: Each environment has its own deployment, so environment parameter is not needed in API calls.
 */
@Slf4j
@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
@Tag(name = "Client API", description = "APIs for SDK flag evaluation and configuration retrieval")
public class ClientApiController {
    
    private final EvaluationService evaluationService;
    private final AuditLogService auditLogService;
    
    @Value("${FEATURE_TOGGLE_ENVIRONMENT:prod}")
    private String currentEnvironment;
    
    /**
     * Single flag evaluation
     * GET /api/client/evaluate/{flagKey}?appKey=xxx&userId=xxx&region=xxx
     */
    @GetMapping("/evaluate/{flagKey}")
    @Operation(
        summary = "Evaluate a single feature flag",
        description = "Evaluates a feature flag for a specific user context. Returns the flag value along with evaluation details including matched rules and conditions.",
        tags = {"Client API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Flag evaluated successfully",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Success Response",
                    value = "{\n  \"success\": true,\n  \"data\": {\n    \"flagKey\": \"enable_new_checkout\",\n    \"enabled\": true,\n    \"value\": \"true\",\n    \"reason\": \"RULE_MATCH\",\n    \"matchedRuleId\": \"rule_1\",\n    \"traceId\": \"trace-abc-123\",\n    \"releaseVersion\": \"v1.2.0\"\n  }\n}"
                ))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Error Response",
                    value = "{\n  \"success\": false,\n  \"error\": \"App not found: invalid-app\"\n}"
                )))
    })
    public ResponseEntity<Map<String, Object>> evaluateFlag(
            @PathVariable String flagKey,
            @Parameter(description = "Application key", required = true, example = "ecommerce-web")
            @RequestParam("appKey") String appKey,
            @Parameter(description = "User ID", required = true, example = "user_123")
            @RequestParam("userId") String userId,
            @Parameter(description = "User region", example = "cn-east")
            @RequestParam(value = "region", required = false) String region) {
        
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
                appKey, flagKey, userContext, currentEnvironment);
            long latency = System.currentTimeMillis() - startTime;
            
            // Send audit log asynchronously
            sendAuditLog(appKey, flagKey, userId, region, detail, latency, null, detail.releaseVersion());
            
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
        description = "Evaluates multiple feature flags in a single request for better performance. Recommended for SDK initialization or page loads requiring multiple flags.",
        tags = {"Client API"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Flags evaluated successfully",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Batch Success",
                    value = "{\n  \"success\": true,\n  \"data\": {\n    \"enable_checkout\": {\n      \"flagKey\": \"enable_checkout\",\n      \"enabled\": true,\n      \"value\": \"true\",\n      \"reason\": \"DEFAULT\"\n    },\n    \"show_promo\": {\n      \"flagKey\": \"show_promo\",\n      \"enabled\": false,\n      \"value\": \"false\",\n      \"reason\": \"DEFAULT\"\n    }\n  }\n}"
                ))),
        @ApiResponse(responseCode = "400", description = "Missing required fields",
            content = @Content(mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Validation Error",
                    value = "{\n  \"success\": false,\n  \"error\": \"Missing required fields: appKey, userId\"\n}"
                )))
    })
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
            
            if (request.flagKeys() == null || request.flagKeys().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "flagKeys cannot be empty"
                ));
            }
            
            // Build user context
            UserContext userContext = new UserContext(
                request.userId(), 
                request.attributes()
            );
            
            // Batch evaluate
            Map<String, EvaluationDetail> results = evaluationService.batchEvaluate(
                request.appKey(), request.flagKeys(), userContext, currentEnvironment);
            
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
            @RequestParam("appKey") String appKey,
            @Parameter(description = "Last known global version for incremental sync", example = "123")
            @RequestParam(value = "lastKnownVersion", required = false) Long lastKnownVersion) {
        
        try {
            // Get global version
            Long currentGlobalVersion = evaluationService.getFlagVersions(appKey, currentEnvironment).values().stream()
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
            var flags = evaluationService.getAllFlagsIncremental(appKey, currentEnvironment, lastKnownVersion);
            
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
            @RequestParam("appKey") String appKey) {
        
        try {
            // Get all flag versions
            Map<String, Long> versions = evaluationService.getFlagVersions(appKey, currentEnvironment);
            
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
     * GET /api/client/cache-stats?appKey=xxx
     */
    @GetMapping("/cache-stats")
    @Operation(
        summary = "Get cache statistics",
        description = "Returns cache statistics including number of cached flags, tracked versions, and global version. Useful for monitoring and debugging."
    )
    public ResponseEntity<Map<String, Object>> getCacheStats(
            @Parameter(description = "Application key", required = true, example = "ecommerce-web")
            @RequestParam("appKey") String appKey) {
        
        try {
            // Get cache stats
            Map<String, Object> stats = evaluationService.getCacheStats(appKey, currentEnvironment);
            
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
    private void sendAuditLog(String appKey, String flagKey, 
                              String userId, String region, EvaluationDetail detail,
                              long latencyMs, String errorMessage, String releaseVersion) {
        try {
            EvaluationEvent event = EvaluationEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .appKey(appKey)
                .environment(currentEnvironment)
                .flagKey(flagKey)
                .enabled(detail.enabled())
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
