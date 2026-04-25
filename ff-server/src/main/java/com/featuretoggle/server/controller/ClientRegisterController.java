package com.featuretoggle.server.controller;

import com.featuretoggle.common.model.FeatureFlag;
import com.featuretoggle.common.model.Rule;
import com.featuretoggle.server.entity.App;
import com.featuretoggle.server.entity.FeatureFlagEntity;
import com.featuretoggle.server.entity.FlagRuleEntity;
import com.featuretoggle.server.repository.AppRepository;
import com.featuretoggle.server.repository.FeatureFlagRepository;
import com.featuretoggle.server.repository.FlagRuleRepository;
import com.featuretoggle.server.service.EvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Client API for SDK flag registration and heartbeat.
 * SDK clients call this to register flags defined in code.
 */
@Slf4j
@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
@Tag(name = "Client Register API", description = "APIs for SDK flag registration")
public class ClientRegisterController {
    
    private final AppRepository appRepository;
    private final FeatureFlagRepository flagRepository;
    private final FlagRuleRepository ruleRepository;
    private final EvaluationService evaluationService;
    private final ObjectMapper objectMapper;
    
    /**
     * Register a flag from SDK client.
     * SDK calls this at startup to publish flags defined in code.
     */
    @PostMapping("/register")
    @Operation(summary = "Register feature flag from SDK client")
    public ResponseEntity<Map<String, Object>> registerFlag(
            @Parameter(description = "Registration request", required = true)
            @RequestBody Map<String, Object> request) {
        
        try {
            String appKey = (String) request.get("appKey");
            String environment = (String) request.get("environment");
            @SuppressWarnings("unchecked")
            Map<String, Object> flagMap = (Map<String, Object>) request.get("flag");
            
            String flagKey = (String) flagMap.get("flagKey");
            
            log.info("Received flag registration: appKey={}, flagKey={}, environment={}", 
                appKey, flagKey, environment);
            
            // Check if flag already exists
            App app = appRepository.findByAppKey(appKey)
                .orElseThrow(() -> new RuntimeException("App not found: " + appKey));
            
            java.util.Optional<FeatureFlagEntity> existingFlag = flagRepository
                .findByAppIdAndEnvironmentAndFlagKey(app.getId(), environment, flagKey);
            
            if (existingFlag.isPresent()) {
                // Flag already exists, do not overwrite (Dashboard has higher priority)
                log.info("Flag {} already exists in Dashboard, skipping registration", flagKey);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Flag already exists, not overwritten",
                    "action", "skipped"
                ));
            }
            
            // Create new flag
            FeatureFlagEntity flagEntity = FeatureFlagEntity.builder()
                .appId(app.getId())
                .flagKey(flagKey)
                .name((String) flagMap.getOrDefault("name", flagKey))
                .environment(environment)
                .status(1)
                .defaultValue((String) flagMap.getOrDefault("defaultValue", "false"))
                .version(1L)
                .build();
            
            flagEntity = flagRepository.save(flagEntity);
            
            // Save rules if present
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> rules = 
                (java.util.List<Map<String, Object>>) flagMap.get("rules");
            
            if (rules != null && !rules.isEmpty()) {
                int priority = 1;
                for (Map<String, Object> ruleMap : rules) {
                    FlagRuleEntity ruleEntity = FlagRuleEntity.builder()
                        .flagId(flagEntity.getId())
                        .priority(priority++)
                        .conditions(objectMapper.writeValueAsString(ruleMap))
                        .actionValue((String) ruleMap.getOrDefault("actionValue", "false"))
                        .description((String) ruleMap.getOrDefault("description", ""))
                        .build();
                    
                    ruleRepository.save(ruleEntity);
                }
            }
            
            // Publish to Redis for other SDK clients (skip if requested)
            Boolean skipPublish = (Boolean) request.get("skipPublish");
            if (!Boolean.TRUE.equals(skipPublish)) {
                evaluationService.publishChange(appKey, flagKey, environment, flagEntity.getVersion());
                log.info("Published config change for flag: {}", flagKey);
            } else {
                log.debug("Skipped publishing for flag registration: {}", flagKey);
            }
            
            log.info("Successfully registered flag: {}", flagKey);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Flag registered successfully",
                "action", "created",
                "version", flagEntity.getVersion()
            ));
            
        } catch (Exception e) {
            log.error("Error registering flag", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
