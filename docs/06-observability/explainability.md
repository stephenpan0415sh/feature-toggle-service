# Explainability Model

The system provides full explainability for every flag evaluation, answering: **Why did this user see this result?**

## 1. EvaluationDetail Structure

Every evaluation returns a rich `EvaluationDetail` object containing the complete decision context:

```json
{
  "flagKey": "new_checkout_flow",
  "enabled": true,
  "value": "v2",
  "reason": "MATCHED_RULE",
  "matchedRuleId": "rule_001",
  "traceId": "trace_abc123",
  "environment": "prod",
  "region": "cn-beijing",
  "releaseVersion": "v1.2.3",
  "evaluatedAt": 1713849600000,
  "userContextSnapshot": {
    "uid": 123,
    "vip_level": 2
  },
  "matchedConditions": [
    "uid in [100, 200, 300]",
    "vip_level >= 2"
  ]
}
```

## 2. Evaluation Reasons

The `reason` field explains why the flag evaluated to a specific value:

| Reason | Description | When Used |
| :--- | :--- | :--- |
| `DEFAULT` | No rules matched, returned default value | Fallback scenario |
| `MATCHED_RULE` | User matched a targeting rule | Standard rule evaluation |
| `KILL_SWITCH` | Kill switch disabled the flag | Emergency disable |
| `WHITELIST` | User is in the whitelist | Forced enable |
| `BLACKLIST` | User is in the blacklist | Forced disable |
| `PERCENTAGE_ROLLOUT` | User matched percentage rollout criteria | Gradual rollout |
| `ERROR` | Error occurred during evaluation | System error |
| `FLAG_NOT_FOUND` | Flag key doesn't exist | Configuration error |

## 3. User Context Snapshot

The `userContextSnapshot` captures the exact attributes used during evaluation:

```json
{
  "userId": "user_123",
  "region": "cn-east",
  "vipLevel": 5,
  "deviceType": "mobile",
  "appVersion": "2.1.0"
}
```

This ensures that:
- **Debugging**: You can reproduce the exact evaluation later.
- **Auditing**: You have proof of what conditions were checked.
- **Analytics**: You can analyze which user segments triggered which rules.

## 4. Usage Examples

### 4.1. Debugging in Production

```java
EvaluationDetail detail = client.evaluate("new_checkout", user);

if (!detail.isSuccess()) {
    log.warn("Evaluation failed: {} (reason: {})", detail.flagKey(), detail.reason());
}

// Log for debugging
log.debug("User {} saw {} because: {}", 
    user.userId(), 
    detail.enabled(), 
    detail.matchedConditions());
```

### 4.2. A/B Test Analysis

```java
// Send evaluation results to analytics
eventBus.publish(new FeatureFlagEvaluationEvent(
    detail.traceId(),
    detail.flagKey(),
    detail.enabled(),
    detail.reason(),
    detail.userContextSnapshot()
));
```

## 5. Integration with Distributed Tracing

The `traceId` field allows correlation with other microservice logs:

```
[API Gateway] traceId=abc123 -> Request received
[Order Service] traceId=abc123 -> Evaluating flag 'new_checkout'
[Feature Toggle] traceId=abc123 -> Flag enabled=true, reason=MATCHED_RULE
[Order Service] traceId=abc123 -> Using new checkout flow
```

---

For Prometheus metrics and monitoring, see [Monitoring](monitoring.md).
