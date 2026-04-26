package com.featuretoggle.server.controller;

import com.featuretoggle.common.dto.ConfigChangeEvent;
import com.featuretoggle.common.model.FeatureFlag;
import com.featuretoggle.server.service.AdminService;
import com.featuretoggle.server.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin API for managing feature flags via dashboard.
 * Note: Each environment has its own deployment, so environment parameter is not needed in API calls.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin API", description = "APIs for managing feature flags (CRUD operations)")
public class AdminController {
    
    private final AdminService adminService;
    private final AuditLogService auditLogService;
    
    @Value("${FEATURE_TOGGLE_ENVIRONMENT:prod}")
    private String currentEnvironment;
    
    /**
     * Create a new feature flag
     */
    @PostMapping("/flags")
    @Operation(summary = "Create a new feature flag")
    public ResponseEntity<Map<String, Object>> createFlag(
            @Parameter(description = "App key", required = true) @RequestParam("appKey") String appKey,
            @RequestBody FeatureFlag flagRequest,
            HttpServletRequest request) {
        
        try {
            FeatureFlag flag = adminService.createFlag(appKey, currentEnvironment, flagRequest);
            
            // Log configuration change
            logConfigChange(appKey, flagRequest.getFlagKey(), 
                ConfigChangeEvent.ChangeType.CREATE, null, flag, "Create flag via admin API", request);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", flag,
                "message", "Flag created successfully"
            ));
        } catch (Exception e) {
            log.error("Error creating flag", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Update an existing feature flag
     */
    @PutMapping("/flags/{flagKey}")
    @Operation(summary = "Update an existing feature flag")
    public ResponseEntity<Map<String, Object>> updateFlag(
            @Parameter(description = "App key", required = true) @RequestParam("appKey") String appKey,
            @Parameter(description = "Flag key", required = true) @PathVariable String flagKey,
            @RequestBody FeatureFlag flagRequest,
            HttpServletRequest request) {
        
        try {
            FeatureFlag flag = adminService.updateFlag(appKey, currentEnvironment, flagKey, flagRequest);
            
            // Get previous state (in production, fetch from DB before update)
            FeatureFlag previousState = adminService.getFlag(appKey, currentEnvironment, flagKey);
            
            // Log configuration change
            logConfigChange(appKey, flagKey, 
                ConfigChangeEvent.ChangeType.UPDATE, previousState, flag, "Update flag via admin API", request);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", flag,
                "message", "Flag updated successfully"
            ));
        } catch (Exception e) {
            log.error("Error updating flag", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Delete a feature flag (hard delete)
     */
    @DeleteMapping("/flags/{flagKey}")
    @Operation(summary = "Delete a feature flag (hard delete)")
    public ResponseEntity<Map<String, Object>> deleteFlag(
            @Parameter(description = "App key", required = true) @RequestParam("appKey") String appKey,
            @Parameter(description = "Flag key", required = true) @PathVariable String flagKey,
            HttpServletRequest request) {
        
        try {
            // Get flag before deletion for audit log
            FeatureFlag previousState = adminService.getFlag(appKey, currentEnvironment, flagKey);
            
            adminService.deleteFlag(appKey, currentEnvironment, flagKey);
            
            // Log configuration change
            logConfigChange(appKey, flagKey, 
                ConfigChangeEvent.ChangeType.DELETE, previousState, null, "Delete flag via admin API", request);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Flag deleted successfully"
            ));
        } catch (Exception e) {
            log.error("Error deleting flag", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get feature flag(s) - returns single flag if flagKey provided, otherwise all flags
     */
    @GetMapping("/flags")
    @Operation(summary = "Get feature flag(s)", description = "Returns a single flag if flagKey is provided, otherwise returns all flags for the app.")
    public ResponseEntity<Map<String, Object>> getFlags(
            @Parameter(description = "App key", required = true) @RequestParam("appKey") String appKey,
            @Parameter(description = "Flag key (optional - if not provided, returns all flags)") @RequestParam(value = "flagKey", required = false) String flagKey) {
        
        try {
            if (flagKey != null && !flagKey.isEmpty()) {
                // Get single flag
                FeatureFlag flag = adminService.getFlag(appKey, currentEnvironment, flagKey);
                if (flag == null) {
                    return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "Flag not found: " + flagKey
                    ));
                }
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", flag
                ));
            } else {
                // Get all flags
                List<FeatureFlag> flags = adminService.listFlags(appKey, currentEnvironment);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", flags,
                    "total", flags.size()
                ));
            }
        } catch (Exception e) {
            log.error("Error getting flag(s)", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Log configuration change event
     */
    private void logConfigChange(String appKey, String flagKey,
                                 ConfigChangeEvent.ChangeType changeType,
                                 FeatureFlag previousState, FeatureFlag newState,
                                 String reason,
                                 HttpServletRequest request) {
        try {
            // Get client IP from request
            String ipAddress = getClientIpAddress(request);
            
            ConfigChangeEvent event = ConfigChangeEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .appKey(appKey)
                .environment(currentEnvironment)
                .flagKey(flagKey)
                .changeType(changeType)
                .changedBy("admin-user") // TODO: Integrate with authentication system in production
                .previousState(previousState != null ? convertToMap(previousState) : null)
                .newState(newState != null ? convertToMap(newState) : null)
                .changeReason(reason)
                .ipAddress(ipAddress)
                .userAgent(request.getHeader("User-Agent"))
                .rolledBack(false)
                .build();
            
            auditLogService.logConfigChange(event);
            
        } catch (Exception e) {
            // Never let audit logging fail the main request
            log.warn("Failed to create config change event", e);
        }
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Convert FeatureFlag to Map for audit logging
     */
    private Map<String, Object> convertToMap(FeatureFlag flag) {
        return Map.of(
            "flagKey", flag.getFlagKey(),
            "name", flag.getName(),
            "status", flag.getStatus(),
            "defaultValue", flag.getDefaultValue(),
            "version", flag.getVersion()
        );
    }
}
