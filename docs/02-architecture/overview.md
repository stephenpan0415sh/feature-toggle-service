# System Architecture Overview

## 📋 Design Goals

Based on the assignment requirements:
> "The system manages thousands of feature flags across >100 applications and services — covering web portals, backend APIs, and mobile clients. It needs to serve flag evaluations at high throughput with low latency while keeping resource usage within reasonable bounds as the feature catalog grows."

### Key Objectives
- **High Throughput**: Support 1000+ QPS per instance
- **Low Latency**: P99 < 5ms for flag evaluation
- **Scalability**: Handle growing flag catalog efficiently
- **Real-time Updates**: Configuration changes propagate < 1 second
- **Cost Efficiency**: Optimize resource usage with multi-level caching

---

## 🏗️ System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Client Applications                          │
│  ┌──────────┐  ┌──────────  ┌──────────┐  ┌──────────────────┐   │
│  │Web Portal│  │Backend API│  │Mobile App│  │Other Services    │   │
│  │(JS SDK)  │  │(Java SDK) │  │(iOS/And) │  │(REST API)        │   │
│  └────┬─────┘  └────┬─────  └────┬─────┘  └────────┬─────────┘   │
└───────┼─────────────┼─────────────┼──────────────────┼──────────────┘
        │             │             │                  │
        │  L1 Cache   │  L1 Cache   │  L1 Cache        │  HTTP
        │ (Local)     │ (Local)     │ (Local)          │
        ▼             ▼             ▼                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         ff-server (Backend)                          │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    Client API Layer                           │  │
│  │  - Flag evaluation (single/batch)                            │  │
│  │  - Configuration pull (incremental sync)                     │  │
│  │  - Version check                                             │  │
│  └──────────────────────────┬───────────────────────────────────┘  │
│  ┌──────────────────────────┴───────────────────────────────────┐  │
│  │                    Admin API Layer                            │  │
│  │  - Flag CRUD operations                                      │  │
│  │  - Rule management                                           │  │
│  │  - Configuration publishing                                  │  │
│  └──────────────────────────┬───────────────────────────────────┘  │
│  ┌──────────────────────────┴───────────────────────────────────┐  │
│  │                   EvaluationService                           │  │
│  │  - L2 Cache (Redis) lookup                                   │  │
│  │  - Rule evaluation engine                                    │  │
│  │  - Metrics collection                                        │  │
│  └──────────────────────────┬───────────────────────────────────┘  │
│  ┌──────────────────────────┴───────────────────────────────────┐  │
│  │              Redis (L2 Cache + Pub/Sub)                       │  │
│  │  - Flag configuration cache                                  │  │
│  │  - Pub/Sub for real-time updates                             │  │
│  │  - Version tracking                                          │  │
│  └──────────────────────────┬───────────────────────────────────┘  │
└─────────────────────────────┼───────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────────┐
                    │   MySQL (L3 Store)  │
                    │  - Feature flags    │
                    │  - Rules            │
                    │  - Applications     │
                    └─────────────────────┘
