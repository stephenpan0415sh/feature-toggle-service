package com.featuretoggle.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Map;

/**
 * User context model for feature flag evaluation.
 * Contains user attributes that are used to match against rule conditions.
 * 
 * Example JSON structure:
 * {
 *   "userId": "12345",
 *   "attributes": {
 *     "uid": 12345,
 *     "region": "cn-beijing",
 *     "vipLevel": 2,
 *     "deviceType": "ios",
 *     "email": "user@example.com",
 *     "accountAge": 365
 *   }
 * }
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserContext(
    /**
     * Unique user identifier
     */
    String userId,

    /**
     * User attributes for rule evaluation
     * Common attributes:
     * - uid: User ID (numeric)
     * - region: Geographic region (e.g., "cn-beijing", "us-west")
     * - vipLevel: VIP level (numeric)
     * - deviceType: Device type (e.g., "ios", "android", "web")
     * - email: Email address
     * - accountAge: Account age in days
     * - isEmployee: Whether user is an employee (boolean)
     */
    Map<String, Object> attributes
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Get a specific attribute value
     * 
     * @param attributeName The attribute name
     * @return The attribute value, or null if not present
     */
    public Object getAttribute(String attributeName) {
        return attributes != null ? attributes.get(attributeName) : null;
    }

    /**
     * Check if user has a specific attribute
     * 
     * @param attributeName The attribute name
     * @return true if the attribute exists
     */
    public boolean hasAttribute(String attributeName) {
        return attributes != null && attributes.containsKey(attributeName);
    }

    /**
     * Get attribute as string
     */
    public String getStringAttribute(String attributeName) {
        Object value = getAttribute(attributeName);
        return value != null ? value.toString() : null;
    }

    /**
     * Get attribute as integer
     */
    public Integer getIntAttribute(String attributeName) {
        Object value = getAttribute(attributeName);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Get attribute as long
     */
    public Long getLongAttribute(String attributeName) {
        Object value = getAttribute(attributeName);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Get attribute as boolean
     */
    public Boolean getBooleanAttribute(String attributeName) {
        Object value = getAttribute(attributeName);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }
}
