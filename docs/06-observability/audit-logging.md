# Audit Logging

The system captures two types of audit events: **Evaluation Events** and **Configuration Change Events**.

## 1. Architecture

Audit logs are sent to **Kafka** asynchronously to avoid blocking the main request path. In the current implementation, this is simulated via local logging.

```
Evaluation Request
      │
      ├─▶ Evaluate Flag (Fast Path)
      │
      └─▶ AuditLogService.logEvaluation() (Async, Non-blocking)
              │
              ▼
          Kafka Topic: ff-audit-evaluations
```

## 2. Event Types

### 2.1. Evaluation Event
Captured for every flag evaluation (sent asynchronously).

**Fields**:
| Field | Description |
| :--- | :--- |
| `eventId` | Unique identifier for the event |
| `timestamp` | Unix timestamp (ms) |
| `appKey` | Application identifier |
| `flagKey` | Flag being evaluated |
| `enabled` | Evaluation result |
| `userId` | End-user identifier |
| `traceId` | Distributed tracing ID |
| `evaluationLatencyMs` | Time taken for evaluation |

### 2.2. Configuration Change Event
Captured when an admin creates, updates, or deletes a flag.

**Fields**:
| Field | Description |
| :--- | :--- |
| `changeType` | CREATE, UPDATE, or DELETE |
| `changedBy` | Admin user who made the change |
| `previousState` | Snapshot of flag before change |
| `newState` | Snapshot of flag after change |
| `changeReason` | Human-readable reason for change |
| `ipAddress` | IP of the admin user |

## 3. Integration with ELK Stack

In production, configure Logstash to consume from Kafka and index into Elasticsearch:

```yaml
# logstash.conf
input {
  kafka {
    bootstrap_servers => "kafka:9092"
    topics => ["ff-audit-evaluations", "ff-audit-config-changes"]
    codec => json
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "feature-toggle-audit-%{+YYYY.MM.dd}"
  }
}
```

## 4. Current Implementation Status

| Component | Status | Notes |
| :--- | :--- | :--- |
| Event Model | ✅ Complete | `EvaluationEvent`, `ConfigChangeEvent` |
| Async Sending | ✅ Simulated | Uses `@Async` or thread pool |
| Kafka Producer | 🔄 Placeholder | Ready for Kafka dependency |
| Kibana Dashboard | ❌ Not Implemented | Would be built in production |

---

For detailed explainability of evaluation results, see [Explainability Model](explainability.md).
