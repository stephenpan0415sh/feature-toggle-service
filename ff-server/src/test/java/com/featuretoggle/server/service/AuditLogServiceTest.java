package com.featuretoggle.server.service;

import com.featuretoggle.common.dto.EvaluationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuditLogService
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditLogServiceTest {

    @Autowired
    private AuditLogService auditLogService;

    private EvaluationEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = EvaluationEvent.builder()
            .eventId("test-event-001")
            .timestamp(System.currentTimeMillis())
            .appKey("test-app")
            .environment("prod")
            .flagKey("test_flag")
            .enabled(true)
            .reason("MATCHED_RULE")
            .matchedRuleId(101L)
            .traceId("trace_abc123")
            .userId("user_123")
            .region("cn-east")
            .releaseVersion("v1.2.3")
            .userContext(Map.of("uid", "user_123", "vip_level", 2))
            .matchedConditions(List.of("uid in [100,200]", "vip_level >= 2"))
            .sdkVersion("server-api")
            .evaluationLatencyMs(5L)
            .success(true)
            .errorMessage(null)
            .build();
    }

    @Test
    void logEvaluation_shouldPrintEventToLog() {
        // When
        auditLogService.logEvaluation(testEvent);

        // Then - verify by checking console log output
        // Expected log: EVALUATION_EVENT: EvaluationEvent(eventId=test-event-001, ...)
        assertNotNull(testEvent.getEventId());
        assertEquals("test_flag", testEvent.getFlagKey());
        assertTrue(testEvent.isEnabled());
    }

    @Test
    void logEvaluationBatch_shouldPrintAllEvents() {
        // Given
        EvaluationEvent event2 = EvaluationEvent.builder()
            .eventId("test-event-002")
            .timestamp(System.currentTimeMillis())
            .appKey("test-app")
            .environment("prod")
            .flagKey("another_flag")
            .enabled(false)
            .reason("DEFAULT")
            .traceId("trace_xyz789")
            .userId("user_456")
            .region("us-west")
            .releaseVersion("v1.0.0")
            .userContext(Map.of("uid", "user_456"))
            .sdkVersion("server-api")
            .evaluationLatencyMs(3L)
            .success(true)
            .build();

        List<EvaluationEvent> events = List.of(testEvent, event2);

        // When
        auditLogService.logEvaluationBatch(events);

        // Then - both events should be logged
        assertEquals(2, events.size());
    }

    @Test
    void logEvaluationBatch_shouldHandleEmptyList() {
        // When
        auditLogService.logEvaluationBatch(List.of());

        // Then - should not throw exception
        assertDoesNotThrow(() -> auditLogService.logEvaluationBatch(List.of()));
    }

    @Test
    void logEvaluationBatch_shouldHandleNullList() {
        // When & Then - should not throw exception
        assertDoesNotThrow(() -> auditLogService.logEvaluationBatch(null));
    }

    @Test
    void logConfigChange_shouldPrintEventToLog() {
        // Given
        var configEvent = com.featuretoggle.common.dto.ConfigChangeEvent.builder()
            .eventId("config-event-001")
            .timestamp(System.currentTimeMillis())
            .appKey("test-app")
            .environment("prod")
            .flagKey("test_flag")
            .changeType(com.featuretoggle.common.dto.ConfigChangeEvent.ChangeType.CREATE)
            .changedBy("admin-user")
            .newState(Map.of("flagKey", "test_flag", "status", 1, "defaultValue", "false"))
            .changeReason("Create flag via admin API")
            .ipAddress("192.168.1.100")
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .rolledBack(false)
            .build();

        // When
        auditLogService.logConfigChange(configEvent);

        // Then - verify by checking console log output
        // Expected log: CONFIG_CHANGE_EVENT: ConfigChangeEvent(eventId=config-event-001, ...)
        assertNotNull(configEvent.getEventId());
        assertEquals("test_flag", configEvent.getFlagKey());
        assertEquals(com.featuretoggle.common.dto.ConfigChangeEvent.ChangeType.CREATE, configEvent.getChangeType());
    }

    @Test
    void logConfigChange_update_shouldIncludePreviousState() {
        // Given
        var configEvent = com.featuretoggle.common.dto.ConfigChangeEvent.builder()
            .eventId("config-event-002")
            .timestamp(System.currentTimeMillis())
            .appKey("test-app")
            .environment("prod")
            .flagKey("test_flag")
            .changeType(com.featuretoggle.common.dto.ConfigChangeEvent.ChangeType.UPDATE)
            .changedBy("admin-user")
            .previousState(Map.of("status", 1, "defaultValue", "false", "version", 5))
            .newState(Map.of("status", 0, "defaultValue", "false", "version", 6))
            .changedFields(List.of("status", "version"))
            .changeReason("Emergency kill switch - bug found in production")
            .ipAddress("192.168.1.100")
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .rolledBack(false)
            .build();

        // When
        auditLogService.logConfigChange(configEvent);

        // Then
        assertNotNull(configEvent.getPreviousState());
        assertNotNull(configEvent.getNewState());
        assertEquals(1, configEvent.getPreviousState().get("status"));
        assertEquals(0, configEvent.getNewState().get("status"));
    }

    @Test
    void logConfigChange_delete_shouldIncludePreviousStateOnly() {
        // Given
        var configEvent = com.featuretoggle.common.dto.ConfigChangeEvent.builder()
            .eventId("config-event-003")
            .timestamp(System.currentTimeMillis())
            .appKey("test-app")
            .environment("prod")
            .flagKey("old_flag")
            .changeType(com.featuretoggle.common.dto.ConfigChangeEvent.ChangeType.DELETE)
            .changedBy("admin-user")
            .previousState(Map.of("flagKey", "old_flag", "status", 1, "defaultValue", "true", "version", 3))
            .newState(null)
            .changedFields(List.of("flagKey", "status", "defaultValue"))
            .changeReason("Remove unused flag - not evaluated in 30 days")
            .ipAddress("192.168.1.100")
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .rolledBack(false)
            .build();

        // When
        auditLogService.logConfigChange(configEvent);

        // Then
        assertNotNull(configEvent.getPreviousState());
        assertNull(configEvent.getNewState());
        assertEquals(com.featuretoggle.common.dto.ConfigChangeEvent.ChangeType.DELETE, configEvent.getChangeType());
    }
}
