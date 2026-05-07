package com.featuretoggle.common.model;

/**
 * Built-in rule IDs for default evaluation scenarios.
 * Reserved IDs: 1-100 for built-in rules, 101+ for database rules.
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
public enum BuiltInRuleId {
    
    /**
     * Flag is disabled (enabled = false)
     */
    FLAG_DISABLED(1L),
    
    /**
     * No rules configured for this flag
     */
    NO_RULES_CONFIGURED(2L),
    
    /**
     * No rules matched, returning default value
     */
    DEFAULT_FALLBACK(3L),
    
    /**
     * Error occurred during evaluation
     */
    ERROR_FALLBACK(4L);
    
    private final Long id;
    
    BuiltInRuleId(Long id) {
        this.id = id;
    }
    
    public Long getId() {
        return id;
    }
    
    @Override
    public String toString() {
        return String.valueOf(id);
    }
}
