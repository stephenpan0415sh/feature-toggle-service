package com.featuretoggle.sdk.java.config;

import com.featuretoggle.sdk.java.listener.FlagUpdateListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis configuration for SDK Pub/Sub.
 * Sets up subscription to app-specific channel for real-time flag updates.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {
    
    private final SdkProperties sdkProperties;
    private final FlagUpdateListener flagUpdateListener;
    
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        // Subscribe to app-specific channel
        String channel = "feature_flag_changes:" + sdkProperties.getAppKey();
        container.addMessageListener(flagUpdateListener, new ChannelTopic(channel));
        
        log.info("Subscribed to Redis channel: {}", channel);
        
        return container;
    }
}
