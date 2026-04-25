# Java SDK Design

This document details the internal architecture and design decisions of the Java SDK.

## 1. Architecture Overview

The SDK follows a **Local-First** architecture with background synchronization:

```
Application Code
      │
      ▼
FeatureToggleClient (L1 Cache: ConcurrentHashMap)
      │
      ├─▶ RuleEvaluator (Local Evaluation)
      │
      ├─▶ Background Sync (Heartbeat + Pub/Sub)
      │
      └─▶ HTTP Client (Communication with ff-server)
```

## 2. Core Components

### 2.1. FeatureToggleClient
The main entry point. It manages the L1 cache and handles communication with the server.
- **Cache**: `ConcurrentHashMap<String, FeatureFlag>` (No TTL, updated via sync).
- **Sync Strategy**: Combination of scheduled polling and real-time Pub/Sub updates.

### 2.2. FlagUpdateListener
Listens to Redis Pub/Sub messages for real-time configuration changes.
- **Debouncing**: Uses a 100ms window to coalesce multiple notifications into a single full sync.
- **Consistency**: Triggers a full config pull (`lastKnownVersion=0`) to ensure L1 is perfectly consistent with the server, including deletions.

### 2.3. RuleEvaluator
The engine that matches rules against `UserContext`.
- Located in `ff-sdk-core` for cross-language reusability.
- Supports various operators (`==`, `!=`, `in`, `contains`).

## 3. Synchronization Mechanism

### 3.1. Full Sync vs. Incremental Sync
- **Full Sync**: Triggered by Pub/Sub or startup. Replaces the entire L1 cache.
- **Incremental Sync**: Triggered by heartbeat. Only pulls flags with `version > lastKnownVersion`.

### 3.2. Consistency Guarantee
| Scenario | Detection Time | Recovery Method |
| :--- | :--- | :--- |
| Pub/Sub Success | < 100ms | Immediate full sync |
| Pub/Sub Failure | < 30s | Heartbeat version check |
| Server Down | N/A | Use stale L1 cache (Fail-safe) |

## 4. Internal API Usage

The SDK automatically interacts with several internal endpoints of `ff-server`:

### 4.1. Flag Registration
- **Endpoint**: `POST /api/client/register`
- **Trigger**: Called at startup for every flag defined via `@FeatureFlag` annotation.
- **Logic**: Checks if the flag exists on the server. If not, it creates it. If it does, the server's configuration takes priority (Dashboard > Code).

### 4.2. Configuration Sync
- **Endpoint**: `GET /api/client/configs?lastKnownVersion=X`
- **Trigger**: Periodic heartbeat or Pub/Sub notification.
- **Logic**: Compares versions and pulls only changed flags to save bandwidth.

### 4.3. Flag Evaluation (Fallback)
- **Endpoint**: `GET /api/client/flags/{key}`
- **Trigger**: Only if the flag is missing from the L1 cache (e.g., first request or cache miss).
- **Logic**: Fetches the flag from the server, evaluates it, and caches it locally.

## 5. Design Decisions

### 5.1. Why ConcurrentHashMap without TTL?
- **Performance**: Avoids the overhead of re-fetching configurations on every expiration.
- **Consistency**: Relies on the server to push updates. The cache is only cleared when the server explicitly says so (via Full Sync).

### 5.2. Why Full Sync on Deletion?
Incremental sync only returns "changed" or "new" flags. It does not return a list of deleted flags. To ensure the SDK doesn't hold "zombie" flags, we trigger a full sync (clearing the local cache) when a deletion is detected via Pub/Sub.

### 5.3. Debouncing Strategy
To prevent the server from being overwhelmed during batch updates, the SDK waits 100ms after the first notification before syncing. Any subsequent notifications within that window are ignored.

## 6. Thread Safety

- **Cache Access**: All read/write operations on the L1 cache are thread-safe.
- **Evaluation**: The `RuleEvaluator` is stateless and can be safely used by multiple threads.
- **Sync Lock**: The `syncScheduled` flag ensures only one sync operation happens at a time.

---

For usage examples, see [SDK Integration Guide](sdk-integration-guide.md).
