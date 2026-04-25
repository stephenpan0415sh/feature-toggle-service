# Known Limitations

## Overview

This document outlines the current limitations of the system and explains the rationale behind design decisions.

---

## 1. Multi-Language SDK Support

### Current Status
- ✅ **Java SDK**: Fully implemented with production-ready features
- ❌ **JavaScript SDK**: Not implemented (architecture designed)
- ❌ **iOS SDK**: Not implemented (architecture designed)
- ❌ **Android SDK**: Not implemented (architecture designed)

### Why Only Java SDK?

**Context**: This project was developed as a technical assessment for a **Senior Java Developer role with architecture focus**.

**Decision Rationale**:

1. **Depth Over Breadth**
   - Implemented a complete, production-ready Java SDK with advanced features:
     - Multi-level caching (L1 memory + L2 Redis)
     - Real-time updates via Redis Pub/Sub
     - Annotation-driven evaluation (@ToggleMethod)
     - Incremental configuration sync
   - Rather than creating shallow implementations of multiple SDKs

2. **Demonstrate Core Competencies**
   - As a Java candidate, showcasing deep Java/Spring expertise is more valuable
   - The Java SDK proves understanding of:
     - Concurrent programming (ConcurrentHashMap, ScheduledExecutorService)
     - Caching strategies
     - Event-driven architecture (Pub/Sub)
     - AOP (annotation-driven evaluation)

3. **Extensible Architecture**
   - System designed for easy multi-language expansion
   - `ff-sdk-core` contains language-agnostic rule evaluation logic
   - Other SDKs only need to implement communication layer and native cache

### How to Add Other SDKs

The architecture makes it straightforward to add new language SDKs:

1. Reuse `ff-sdk-core` for evaluation logic (or reimplement ~200 lines)
2. Implement HTTP client for config sync
3. Use native storage (LocalStorage, CoreData, SharedPreferences)
4. Follow the same API contract

📖 **Detailed explanation**: [docs/04-sdk-guide/README.md](../05-sdk-guide/README.md#why-only-java-sdk)

---

## 2. Authentication & Authorization

### Current Status
- ❌ No authentication on Admin API
- ❌ No API key validation on Client API
- ❌ No rate limiting

### Why Not Implemented?

**Focus on Core Requirements**: The assignment emphasized:
- Caching strategy
- API design
- Observability
- Explainability

Authentication, while important for production, was deprioritized to focus on demonstrating system design skills.

### Production Recommendation

Implement:
- OAuth2 + JWT for Admin API
- API key authentication for Client API
- Rate limiting (e.g., 1000 req/min per app)
- IP whitelisting for admin access

---

## 3. Real Kafka Integration

### Current Status
- ⚠️ Audit logging simulated with console output
- ❌ No actual Kafka producer/consumer

### Why Simulated?

**Infrastructure Complexity**: Setting up a real Kafka cluster requires:
- Additional infrastructure (Zookeeper, Kafka brokers)
- Complex configuration
- Operational overhead

For a technical assessment, demonstrating the **design pattern** (async audit logging with fail-safe) is more valuable than infrastructure setup.

### Production Implementation

Replace `AuditLogService` with:
```java
@Service
public class AuditLogService {
    @Autowired
    private KafkaTemplate<String, EvaluationEvent> kafkaTemplate;
    
    public void logEvaluation(EvaluationEvent event) {
        kafkaTemplate.send("feature-flag-evaluations", event);
    }
}
```

---

## 4. Load Testing Automation

### Current Status
- ❌ No automated performance test scripts
- ✅ Performance testing guide provided

### Why Not Automated?

**Time Constraints**: Creating comprehensive JMeter/Gatling scripts requires significant time investment. Instead, provided:
- Performance targets and benchmarks
- Testing methodology documentation
- Manual testing commands (wrk, curl)

### Next Steps

See [docs/07-testing/performance-testing.md](../08-testing/performance-testing.md) for:
- Test scenarios
- Expected benchmarks
- Tool usage examples

---

## Summary

| Limitation | Priority to Fix | Effort | Impact |
|-----------|----------------|--------|--------|
| Multi-language SDKs | Medium | High | User adoption |
| Authentication | High | Medium | Security |
| Real Kafka | Medium | Low | Observability |
| Load testing automation | Low | Medium | Validation |

**Key Takeaway**: These limitations are conscious trade-offs to focus on demonstrating **system design capabilities** and **core architectural patterns** within the scope of a technical assessment.