```

### Architecture Layers

| Layer | Component | Responsibility |
|-------|-----------|----------------|
| **L1 Cache** | SDK local memory | Sub-millisecond evaluation, ConcurrentHashMap |
| **L2 Cache** | Redis | Shared cache across instances, Pub/Sub notifications |
| **L3 Store** | MySQL | Persistent storage, source of truth |
| **Communication** | HTTP API + Redis Pub/Sub | Sync + async update propagation |

---

## 📦 Module Responsibilities

### Project Structure

```
feature-toggle-service/
├── ff-common/              # Shared models and utilities
├── ff-server/              # Spring Boot backend
├── ff-sdk-java/            # Java SDK implementation
├── ff-sdk-core/            # Platform-agnostic rule evaluator
└── docs/                   # Documentation
```

### Module Details

#### 1. ff-common (Shared Module)
**Purpose**: Ensure consistency across server and SDK

**Contents**:
- **Models**: `FeatureFlag`, `Rule`, `Condition`, `UserContext`, `EvaluationDetail`
- **DTOs**: `EvaluationEvent`, `ConfigChangeEvent`, `EvaluationResponse`
- **Exceptions**: `FlagNotFoundException`, `InvalidRuleException`
- **Constants**: Common configuration keys

**Why Separate?**:
- Single source of truth for data models
- SDK and server use identical rule evaluation logic
- Easy to maintain API contracts

#### 2. ff-server (Backend Service)
**Purpose**: Admin management + Client API serving

**Components**:
- **Controllers**: `AdminController`, `ClientApiController`, `ClientRegisterController`
- **Services**: `EvaluationService`, `AdminService`, `FlagCacheService`, `AuditLogService`
- **Repositories**: JPA repositories for MySQL
- **Config**: Redis, Prometheus, OpenAPI

**Key Features**:
- Multi-level caching (L2 Redis + L3 MySQL)
- Incremental configuration sync
- Real-time updates via Redis Pub/Sub
- Prometheus metrics collection
- Audit logging (Kafka-ready)

**Admin Dashboard (Future)**:
- Currently, admin operations are performed via REST API (Swagger UI).
- A web-based Admin Dashboard is planned for production to provide:
  - Visual flag management interface
  - Rule builder with drag-and-drop
  - Real-time evaluation testing
  - Audit log viewer
  - User permission management

#### 3. ff-sdk-java (Java SDK)
**Purpose**: Client-side flag evaluation for Java applications

**Components**:
- **FeatureToggleClient**: Main client with local cache
- **RuleEvaluator**: Rule evaluation engine (from ff-sdk-core)
- **Annotations**: `@ToggleMethod`, `@FeatureFlag`, `@Rule`
- **AOP**: `FeatureToggleAspect` for annotation-driven evaluation

**Key Features**:
- L1 local cache (ConcurrentHashMap)
- Background sync (scheduled polling)
- Redis Pub/Sub subscription (real-time updates)
- Annotation-driven evaluation
- Fail-safe design

#### 4. ff-sdk-core (Core Logic)
**Purpose**: Language-agnostic rule evaluation engine

**Components**:
- **RuleEvaluator**: Match rules against user context
- **PercentageCalculator**: Consistent hashing for percentage rollout

**Why Separate?**:
- Can be reused by other language SDKs
- Pure algorithm, no dependencies on HTTP/Redis
- Easy to test and maintain

---

## 🔄 Data Flow

### 1. Flag Evaluation Flow

```
SDK Request
    │
    ├─▶ Check L1 Cache (Local)
    │   ├─ Hit → Evaluate locally → Return
    │   └─ Miss →
    │
    ├─▶ HTTP Request to ff-server
    │   │
    │   ├─▶ Check L2 Cache (Redis)
    │   │   ├─ Hit → Evaluate → Return + Update L1
    │   │   └─ Miss →
    │   │
    │   ├─▶ Query L3 Store (MySQL)
    │   │   ├─ Found → Cache in L2 → Evaluate → Return
    │   │   └─ Not Found → Cache null → Return default
    │   │
    │   └─▶ Record Metrics + Audit Log
    │
    └─▶ SDK caches result in L1
```

**Performance**:
- L1 Hit: < 1ms
- L2 Hit: 2-3ms
- L3 Query: 10-20ms (cached afterwards)

### 2. Configuration Change Propagation

```
Admin updates flag via Admin API
    │
    ├─▶ Update MySQL (L3)
    │
    ├─▶ Update Redis cache (L2)
    │
    ├─▶ Publish Redis Pub/Sub message
    │   │
    │   ├─▶ ff-server instance #1 receives → Updates local state
    │   ├─▶ ff-server instance #2 receives → Updates local state
    │   └─▶ ...
    │
    └─▶ SDK receives Pub/Sub message (if subscribed)
        │
        ├─▶ Fetch updated config from ff-server
        │
        └─▶ Update L1 cache
```

**Latency**: < 100ms end-to-end

### 3. Incremental Sync Flow

```
SDK startup or periodic sync
    │
    ├─▶ Send lastKnownVersion to server
    │
    ├─▶ Server compares with current global version
    │   │
    │   ├─ No changes → Return empty list
    │   │
    │   └─ Has changes →
    │       │
    │       ├─ Query only changed flags (version > lastKnownVersion)
    │       │
    │       └─ Return delta + new global version
    │
    └─▶ SDK updates L1 cache with delta
