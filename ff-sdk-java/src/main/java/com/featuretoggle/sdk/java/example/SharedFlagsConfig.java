package com.featuretoggle.sdk.java.example;

import com.featuretoggle.common.model.Condition;
import com.featuretoggle.common.model.FeatureFlag;
import com.featuretoggle.common.model.Rule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Example configuration demonstrating programmatic flag registration via Spring Beans.
 * Suitable for infrastructure-level flags shared across multiple services.
 */
@Configuration
public class SharedFlagsConfig {
    
    /**
     * Database connection timeout flag - shared by multiple services.
     * Registered via @Bean instead of annotation.
     */
    @Bean
    public FeatureFlag dbConnectionTimeout() {
        return FeatureFlag.builder()
            .flagKey("db_connection_timeout")
            .name("Database Connection Timeout")
            .defaultValue("30000")
            .rules(List.of(
                Rule.builder()
                    .priority(1)
                    .type(Rule.RuleType.TARGETING)
                    .ruleDefaultEnabled(true)
                    .conditions(List.of(
                        new Condition("region", Condition.Operator.IN, List.of("beijing"))
                    ))
                    .build()
            ))
            .build();
    }
    
    /**
     * API rate limit flag - simple on/off without rules.
     */
    @Bean
    public FeatureFlag apiRateLimit() {
        return FeatureFlag.builder()
            .flagKey("api_rate_limit")
            .name("API Rate Limit Threshold")
            .defaultValue("1000")
            .build();
    }
}
