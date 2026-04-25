# Observability Overview

This section documents how the Feature Toggle Service provides visibility into its operations through metrics, audit logs, and explainability.

## 1. Monitoring & Metrics

- **Prometheus Metrics**: Evaluation latency, cache hit rate, error rate
- **Health Checks**: Database, Redis, and overall system health
- **Alerting**: Recommended thresholds for production

📄 [Monitoring Guide](monitoring.md)

## 2. Audit Logging

- **Evaluation Events**: Asynchronous logging of every flag evaluation
- **Configuration Changes**: Audit trail for admin actions (create, update, delete)
- **Kafka Integration**: Ready for production-scale event streaming

📄 [Audit Logging Guide](audit-logging.md)

## 3. Explainability Model

- **EvaluationDetail**: Rich response object answering "Why did this user see this result?"
- **User Context Snapshot**: Complete record of attributes used during evaluation
- **Distributed Tracing**: `traceId` for correlating with microservice logs

📄 [Explainability Model](explainability.md)

---

## Why Observability Matters

Feature toggles are **decision points** in your application. When something goes wrong, you need to know:

1. **Which users** were affected?
2. **Why** did they see a specific result?
3. **When** did the configuration change occur?

This observability stack provides complete answers to these questions.
