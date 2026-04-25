package com.featuretoggle.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.featuretoggle.common.model.EvaluationDetail;

import java.io.Serializable;
import java.util.Map;

/**
 * Response DTO for flag evaluation.
 * 
 * Example JSON:
 * {
 *   "success": true,
 *   "data": {
 *     "new_checkout": {
 *       "flagKey": "new_checkout",
 *       "enabled": true,
 *       "value": "v2",
 *       "reason": "MATCHED_RULE",
 *       "matchedRuleId": "rule_001",
 *       "environment": "prod",
 *       "region": "cn-east",
 *       "evaluatedAt": 1713849600000,
 *       "traceId": "trace_abc123"
 *     },
 *     "free_shipping": {
 *       "flagKey": "free_shipping",
 *       "enabled": false,
 *       "value": null,
 *       "reason": "DEFAULT",
 *       "environment": "prod",
 *       "traceId": "trace_abc123"
 *     }
 *   },
 *   "error": null
 * }
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationResponse(
    /**
     * Whether the request was successful
     */
    boolean success,

    /**
     * Map of flag key to evaluation detail
     */
    Map<String, EvaluationDetail> data,

    /**
     * Error message if success is false
     */
    String error
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Create a successful response
     */
    public static EvaluationResponse success(Map<String, EvaluationDetail> data) {
        return new EvaluationResponse(true, data, null);
    }

    /**
     * Create an error response
     */
    public static EvaluationResponse error(String errorMessage) {
        return new EvaluationResponse(false, null, errorMessage);
    }
}
