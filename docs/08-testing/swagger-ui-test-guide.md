# Feature Toggle Service - Swagger UI Complete Test Guide

## Test Environment Setup

**Swagger UI URL**: http://localhost:8080/swagger-ui.html

**Test Application Info**:
- App Key: `prod`
- Environment: Auto-detected from `FEATURE_TOGGLE_ENVIRONMENT` (default: prod)

---

## Test Flow Overview

1. **Create App and Flags via SDK Registration API** (Client Register API)
2. **Test 5 Rule Conditions** (Client API - Evaluate)
3. **Batch Evaluation Test** (Client API - Batch Evaluate)
4. **Update Flag Rules** (Admin API)
5. **Get Flag Details** (Admin API)
6. **Delete Flag** (Admin API)
7. **Incremental Sync Test** (Client API - Configs)

---

## Step 1: Create App and All Test Flags via SDK Registration API

**Note**: ClientRegisterController auto-creates apps if they don't exist. No manual database insertion needed.

Call `POST /api/client/register` to register the following 5 flags:

### 1.1 EQ Condition - enable_premium_feature

```json
{
  "appKey": "prod",
  "environment": "prod",
  "flag": {
    "flagKey": "enable_premium_feature",
    "name": "Enable Premium Feature",
    "defaultValue": "false",
    "rules": [
      {
        "priority": 1,
        "type": "TARGETING",
        "conditions": [
          {
            "attribute": "vipLevel",
            "operator": "EQ",
            "values": [5]
          }
        ],
        "actionValue": "true"
      }
    ]
  },
  "skipPublish": true
}
```

### 1.2 IN Condition - enable_regional_promo

```json
{
  "appKey": "prod",
  "environment": "prod",
  "flag": {
    "flagKey": "enable_regional_promo",
    "name": "Enable Regional Promotion",
    "defaultValue": "false",
    "rules": [
      {
        "priority": 1,
        "type": "TARGETING",
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
  },
  "skipPublish": true
}
```

### 1.3 GTE Condition - enable_high_value_discount

```json
{
  "appKey": "prod",
  "environment": "prod",
  "flag": {
    "flagKey": "enable_high_value_discount",
    "name": "Enable High Value Discount",
    "defaultValue": "false",
    "rules": [
      {
        "priority": 1,
        "type": "TARGETING",
        "conditions": [
          {
            "attribute": "totalSpent",
            "operator": "GTE",
            "values": [1000]
          }
        ],
        "actionValue": "true"
      }
    ]
  },
  "skipPublish": true
}
```

### 1.4 LTE Condition - enable_new_user_bonus

```json
{
  "appKey": "prod",
  "environment": "prod",
  "flag": {
    "flagKey": "enable_new_user_bonus",
    "name": "Enable New User Bonus",
    "defaultValue": "false",
    "rules": [
      {
        "priority": 1,
        "type": "TARGETING",
        "conditions": [
          {
            "attribute": "orderCount",
            "operator": "LTE",
            "values": [3]
          }
        ],
        "actionValue": "true"
      }
    ]
  },
  "skipPublish": true
}
```

### 1.5 CONTAINS Condition - enable_mobile_app_feature

```json
{
  "appKey": "prod",
  "environment": "prod",
  "flag": {
    "flagKey": "enable_mobile_app_feature",
    "name": "Enable Mobile App Feature",
    "defaultValue": "false",
    "rules": [
      {
        "priority": 1,
        "type": "TARGETING",
        "conditions": [
          {
            "attribute": "deviceType",
            "operator": "CONTAINS",
            "values": ["mobile"]
          }
        ],
        "actionValue": "true"
      }
    ]
  },
  "skipPublish": true
}
```

**Expected Response** (for each registration):
```json
{
  "success": true,
  "message": "Flag registered successfully",
  "action": "created",
  "version": 1
}
```

---

## Step 1.6: Verify All Flags Registered

**API**: `GET /api/admin/flags`

**Query Parameters**:
- `appKey`: `prod`
- `flagKey`: (optional - leave empty to get all flags)

**Expected Response**:
```json
{
  "success": true,
  "data": [
    {"flagKey": "enable_premium_feature", "version": 1},
    {"flagKey": "enable_regional_promo", "version": 1},
    {"flagKey": "enable_high_value_discount", "version": 1},
    {"flagKey": "enable_new_user_bonus", "version": 1},
    {"flagKey": "enable_mobile_app_feature", "version": 1}
  ],
  "total": 5
}
```

