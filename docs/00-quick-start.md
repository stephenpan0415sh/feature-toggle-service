# Quick Start Guide

## Prerequisites

- Java 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

## 1. Environment Setup

### Start MySQL

```bash
# Docker approach
docker run -d \
  --name mysql-ft \
  -e MYSQL_ROOT_PASSWORD=root \
  -p 3306:3306 \
  mysql:8.0

# Initialize database
mysql -h localhost -u root -proot < ff-server/src/main/resources/schema.sql
```

### Start Redis

```bash
# Docker approach
docker run -d \
  --name redis-ft \
  -p 6379:6379 \
  redis:6.0

# Or use local installation
redis-server
```

## 2. Build Project

```bash
cd feature-toggle-service
mvn clean install -DskipTests
```

## 3. Start Service

```bash
cd ff-server
mvn spring-boot:run
```

Service will start at http://localhost:8080

## 4. Verify Service

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

Expected output:

```json
{"status":"UP"}
```

### Access Swagger Documentation

Open browser: http://localhost:8080/swagger-ui.html

### View Prometheus Metrics

```bash
curl http://localhost:8080/actuator/prometheus | grep feature_flag
```

## 5. Quick Test

### Create Application

```bash
curl -X POST http://localhost:8080/api/admin/apps \
  -H "Content-Type: application/json" \
  -d '{
    "appKey": "test-app",
    "name": "Test Application"
  }'
```

### Create Feature Flag

```bash
curl -X POST http://localhost:8080/api/admin/flags?appKey=test-app&environment=prod \
  -H "Content-Type: application/json" \
  -d '{
    "flagKey": "new_checkout",
    "name": "New Checkout Flow",
    "defaultValue": "false",
    "status": 1,
    "rules": [
      {
        "priority": 1,
        "type": "TARGETING",
        "conditions": [
          {
            "attribute": "region",
            "operator": "EQ",
            "values": ["cn-beijing"]
          }
        ],
        "actionValue": "true"
      }
    ]
  }'
```

### Evaluate Flag

```bash
curl http://localhost:8080/api/client/flags/new_checkout?appKey=test-app&userId=user_123&region=cn-beijing&environment=prod
```

Expected output:

```json
{
  "success": true,
  "data": {
    "flagKey": "new_checkout",
    "enabled": true,
    "value": "true",
    "reason": "MATCHED_RULE",
    "matchedRuleId": "rule_1",
    "region": "cn-beijing",
    "environment": "prod"
  }
}
```

### Batch Evaluation

```bash
curl -X POST http://localhost:8080/api/client/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "appKey": "test-app",
    "userId": "user_123",
    "attributes": {"region": "cn-beijing"},
    "flagKeys": ["new_checkout"]
  }'
```

### Pull Configuration (Incremental Sync)

```bash
# First full pull
curl http://localhost:8080/api/client/configs?appKey=test-app&lastKnownVersion=0&environment=prod

# Note the returned globalVersion, e.g., 123

# Subsequent incremental pull (only returns changed flags)
curl http://localhost:8080/api/client/configs?appKey=test-app&lastKnownVersion=123&environment=prod
```

## 6. Monitoring Metrics

### View Cache Statistics

```bash
curl http://localhost:8080/api/client/cache-stats?appKey=test-app&environment=prod
```

### Prometheus Metrics

```bash
# All feature flag related metrics
curl http://localhost:8080/actuator/prometheus | grep feature_flag

# Example output:
# feature_flag_evaluation_duration_seconds{quantile="0.5"} 0.002
# feature_flag_evaluation_duration_seconds{quantile="0.95"} 0.004
# feature_flag_evaluation_duration_seconds{quantile="0.99"} 0.005
# feature_flag_cache_hit_total 95
# feature_flag_cache_miss_total 5
# feature_flag_cached_flags{app_key="test-app",environment="prod"} 1
```

### Calculate Cache Hit Rate

```bash
# Extract from Prometheus metrics
HITS=$(curl -s http://localhost:8080/actuator/prometheus | grep "cache_hit_total" | awk '{print $2}')
MISSES=$(curl -s http://localhost:8080/actuator/prometheus | grep "cache_miss_total" | awk '{print $2}')
TOTAL=$((HITS + MISSES))
RATE=$(echo "scale=2; $HITS * 100 / $TOTAL" | bc)
echo "Cache Hit Rate: ${RATE}%"
```

## 7. Log Viewing

### Audit Logs (Evaluation Events)

```bash
# Search in console or log file
tail -f ff-server/logs/application.log | grep EVALUATION_EVENT
```

Example output:

```json
EVALUATION_EVENT: {
  "eventId": "evt_001",
  "timestamp": 1713849600000,
  "appKey": "test-app",
  "flagKey": "new_checkout",
  "userId": "user_123",
  "enabled": true,
  "reason": "MATCHED_RULE",
  "traceId": "trace_abc123"
}
```

### Configuration Change Logs

```bash
tail -f ff-server/logs/application.log | grep CONFIG_CHANGE_EVENT
```

## 8. Performance Testing

### Simple Load Test (using wrk)

```bash
# Install wrk (macOS)
brew install wrk

# Run load test
wrk -t4 -c100 -d30s \
  http://localhost:8080/api/client/flags/new_checkout?appKey=test-app&userId=user_123&environment=prod
```

### Using JMeter

```bash
# Import test plan (need to create first)
jmeter -n -t performance-test.jmx -l results.jtl

# Generate report
jmeter -g results.jtl -o report/
```

See [Performance Testing Guide](../08-testing/performance-testing.md) for detailed scenarios.

## 9. Troubleshooting

### Q: MySQL Connection Failed

```
Solution: Check if MySQL is running and password is correct
mysql -h localhost -u root -proot
```

### Q: Redis Connection Failed

```
Solution: Check if Redis is running
redis-cli ping
# Should return PONG
```

### Q: Port Already in Use

```
Solution: Modify server.port in application.yml
or kill the process using port 8080
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Q: Cache Hit Rate is 0

```
Solution:
1. Ensure you call evaluation API first to warm up cache
2. Check Redis is working: redis-cli keys "flag:*"
3. Check logs for cache errors
```

## 10. Stop Service

```bash
# Ctrl+C to stop Maven process

# Stop Docker containers
docker stop mysql-ft redis-ft
docker rm mysql-ft redis-ft
```

---

## 📚 Next Steps

- Read [Architecture Overview](../02-architecture/overview.md) to understand system design
- View [API Reference](../04-api-guide/README.md) for API specifications
- Read [SDK Integration Guide](../05-sdk-guide/sdk-integration-guide.md) to learn SDK usage
- View [Observability](../06-observability/README.md) for monitoring and auditing

---

**Happy Coding!** 🚀
