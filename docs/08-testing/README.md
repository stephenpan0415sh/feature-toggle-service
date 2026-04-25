# Testing Strategy

This document outlines the testing approach for the Feature Toggle Service, covering unit tests, integration tests, and performance testing.

## 1. Test Coverage Overview

| Module | Tests | Coverage | Status |
| :--- | :--- | :--- | :--- |
| **ff-sdk-core** | 2 test classes | 95%+ | ✅ Complete |
| **ff-sdk-java** | 3 test classes | 90%+ | ✅ Complete |
| **ff-server** | 4 test classes | 85%+ | ✅ Complete |

### Test Files

#### ff-sdk-core
- `PercentageCalculatorTest.java`: Hash algorithm and percentage rollout
- `RuleEvaluatorTest.java`: Rule matching logic

#### ff-sdk-java
- `FeatureFlagAnnotationScannerTest.java`: Annotation scanning
- `FeatureFlagBeanScannerTest.java`: Bean registration
- `FlagUpdateListenerTest.java`: Pub/Sub message handling

#### ff-server
- `AdminServiceTest.java`: Flag CRUD operations
- `AuditLogServiceTest.java`: Audit event logging
- `EvaluationServiceTest.java`: Flag evaluation logic
- `FlagCacheServiceTest.java`: Redis cache operations

## 2. Unit Testing

### 2.1. Running Tests

```bash
# Run all tests
mvn test

# Run specific module tests
cd ff-server && mvn test
cd ff-sdk-core && mvn test
```

### 2.2. Test Data Standards

All test data follows these conventions:
- **region**: Non-null, uses realistic values (`cn-beijing`, `us-west`)
- **releaseVersion**: Non-null, follows semver (`v1.0.0`)
- **userId**: Realistic format (`user_123`, `admin_456`)

### 2.3. Mock Strategy

- **Redis**: Uses `@MockBean` for RedisTemplate
- **Database**: Uses H2 in-memory for repository tests
- **HTTP Client**: Mocks RestTemplate responses

## 3. Integration Testing

### 3.1. API Integration Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
class ClientApiControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void evaluateFlag_Success() throws Exception {
        mockMvc.perform(get("/api/client/flags/new_checkout")
                .param("appKey", "test-app")
                .param("userId", "user_123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
}
```

### 3.2. Pub/Sub Integration Tests

```java
@Test
void testPubSubMessageHandling() {
    // Simulate Redis message
    String message = "{\"flagKey\":\"test_flag\",\"action\":\"UPDATE\"}";
    
    // Verify listener processes message
    flagUpdateListener.onMessage(message.getBytes());
    
    // Assert cache was updated
    verify(flagCacheService).updateCache(any());
}
```

## 4. Performance Testing

See [performance-testing.md](performance-testing.md) for:
- JMeter test plans
- Load testing scenarios
- Benchmark results

## 5. Test Automation

### 5.1. CI/CD Pipeline

```yaml
# .github/workflows/test.yml
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Run Tests
        run: mvn clean test
      - name: Upload Coverage
        run: mvn jacoco:report
```

### 5.2. Code Coverage

Target: **80%+** line coverage for all modules

```bash
# Generate coverage report
mvn jacoco:report

# View report
open ff-server/target/site/jacoco/index.html
```

---

For production deployment, see [Operations Guide](../07-operations/README.md).
