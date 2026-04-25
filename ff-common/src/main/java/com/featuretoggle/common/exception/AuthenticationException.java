package com.featuretoggle.common.exception;

/**
 * Exception thrown when authentication or authorization fails.
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
public class AuthenticationException extends FeatureToggleException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super("AUTHENTICATION_FAILED", message, 401);
    }

    public AuthenticationException() {
        super("AUTHENTICATION_FAILED", "Invalid or missing credentials", 401);
    }
}
