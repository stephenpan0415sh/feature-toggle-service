package com.featuretoggle.common.exception;

import lombok.Getter;

/**
 * Base exception for feature toggle system.
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
@Getter
public class FeatureToggleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Error code
     */
    private final String errorCode;

    /**
     * HTTP status code (if applicable)
     */
    private final int httpStatus;

    public FeatureToggleException(String message) {
        super(message);
        this.errorCode = "INTERNAL_ERROR";
        this.httpStatus = 500;
    }

    public FeatureToggleException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = 500;
    }

    public FeatureToggleException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public FeatureToggleException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "INTERNAL_ERROR";
        this.httpStatus = 500;
    }

    public FeatureToggleException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = 500;
    }
}
