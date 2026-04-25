package com.featuretoggle.common.constants;

/**
 * Common constants used across the feature toggle system.
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
public final class CommonConstants {

    private CommonConstants() {
        // Prevent instantiation
    }

    /**
     * Default environment if not specified
     */
    public static final String DEFAULT_ENVIRONMENT = "prod";

    /**
     * Default SDK version
     */
    public static final String DEFAULT_SDK_VERSION = "1.0.0";

    /**
     * Default flag status (enabled)
     */
    public static final int FLAG_STATUS_ENABLED = 1;

    /**
     * Default flag status (disabled)
     */
    public static final int FLAG_STATUS_DISABLED = 0;

    /**
     * Redis channel name for configuration changes
     */
    public static final String REDIS_CONFIG_CHANNEL = "feature_flag_config_changes";

    /**
     * Default polling interval for Web/Mobile SDKs (in milliseconds)
     */
    public static final long DEFAULT_POLLING_INTERVAL_MS = 60000; // 60 seconds

    /**
     * Version check interval (in milliseconds)
     */
    public static final long VERSION_CHECK_INTERVAL_MS = 300000; // 5 minutes

    /**
     * Local snapshot file name pattern
     */
    public static final String SNAPSHOT_FILE_PATTERN = "ff_snapshot_%s.json";

    /**
     * HTTP header for API key
     */
    public static final String HEADER_API_KEY = "X-API-Key";

    /**
     * HTTP header for signature
     */
    public static final String HEADER_SIGNATURE = "X-Signature";

    /**
     * HTTP header for timestamp
     */
    public static final String HEADER_TIMESTAMP = "X-Timestamp";

    /**
     * Request attribute for trace ID
     */
    public static final String ATTR_TRACE_ID = "traceId";

    /**
     * Audit log action types
     */
    public static final class AuditActions {
        public static final String CREATE = "CREATE";
        public static final String UPDATE = "UPDATE";
        public static final String DELETE = "DELETE";
        public static final String PUBLISH = "PUBLISH";
        public static final String ROLLBACK = "ROLLBACK";
    }

    /**
     * Common user attribute names
     */
    public static final class UserAttributes {
        public static final String UID = "uid";
        public static final String REGION = "region";
        public static final String VIP_LEVEL = "vipLevel";
        public static final String DEVICE_TYPE = "deviceType";
        public static final String EMAIL = "email";
        public static final String ACCOUNT_AGE = "accountAge";
        public static final String IS_EMPLOYEE = "isEmployee";
    }

    /**
     * Supported environments
     */
    public static final class Environments {
        public static final String DEV = "dev";
        public static final String STAGING = "staging";
        public static final String PROD = "prod";
    }
}
