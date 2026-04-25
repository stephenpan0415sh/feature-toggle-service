package com.featuretoggle.sdk.java.config;

import com.featuretoggle.sdk.java.FeatureToggleClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for Feature Toggle SDK
 */
@Configuration
@EnableConfigurationProperties(SdkProperties.class)
public class FeatureToggleAutoConfiguration {
    
    @Bean(destroyMethod = "stop")
    public FeatureToggleClient featureToggleClient(SdkProperties properties) {
        FeatureToggleClient client = new FeatureToggleClient(properties);
        client.start();
        return client;
    }
}