**Note**: Verify all 5 flags are present before proceeding.

---

## Step 2: Test Rule Condition Evaluation

### 2.1 Test EQ Condition

**API**: `POST /api/client/evaluate`

**Test Case 1 - Match Success (vipLevel=5)**:
```json
{
  "appKey": "prod",
  "userId": "user_vip5",
  "flagKeys": ["enable_premium_feature"],
  "attributes": {
    "vipLevel": 5
  }
}
```
**Expected**: `enabled: true, reason: "MATCHED_RULE"`

**Test Case 2 - No Match (vipLevel=3)**:
```json
{
  "appKey": "prod",
  "userId": "user_regular",
  "flagKeys": ["enable_premium_feature"],
  "attributes": {
    "vipLevel": 3
  }
}
```
**Expected**: `enabled: false, reason: "DEFAULT"`

---

### 2.2 Test IN Condition

**API**: `POST /api/client/evaluate`

**Test Case 1 - Match Success (region=cn-east)**:
```json
{
  "appKey": "prod",
  "userId": "user_001",
  "flagKeys": ["enable_regional_promo"],
  "attributes": {
    "region": "cn-east"
  }
}
```
**Expected**: `enabled: true, reason: "MATCHED_RULE"`

**Test Case 2 - No Match (region=cn-north)**:
```json
{
  "appKey": "prod",
  "userId": "user_002",
  "flagKeys": ["enable_regional_promo"],
  "attributes": {
    "region": "cn-north"
  }
}
```
**Expected**: `enabled: false, reason: "DEFAULT"`

---

### 2.3 Test GTE Condition

**API**: `POST /api/client/evaluate`

**Test Case 1 - Match Success (totalSpent=1500)**:
```json
{
  "appKey": "prod",
  "userId": "user_big_spender",
  "flagKeys": ["enable_high_value_discount"],
  "attributes": {
    "totalSpent": 1500
  }
}
```
**Expected**: `enabled: true, reason: "MATCHED_RULE"`

**Test Case 2 - Boundary Value (totalSpent=1000)**:
```json
{
  "appKey": "prod",
  "userId": "user_exact_1000",
  "flagKeys": ["enable_high_value_discount"],
  "attributes": {
    "totalSpent": 1000
  }
}
```
**Expected**: `enabled: true, reason: "MATCHED_RULE"` (GTE includes equality)

**Test Case 3 - No Match (totalSpent=999)**:
```json
{
  "appKey": "prod",
  "userId": "user_low_spender",
  "flagKeys": ["enable_high_value_discount"],
  "attributes": {
    "totalSpent": 999
  }
}
```
**Expected**: `enabled: false, reason: "DEFAULT"`

---

### 2.4 Test LTE Condition

**API**: `POST /api/client/evaluate`

**Test Case 1 - Match Success (orderCount=2)**:
```json
{
  "appKey": "prod",
  "userId": "new_user_001",
  "flagKeys": ["enable_new_user_bonus"],
  "attributes": {
    "orderCount": 2
  }
}
```
**Expected**: `enabled: true, reason: "MATCHED_RULE"`

**Test Case 2 - Boundary Value (orderCount=3)**:
```json
{
  "appKey": "prod",
  "userId": "new_user_002",
  "flagKeys": ["enable_new_user_bonus"],
  "attributes": {
    "orderCount": 3
  }
}
```
**Expected**: `enabled: true, reason: "MATCHED_RULE"` (LTE includes equality)

**Test Case 3 - No Match (orderCount=4)**:
```json
{
  "appKey": "prod",
  "userId": "regular_user",
  "flagKeys": ["enable_new_user_bonus"],
  "attributes": {
    "orderCount": 4
  }
}
```
**Expected**: `enabled: false, reason: "DEFAULT"`

---

### 2.5 Test CONTAINS Condition

**API**: `POST /api/client/evaluate`

**Test Case 1 - Match Success (deviceType contains "mobile")**:
```json
{
  "appKey": "prod",
  "userId": "mobile_user",
  "flagKeys": ["enable_mobile_app_feature"],
  "attributes": {
    "deviceType": "iPhone_mobile_ios"
  }
}
```
**Expected**: `enabled: true, reason: "MATCHED_RULE"`

