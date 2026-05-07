package com.featuretoggle.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Evaluation event for audit logging and observability.
 * Captures complete context of a feature flag evaluation for debugging and analysis.
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Event ID for tracing
     */
    private String eventId;

    /**
     * Timestamp when evaluation occurred (milliseconds since epoch)
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
     * Flag key that was evaluated
     */
    private String flagKey;

    /**
     * Evaluation result
     */
    private boolean enabled;

    /**
     * Reason for the evaluation result
     */
    private String reason;

    /**
     * Matched rule ID (if applicable)
     */
    private Long matchedRuleId;

    /**
     * Trace ID for correlation with other logs
     */
    private String traceId;

    /**
     * User ID who triggered the evaluation
     */
    private String userId;

    /**
     * Geographic region (e.g., cn-east, us-west)
     */
    private String region;

    /**
     * Release version associated with this evaluation
     */
    private String releaseVersion;

    /**
     * User context snapshot (attributes used in evaluation)
     */
    private Map<String, Object> userContext;

    /**
     * List of matched condition descriptions
     */
    private List<String> matchedConditions;

    /**
     * SDK version that performed the evaluation
     */
    private String sdkVersion;

    /**
     * Evaluation latency in milliseconds
     */
    private Long evaluationLatencyMs;

    /**
     * Whether the evaluation was successful
     */
    private boolean success;

    /**
     * Error message if evaluation failed
     */
    private String errorMessage;
}
