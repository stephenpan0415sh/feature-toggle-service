# Swagger API Documentation Enhancement Guide

## Current Status

All controllers have basic `@Operation` annotations but lack detailed examples.

## Recommended Enhancements

### 1. ClientApiController - Add Request/Response Examples

#### POST /api/client/evaluate (Batch Evaluation)

**Request Body Example**:
```json
{
  "appKey": "ecommerce-web",
  "userId": "user_123",
  "flagKeys": ["enable_checkout", "show_promo", "dark_mode"],
  "attributes": {
    "region": "cn-east",
    "vipLevel": 5,
    "deviceType": "mobile"
  }
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "data": {
    "enable_checkout": {
      "flagKey": "enable_checkout",
      "enabled": true,
      "value": "true",
      "reason": "RULE_MATCH",
      "matchedRuleId": "rule_vip_users",
      "traceId": "trace-abc-123",
      "releaseVersion": "v1.2.0",
      "userContextSnapshot": {
        "userId": "user_123",
        "region": "cn-east"
      }
    },
    "show_promo": {
      "flagKey": "show_promo",
      "enabled": false,
      "value": "false",
      "reason": "DEFAULT"
    }
  }
}
```

**Error Response (400)**:
```json
{
  "success": false,
  "error": "Missing required fields: appKey, userId"
}
```

---

#### GET /api/client/configs (Incremental Sync)

**Query Parameters**:
- `appKey`: Required - Application identifier
- `lastKnownVersion`: Optional - Last known global version for incremental sync

**Example Request**:
```
GET /api/client/configs?appKey=ecommerce-web&lastKnownVersion=123
```

**Success Response with Changes (200)**:
```json
{
  "success": true,
  "data": {
    "globalVersion": 125,
    "flags": [
      {
        "flagKey": "new_feature",
        "name": "New Feature Flag",
        "status": 1,
        "defaultValue": "false",
        "version": 125,
        "rules": [
          {
            "id": "rule_1",
            "priority": 1,
            "conditions": [
              {
                "attribute": "region",
                "operator": "IN",
                "values": ["cn-east", "cn-south"]
              }
            ],
            "actionValue": "true"
          }
        ]
      }
    ],
    "hasChanges": true
  }
}
```

**No Changes Response (200)**:
```json
{
  "success": true,
  "data": {
    "globalVersion": 123,
    "flags": [],
    "hasChanges": false
  }
}
```

---

#### GET /api/client/versions (Version Check)

**Example Request**:
```
GET /api/client/versions?appKey=ecommerce-web
```

**Success Response (200)**:
```json
{
  "success": true,
  "data": {
    "globalVersion": 125,
    "flagVersions": {
      "enable_checkout": 120,
      "show_promo": 115,
      "dark_mode": 125,
      "new_feature": 125
    }
  }
}
```

---

#### GET /api/client/cache-stats (Cache Monitoring)

**Example Request**:
```
GET /api/client/cache-stats?appKey=ecommerce-web
```

**Success Response (200)**:
```json
{
  "success": true,
  "data": {
    "appKey": "ecommerce-web",
    "environment": "prod",
    "cachedFlags": 45,
    "trackedVersions": 45,
    "globalVersion": 125
  }
}
```

---

### 2. AdminController - Add CRUD Examples

#### POST /api/admin/flags (Create Flag)

**Request Body Example**:
```json
{
  "flagKey": "enable_dark_mode",
  "name": "Enable Dark Mode",
  "description": "Allows users to enable dark mode UI",
  "status": 1,
  "defaultValue": "false",
  "rules": [
    {
      "priority": 1,
      "conditions": [
        {
          "attribute": "vipLevel",
          "operator": "GTE",
          "values": [5]
        }
      ],
      "actionValue": "true",
      "description": "Enable for VIP users"
    }
  ]
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "data": {
    "flagKey": "enable_dark_mode",
    "name": "Enable Dark Mode",
    "status": 1,
    "defaultValue": "false",
    "version": 1
  },
  "message": "Flag created successfully"
}
```

---

#### PUT /api/admin/flags (Update Flag)

**Query Parameters**:
- `appKey`: Application key
- `flagKey`: Flag identifier to update