**Test Case 2 - No Match (deviceType does not contain "mobile")**:
```json
{
  "appKey": "prod",
  "userId": "desktop_user",
  "flagKeys": ["enable_mobile_app_feature"],
  "attributes": {
    "deviceType": "desktop_chrome"
  }
}
```
**Expected**: `enabled: false, reason: "DEFAULT"`

---

## Step 3: Batch Evaluation Test

**API**: `POST /api/client/evaluate`

**Request Body** - All Conditions Match:
```json
{
  "appKey": "prod",
  "userId": "user_comprehensive",
  "flagKeys": [
    "enable_premium_feature",
    "enable_regional_promo",
    "enable_high_value_discount",
    "enable_new_user_bonus",
    "enable_mobile_app_feature"
  ],
  "attributes": {
    "vipLevel": 5,
    "region": "cn-east",
    "totalSpent": 1500,
    "orderCount": 2,
    "deviceType": "mobile_android"
  }
}
```

**Expected Response**:
```json
{
  "success": true,
  "data": {
    "enable_premium_feature": {
      "enabled": true,
      "reason": "MATCHED_RULE"
    },
    "enable_regional_promo": {
      "enabled": true,
      "reason": "MATCHED_RULE"
    },
    "enable_high_value_discount": {
      "enabled": true,
      "reason": "MATCHED_RULE"
    },
    "enable_new_user_bonus": {
      "enabled": true,
      "reason": "MATCHED_RULE"
    },
    "enable_mobile_app_feature": {
      "enabled": true,
      "reason": "MATCHED_RULE"
    }
  }
}
```

---

## Step 4: Update Flag Test

### 4.1 Update Flag Rule

**API**: `PUT /api/admin/flags`

**Query Parameters**:
- `appKey`: `prod`
- `flagKey`: `enable_premium_feature`

**Request Body**:
```json
{
  "flagKey": "enable_premium_feature",
  "name": "Enable Premium Feature (Updated)",
  "defaultValue": "false",
  "status": 1,
  "rules": [
    {
      "priority": 1,
      "type": "TARGETING",
      "conditions": [
        {
          "attribute": "vipLevel",
          "operator": "EQ",
          "values": [5]
        }
      ],
      "actionValue": "true"
    }
  ]
}
```

**Expected Response**:
```json
{
  "success": true,
  "message": "Flag updated successfully",
  "data": {
    "flagKey": "enable_premium_feature",
    "version": 2
  }
}
```

---

### 4.2 Verify Update via Evaluation

**API**: `POST /api/client/evaluate`

```json
{
  "appKey": "prod",
  "userId": "user_vip5",
  "flagKeys": ["enable_premium_feature"],
  "attributes": {
    "vipLevel": 5
  }
}
```

**Expected**: Still `enabled: true` (rule unchanged, version incremented)

---

## Step 5: Get Flag Details

### 5.1 Get Single Flag

**API**: `GET /api/admin/flags`

**Query Parameters**:
- `appKey`: `prod`
- `flagKey`: `enable_premium_feature`

**Expected Response**:
```json
{
  "success": true,
  "data": {
    "flagKey": "enable_premium_feature",
    "name": "Enable Premium Feature (Updated)",
    "version": 2,
    "rules": [...]
  }
}
```

### 5.2 List All Flags

**API**: `GET /api/admin/flags`

**Query Parameters**:
- `appKey`: `prod`
- `flagKey`: (leave empty)

**Expected Response**:
```json
{
  "success": true,
  "data": [
    {"flagKey": "enable_premium_feature", "version": 2},
    {"flagKey": "enable_regional_promo", "version": 1},
    {"flagKey": "enable_high_value_discount", "version": 1},
    {"flagKey": "enable_new_user_bonus", "version": 1},
    {"flagKey": "enable_mobile_app_feature", "version": 1}
  ],
  "total": 5
}
```

---

## Step 6: Incremental Sync Test

### 6.1 Initial Full Sync

**API**: `GET /api/client/configs`

**Query Parameter**: `appKey = prod`

**Expected Response**:
```json
{
  "success": true,
  "data": {
    "globalVersion": 100,
    "flags": [...5 flags...],
    "hasChanges": true
  }
}
```

**Record globalVersion, assume it's 100**

### 6.2 Incremental Sync - No Changes

**API**: `GET /api/client/configs`

**Query Parameters**: `appKey=prod&lastKnownVersion=100`

