# Feature Toggle Service

> **Enterprise-grade Feature Flag Management System** for high-throughput, low-latency flag evaluation at scale.

[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

---

## 🎯 Assignment Requirements

| Requirement | Status | Details |
|-------------|--------|---------|
| **Caching Strategy** | ✅ Complete | Multi-level cache (L1/L2/L3) with incremental sync |
| **Client SDK** | ⚠️ Partial | Java SDK implemented; architecture ready for multi-language |
| **Full API Design** | ✅ Complete | Admin API + Client API with OpenAPI docs |
| **Observability** | ✅ Complete | Prometheus metrics + ELK audit logging |
| **Explainability** | ✅ Complete | Full context: who/when/where/why for every evaluation |

📊 **Detailed analysis**: [docs/09-project-status/completion-summary.md](docs/09-project-status/completion-summary.md)

---

## 🚀 Quick Start

```bash
docker-compose up -d  # Start MySQL + Redis
mvn clean install && cd ff-server && mvn spring-boot:run
open http://localhost:8080/swagger-ui.html
```

📘 **Full guide**: [docs/00-quick-start.md](docs/00-quick-start.md)

---

## 📚 Documentation

### Getting Started
- [Requirements Overview](docs/01-requirements/overview.md)
- [Quick Start Guide](QUICK_START.md)

### Architecture & Design
- [System Architecture](docs/02-architecture/overview.md)
- [Caching Strategy](docs/02-architecture/caching-strategy.md)
- [Technology Comparison](docs/02-architecture/tech-stack-comparison.md)
- [Data Model](docs/02-architecture/data-model.md)

### API Reference
- [Admin API](docs/04-api-guide/admin-api.md)
- [Client API](docs/04-api-guide/client-api.md)
- [API Examples](docs/04-api-guide/api-examples.md)

### SDK Integration
- [Integration Guide](docs/05-sdk-guide/sdk-integration-guide.md)
- [SDK Design](docs/05-sdk-guide/sdk-design.md)

### Observability
- [Overview](docs/06-observability/README.md)
- [Monitoring (Prometheus)](docs/06-observability/monitoring.md)
- [Audit Logging (ELK)](docs/06-observability/audit-logging.md)
- **[Explainability Model](docs/06-observability/explainability.md)** ⭐

### Operations
- [Operations Guide](docs/07-operations/README.md)

### Testing
- [Performance Testing](docs/08-testing/performance-testing.md)

### Project Status
- [Completion Summary](docs/09-project-status/completion-summary.md)
- [Known Limitations](docs/09-project-status/known-limitations.md)

---

## ✨ Key Features

- **🏎️ High Performance**: Sub-5ms P99 latency, 2000+ QPS
- **🔄 Real-time Updates**: Redis Pub/Sub (<100ms propagation)
- **🎯 Explainability**: Full evaluation context with traceId
- **📊 Observability**: Prometheus metrics + ELK audit trail
- **🛡️ Production Ready**: Fail-safe design, async logging

---

## 💻 Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.x |
| Database | MySQL 8.0 |
| Cache | Redis 6.x |
| SDK | Java 17+ |
| Monitoring | Micrometer + Prometheus |
| API Docs | SpringDoc OpenAPI |

---

## 📦 Modules

- **ff-common**: Shared models and DTOs
- **ff-server**: Spring Boot backend (Admin + Client APIs)
- **ff-sdk-java**: Java SDK with local cache
- **ff-sdk-core**: Platform-agnostic rule evaluator

📂 **Details**: [docs/02-architecture/overview.md](docs/02-architecture/overview.md)

---

## 📚 Project Documentation

For a deeper understanding of the system design and implementation:

- [System Architecture & Design](docs/02-architecture/overview.md)
- [API Reference (Admin & Client)](docs/04-api-guide/admin-api.md)
- [SDK Integration Guide](docs/05-sdk-guide/sdk-integration-guide.md)
- [Data Model & Schema](docs/03-data-model/data-model.md)

---

**📖 All documentation**: [docs/](docs/) directory