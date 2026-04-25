package com.featuretoggle.sdk.core.evaluator;

import com.featuretoggle.common.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RuleEvaluator.
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
class RuleEvaluatorTest {

    private final RuleEvaluator evaluator = new RuleEvaluator();

    // Test: Disabled flag returns default value
    @Test
    void shouldReturnDefaultWhenFlagIsDisabled() {
        FeatureFlag flag = createFlag("test_flag", 0, "false", List.of());
        UserContext user = createUser("123", Map.of("uid", 123));

        EvaluationDetail result = evaluator.evaluate(flag, user);

        assertFalse(result.enabled());
        assertEquals(EvaluationDetail.EvaluationReason.DEFAULT, result.reason());
        assertEquals("false", result.value());
    }

    // Test: No rules returns default value
    @Test
    void shouldReturnDefaultWhenNoRules() {
        FeatureFlag flag = createFlag("test_flag", 1, "true", List.of());
        UserContext user = createUser("123", Map.of("uid", 123));

        EvaluationDetail result = evaluator.evaluate(flag, user);

        assertTrue(result.enabled());
        assertEquals(EvaluationDetail.EvaluationReason.DEFAULT, result.reason());
    }

    // Test: Kill switch disables flag
    @Test
    void shouldDisableFlagWhenKillSwitchActive() {
        Rule killSwitch = Rule.builder()
            .id("rule_kill")
            .priority(0)
            .type(Rule.RuleType.KILL_SWITCH)
            .actionValue("false")
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "true", List.of(killSwitch));
        UserContext user = createUser("123", Map.of("uid", 123));

        EvaluationDetail result = evaluator.evaluate(flag, user);

