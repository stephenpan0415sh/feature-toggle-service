package com.featuretoggle.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featuretoggle.common.dto.ConfigChangeEvent;
import com.featuretoggle.common.dto.EvaluationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Audit log service for sending evaluation events to Kafka.
 * In production, this would send events to Kafka for ELK processing.
 * For demo purposes, this implementation logs to console.
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Send evaluation event to Kafka (simulated).
     * In production, this would use KafkaTemplate to send to a real Kafka cluster.
     * 
     * @param event The evaluation event to log
     */
    // TODO: Implement Kafka integration - inject KafkaTemplate and send to "feature-flag-evaluations" topic
    public void logEvaluation(EvaluationEvent event) {
        try {
            // Convert to JSON for better readability
            String json = objectMapper.writeValueAsString(event);
            
            // Simulate async sending to Kafka
            // In production: kafkaTemplate.send("feature-flag-evaluations", event);
            
            // For demo: log as JSON (can be captured by filebeat -> ELK)
            log.info("EVALUATION_EVENT: {}", json);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize evaluation event", e);
        } catch (Exception e) {
            // Never let audit logging fail the main request
            log.error("Failed to send evaluation event to Kafka", e);
        }
    }

    /**
     * Log configuration change event (for admin operations).
     * Tracks who changed what, when, and why.
     * 
     * @param event The configuration change event
     */
    // TODO: Implement Kafka integration - inject KafkaTemplate and send to "feature-flag-changes" topic
    public void logConfigChange(ConfigChangeEvent event) {
        try {
            // Convert to JSON for better readability
            String json = objectMapper.writeValueAsString(event);
            
            // Simulate async sending to Kafka
            // In production: kafkaTemplate.send("feature-flag-changes", event);
            
            // For demo: log as JSON
            log.info("CONFIG_CHANGE_EVENT: {}", json);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize config change event", e);
        } catch (Exception e) {
            log.error("Failed to send config change event to Kafka", e);
        }
    }

    /**
     * Batch send evaluation events (for high-throughput scenarios).
     * 
     * @param events List of evaluation events
     */
    // TODO: Implement Kafka batch sending using kafkaTemplate.send() for better performance
    public void logEvaluationBatch(java.util.List<EvaluationEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        
        for (EvaluationEvent event : events) {
            logEvaluation(event);
        }
    }
}
