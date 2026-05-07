package com.featuretoggle.common.exception;

/**
 * Exception thrown when rule evaluation fails.
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
public class RuleEvaluationException extends FeatureToggleException {

    private static final long serialVersionUID = 1L;

    private final String ruleId;
    private final String flagKey;

    public RuleEvaluationException(String message, String ruleId, String flagKey) {
        super("RULE_EVALUATION_ERROR", message, 500);
        this.ruleId = ruleId;
        this.flagKey = flagKey;
    }

    public RuleEvaluationException(String message, String ruleId, String flagKey, Throwable cause) {
        super("RULE_EVALUATION_ERROR", message, cause);
        this.ruleId = ruleId;
        this.flagKey = flagKey;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getFlagKey() {
        return flagKey;
    }
}
