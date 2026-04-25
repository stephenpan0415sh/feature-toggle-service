package com.featuretoggle.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Feature Flag model representing a toggleable feature.
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeatureFlag implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for the flag
     */
    private Long id;

    /**
     * Application ID that owns this flag
     */
    private Long appId;

    /**
     * Unique flag key within the application (e.g., "new_checkout_flow")
     */
    private String flagKey;

    /**
     * Human-readable name
     */
    private String name;

    /**
     * Environment: dev, staging, prod
     */
    private String environment;

    /**
     * Flag status: 0-disabled, 1-enabled
     */
    private Integer status;

    /**
     * Default value when no rules match
     */
    private String defaultValue;

    /**
     * Optimistic locking version number
     */
    private Long version;

    /**
     * Associated release version (e.g., "v1.2.3", "release-2024-04")
     */
    private String releaseVersion;

    /**
     * List of evaluation rules (ordered by priority)
     */
    private List<Rule> rules;

    /**
     * Creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Check if the flag is enabled
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
