package com.featuretoggle.sdk.java.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Redis connection configuration for SDK.
 */
@Configuration
@RequiredArgsConstructor
public class RedisConfig {
    
    private final SdkProperties sdkProperties;
    
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(sdkProperties.getRedisHost());
        config.setPort(sdkProperties.getRedisPort());
        
        if (sdkProperties.getRedisPassword() != null && !sdkProperties.getRedisPassword().isEmpty()) {
            config.setPassword(sdkProperties.getRedisPassword());
        }
        
        return new LettuceConnectionFactory(config);
    }
}
