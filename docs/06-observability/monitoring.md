# Monitoring & Metrics

The system exposes comprehensive metrics via **Prometheus** for real-time monitoring and alerting.

## 1. Exposed Metrics

All metrics are available at `http://localhost:8080/actuator/prometheus`.

### 1.1. Evaluation Metrics

| Metric Name | Type | Description |
| :--- | :--- | :--- |
| `feature_flag_evaluation_total` | Counter | Total number of flag evaluations |
| `feature_flag_evaluation_success_total` | Counter | Number of successful evaluations |
| `feature_flag_evaluation_error_total` | Counter | Number of failed evaluations |
| `feature_flag_evaluation_duration` | Timer | Evaluation latency (P50, P95, P99) |

### 1.2. Cache Metrics

| Metric Name | Type | Description |
| :--- | :--- | :--- |
| `feature_flag_cache_hit_total` | Counter | Number of L2 (Redis) cache hits |
| `feature_flag_cache_miss_total` | Counter | Number of L2 cache misses |
| `feature_flag_cached_flags` | Gauge | Number of flags cached per app/environment |

## 2. Example Queries

Use these PromQL queries in **Grafana** to build dashboards:

### 2.1. Evaluation Latency (P99)
```promql
histogram_quantile(0.99, rate(feature_flag_evaluation_duration_bucket[5m]))
```

### 2.2. Cache Hit Rate
```promql
rate(feature_flag_cache_hit_total[5m]) / (rate(feature_flag_cache_hit_total[5m]) + rate(feature_flag_cache_miss_total[5m]))
```

### 2.3. Error Rate
```promql
rate(feature_flag_evaluation_error_total[5m]) / rate(feature_flag_evaluation_total[5m])
```

## 3. Health Checks

Spring Boot Actuator provides health endpoints:

- **Overall Health**: `GET /actuator/health`
- **Database Health**: `GET /actuator/health/db`
- **Redis Health**: `GET /actuator/health/redis`

## 4. Alerting Recommendations

| Alert | Condition | Severity |
| :--- | :--- | :--- |
| High Latency | P99 > 10ms for 5min | Warning |
| Cache Hit Rate Drop | < 80% for 10min | Warning |
| Error Rate Spike | > 1% for 2min | Critical |
| Redis Down | Health check fails | Critical |

---

For detailed audit logging configuration, see [Audit Logging](audit-logging.md).