        assertFalse(result.enabled());
        assertEquals(EvaluationDetail.EvaluationReason.KILL_SWITCH, result.reason());
        assertEquals("rule_kill", result.matchedRuleId());
    }

    // Test: Whitelist matches user
    @Test
    void shouldMatchWhitelistRule() {
        Condition condition = new Condition("uid", Condition.Operator.IN, List.of(1001, 1002, 1003));
        Rule whitelist = Rule.builder()
            .id("rule_whitelist")
            .priority(1)
            .type(Rule.RuleType.WHITELIST)
            .conditions(List.of(condition))
            .actionValue("v2")
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "v1", List.of(whitelist));
        
        // User in whitelist
        UserContext userInList = createUser("1001", Map.of("uid", 1001));
        EvaluationDetail result1 = evaluator.evaluate(flag, userInList);
        assertTrue(result1.enabled());
        assertEquals(EvaluationDetail.EvaluationReason.WHITELIST, result1.reason());

        // User not in whitelist
        UserContext userNotInList = createUser("9999", Map.of("uid", 9999));
        EvaluationDetail result2 = evaluator.evaluate(flag, userNotInList);
        assertEquals(EvaluationDetail.EvaluationReason.DEFAULT, result2.reason());
    }

    // Test: Targeting rule with multiple conditions (AND logic)
    @Test
    void shouldMatchTargetingRuleWithAllConditionsMet() {
        Condition regionCondition = new Condition("region", Condition.Operator.IN, List.of("cn-beijing"));
        Condition vipCondition = new Condition("vipLevel", Condition.Operator.GTE, List.of(2));
        
        Rule targeting = Rule.builder()
            .id("rule_targeting")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(regionCondition, vipCondition))
            .actionValue("v2")
            .description("VIP users in Beijing")
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "v1", List.of(targeting));
        
        // User matches all conditions
        UserContext matchingUser = createUser("123", Map.of(
            "uid", 123,
            "region", "cn-beijing",
            "vipLevel", 3
        ));
        EvaluationDetail result = evaluator.evaluate(flag, matchingUser);
        
        assertTrue(result.enabled());
        assertEquals(EvaluationDetail.EvaluationReason.MATCHED_RULE, result.reason());
        assertNotNull(result.matchedConditions());
        assertEquals(2, result.matchedConditions().size());
    }

    // Test: Targeting rule fails when one condition not met
    @Test
    void shouldNotMatchTargetingRuleWhenConditionFails() {
        Condition regionCondition = new Condition("region", Condition.Operator.IN, List.of("cn-beijing"));
        Condition vipCondition = new Condition("vipLevel", Condition.Operator.GTE, List.of(2));
        
        Rule targeting = Rule.builder()
            .id("rule_targeting")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(regionCondition, vipCondition))
            .actionValue("v2")
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "v1", List.of(targeting));
        
        // User has wrong region
        UserContext nonMatchingUser = createUser("123", Map.of(
            "uid", 123,
            "region", "cn-shanghai", // Wrong region
            "vipLevel", 3
        ));
        EvaluationDetail result = evaluator.evaluate(flag, nonMatchingUser);
        
        assertEquals(EvaluationDetail.EvaluationReason.DEFAULT, result.reason());
    }

    // Test: Percentage rollout is deterministic
    @Test
    void shouldBeDeterministicForPercentageRollout() {
        Rule percentageRule = Rule.builder()
            .id("rule_percentage")
            .priority(1)
            .type(Rule.RuleType.PERCENTAGE_ROLLOUT)
            .percentage(50)
            .hashAttribute("uid")
            .actionValue("v2")
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "v1", List.of(percentageRule));
        UserContext user = createUser("12345", Map.of("uid", 12345));

        // Evaluate multiple times - should always get same result
        EvaluationDetail result1 = evaluator.evaluate(flag, user);
        EvaluationDetail result2 = evaluator.evaluate(flag, user);
        EvaluationDetail result3 = evaluator.evaluate(flag, user);

        assertEquals(result1.enabled(), result2.enabled());
        assertEquals(result2.enabled(), result3.enabled());
    }

    // Test: Rules evaluated in priority order
    @Test
    void shouldEvaluateRulesInPriorityOrder() {
        // Low priority rule that would match
        Rule lowPriority = Rule.builder()
            .id("rule_low")
            .priority(10)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(new Condition("uid", Condition.Operator.IN, List.of(123))))
            .actionValue("low_priority_value")
            .build();

        // High priority rule that also matches
        Rule highPriority = Rule.builder()
            .id("rule_high")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(new Condition("uid", Condition.Operator.IN, List.of(123))))
            .actionValue("high_priority_value")
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "default", List.of(lowPriority, highPriority));
        UserContext user = createUser("123", Map.of("uid", 123));

        EvaluationDetail result = evaluator.evaluate(flag, user);

        // Should match high priority rule first
        assertEquals("rule_high", result.matchedRuleId());
        assertEquals("high_priority_value", result.value());
    }

    // Test: String contains operator
    @Test
    void shouldMatchStringContainsOperator() {
        Condition condition = new Condition("email", Condition.Operator.CONTAINS, List.of("@company.com"));
        Rule rule = Rule.builder()
            .id("rule_email")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .actionValue("employee")
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "public", List.of(rule));
        UserContext user = createUser("123", Map.of("email", "john@company.com"));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        
        assertTrue(result.enabled());
        assertEquals("employee", result.value());
    }

    // Test: Blacklist blocks user even when default is true
    @Test
    void shouldBlockBlacklistedUserEvenWhenDefaultIsTrue() {
        Condition blacklistCondition = new Condition("uid", Condition.Operator.IN, List.of(9999));
        Rule blacklist = Rule.builder()
            .id("rule_blacklist")
            .priority(1)
            .type(Rule.RuleType.BLACKLIST)
            .conditions(List.of(blacklistCondition))
            .actionValue("v1") // This value is ignored for blacklist
            .build();

        // Flag default is TRUE (enabled)
        FeatureFlag flag = createFlag("test_flag", 1, "true", List.of(blacklist));
        
        // Blacklisted user
        UserContext blacklistedUser = createUser("9999", Map.of("uid", 9999));
        EvaluationDetail result = evaluator.evaluate(flag, blacklistedUser);
        
        // Blacklist always returns false regardless of default value
        assertFalse(result.enabled(), "Blacklisted user should not see the feature");
        assertEquals(EvaluationDetail.EvaluationReason.BLACKLIST, result.reason());
        assertEquals("rule_blacklist", result.matchedRuleId());
        
        // Non-blacklisted user should get default (true)
        UserContext normalUser = createUser("1234", Map.of("uid", 1234));
        EvaluationDetail normalResult = evaluator.evaluate(flag, normalUser);
        assertTrue(normalResult.enabled(), "Normal user should see the feature (default=true)");
        assertEquals(EvaluationDetail.EvaluationReason.DEFAULT, normalResult.reason());
    }

    // Helper methods

    private FeatureFlag createFlag(String flagKey, int status, String defaultValue, List<Rule> rules) {
        return new FeatureFlag(
            null, // id
            1L, // appId
            flagKey,
            "Test Flag",
            "prod",
            status,
            defaultValue,
            1L, // version
            "v1.0.0", // releaseVersion
            rules,
            null, // createdAt
            null  // updatedAt
        );
    }

    private UserContext createUser(String userId, Map<String, Object> attributes) {
        return new UserContext(userId, attributes);
    }
}
