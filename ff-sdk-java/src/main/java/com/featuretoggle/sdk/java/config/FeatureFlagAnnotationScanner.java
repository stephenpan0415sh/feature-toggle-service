package com.featuretoggle.sdk.java.config;

import com.featuretoggle.common.model.Condition;
import com.featuretoggle.common.model.FeatureFlag;
import com.featuretoggle.common.model.Rule;
import com.featuretoggle.sdk.java.FeatureToggleClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scans Spring context for @FeatureFlag annotations and registers them to the SDK client.
 */
@Slf4j
@Component
public class FeatureFlagAnnotationScanner {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private FeatureToggleClient featureToggleClient;
    
    @PostConstruct
    public void scanAndRegister() {
        log.info("Scanning for @FeatureFlag annotations...");
        
        // Scan beans with annotation FeatureFlag (use fully qualified name)
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(
            com.featuretoggle.sdk.java.annotation.FeatureFlag.class);
        
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Class<?> beanClass = entry.getValue().getClass();
            
            // Get all @FeatureFlag annotations
            com.featuretoggle.sdk.java.annotation.FeatureFlag[] flagAnnotations = 
                beanClass.getAnnotationsByType(com.featuretoggle.sdk.java.annotation.FeatureFlag.class);
            
            for (com.featuretoggle.sdk.java.annotation.FeatureFlag flagAnnotation : flagAnnotations) {
                FeatureFlag flag = convertToFeatureFlag(flagAnnotation);
                featureToggleClient.registerFlag(flag);
                log.info("Registered flag from annotation: {} on bean {}", flag.getFlagKey(), entry.getKey());
            }
        }
        
        log.info("Annotation scanning completed, registered {} flags", 
            featureToggleClient.getFlagCache().size());
    }
    
    /**
     * Convert annotation to FeatureFlag model
     */
    private FeatureFlag convertToFeatureFlag(com.featuretoggle.sdk.java.annotation.FeatureFlag annotation) {
        List<Rule> rules = new ArrayList<>();
        
        for (com.featuretoggle.sdk.java.annotation.Rule ruleAnnotation : annotation.rules()) {
            Rule rule = Rule.builder()
                .id(ruleAnnotation.id().isEmpty() ? "rule_" + UUID.randomUUID().toString().substring(0, 8) : ruleAnnotation.id())
                .priority(ruleAnnotation.priority())
                .type(ruleAnnotation.type())
                .actionValue(ruleAnnotation.actionValue())
                .percentage(ruleAnnotation.percentage() > 0 ? ruleAnnotation.percentage() : null)
                .hashAttribute(ruleAnnotation.hashAttribute().isEmpty() ? null : ruleAnnotation.hashAttribute())
                .conditions(convertConditions(ruleAnnotation.conditions()))
                .build();
            
            rules.add(rule);
        }
        
        return FeatureFlag.builder()
            .flagKey(annotation.flagKey())
            .name(annotation.name())
            .defaultValue(annotation.defaultValue())
            .status(1)
            .rules(rules)
            .build();
    }
    
    /**
     * Convert Condition annotations to Condition models
     */
    private List<Condition> convertConditions(com.featuretoggle.sdk.java.annotation.Condition[] annotations) {
        List<Condition> conditions = new ArrayList<>();
        
        for (com.featuretoggle.sdk.java.annotation.Condition annotation : annotations) {
            Condition.Operator operator = switch (annotation.operator()) {
                case EQ -> Condition.Operator.EQ;
                case NEQ -> Condition.Operator.NEQ;
                case IN -> Condition.Operator.IN;
                case NOT_IN -> Condition.Operator.NOT_IN;
                case GT -> Condition.Operator.GT;
                case GTE -> Condition.Operator.GTE;
                case LT -> Condition.Operator.LT;
                case LTE -> Condition.Operator.LTE;
                case CONTAINS -> Condition.Operator.CONTAINS;
                case STARTS_WITH -> Condition.Operator.STARTS_WITH;
                case ENDS_WITH -> Condition.Operator.ENDS_WITH;
                case REGEX -> Condition.Operator.REGEX;
                case IS_TRUE -> Condition.Operator.IS_TRUE;
                case IS_FALSE -> Condition.Operator.IS_FALSE;
            };
            
            Condition condition = new Condition(annotation.attribute(), operator, List.of(annotation.values()));
            conditions.add(condition);
        }
        
        return conditions;
    }
}
