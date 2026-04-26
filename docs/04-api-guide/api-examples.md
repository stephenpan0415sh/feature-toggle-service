# API Usage Examples

This document provides practical examples of how to interact with the Feature Toggle Service APIs using `curl`.

## Prerequisites

- Server running at `http://localhost:8080`
- An application registered with `appKey`: `my-app`

---

## Admin API Examples

### 1. Create a Flag
```bash
curl -X POST "http://localhost:8080/api/admin/flags?appKey=my-app" \
     -H "Content-Type: application/json" \
     -d '{
       "flagKey": "enable_dark_mode",
       "name": "Enable Dark Mode",
       "status": 1,
       "defaultValue": "false"
     }'
```

### 2. Update a Flag
```bash
curl -X PUT "http://localhost:8080/api/admin/flags/enable_dark_mode?appKey=my-app" \
     -H "Content-Type: application/json" \
     -d '{
       "status": 1,
       "defaultValue": "true",
       "rules": [
         {
           "priority": 1,
           "conditions": [{"attribute": "region", "operator": "==", "values": ["US"]}],
           "actionValue": "false"
         }
       ]
     }'
```

### 3. List All Flags
```bash
curl "http://localhost:8080/api/admin/flags?appKey=my-app"
```

---

## Client API Examples

### 1. Evaluate a Single Flag
```bash
curl "http://localhost:8080/api/client/evaluate/enable_dark_mode?appKey=my-app&userId=user_123&region=US"
```

### 2. Batch Evaluate Flags
```bash
curl -X POST "http://localhost:8080/api/client/evaluate" \
     -H "Content-Type: application/json" \
     -d '{
       "appKey": "my-app",
       "userId": "user_123",
       "flagKeys": ["enable_dark_mode", "show_welcome_banner"],
       "attributes": {
         "region": "US",
         "vipLevel": 3
       }
     }'
```

### 3. SDK Configuration Sync (Incremental)
```bash
# First sync (Full)
curl "http://localhost:8080/api/client/configs?appKey=my-app"

# Subsequent sync (Incremental)
curl "http://localhost:8080/api/client/configs?appKey=my-app&lastKnownVersion=105"
```

---

## Error Handling

All APIs return a consistent error format:
```json
{
  "success": false,
  "error": "Flag not found: enable_dark_mode"
}
```

Common HTTP Status Codes:
- `200 OK`: Request succeeded.
- `400 Bad Request`: Invalid parameters or payload.
- `404 Not Found`: Resource does not exist.
- `500 Internal Server Error`: Server-side exception.
