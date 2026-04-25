package com.featuretoggle.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for batch flag evaluation.
 * 
 * Example JSON:
 * {
 *   "appKey": "ecommerce-web",
 *   "userId": "12345",
 *   "attributes": {
 *     "region": "cn-east",
 *     "deviceType": "ios",
 *     "vipLevel": 2
 *   },
 *   "flagKeys": ["new_checkout", "free_shipping", "dark_mode"]
 * }
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchEvaluationRequest(
    /**
     * Application key for authentication
     */
    String appKey,

    /**
     * Application secret for authentication
     */
    String appSecret,

    /**
     * User ID for evaluation
     */
    String userId,

    /**
     * User attributes for rule matching
     */
    Map<String, Object> attributes,

    /**
     * List of flag keys to evaluate
     */
    List<String> flagKeys
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
