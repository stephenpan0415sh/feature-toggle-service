package com.featuretoggle.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Evaluation detail model providing explainability for feature flag decisions.
 * Answers: Is it enabled? For whom? In which region? Why?
 * 
 * Example JSON structure:
 * {
 *   "flagKey": "new_checkout_flow",
 *   "enabled": true,
 *   "reason": "MATCHED_RULE",
 *   "matchedRuleId": "rule_001",
 *   "traceId": "trace_abc123",
 *   "environment": "prod",
 *   "region": "cn-beijing",
 *   "evaluatedAt": 1713849600000,
 *   "userContextSnapshot": {
 *     "uid": 123,
 *     "vip_level": 2
 *   },
 *   "matchedConditions": [
 *     "uid in [100, 200, 300]",
 *     "vip_level >= 2"
 *   ]
 * }
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationDetail(
    /**
     * Flag key that was evaluated
     */
    String flagKey,

    /**
     * Whether the flag is enabled (simple boolean output)
     */
    boolean enabled,

    /**
     * Reason for the evaluation result
     */
    EvaluationReason reason,

    /**
     * ID of the matched rule (if applicable).
     * Built-in rules: 1-100, Database rules: 101+
     */
    Long matchedRuleId,

    /**
     * Trace ID for debugging and correlation
     */
    String traceId,

    // Context information

    /**
     * Environment where evaluation occurred (prod/staging/dev)
     */
    String environment,

    /**
     * Geographic region (e.g., cn-east, us-west)
     */
    String region,

    /**
     * Release version associated with this flag evaluation
     * Example: "v1.2.3", "release-2024-04"
     */
    String releaseVersion,

    /**
     * Timestamp when evaluation occurred (milliseconds since epoch)
     */
    Long evaluatedAt,

    // User context snapshot

    /**
     * Snapshot of user attributes that were used in evaluation
     */
    Map<String, Object> userContextSnapshot,

    /**
     * List of human-readable condition descriptions that matched
     * Example: ["uid in [100,200]", "vip_level >= 2"]
     */
    List<String> matchedConditions
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Evaluation reasons explaining why a flag was evaluated to a certain value
     */
    public enum EvaluationReason {
        /**
         * No rules matched, returned default value
         */
        DEFAULT(BuiltInRuleId.DEFAULT_FALLBACK.getId()),

        /**
         * Matched a targeting rule
         */
        MATCHED_RULE(null),

        /**
         * Kill switch disabled the flag
         */
        KILL_SWITCH(null),

        /**
         * User is in whitelist
         */
        WHITELIST(null),

        /**
         * User is in blacklist
         */
        BLACKLIST(null),

        /**
         * User matched percentage rollout criteria
         */
        PERCENTAGE_ROLLOUT(null),

        /**
         * Error occurred during evaluation, returned default value
         */
        ERROR(BuiltInRuleId.ERROR_FALLBACK.getId()),

        /**
         * SDK version is incompatible, returned default value
         */
        SDK_INCOMPATIBLE(BuiltInRuleId.ERROR_FALLBACK.getId()),

        /**
         * Flag not found
         */
        FLAG_NOT_FOUND(BuiltInRuleId.DEFAULT_FALLBACK.getId());

        private final Long ruleId;

        EvaluationReason(Long ruleId) {
            this.ruleId = ruleId;
        }

        /**
         * Get the rule ID for this reason.
         * Returns null if this reason corresponds to an actual database rule (matchedRuleId will be set separately).
         * Returns built-in rule ID for default/error scenarios.
         */
        public Long getRuleId() {
            return ruleId;
        }
    }

    /**
     * Compact constructor to set default evaluatedAt timestamp
     */
    public EvaluationDetail {
        if (evaluatedAt == null) {
            evaluatedAt = System.currentTimeMillis();
        }
    }

    /**
     * Create a default evaluation detail (flag not found or error)
     */
    public static EvaluationDetail createDefault(String flagKey, String defaultValue, EvaluationReason reason) {
        Long ruleId = reason != null ? reason.getRuleId() : BuiltInRuleId.DEFAULT_FALLBACK.getId();
        return new EvaluationDetail(
                flagKey,
                Boolean.parseBoolean(defaultValue),
                reason,
                ruleId,  // matchedRuleId
                null,  // traceId
                null,  // environment
                null,  // region
                null,  // releaseVersion
                System.currentTimeMillis(),
                null,  // userContextSnapshot
                null   // matchedConditions
        );
    }

    /**
     * Check if evaluation was successful (not an error)
     */
    public boolean isSuccess() {
        return reason != EvaluationReason.ERROR && 
               reason != EvaluationReason.FLAG_NOT_FOUND &&
               reason != EvaluationReason.SDK_INCOMPATIBLE;
    }

    /**
     * Generate a summary string for logging
     */
    public String toSummaryString() {
        return String.format("Flag[%s] -> enabled=%s, reason=%s, ruleId=%s",
                flagKey, enabled, reason, matchedRuleId);
    }
}
