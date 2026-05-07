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
            .id("101")
            .priority(0)
            .type(Rule.RuleType.KILL_SWITCH)
            .ruleDefaultEnabled(false)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "true", List.of(killSwitch));
        UserContext user = createUser("123", Map.of("uid", 123));

        EvaluationDetail result = evaluator.evaluate(flag, user);

        assertFalse(result.enabled());
        assertEquals(EvaluationDetail.EvaluationReason.KILL_SWITCH, result.reason());
        assertEquals(Long.valueOf(101), result.matchedRuleId());
    }

    // Test: Whitelist matches user
    @Test
    void shouldMatchWhitelistRule() {
        Condition condition = new Condition("uid", Condition.Operator.IN, List.of(1001, 1002, 1003));
        Rule whitelist = Rule.builder()
            .id("102")
            .priority(1)
            .type(Rule.RuleType.WHITELIST)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
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
            .id("103")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(regionCondition, vipCondition))
            .ruleDefaultEnabled(true)
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
            .id("104")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(regionCondition, vipCondition))
            .ruleDefaultEnabled(true)
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
            .id("105")
            .priority(1)
            .type(Rule.RuleType.PERCENTAGE_ROLLOUT)
            .percentage(50)
            .hashAttribute("uid")
            .ruleDefaultEnabled(true)
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
        // High priority rule that also matches
        Rule highPriority = Rule.builder()
            .id("106")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(new Condition("uid", Condition.Operator.IN, List.of(123))))
            .ruleDefaultEnabled(true)
            .build();

        // Low priority rule that would match
        Rule lowPriority = Rule.builder()
            .id("107")
            .priority(10)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(new Condition("uid", Condition.Operator.IN, List.of(123))))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "default", List.of(highPriority, lowPriority));
        UserContext user = createUser("123", Map.of("uid", 123));

        EvaluationDetail result = evaluator.evaluate(flag, user);

        // Should match high priority rule first
        assertEquals(Long.valueOf(106), result.matchedRuleId());
        assertTrue(result.enabled());
    }

    // Test: String contains operator
    @Test
    void shouldMatchStringContainsOperator() {
        Condition condition = new Condition("email", Condition.Operator.CONTAINS, List.of("@company.com"));
        Rule rule = Rule.builder()
            .id("108")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "public", List.of(rule));
        UserContext user = createUser("123", Map.of("email", "john@company.com"));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        
        assertTrue(result.enabled());
    }

    // Test: Blacklist blocks user even when default is true
    @Test
    void shouldBlockBlacklistedUserEvenWhenDefaultIsTrue() {
        Condition blacklistCondition = new Condition("uid", Condition.Operator.IN, List.of(9999));
        Rule blacklist = Rule.builder()
            .id("109")
            .priority(1)
            .type(Rule.RuleType.BLACKLIST)
            .conditions(List.of(blacklistCondition))
            .ruleDefaultEnabled(true) // This value is ignored for blacklist
            .build();

        // Flag default is TRUE (enabled)
        FeatureFlag flag = createFlag("test_flag", 1, "true", List.of(blacklist));
        
        // Blacklisted user
        UserContext blacklistedUser = createUser("9999", Map.of("uid", 9999));
        EvaluationDetail result = evaluator.evaluate(flag, blacklistedUser);
        
        // Blacklist always returns false regardless of default value
        assertFalse(result.enabled(), "Blacklisted user should not see the feature");
        assertEquals(EvaluationDetail.EvaluationReason.BLACKLIST, result.reason());
        assertEquals(Long.valueOf(109), result.matchedRuleId());
        
        // Non-blacklisted user should get default (true)
        UserContext normalUser = createUser("1234", Map.of("uid", 1234));
        EvaluationDetail normalResult = evaluator.evaluate(flag, normalUser);
        assertTrue(normalResult.enabled(), "Normal user should see the feature (default=true)");
        assertEquals(EvaluationDetail.EvaluationReason.DEFAULT, normalResult.reason());
    }

    // Test: EQ operator with numbers
    @Test
    void shouldMatchEqualOperatorWithNumbers() {
        Condition condition = new Condition("age", Condition.Operator.EQ, List.of(25));
        Rule rule = Rule.builder()
            .id("110")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "false", List.of(rule));
        UserContext user = createUser("123", Map.of("age", 25));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        assertTrue(result.enabled());
        assertEquals(EvaluationDetail.EvaluationReason.MATCHED_RULE, result.reason());
    }

    // Test: NEQ operator
    @Test
    void shouldMatchNotEqualOperator() {
        Condition condition = new Condition("status", Condition.Operator.NEQ, List.of("inactive"));
        Rule rule = Rule.builder()
            .id("111")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "false", List.of(rule));
        UserContext user = createUser("123", Map.of("status", "active"));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        assertTrue(result.enabled());
    }

    // Test: NOT_IN operator
    @Test
    void shouldMatchNotInOperator() {
        Condition condition = new Condition("country", Condition.Operator.NOT_IN, List.of("CN", "RU"));
        Rule rule = Rule.builder()
            .id("112")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "false", List.of(rule));
        UserContext user = createUser("123", Map.of("country", "US"));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        assertTrue(result.enabled());
    }

    // Test: GT operator with numbers
    @Test
    void shouldMatchGreaterThanOperator() {
        Condition condition = new Condition("score", Condition.Operator.GT, List.of(80));
        Rule rule = Rule.builder()
            .id("113")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "false", List.of(rule));
        UserContext user = createUser("123", Map.of("score", 95));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        assertTrue(result.enabled());
    }

    // Test: GTE operator
    @Test
    void shouldMatchGreaterThanOrEqualOperator() {
        Condition condition = new Condition("score", Condition.Operator.GTE, List.of(80));
        Rule rule = Rule.builder()
            .id("114")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "false", List.of(rule));
        UserContext user = createUser("123", Map.of("score", 80));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        assertTrue(result.enabled());
    }

    // Test: LT operator
    @Test
    void shouldMatchLessThanOperator() {
        Condition condition = new Condition("age", Condition.Operator.LT, List.of(18));
        Rule rule = Rule.builder()
            .id("115")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "false", List.of(rule));
        UserContext user = createUser("123", Map.of("age", 16));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        assertTrue(result.enabled());
    }

    // Test: LTE operator
    @Test
    void shouldMatchLessThanOrEqualOperator() {
        Condition condition = new Condition("age", Condition.Operator.LTE, List.of(18));
        Rule rule = Rule.builder()
            .id("116")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "false", List.of(rule));
        UserContext user = createUser("123", Map.of("age", 18));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        assertTrue(result.enabled());
    }

    // Test: STARTS_WITH operator
    @Test
    void shouldMatchStartsWithOperator() {
        Condition condition = new Condition("email", Condition.Operator.STARTS_WITH, List.of("admin@"));
        Rule rule = Rule.builder()
            .id("117")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "false", List.of(rule));
        UserContext user = createUser("123", Map.of("email", "admin@company.com"));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        assertTrue(result.enabled());
    }

    // Test: ENDS_WITH operator
    @Test
    void shouldMatchEndsWithOperator() {
        Condition condition = new Condition("domain", Condition.Operator.ENDS_WITH, List.of(".edu"));
        Rule rule = Rule.builder()
            .id("118")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "false", List.of(rule));
        UserContext user = createUser("123", Map.of("domain", "university.edu"));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        assertTrue(result.enabled());
    }

    // Test: REGEX operator
    @Test
    void shouldMatchRegexOperator() {
        Condition condition = new Condition("phone", Condition.Operator.REGEX, List.of("^1[3-9]\\d{9}$"));
        Rule rule = Rule.builder()
            .id("119")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "false", List.of(rule));
        UserContext user = createUser("123", Map.of("phone", "13812345678"));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        assertTrue(result.enabled());
    }

    // Test: IS_TRUE operator
    @Test
    void shouldMatchIsTrueOperator() {
        Condition condition = new Condition("isPremium", Condition.Operator.IS_TRUE, List.of());
        Rule rule = Rule.builder()
            .id("120")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "false", List.of(rule));
        UserContext user = createUser("123", Map.of("isPremium", true));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        assertTrue(result.enabled());
    }

    // Test: IS_FALSE operator
    @Test
    void shouldMatchIsFalseOperator() {
        Condition condition = new Condition("isBanned", Condition.Operator.IS_FALSE, List.of());
        Rule rule = Rule.builder()
            .id("121")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "false", List.of(rule));
        UserContext user = createUser("123", Map.of("isBanned", false));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        assertTrue(result.enabled());
    }

    // Test: String comparison with GT/LT operators
    @Test
    void shouldCompareStringsWithGreaterThanOperator() {
        Condition condition = new Condition("name", Condition.Operator.GT, List.of("Alice"));
        Rule rule = Rule.builder()
            .id("122")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(List.of(condition))
            .ruleDefaultEnabled(true)
            .build();

        FeatureFlag flag = createFlag("test_flag", 1, "false", List.of(rule));
        UserContext user = createUser("123", Map.of("name", "Bob"));

        EvaluationDetail result = evaluator.evaluate(flag, user);
        assertTrue(result.enabled());
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