```

**Benefits**:
- Reduces bandwidth by 90%+ (only transfer changes)
- Faster sync for large flag catalogs
- Lower server load

---

## 🎯 Key Design Decisions

### 1. Multi-Level Caching

**Decision**: L1 (SDK local) + L2 (Redis) + L3 (MySQL)

**Rationale**:
- **L1**: Eliminates network latency for repeated evaluations
- **L2**: Shared cache across SDK instances, reduces DB load
- **L3**: Persistent storage, source of truth

**Trade-offs**:
- ✅ Pros: Sub-ms latency, high throughput, DB protection
- ⚠️ Cons: Cache consistency complexity, memory usage

### 2. Redis Pub/Sub for Real-time Updates

**Decision**: Use Redis Pub/Sub instead of WebSocket or polling

**Rationale**:
- Redis already used for L2 cache (no new infrastructure)
- Push-based (faster than polling)
- Simple protocol, reliable delivery

**Trade-offs**:
- ✅ Pros: Low latency, simple, cost-effective
- ⚠️ Cons: No message persistence (acceptable for config updates)

### 3. Incremental Sync with Version Tracking

**Decision**: Track global version + per-flag version

**Rationale**:
- SDK can quickly check if update is needed
- Server only returns changed flags
- Reduces network bandwidth and processing

**Trade-offs**:
- ✅ Pros: Efficient, scalable for large catalogs
- ⚠️ Cons: Version management complexity

### 4. Annotation-Driven Evaluation (Java SDK)

**Decision**: Provide `@ToggleMethod` annotation for AOP-based evaluation

**Rationale**:
- Zero boilerplate code for developers
- Declarative configuration
- Consistent with Spring ecosystem patterns

**Trade-offs**:
- ✅ Pros: Developer experience, clean code
- ⚠️ Cons: AOP overhead (~0.1ms), learning curve

### 5. Fail-Safe Design

**Decision**: Cache failures don't block evaluations

**Rationale**:
- Redis down → Fall back to MySQL
- MySQL down → Use cached values
- All down → Return default values

**Trade-offs**:
- ✅ Pros: High availability, graceful degradation
- ⚠️ Cons: May serve stale data during outages

---

## 🛠️ Technology Selection

We selected our stack by balancing **Requirement Fit** (Performance, Availability) and **Implementation Complexity**. The core decision was choosing the communication/persistence layer for real-time config updates.

### 1. Communication & Cache Layer Comparison

| Dimension | ZooKeeper (CP) | Nacos (AP/CP) | **Our Solution: Redis Pub/Sub (AP)** |
| :--- | :--- | :--- | :--- |
| **Requirement Fit** | ❌ Low. CP model causes unavailability during elections. Toggles need high availability. | ⚠️ Medium. Supports AP but is a heavy-weight configuration center. | ✅ **High**. Pure AP model ensures service availability. Millisecond-level broadcast. |
| **Implementation Difficulty** | 🔴 Hard. Complex cluster management; requires dedicated ops support. | 🟡 Moderate. Heavy dependencies; steep learning curve for integration. | 🟢 **Easy**. Simple Publish/Subscribe API with minimal configuration. |
| **Resource Usage** | High. Dedicated nodes for coordination only. | High. Requires significant memory and CPU for its full feature set. | **Low**. Lightweight and efficient for messaging and caching. |
| **Latency** | Low, but inconsistent under stress. | Medium (Polling-based default). | **Ultra-low** (Event-driven push). |

#### Why we chose Redis Pub/Sub?
- **Simplicity**: Redis provides a straightforward Pub/Sub mechanism that is easy to implement and maintain.
- **Availability First**: Feature toggles must never block business logic. Redis's AP nature aligns perfectly with this requirement.

### 2. Persistence Layer Comparison

| Dimension | PostgreSQL | MongoDB | **Our Solution: MySQL** |
| :--- | :--- | :--- | :--- |
| **Requirement Fit** | ⭐⭐⭐⭐⭐. Excellent for relational data. | ⭐⭐⭐. Good for flexible schemas, but overkill for structured flags. | ⭐⭐⭐⭐⭐. Industry standard for structured flag definitions and rules. |
| **Implementation Difficulty** | 🟢 Easy. Mature ecosystem. | 🟡 Moderate. Requires different modeling patterns. | 🟢 **Easy**. Spring Data JPA provides out-of-the-box support. |

---

## 🔒 Consistency Guarantees

### Eventual Consistency Model

| Scenario | Consistency | Latency |
|----------|-------------|---------|
| L1 Cache Hit | Strong (within SDK instance) | < 1ms |
| L2 Cache Hit | Eventual (across instances) | 2-3ms |
| Config Update | Eventual (propagates via Pub/Sub) | < 100ms |
| Incremental Sync | Eventual (next sync cycle) | 30s (default) |

### Cache Invalidation Strategy

1. **Write-through**: Admin updates → Update L2 → Publish Pub/Sub
2. **TTL**: L1 cache expires after configured interval
3. **Push-based**: Pub/Sub triggers immediate L1 invalidation
4. **Version-based**: SDK detects stale cache via version mismatch

---

## 📊 Scalability Considerations

### Horizontal Scaling

- **ff-server**: Stateless, can scale horizontally behind load balancer
- **Redis**: Single instance supports ~100K ops/sec (sufficient for most cases)
- **MySQL**: Read replicas for high-traffic scenarios

### Vertical Scaling

- **SDK memory**: Each SDK instance caches ~1000 flags (~1-2MB RAM)
- **Redis memory**: 10K flags ~ 10-20MB (with rules and metadata)
- **MySQL storage**: 100K flags ~ 50-100MB

### Performance Optimization

1. **Connection pooling**: Redis and MySQL connection pools
2. **Async operations**: Audit logging, metrics collection
3. **Batch operations**: Batch evaluation reduces HTTP overhead
4. **Compression**: Gzip for large config payloads (optional)

---

## 🚀 Next Steps

Explore detailed architecture topics:

1. **[Caching Strategy](caching-strategy.md)** - Multi-level cache design
2. **[Technology Comparison](tech-stack-comparison.md)** - Why these technologies?
3. **[Data Model](data-model.md)** - Database schema and design

---

*This document provides a high-level overview of the system architecture. See detailed documents for in-depth analysis.*