**Expected Response**:
```json
{
  "success": true,
  "data": {
    "globalVersion": 100,
    "flags": [],
    "hasChanges": false
  }
}
```

### 6.3 Incremental Sync - With Changes

After Step 4 update, perform incremental sync:

**API**: `GET /api/client/configs`

**Query Parameters**: `appKey=prod&lastKnownVersion=100`

**Expected Response**:
```json
{
  "success": true,
  "data": {
    "globalVersion": 101,
    "flags": [
      {
        "flagKey": "enable_premium_feature",
        "name": "Enable Premium Feature (Updated)",
        "version": 2
      }
    ],
    "hasChanges": true
  }
}
```

**Note**: Only returns changed flags

---

## Step 7: Delete Flag Test

### 7.1 Delete Flag

**API**: `DELETE /api/admin/flags`

**Query Parameters**:
- `appKey`: `prod`
- `flagKey`: `enable_new_user_bonus`

**Expected Response**:
```json
{
  "success": true,
  "message": "Flag deleted successfully"
}
```

### 7.2 Verify Deletion

**API**: `GET /api/admin/flags`

**Query Parameters**:
- `appKey`: `prod`
- `flagKey`: `enable_new_user_bonus`

**Expected Response**:
```json
{
  "success": false,
  "error": "Flag not found: enable_new_user_bonus"
}
```

### 7.3 Evaluate Deleted Flag

**API**: `POST /api/client/evaluate`

```json
{
  "appKey": "prod",
  "userId": "user_test",
  "flagKeys": ["enable_new_user_bonus"],
  "attributes": {}
}
```

**Expected Response**:
```json
{
  "success": true,
  "data": {
    "enable_new_user_bonus": {
      "flagKey": "enable_new_user_bonus",
      "enabled": false,
      "value": "false",
      "reason": "DEFAULT"
    }
  }
}
```

**Note**: Flag deleted, SDK returns default value (Fail-Safe design).

---

## Step 8: Cache Statistics and Version Check

### 8.1 View Cache Statistics

**API**: `GET /api/client/cache-stats`

**Query Parameter**: `appKey = prod`

**Expected Response**:
```json
{
  "success": true,
  "data": {
    "appKey": "prod",
    "environment": "prod",
    "cachedFlags": 4,
    "trackedVersions": 4,
    "globalVersion": 101
  }
}
```

**Note**: One flag deleted, 4 remaining

### 8.2 Get All Flag Versions

**API**: `GET /api/client/versions`

**Query Parameter**: `appKey = prod`

**Expected Response**:
```json
{
  "success": true,
  "data": {
    "globalVersion": 101,
    "flagVersions": {
      "enable_premium_feature": 2,
      "enable_regional_promo": 1,
      "enable_high_value_discount": 1,
      "enable_mobile_app_feature": 1
    }
  }
}
```

---

## Step 9: Cleanup (Optional)

Delete all test flags:

1. `DELETE /api/admin/flags?appKey=prod&flagKey=enable_premium_feature`
2. `DELETE /api/admin/flags?appKey=prod&flagKey=enable_regional_promo`
3. `DELETE /api/admin/flags?appKey=prod&flagKey=enable_high_value_discount`
4. `DELETE /api/admin/flags?appKey=prod&flagKey=enable_mobile_app_feature`

---

## Test Completion Checklist

- [ ] All 5 rule conditions (EQ, IN, GTE, LTE, CONTAINS) tested
- [ ] Match success and failure cases verified
- [ ] Batch evaluation working correctly
- [ ] Flag update and version increment verified
- [ ] Incremental sync returning only changed flags
- [ ] Flag deletion working correctly
- [ ] Cache statistics accurate
- [ ] Fail-safe behavior confirmed (deleted flag returns DEFAULT)

---

## Common Issues

### Issue 1: "App not found" Error
**Solution**: ClientRegisterController auto-creates apps. No manual setup needed.

### Issue 2: Evaluation Returns DEFAULT Instead of MATCHED_RULE
**Solution**: Check if rule `type` is set to `"TARGETING"` in the request body.

### Issue 3: Incremental Sync Returns All Flags
**Solution**: Ensure `lastKnownVersion` parameter is provided and matches the current global version.

### Issue 4: Cache Stats Show Incorrect Count
**Solution**: Restart the application to clear stale cache entries.
