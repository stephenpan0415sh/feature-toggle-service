package com.featuretoggle.sdk.java.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SDK configuration properties
 */
@Data
@ConfigurationProperties(prefix = "feature-toggle.sdk")
public class SdkProperties {
    
    /**
     * Application key
     */
    private String appKey;
    
    /**
     * Server URL for fetching configurations
     */
    private String serverUrl = "http://localhost:8080";
    
    /**
     * Environment (default: prod)
     */
    private String environment = "prod";
    
    /**
     * Pull interval in milliseconds (default: 5 minutes)
     * Note: Deprecated in favor of Redis Pub/Sub
     */
    @Deprecated
    private long pullInterval = 300000;
    
    /**
     * Redis host (default: localhost)
     */
    private String redisHost = "localhost";
    
    /**
     * Redis port (default: 6379)
     */
    private int redisPort = 6379;
    
    /**
     * Redis password (optional)
     */
    private String redisPassword;
}
