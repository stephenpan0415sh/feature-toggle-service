package com.featuretoggle.common.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for BuiltInRuleId enum
 */
class BuiltInRuleIdTest {

    @Test
    void testBuiltInRuleIds() {
        // Verify all built-in rule IDs have correct format
        assertEquals("builtin:flag-disabled", BuiltInRuleId.FLAG_DISABLED.getId());
        assertEquals("builtin:no-rules-configured", BuiltInRuleId.NO_RULES_CONFIGURED.getId());
        assertEquals("builtin:default-fallback", BuiltInRuleId.DEFAULT_FALLBACK.getId());
        assertEquals("builtin:error-fallback", BuiltInRuleId.ERROR_FALLBACK.getId());
    }

    @Test
    void testToString() {
        // Verify toString returns the ID
        assertEquals("builtin:flag-disabled", BuiltInRuleId.FLAG_DISABLED.toString());
        assertEquals("builtin:no-rules-configured", BuiltInRuleId.NO_RULES_CONFIGURED.toString());
        assertEquals("builtin:default-fallback", BuiltInRuleId.DEFAULT_FALLBACK.toString());
        assertEquals("builtin:error-fallback", BuiltInRuleId.ERROR_FALLBACK.toString());
    }

    @Test
    void testEvaluationDetailWithBuiltInRuleId() {
        // Test that EvaluationDetail properly uses built-in rule IDs from EvaluationReason
        EvaluationDetail detail = new EvaluationDetail(
            "test-flag",
            false,
            EvaluationDetail.EvaluationReason.DEFAULT,
            EvaluationDetail.EvaluationReason.DEFAULT.getRuleId(),
            "trace-123",
            "prod",
            "cn-east",
            "v1.0.0",
            System.currentTimeMillis(),
            null,
            null
        );

        assertNotNull(detail.matchedRuleId());
        assertEquals(Long.valueOf(4), detail.matchedRuleId());
        
        // Test that DEFAULT reason has built-in rule ID
        assertTrue(EvaluationDetail.EvaluationReason.DEFAULT.getRuleId() != null);
        assertEquals(Long.valueOf(4), EvaluationDetail.EvaluationReason.DEFAULT.getRuleId());
        
        // Test that MATCHED_RULE does not have built-in rule ID
        assertNull(EvaluationDetail.EvaluationReason.MATCHED_RULE.getRuleId());
    }
}
