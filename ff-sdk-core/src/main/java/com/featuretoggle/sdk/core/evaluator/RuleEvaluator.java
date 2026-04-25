package com.featuretoggle.sdk.core.evaluator;

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
     * @param userContext The user context containing attributes
     * @return EvaluationDetail with full explainability
     */
    public EvaluationDetail evaluate(FeatureFlag flag, UserContext userContext) {
        try {
            // Check if flag is disabled
            if (!flag.isEnabled()) {
                return buildEvaluationDetail(flag, userContext, 
                    EvaluationDetail.EvaluationReason.DEFAULT, 
                    null, 
                    flag.getDefaultValue(),
                    Boolean.parseBoolean(flag.getDefaultValue()));
            }

            // Get rules sorted by priority
            List<Rule> rules = flag.getRules();
            if (rules == null || rules.isEmpty()) {
                return buildEvaluationDetail(flag, userContext,
                    EvaluationDetail.EvaluationReason.DEFAULT,
                    null,
                    flag.getDefaultValue(),
                    Boolean.parseBoolean(flag.getDefaultValue()));
            }

            // Sort rules by priority (lower number = higher priority)
            List<Rule> sortedRules = rules.stream()
                .sorted(Comparator.comparingInt(Rule::getPriority))
                .collect(Collectors.toList());

            // Evaluate rules in priority order
            for (Rule rule : sortedRules) {
                EvaluationResult result = evaluateRule(rule, userContext);
                
                if (result.matched()) {
                    boolean enabled = determineEnabledState(rule, result.value());
                    return buildEvaluationDetail(flag, userContext,
                        result.reason(),
                        rule.getId(),
                        result.value(),
                        enabled);
                }
                
                // Blacklist is special: if matched, immediately return false
                if (result.reason() == EvaluationDetail.EvaluationReason.BLACKLIST) {
                    log.debug("User {} blacklisted by rule {}, returning false", 
                        userContext.userId(), rule.getId());
                    return buildEvaluationDetail(flag, userContext,
                        EvaluationDetail.EvaluationReason.BLACKLIST,
                        rule.getId(),
                        "false",
                        false);
                }
            }

            // No rules matched, return default value
            return buildEvaluationDetail(flag, userContext,
                EvaluationDetail.EvaluationReason.DEFAULT,
                null,
                flag.getDefaultValue(),
                Boolean.parseBoolean(flag.getDefaultValue()));

        } catch (Exception e) {
            log.error("Error evaluating flag: {}", flag.getFlagKey(), e);
            return buildEvaluationDetail(flag, userContext,
                EvaluationDetail.EvaluationReason.ERROR,
                null,
                flag.getDefaultValue(),
                Boolean.parseBoolean(flag.getDefaultValue()));
        }
    }

    /**
     * Evaluate a single rule against user context.
     * 
     * @param rule The rule to evaluate
     * @param userContext The user context
     * @return EvaluationResult indicating if the rule matched
     */
    private EvaluationResult evaluateRule(Rule rule, UserContext userContext) {
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
            log.warn("Error evaluating rule {}: {}", rule.getId(), e.getMessage());
            return EvaluationResult.notMatched();
        }
    }

    /**
     * Evaluate kill switch rule.
     * Kill switch always matches and returns the action value (usually "false").
     */
    private EvaluationResult evaluateKillSwitch(Rule rule) {
        log.debug("Kill switch rule {} activated", rule.getId());
        return new EvaluationResult(true, rule.getActionValue(), 
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
            return new EvaluationResult(true, rule.getActionValue(),
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
            log.debug("User {} matched blacklist rule {}, returning default", userId, rule.getId());
            // Blacklist means "don't apply this rule", so we return not matched
            // but with a special marker to use default value
            return new EvaluationResult(false, null,
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
            return new EvaluationResult(true, rule.getActionValue(),
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
            return new EvaluationResult(true, rule.getActionValue(),
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
     * Determine if the flag is enabled based on the rule's action value.
     */
    private boolean determineEnabledState(Rule rule, String actionValue) {
        if (actionValue == null) {
            return false;
        }
        // For kill switch, action value "false" means disabled
        if (rule.getType() == Rule.RuleType.KILL_SWITCH) {
            return Boolean.parseBoolean(actionValue);
        }
        // For other rules, any non-null action value means enabled
        return true;
    }

    /**
     * Build evaluation detail with full explainability.
     */
    private EvaluationDetail buildEvaluationDetail(
            FeatureFlag flag,
            UserContext userContext,
            EvaluationDetail.EvaluationReason reason,
            String matchedRuleId,
            String value,
            boolean enabled) {
        
        // Extract matched conditions for explainability
        List<String> matchedConditions = null;
        if (matchedRuleId != null && flag.getRules() != null) {
            Optional<Rule> matchedRule = flag.getRules().stream()
                .filter(r -> r.getId().equals(matchedRuleId))
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
            value,
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
        String value,
        EvaluationDetail.EvaluationReason reason,
        List<String> matchedConditions
    ) {
        static EvaluationResult notMatched() {
            return new EvaluationResult(false, null, null, null);
        }

        EvaluationResult(boolean matched, String value, EvaluationDetail.EvaluationReason reason) {
            this(matched, value, reason, null);
        }
    }
}
