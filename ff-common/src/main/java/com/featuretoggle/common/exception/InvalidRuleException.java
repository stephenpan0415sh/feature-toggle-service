package com.featuretoggle.common.exception;

/**
 * Exception thrown when rule configuration is invalid.
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
public class InvalidRuleException extends FeatureToggleException {

    private static final long serialVersionUID = 1L;

    public InvalidRuleException(String message) {
        super("INVALID_RULE", message, 400);
    }

    public InvalidRuleException(String ruleId, String message) {
        super("INVALID_RULE", 
              String.format("Invalid rule '%s': %s", ruleId, message), 
              400);
    }
}
