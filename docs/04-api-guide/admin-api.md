# Admin API Reference

Base URL: `/api/admin`

## 1. Create Flag
**POST** `/flags`

Creates a new feature flag for a specific application and environment.

### Request Parameters
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `appKey` | String | Query | Yes | Application identifier |
| `environment` | String | Query | Yes | Target environment (e.g., `prod`, `dev`) |

### Request Body
```json
{
  "flagKey": "enable_new_checkout",
  "name": "New Checkout Flow",
  "status": 1,
  "defaultValue": "false",
  "rules": []
}
```

### Response
```json
{
  "success": true,
  "data": { ...FeatureFlagObject },
  "message": "Flag created successfully"
}
```

---

## 2. Update Flag
**PUT** `/flags/{flagKey}`

Updates an existing feature flag configuration.

### Path Parameters
| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `flagKey` | String | Yes | Unique key of the flag |

### Request Parameters
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `appKey` | String | Query | Yes | Application identifier |
| `environment` | String | Query | Yes | Target environment |

### Response
```json
{
  "success": true,
  "data": { ...UpdatedFeatureFlagObject },
  "message": "Flag updated successfully"
}
```

---

## 3. Delete Flag
**DELETE** `/flags/{flagKey}`

Permanently deletes a feature flag (Hard Delete).

### Path Parameters
| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `flagKey` | String | Yes | Unique key of the flag |

### Request Parameters
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `appKey` | String | Query | Yes | Application identifier |
| `environment` | String | Query | Yes | Target environment |

---

## 4. Get Flag Details
**GET** `/flags/{flagKey}`

Retrieves the full configuration of a specific flag.

### Path Parameters
| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `flagKey` | String | Yes | Unique key of the flag |

### Request Parameters
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `appKey` | String | Query | Yes | Application identifier |
| `environment` | String | Query | Yes | Target environment |

---

## 5. List Flags
**GET** `/flags`

Lists all feature flags for an application in a specific environment.

### Request Parameters
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `appKey` | String | Query | Yes | Application identifier |
| `environment` | String | Query | Yes | Target environment |

### Response
```json
{
  "success": true,
  "data": [ ...FeatureFlagObjects ],
  "total": 10
}
```

---

> **Note**: For interactive testing and detailed schema definitions, please visit the [Swagger UI](http://localhost:8080/swagger-ui.html).
