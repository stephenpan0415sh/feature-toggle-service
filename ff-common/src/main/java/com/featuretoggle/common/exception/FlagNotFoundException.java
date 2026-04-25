package com.featuretoggle.common.exception;

/**
 * Exception thrown when a feature flag is not found.
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
public class FlagNotFoundException extends FeatureToggleException {

    private static final long serialVersionUID = 1L;

    public FlagNotFoundException(String flagKey) {
        super("FLAG_NOT_FOUND", "Feature flag not found: " + flagKey, 404);
    }

    public FlagNotFoundException(String flagKey, String environment) {
        super("FLAG_NOT_FOUND", 
              String.format("Feature flag '%s' not found in environment '%s'", flagKey, environment), 
              404);
    }
}
