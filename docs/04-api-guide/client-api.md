# Client API Reference

Base URL: `/api/client`

These APIs are primarily used by the **SDK** for evaluation, synchronization, and registration.

---

## 1. Evaluate Flag
**GET** `/evaluate/{flagKey}`

Evaluates a specific feature flag for a given user context. Returns detailed explainability info.

### Path Parameters
| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `flagKey` | String | Yes | Unique key of the flag |

### Request Parameters
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `appKey` | String | Query | Yes | Application identifier |
| `userId` | String | Query | Yes | Current user identifier |
| `region` | String | Query | No | User's region (e.g., `cn-east`) |

### Response
```json
{
  "success": true,
  "data": {
    "enabled": true,
    "value": "true",
    "reason": "RULE_MATCH",
    "matchedRuleId": "rule_123",
    "traceId": "trace_abc"
  }
}
```

---

## 2. Batch Evaluate
**POST** `/evaluate`

Evaluates multiple flags in a single request. Recommended for SDK initialization.

### Request Body
```json
{
  "appKey": "ecommerce-web",
  "userId": "user_123",
  "flagKeys": ["enable_new_checkout", "show_promo_banner"],
  "attributes": {
    "region": "cn-east",
    "vipLevel": 5
  }
}
```

### Response
```json
{
  "success": true,
  "data": {
    "enable_new_checkout": { ...EvaluationDetail },
    "show_promo_banner": { ...EvaluationDetail }
  }
}
```

---

## 3. Get Configs (Sync)
**GET** `/configs`

Retrieves feature flag configurations for SDK initialization. Supports **incremental sync**.

### Request Parameters
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `appKey` | String | Query | Yes | Application identifier |
| `lastKnownVersion` | Long | Query | No | Version for incremental sync |

### Response
```json
{
  "success": true,
  "data": {
    "globalVersion": 105,
    "flags": [ ...ChangedFlags ],
    "hasChanges": true
  }
}
```

**Note**: Deleted flags are handled by full cache replacement on the SDK side. The SDK clears its local cache and reloads all flags when a deletion is detected via Pub/Sub or heartbeat sync.

---

## 4. Get Flag Versions
**GET** `/versions`

Returns current global and individual flag versions. Used for lightweight polling.

### Request Parameters
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `appKey` | String | Query | Yes | Application identifier |

### Response
```json
{
  "success": true,
  "data": {
    "globalVersion": 105,
    "flagVersions": {
      "enable_new_checkout": 105,
      "show_promo_banner": 100
    }
  }
}
```

---

## 5. Cache Stats
**GET** `/cache-stats`

Returns cache statistics for monitoring and debugging.

### Request Parameters
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `appKey` | String | Query | Yes | Application identifier |

### Response
```json
{
  "success": true,
  "data": {
    "cachedFlagsCount": 50,
    "globalVersion": 105
  }
}
```

---

## 6. Register Flag (Internal)
**POST** `/register`

**Internal API**: Used automatically by the SDK during startup to register flags defined in code.

### Request Body
```json
{
  "appKey": "ecommerce-web",
  "flag": {
    "flagKey": "enable_dark_mode",
    "name": "Enable Dark Mode",
    "defaultValue": "false",
    "rules": []
  },
  "skipPublish": true
}
```

### Response
```json
{
  "success": true,
  "message": "Flag registered successfully",
  "action": "created",
  "version": 1
}
```

**Note**: This API is called internally by `FeatureToggleClient.registerFlag()`. Manual invocation is generally not required.

---

> **Note**: For interactive testing and detailed schema definitions, please visit the [Swagger UI](http://localhost:8080/swagger-ui.html).
