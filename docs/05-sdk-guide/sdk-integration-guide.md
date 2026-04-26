# Java SDK Integration Guide

This guide helps developers integrate the Feature Toggle Java SDK into their Spring Boot applications.

## 1. Installation

Add the SDK dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.featuretoggle</groupId>
    <artifactId>ff-sdk-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 2. Integration Methods

The SDK supports two integration approaches depending on your application type.

### 2.1. Spring Boot Application (Recommended)

For Spring Boot projects, the SDK provides auto-configuration.

**Step 1: Configure in `application.yml`**

```yaml
feature-toggle:
  server-url: http://feature-toggle-server:8080
  app-key: ${APP_KEY:my-application}
  environment: ${ENV:prod}
  pull-interval: 300000  # Heartbeat sync interval in milliseconds (default: 5 minutes). Set to 0 to disable.
```

**Step 2: Use via Dependency Injection**

```java
@Service
public class OrderService {
    
    @Autowired
    private FeatureToggleClient toggleClient;
    
    public void processOrder() {
        // Client is automatically initialized and synced
    }
}
```

### 2.2. Manual Initialization (Non-Spring)

For non-Spring applications or when you need dynamic configuration:

```java
// 1. Configure
SdkProperties properties = new SdkProperties();
properties.setAppKey("my-app");
properties.setServerUrl("http://localhost:8080");
properties.setEnvironment("prod");
properties.setPullInterval(30000);

// 2. Initialize and start
FeatureToggleClient client = new FeatureToggleClient(properties);
client.start();

// 3. Use
UserContext user = new UserContext("user123", Map.of("region", "cn-beijing"));
EvaluationDetail result = client.evaluate("new_checkout", user);

// 4. Shutdown when application stops
client.stop();
```

## 3. Quick Start

### 3.1. Enable a Feature

```java
@Autowired
private FeatureToggleClient toggleClient;

public void processOrder() {
    UserContext user = new UserContext("user_123", Map.of("region", "US"));
    
    if (toggleClient.isEnabled("enable_new_checkout", user)) {
        // New checkout logic
    } else {
        // Old checkout logic
    }
}
```

### 3.2. Get Flag Value

```java
EvaluationDetail detail = toggleClient.evaluate("promo_discount_percentage", user);
String value = detail.value(); // e.g., "20"
```

## 4. Annotation-Driven Usage

The SDK supports AOP-based evaluation to keep your business logic clean.

### 4.1. Enable a Method via Flag

```java
@ToggleMethod(
    flagKey = "enable_fast_search",
    defaultValue = "false"
)
public SearchResult search(String query) {
    // Fast search algorithm
    return new SearchResult();
}
```
*If the flag is disabled, the method returns the default value immediately without executing the body.*

### 4.2. Shared Flags Configuration

For flags used across multiple methods, define them in a configuration class:

```java
@FeatureFlags({
    @FeatureFlag(flagKey = "enable_dark_mode", defaultValue = "false"),
    @FeatureFlag(flagKey = "enable_notifications", defaultValue = "true")
})
public class SharedFlagsConfig {
    // Configuration holder
}
```

## 5. User Context

The `UserContext` provides attributes for rule evaluation:

```java
Map<String, Object> attributes = new HashMap<>();
attributes.put("region", "cn-east");
attributes.put("vipLevel", 5);
attributes.put("deviceType", "mobile");

UserContext user = new UserContext("user_123", attributes);
```

## 6. Batch Evaluation

For scenarios requiring multiple flags (e.g., page load), use batch evaluation:

```java
List<String> flagKeys = Arrays.asList("flag_a", "flag_b", "flag_c");
Map<String, EvaluationDetail> results = toggleClient.batchEvaluate(flagKeys, user);
```

## 7. Fail-Safe Behavior

The SDK is designed to be fail-safe:
- **Server Down**: Uses the last known cached configuration.
- **Network Timeout**: Returns the default value specified in the flag.
- **Exception Handling**: Any evaluation error is logged, and the default value is returned.

---

For more details on how the SDK works internally, see [SDK Design](sdk-design.md).
