# SDK Guide Overview

## Available SDKs

| SDK | Status | Documentation |
|-----|--------|---------------|
| **Java SDK** | ✅ Implemented | [Integration Guide](sdk-integration-guide.md) & [Design](sdk-design.md) |
| JavaScript SDK | 📋 Architecture Ready | See [Design](sdk-design.md) |
| iOS SDK | 📋 Architecture Ready | See [Design](sdk-design.md) |
| Android SDK | 📋 Architecture Ready | See [Design](sdk-design.md) |

---

## Why Only Java SDK?

### Context
This project was developed as a technical assessment for a **Senior Java Developer role with architecture focus**. The primary goal was to demonstrate:

1. **System Design Skills**: Architecture, caching strategy, API design
2. **Java Expertise**: Spring Boot, concurrency, design patterns
3. **Problem-Solving Approach**: How I tackle complex distributed system challenges

### Decision Rationale

**Priority 1: Depth over Breadth**
- Implemented a **complete, production-ready Java SDK** with:
  - Multi-level caching (L1 memory + L2 Redis)
  - Real-time updates via Redis Pub/Sub
  - Annotation-driven evaluation (@ToggleMethod)
  - Incremental configuration sync
- Rather than creating shallow implementations of multiple SDKs

**Priority 2: Demonstrate Core Competencies**
- As a Java candidate, showcasing deep Java/Spring expertise is more valuable than superficial multi-language support
- The Java SDK implementation proves understanding of:
  - Concurrent programming (ConcurrentHashMap, ScheduledExecutorService)
  - Caching strategies
  - Event-driven architecture (Pub/Sub)
  - AOP (annotation-driven evaluation)

**Priority 3: Extensible Architecture**
- Designed the system so other language SDKs can be added easily
- `ff-sdk-core` module contains **language-agnostic rule evaluation logic**
- Other SDKs only need to implement:
  - Communication layer (HTTP/WebSocket client)
  - Native cache (LocalStorage, CoreData, SharedPreferences)
  - Language-specific idioms

---

## Multi-Language SDK Architecture

The system is designed for easy cross-language SDK implementation:

```
┌─────────────────────────────────────┐
│     ff-sdk-core (Shared Logic)      │
│  - Rule evaluation engine           │
│  - Condition matching               │
│  - Percentage rollout calculation   │
│  - Platform-agnostic (pure Java)    │
└──────────────┬──────────────────────┘
               │ Can be compiled to
               │ native code or reused
       ┌───────┼────────┐
       │       │        │
  ┌────▼───┐ ┌─▼────┐ ┌─▼──────┐
  │Java SDK│ │JS SDK│ │iOS SDK │
  │        │ │      │ │        │
  │- HTTP  │ │- Fetch│ │- NSURL │
  │- Redis │ │- Local│ │- Core  │
  │- Cache │ │Storage│ │ Data   │
  └────────┘ └──────┘ └────────┘
```

### Implementation Pattern for New SDKs

1. **Reuse Core Logic**
   - Option A: Compile `ff-sdk-core` to native (GraalVM, WASM)
   - Option B: Reimplement evaluation algorithm (simple, ~200 lines)

2. **Implement Communication Layer**
   ```typescript
   // Example: JavaScript SDK
   class FeatureToggleClient {
     async syncConfig() {
       const response = await fetch(
         `${serverUrl}/api/client/configs?appKey=${appKey}&lastKnownVersion=${version}`
       );
       return response.json();
     }
   }
   ```

3. **Implement Native Cache**
   ```javascript
   // Browser: LocalStorage
   localStorage.setItem('ff_config', JSON.stringify(config));
   
   // iOS: CoreData/UserDefaults
   UserDefaults.standard.set(config, forKey: "ff_config")
   
   // Android: SharedPreferences
   sharedPreferences.edit().putString("ff_config", config).apply()
   ```

4. **Follow API Contract**
   - All SDKs use the same Client API endpoints
   - Same request/response format
   - Same version tracking mechanism

See [sdk-design.md](sdk-design.md) for detailed specifications.

---

## Quick Start: Java SDK

```java
// 1. Add dependency
// <dependency>
//   <groupId>com.featuretoggle</groupId>
//   <artifactId>ff-sdk-java</artifactId>
//   <version>1.0.0</version>
// </dependency>

// 2. Configure
SdkProperties properties = new SdkProperties();
properties.setAppKey("my-app");
properties.setServerUrl("http://localhost:8080");
properties.setEnvironment("prod");

// 3. Initialize
FeatureToggleClient client = new FeatureToggleClient(properties);
client.start();

// 4. Evaluate
UserContext user = new UserContext("user123", Map.of("region", "cn-beijing"));
EvaluationDetail result = client.evaluate("new_checkout", user);

if (result.enabled()) {
    // Show new checkout flow
}
```

📘 **Full Java SDK guide**: [Integration Guide](sdk-integration-guide.md)

---

## Future Plans

If this system were to move to production, the next steps would be:

1. **JavaScript SDK** (Week 1-2)
   - Browser support with LocalStorage
   - React hooks for component-level evaluation
   - Bundle size optimization (<10KB gzipped)

2. **Mobile SDKs** (Week 3-4)
   - iOS SDK with CoreData persistence
   - Android SDK with SharedPreferences
   - Offline-first design

3. **SDK Testing Framework**
   - Cross-language integration tests
   - Performance benchmarks per platform
   - Compatibility matrix

---

## Summary

**What's implemented**: Complete, production-ready Java SDK  
**What's designed**: Architecture ready for multi-language expansion  
**Why**: Demonstrated depth in Java while maintaining extensibility  

The focus was on proving **system design capabilities** and **Java expertise**, not on building a complete multi-language SDK suite. The architecture ensures that adding other languages is straightforward when needed.
