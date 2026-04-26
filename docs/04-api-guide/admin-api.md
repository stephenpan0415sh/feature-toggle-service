# Admin API Reference

Base URL: `/api/admin`

## 1. Create Flag
**POST** `/flags`

Creates a new feature flag for the current environment.

### Request Parameters
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `appKey` | String | Query | Yes | Application identifier |

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
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `flagKey` | String | Path | Yes | Flag key to update |

### Request Parameters
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `appKey` | String | Query | Yes | Application identifier |

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
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `flagKey` | String | Path | Yes | Flag key to delete |

### Request Parameters
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `appKey` | String | Query | Yes | Application identifier |

---

## 4. Get Flag(s)
**GET** `/flags`

Retrieves feature flag configuration. Returns a single flag if `flagKey` is provided, otherwise returns all flags for the application.

### Request Parameters
| Parameter | Type | Location | Required | Description |
| :--- | :--- | :--- | :--- | :--- |
| `appKey` | String | Query | Yes | Application identifier |
| `flagKey` | String | Query | No | Flag key (optional - if not provided, returns all flags) |

### Response (Single Flag)
```json
{
  "success": true,
  "data": { ...FeatureFlagObject }
}
```

### Response (All Flags)
```json
{
  "success": true,
  "data": [ ...FeatureFlagObjects ],
  "total": 10
}
```

---

> **Note**: For interactive testing and detailed schema definitions, please visit the [Swagger UI](http://localhost:8080/swagger-ui.html).