**Request Body Example**:
```json
{
  "name": "Enable Dark Mode (Updated)",
  "status": 1,
  "defaultValue": "true",
  "rules": [
    {
      "priority": 1,
      "conditions": [],
      "actionValue": "true",
      "description": "Enabled by default"
    }
  ]
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "data": {
    "flagKey": "enable_dark_mode",
    "name": "Enable Dark Mode (Updated)",
    "status": 1,
    "defaultValue": "true",
    "version": 2
  },
  "message": "Flag updated successfully"
}
```

---

#### DELETE /api/admin/flags (Delete Flag)

**Query Parameters**:
- `appKey`: Application key
- `flagKey`: Flag identifier to delete

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Flag deleted successfully"
}
```

**Error Response (404)**:
```json
{
  "success": false,
  "error": "Flag not found: enable_dark_mode"
}
```

---

#### GET /api/admin/flags (Get Flag(s))

**Query Parameters**:
- `appKey`: Application key (required)
- `flagKey`: Flag identifier (optional - omit to get all flags)

**Get Single Flag**:
```
GET /api/admin/flags?appKey=ecommerce-web&flagKey=enable_dark_mode
```

**Get All Flags**:
```
GET /api/admin/flags?appKey=ecommerce-web
```

**Success Response (Single Flag - 200)**:
```json
{
  "success": true,
  "data": {
    "flagKey": "enable_dark_mode",
    "name": "Enable Dark Mode",
    "status": 1,
    "defaultValue": "false",
    "version": 1
  }
}
```

**Success Response (All Flags - 200)**:
```json
{
  "success": true,
  "data": [
    {
      "flagKey": "enable_checkout",
      "name": "Enable New Checkout",
      "status": 1,
      "defaultValue": "false",
      "version": 120
    },
    {
      "flagKey": "show_promo",
      "name": "Show Promotion Banner",
      "status": 1,
      "defaultValue": "true",
      "version": 115
    }
  ],
  "total": 2
}
```

---

### 3. ClientRegisterController - SDK Registration

#### POST /api/client/register (Register Flag from SDK)

**Request Body Example**:
```json
{
  "appKey": "ecommerce-web",
  "environment": "prod",
  "flag": {
    "flagKey": "enable_fast_search",
    "name": "Enable Fast Search",
    "defaultValue": "false",
    "rules": [
      {
        "priority": 1,
        "conditions": [
          {
            "attribute": "deviceType",
            "operator": "EQ",
            "values": ["mobile"]
          }
        ],
        "actionValue": "true",
        "description": "Enable for mobile devices"
      }
    ]
  },
  "skipPublish": false
}
```

**Success Response - New Flag (200)**:
```json
{
  "success": true,
  "message": "Flag registered successfully",
  "action": "created",
  "version": 1
}
```

**Success Response - Already Exists (200)**:
```json
{
  "success": true,
  "message": "Flag already exists, not overwritten",
  "action": "skipped"
}
```

**Error Response (400)**:
```json
{
  "success": false,
  "error": "App not found: invalid-app"
}
```

---

## Implementation Priority

### High Priority (Must Have)
1. ✅ ClientApiController - @Tag added
2. ✅ Batch evaluation request/response examples
3. ✅ Single flag evaluation examples

### Medium Priority (Should Have)
4. AdminController CRUD examples
5. Config sync incremental examples
6. Error response examples for all endpoints

### Low Priority (Nice to Have)
7. Cache stats monitoring examples
8. Version check examples
9. SDK registration examples

---

## Code Template for Adding Examples

```java
@Operation(
    summary = "Brief description",
    description = "Detailed description with use cases",
    tags = {"API Group Name"}
)
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Success case",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(
                name = "Example Name",
                value = "{\n  \"key\": \"value\"\n}"
            ))),
    @ApiResponse(responseCode = "400", description = "Error case",
        content = @Content(mediaType = "application/json",
            examples = @ExampleObject(
                name = "Error Example",
                value = "{\n  \"error\": \"message\"\n}"
            )))
})
```

---

## Testing Swagger UI

After adding examples, test at:
```
http://localhost:8080/swagger-ui.html
```

Verify:
- All endpoints have clear descriptions
- Request/response examples are visible
- Try it out functionality works
- Example data is realistic and helpful
