package com.featuretoggle.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Rule model for feature flag evaluation.
 * Rules are evaluated in priority order (lower number = higher priority).
 * 
 * Example JSON structure:
 * {
 *   "id": "rule_001",
 *   "priority": 1,
 *   "type": "TARGETING",
 *   "conditions": [
 *     {"attribute": "uid", "operator": "in", "values": [1001, 1002, 1003]},
 *     {"attribute": "region", "operator": "eq", "values": ["cn-beijing"]}
 *   ],
 *   "actionValue": "v2",
 *   "percentage": null,
 *   "hashAttribute": null,
 *   "description": "VIP users in Beijing"
 * }
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Rule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Rule types
     */
    public enum RuleType {
        /**
         * Kill switch - immediately disables the flag for all users
         */
        KILL_SWITCH,

        /**
         * Whitelist - specific users always get the action value
         */
        WHITELIST,

        /**
         * Blacklist - specific users always get default value
         */
        BLACKLIST,

        /**
         * Targeting rules based on user attributes
         */
        TARGETING,

        /**
         * Percentage rollout using deterministic hashing
         */
        PERCENTAGE_ROLLOUT
    }

    /**
     * Unique rule identifier
     */
    private String id;

    /**
     * Priority (lower number = higher priority, evaluated first)
     */
    private Integer priority;

    /**
     * Type of rule
     */
    private RuleType type;

    /**
     * List of conditions that must be met (AND logic)
     */
    private List<Condition> conditions;

    /**
     * Value to return when this rule matches
     */
    private String actionValue;

    /**
     * Percentage for PERCENTAGE_ROLLOUT type (0-100)
     */
    private Integer percentage;

    /**
     * User attribute to use for hashing (e.g., "uid", "email")
     * Only used for PERCENTAGE_ROLLOUT type
     */
    private String hashAttribute;

    /**
     * Human-readable description for explainability
     */
    private String description;

    /**
     * Check if this is a kill switch rule
     */
    @JsonProperty("isKillSwitch")
    public boolean isKillSwitch() {
        return type == RuleType.KILL_SWITCH;
    }

    /**
     * Check if this is a percentage rollout rule
     */
    @JsonProperty("isPercentageRollout")
    public boolean isPercentageRollout() {
        return type == RuleType.PERCENTAGE_ROLLOUT;
    }

    /**
     * Validate rule configuration
     */
    public void validate() {
        if (type == null) {
            throw new IllegalArgumentException("Rule type cannot be null");
        }

        if (priority == null || priority < 0) {
            throw new IllegalArgumentException("Rule priority must be non-negative");
        }

        if (type == RuleType.PERCENTAGE_ROLLOUT) {
            if (percentage == null || percentage < 0 || percentage > 100) {
                throw new IllegalArgumentException("Percentage must be between 0 and 100");
            }
            if (hashAttribute == null || hashAttribute.isEmpty()) {
                throw new IllegalArgumentException("Hash attribute is required for percentage rollout");
            }
        }

        if (actionValue == null || actionValue.isEmpty()) {
            throw new IllegalArgumentException("Action value cannot be empty");
        }
    }
}
