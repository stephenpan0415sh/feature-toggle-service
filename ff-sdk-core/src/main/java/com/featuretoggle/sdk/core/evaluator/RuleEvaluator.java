package com.featuretoggle.sdk.core.evaluator;

import com.featuretoggle.common.exception.RuleEvaluationException;
import com.featuretoggle.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core rule evaluator that matches user context against feature flag rules.
 * This is a pure function with no external dependencies (no Redis, HTTP, etc.).
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
public class RuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RuleEvaluator.class);

    /**
     * Evaluate a feature flag for a given user context.
     * 
     * @param flag The feature flag to evaluate
     * @param userContext The user context containing attributes (can be null)
     * @return EvaluationDetail with full explainability
     */
    public EvaluationDetail evaluate(FeatureFlag flag, UserContext userContext) {
        // Handle null userContext by creating empty one
        if (userContext == null) {
            userContext = new UserContext("", Map.of());
        }
        
        try {
            // Check if flag is disabled
            if (!flag.isEnabled()) {
                return buildEvaluationDetail(flag, userContext, 
                    EvaluationDetail.EvaluationReason.DEFAULT, 
                    BuiltInRuleId.FLAG_DISABLED.getId(),  // Flag is disabled
                    Boolean.parseBoolean(flag.getDefaultValue()));
            }

            // Get rules sorted by priority
            List<Rule> rules = flag.getRules();
            if (rules == null || rules.isEmpty()) {
                return buildEvaluationDetail(flag, userContext,
                    EvaluationDetail.EvaluationReason.DEFAULT,
                    BuiltInRuleId.NO_RULES_CONFIGURED.getId(),  // No rules configured
                    Boolean.parseBoolean(flag.getDefaultValue()));
            }

            // Rules are already sorted by priority from database (orderBy priority ASC)
            // Evaluate rules in priority order
            for (Rule rule : rules) {
                EvaluationResult result = evaluateRule(rule, userContext, flag.getFlagKey());
                
                if (result.matched()) {
                    boolean enabled = result.enabled();
                    
                    // Blacklist is special: if matched, immediately return false
                    if (result.reason() == EvaluationDetail.EvaluationReason.BLACKLIST) {
                        log.debug("User {} blacklisted by rule {}, returning false", 
                            userContext.userId(), rule.getId());
                        enabled = false;
                    }
                    
                    return buildEvaluationDetail(flag, userContext,
                        result.reason(),
                        Long.parseLong(rule.getId()),
                        enabled);
                }
            }

            // No rules matched, return default value
            return buildEvaluationDetail(flag, userContext,
                EvaluationDetail.EvaluationReason.DEFAULT,
                BuiltInRuleId.DEFAULT_FALLBACK.getId(),
                Boolean.parseBoolean(flag.getDefaultValue()));

        } catch (Exception e) {
            log.error("Error evaluating flag: {}", flag.getFlagKey(), e);
            return buildEvaluationDetail(flag, userContext,
                EvaluationDetail.EvaluationReason.ERROR,
                BuiltInRuleId.ERROR_FALLBACK.getId(),
                Boolean.parseBoolean(flag.getDefaultValue()));
        }
    }

    /**
     * Evaluate a single rule against user context.
     * 
     * @param rule The rule to evaluate
     * @param userContext The user context
     * @param flagKey The feature flag key (for error context)
     * @return EvaluationResult indicating if the rule matched
     */
    private EvaluationResult evaluateRule(Rule rule, UserContext userContext, String flagKey) {
        try {
            // Validate rule
            rule.validate();

            // Handle different rule types
            return switch (rule.getType()) {
                case KILL_SWITCH -> evaluateKillSwitch(rule);
                case WHITELIST -> evaluateWhitelist(rule, userContext);
                case BLACKLIST -> evaluateBlacklist(rule, userContext);
                case TARGETING -> evaluateTargeting(rule, userContext);
                case PERCENTAGE_ROLLOUT -> evaluatePercentageRollout(rule, userContext);
            };
        } catch (Exception e) {
            // Wrap unexpected exceptions in business exception with full context
            log.error("Unexpected error evaluating rule {} for flag {}", rule.getId(), flagKey, e);
            throw new RuleEvaluationException(
                "Rule evaluation failed: " + e.getMessage(),
                rule.getId(),
                flagKey,
                e
            );
        }
    }

    /**
     * Evaluate kill switch rule.
     * Kill switch always matches and returns the action enabled value (usually false).
     */
    private EvaluationResult evaluateKillSwitch(Rule rule) {
        log.debug("Kill switch rule {} activated", rule.getId());
        return new EvaluationResult(true, rule.getRuleDefaultEnabled(), 
            EvaluationDetail.EvaluationReason.KILL_SWITCH);
    }

    /**
     * Evaluate whitelist rule.
     * Checks if user ID is in the whitelist.
     */
    private EvaluationResult evaluateWhitelist(Rule rule, UserContext userContext) {
        List<Condition> conditions = rule.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return EvaluationResult.notMatched();
        }

        // Get the first condition (should be uid IN [...])
        Condition condition = conditions.get(0);
        Object userId = userContext.getAttribute("uid");
        
        if (userId == null || condition.values() == null) {
            return EvaluationResult.notMatched();
        }

        boolean isInList = condition.values().stream()
            .anyMatch(value -> value.equals(userId));

        if (isInList) {
            log.debug("User {} matched whitelist rule {}", userId, rule.getId());
            return new EvaluationResult(true, rule.getRuleDefaultEnabled(),
                EvaluationDetail.EvaluationReason.WHITELIST);
        }

        return EvaluationResult.notMatched();
    }

    /**
     * Evaluate blacklist rule.
     * If user is in blacklist, return default value (not matched from rule perspective).
     */
    private EvaluationResult evaluateBlacklist(Rule rule, UserContext userContext) {
        List<Condition> conditions = rule.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return EvaluationResult.notMatched();
        }

        Condition condition = conditions.get(0);
        Object userId = userContext.getAttribute("uid");
        
        if (userId == null || condition.values() == null) {
            return EvaluationResult.notMatched();
        }

        boolean isInList = condition.values().stream()
            .anyMatch(value -> value.equals(userId));

        if (isInList) {
            log.debug("User {} matched blacklist rule {}, returning false", userId, rule.getId());
            // Blacklist matched, return false
            return new EvaluationResult(true, false,
                EvaluationDetail.EvaluationReason.BLACKLIST);
        }

        return EvaluationResult.notMatched();
    }

    /**
     * Evaluate targeting rule.
     * All conditions must match (AND logic).
     */
    private EvaluationResult evaluateTargeting(Rule rule, UserContext userContext) {
        List<Condition> conditions = rule.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return EvaluationResult.notMatched();
        }

        // All conditions must match (AND logic)
        boolean allMatched = conditions.stream()
            .allMatch(condition -> evaluateCondition(condition, userContext));

        if (allMatched) {
            List<String> matchedConditions = conditions.stream()
                .map(Condition::toReadableString)
                .collect(Collectors.toList());
            
            log.debug("User matched targeting rule {}: {}", rule.getId(), matchedConditions);
            return new EvaluationResult(true, rule.getRuleDefaultEnabled(),
                EvaluationDetail.EvaluationReason.MATCHED_RULE,
                matchedConditions);
        }

        return EvaluationResult.notMatched();
    }

    /**
     * Evaluate percentage rollout rule.
     * Uses deterministic hashing to ensure consistent results for the same user.
     */
    private EvaluationResult evaluatePercentageRollout(Rule rule, UserContext userContext) {
        String hashAttribute = rule.getHashAttribute();
        if (hashAttribute == null || hashAttribute.isEmpty()) {
            log.warn("Percentage rollout rule {} missing hashAttribute", rule.getId());
            return EvaluationResult.notMatched();
        }

        Object attributeValue = userContext.getAttribute(hashAttribute);
        if (attributeValue == null) {
            log.debug("User missing attribute {} for percentage rollout", hashAttribute);
            return EvaluationResult.notMatched();
        }

        // Calculate hash and determine if user is in percentage
        int percentage = rule.getPercentage();
        boolean isInPercentage = PercentageCalculator.isInPercentage(
            attributeValue.toString(), 
            percentage
        );

        if (isInPercentage) {
            log.debug("User {} in {}% rollout for rule {}", 
                attributeValue, percentage, rule.getId());
            return new EvaluationResult(true, rule.getRuleDefaultEnabled(),
                EvaluationDetail.EvaluationReason.PERCENTAGE_ROLLOUT);
        }

        return EvaluationResult.notMatched();
    }

    /**
     * Evaluate a single condition against user context.
     */
    private boolean evaluateCondition(Condition condition, UserContext userContext) {
        Object userValue = userContext.getAttribute(condition.attribute());
        
        // Handle unary operators
        if (condition.operator() == Condition.Operator.IS_TRUE) {
            return userValue instanceof Boolean && (Boolean) userValue;
        }
        if (condition.operator() == Condition.Operator.IS_FALSE) {
            return userValue instanceof Boolean && !(Boolean) userValue;
        }

        if (userValue == null || condition.values() == null || condition.values().isEmpty()) {
            return false;
        }

        return switch (condition.operator()) {
            case EQ -> compareEqual(userValue, condition.values().get(0));
            case NEQ -> !compareEqual(userValue, condition.values().get(0));
            case IN -> isInList(userValue, condition.values());
            case NOT_IN -> !isInList(userValue, condition.values());
            case GT -> compareGreaterThan(userValue, condition.values().get(0));
            case GTE -> compareGreaterThanOrEqual(userValue, condition.values().get(0));
            case LT -> compareLessThan(userValue, condition.values().get(0));
            case LTE -> compareLessThanOrEqual(userValue, condition.values().get(0));
            case CONTAINS -> compareContains(userValue, condition.values().get(0));
            case STARTS_WITH -> compareStartsWith(userValue, condition.values().get(0));
            case ENDS_WITH -> compareEndsWith(userValue, condition.values().get(0));
            case REGEX -> compareRegex(userValue, condition.values().get(0));
            default -> false;
        };
    }

    // Comparison helper methods

    private boolean compareEqual(Object userValue, Object conditionValue) {
        if (userValue instanceof Number && conditionValue instanceof Number) {
            return ((Number) userValue).doubleValue() == ((Number) conditionValue).doubleValue();
        }
        return Objects.equals(userValue.toString(), conditionValue.toString());
    }

    private boolean isInList(Object userValue, List<Object> values) {
        return values.stream().anyMatch(value -> compareEqual(userValue, value));
    }

    private boolean compareGreaterThan(Object userValue, Object conditionValue) {
        if (userValue instanceof Number && conditionValue instanceof Number) {
            return ((Number) userValue).doubleValue() > ((Number) conditionValue).doubleValue();
        }
        return userValue.toString().compareTo(conditionValue.toString()) > 0;
    }

    private boolean compareGreaterThanOrEqual(Object userValue, Object conditionValue) {
        if (userValue instanceof Number && conditionValue instanceof Number) {
            return ((Number) userValue).doubleValue() >= ((Number) conditionValue).doubleValue();
        }
        return userValue.toString().compareTo(conditionValue.toString()) >= 0;
    }

    private boolean compareLessThan(Object userValue, Object conditionValue) {
        if (userValue instanceof Number && conditionValue instanceof Number) {
            return ((Number) userValue).doubleValue() < ((Number) conditionValue).doubleValue();
        }
        return userValue.toString().compareTo(conditionValue.toString()) < 0;
    }

    private boolean compareLessThanOrEqual(Object userValue, Object conditionValue) {
        if (userValue instanceof Number && conditionValue instanceof Number) {
            return ((Number) userValue).doubleValue() <= ((Number) conditionValue).doubleValue();
        }
        return userValue.toString().compareTo(conditionValue.toString()) <= 0;
    }

    private boolean compareContains(Object userValue, Object conditionValue) {
        return userValue.toString().contains(conditionValue.toString());
    }

    private boolean compareStartsWith(Object userValue, Object conditionValue) {
        return userValue.toString().startsWith(conditionValue.toString());
    }

    private boolean compareEndsWith(Object userValue, Object conditionValue) {
        return userValue.toString().endsWith(conditionValue.toString());
    }

    private boolean compareRegex(Object userValue, Object conditionValue) {
        try {
            return userValue.toString().matches(conditionValue.toString());
        } catch (Exception e) {
            log.warn("Invalid regex pattern: {}", conditionValue);
            return false;
        }
    }

    /**
     * Build evaluation detail with full explainability.
     */
    private EvaluationDetail buildEvaluationDetail(
            FeatureFlag flag,
            UserContext userContext,
            EvaluationDetail.EvaluationReason reason,
            Long matchedRuleId,
            boolean enabled) {
        
        // Extract matched conditions for explainability (only for database rules)
        List<String> matchedConditions = null;
        if (matchedRuleId != null && matchedRuleId > 100 && flag.getRules() != null) {
            Optional<Rule> matchedRule = flag.getRules().stream()
                .filter(r -> r.getId().equals(String.valueOf(matchedRuleId)))
                .findFirst();
            
            if (matchedRule.isPresent() && matchedRule.get().getConditions() != null) {
                matchedConditions = matchedRule.get().getConditions().stream()
                    .map(Condition::toReadableString)
                    .collect(Collectors.toList());
            }
        }

        // Build user context snapshot (only relevant attributes)
        Map<String, Object> userSnapshot = userContext.attributes();

        return new EvaluationDetail(
            flag.getFlagKey(),
            enabled,
            reason,
            matchedRuleId,
            UUID.randomUUID().toString(), // traceId
            flag.getEnvironment(),
            userContext.getStringAttribute("region"),
            flag.getReleaseVersion(),
            System.currentTimeMillis(),
            userSnapshot,
            matchedConditions
        );
    }

    /**
     * Internal result of rule evaluation.
     */
    private record EvaluationResult(
        boolean matched,
        boolean enabled,
        EvaluationDetail.EvaluationReason reason,
        List<String> matchedConditions
    ) {
        static EvaluationResult notMatched() {
            return new EvaluationResult(false, false, null, null);
        }

        EvaluationResult(boolean matched, boolean enabled, EvaluationDetail.EvaluationReason reason) {
            this(matched, enabled, reason, null);
        }
        
        /**
         * Get the enabled state (for backward compatibility)
         */
        public boolean enabled() {
            return enabled;
        }
    }
}
