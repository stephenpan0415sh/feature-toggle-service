# Testing Strategy

This document outlines the testing approach for the Feature Toggle Service, covering unit tests, integration tests, and performance testing.

## 1. Test Coverage Overview

| Module | Tests | Instruction Coverage | Branch Coverage | Status |
| :--- | :--- | :--- | :--- | :--- |
| **ff-sdk-core** | 2 test classes | 87% | 54% | ✅ Complete |
| **ff-sdk-java** | 4 test classes | 64% | 39% | ✅ Core Logic Covered |
| **ff-server** | 6 test classes | 66% | 42% | ✅ Core Logic Covered |

### Coverage Analysis & Limitations

The coverage percentages reflect a pragmatic approach to testing under tight deadlines:

- **Core Logic Focus**: We prioritized the evaluation engine (`ff-sdk-core`), service layer logic, and flag synchronization mechanisms. These are the most critical parts of the system.
- **Excluded Components**: Configuration classes, Entity/DTO data structures, and example/demo code were excluded from coverage targets as they contain minimal business logic.
- **SDK Integration**: The `ff-sdk-java` module focuses on AOP interception and client communication. While configuration scanning has lower coverage, the core `FeatureToggleClient` and `FeatureToggleAspect` are thoroughly tested.
- **Server API**: The `ff-server` module achieves high coverage in its Service layer (69%), ensuring that flag CRUD and evaluation rules work correctly. Controller tests cover the primary API paths.

### How to View Reports

Detailed HTML reports are generated in each module's `target/site/jacoco/` directory after running `mvn verify`.

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
