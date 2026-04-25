package com.featuretoggle.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * Configuration change event for audit logging.
 * Captures all changes to feature flags for compliance and debugging.
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigChangeEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Event ID for tracing
     */
    private String eventId;

    /**
     * Timestamp when change occurred (milliseconds since epoch)
     */
    private Long timestamp;

    /**
     * Application key
     */
    private String appKey;

    /**
     * Environment (prod/staging/dev)
     */
    private String environment;

    /**
     * Flag key that was changed
     */
    private String flagKey;

    /**
     * Type of change: CREATE, UPDATE, DELETE, PUBLISH
     */
    private ChangeType changeType;

    /**
     * User who made the change
     */
    private String changedBy;

    /**
     * Previous state (before change)
     */
    private Map<String, Object> previousState;

    /**
     * New state (after change)
     */
    private Map<String, Object> newState;

    /**
     * List of changed fields
     * Example: ["status", "rules", "defaultValue"]
     */
    private java.util.List<String> changedFields;

    /**
     * Reason for the change (optional)
     */
    private String changeReason;

    /**
     * IP address of the user who made the change
     */
    private String ipAddress;

    /**
     * User agent of the client
     */
    private String userAgent;

    /**
     * Whether this change was rolled back
     */
    private boolean rolledBack;

    /**
     * Rollback reason (if applicable)
     */
    private String rollbackReason;

    /**
     * Change type enum
     */
    public enum ChangeType {
        CREATE,
        UPDATE,
        DELETE,
        PUBLISH,
        ROLLBACK
    }
}
