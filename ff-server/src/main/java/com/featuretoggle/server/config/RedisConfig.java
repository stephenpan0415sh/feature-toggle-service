package com.featuretoggle.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;

/**
 * Redis configuration for caching and pub/sub
 */
@Configuration
public class RedisConfig {

    @Value("${feature-toggle.redis-channel:feature_flag_changes}")
    private String redisChannel;

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public ChannelTopic channelTopic() {
        return new ChannelTopic(redisChannel);
    }
}
