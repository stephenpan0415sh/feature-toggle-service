package com.featuretoggle.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;

/**
 * Condition model for rule evaluation.
 * Represents a single condition like "uid in [1, 100]" or "region equals cn-beijing".
 * 
 * Example JSON structures:
 * 
 * 1. IN operator (user ID in list):
 * {
 *   "attribute": "uid",
 *   "operator": "in",
 *   "values": [1001, 1002, 1003]
 * }
 * 
 * 2. EQ operator (exact match):
 * {
 *   "attribute": "region",
 *   "operator": "eq",
 *   "values": ["cn-beijing"]
 * }
 * 
 * 3. GTE operator (greater than or equal):
 * {
 *   "attribute": "vip_level",
 *   "operator": "gte",
 *   "values": [2]
 * }
 * 
 * 4. CONTAINS operator (string contains):
 * {
 *   "attribute": "email",
 *   "operator": "contains",
 *   "values": ["@company.com"]
 * }
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Condition(
    /**
     * User attribute name to evaluate (e.g., "uid", "region", "vip_level")
     */
    String attribute,

    /**
     * Operator to use for comparison
     */
    Operator operator,

    /**
     * Values to compare against (can be numbers, strings, or booleans)
     * For unary operators (IS_TRUE, IS_FALSE), this can be empty
     */
    List<Object> values
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Supported operators for condition evaluation
     */
    public enum Operator {
        /**
         * Equals - exact match
         */
        EQ,

        /**
         * Not equals
         */
        NEQ,

        /**
         * In - value is in the list
         */
        IN,

        /**
         * Not in - value is not in the list
         */
        NOT_IN,

        /**
         * Greater than
         */
        GT,

        /**
         * Greater than or equal
         */
        GTE,

        /**
         * Less than
         */
        LT,

        /**
         * Less than or equal
         */
        LTE,

        /**
         * String contains substring
         */
        CONTAINS,

        /**
         * String starts with prefix
         */
        STARTS_WITH,

        /**
         * String ends with suffix
         */
        ENDS_WITH,

        /**
         * Regular expression match
         */
        REGEX,

        /**
         * Boolean is true
         */
        IS_TRUE,

        /**
         * Boolean is false
         */
        IS_FALSE
    }

    /**
     * Compact constructor for validation
     */
    public Condition {
        if (attribute == null || attribute.isEmpty()) {
            throw new IllegalArgumentException("Condition attribute cannot be empty");
        }

        if (operator == null) {
            throw new IllegalArgumentException("Condition operator cannot be null");
        }

        // Unary operators don't require values
        if (operator != Operator.IS_TRUE && operator != Operator.IS_FALSE) {
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("Condition values cannot be empty for operator: " + operator);
            }
        }
    }

    /**
     * Generate human-readable description for explainability
     * Example: "uid in [1001, 1002, 1003]" or "region equals cn-beijing"
     */
    public String toReadableString() {
        if (operator == Operator.IS_TRUE) {
            return attribute + " is true";
        }
        if (operator == Operator.IS_FALSE) {
            return attribute + " is false";
        }

        String operatorStr = formatOperator(operator);
        String valuesStr = formatValues(values);

        return attribute + " " + operatorStr + " " + valuesStr;
    }

    /**
     * Format operator for human-readable output
     */
    private String formatOperator(Operator op) {
        return switch (op) {
            case EQ -> "equals";
            case NEQ -> "not equals";
            case IN -> "in";
            case NOT_IN -> "not in";
            case GT -> "greater than";
            case GTE -> "greater than or equal";
            case LT -> "less than";
            case LTE -> "less than or equal";
            case CONTAINS -> "contains";
            case STARTS_WITH -> "starts with";
            case ENDS_WITH -> "ends with";
            case REGEX -> "matches regex";
            default -> op.name().toLowerCase();
        };
    }

    /**
     * Format values for human-readable output
     */
    private String formatValues(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        if (values.size() == 1) {
            return values.get(0).toString();
        }

        // Multiple values - format as array
        return "[" + String.join(", ", 
            values.stream().map(Object::toString).toArray(String[]::new)) + "]";
    }
}
