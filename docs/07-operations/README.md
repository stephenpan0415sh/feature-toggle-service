# Operations & Deployment Guide

This section covers deployment, monitoring, backup, and scaling procedures for the Feature Toggle Service.

## 1. Deployment

### 1.1. Kubernetes Deployment

The system is designed for cloud-native deployment using Kubernetes.

**Required Manifests**:
- `deployment.yaml`: Main service deployment with 2 replicas
- `service.yaml`: ClusterIP service for internal communication
- `configmap.yaml`: Non-sensitive configuration (DB URL, Redis host)
- `secret.yaml`: Sensitive data (DB credentials)

**Deployment Command**:
```bash
kubectl apply -f k8s/deployment.yaml
```

### 1.2. Resource Requirements

| Component | CPU Request | Memory Request | CPU Limit | Memory Limit |
| :--- | :--- | :--- | :--- | :--- |
| ff-server | 500m | 512Mi | 1000m | 1Gi |
| MySQL | 1000m | 1Gi | 2000m | 2Gi |
| Redis | 250m | 256Mi | 500m | 512Mi |

### 1.3. Health Checks

- **Liveness Probe**: `/actuator/health/liveness` (restarts unhealthy pods)
- **Readiness Probe**: `/actuator/health/readiness` (removes from load balancer)

## 2. Scaling

### 2.1. Horizontal Scaling

The system is **stateless** and scales horizontally:

```bash
kubectl scale deployment ff-server --replicas=5
```

**Scaling Considerations**:
- Redis Pub/Sub ensures all instances receive configuration updates
- Each instance maintains its own L2 cache
- No shared state between instances (except Redis/MySQL)

### 2.2. Vertical Scaling

If a single instance cannot handle the load:
- Increase CPU limit to 2000m
- Increase memory limit to 2Gi
- Tune JVM heap size (`-Xmx1536m`)

## 3. Configuration Management

### 3.1. Environment Variables

| Variable | Description | Example |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | MySQL connection string | `jdbc:mysql://mysql:3306/ff` |
| `SPRING_REDIS_HOST` | Redis hostname | `redis-service` |
| `SPRING_PROFILES_ACTIVE` | Active profile | `prod` |

### 3.2. Application Configuration

Key `application.yml` settings:
```yaml
feature-toggle:
  pull-interval: 300000  # Heartbeat sync interval (ms, default: 5 min). Set to 0 for Pub/Sub only mode.
  cache-ttl: 3600       # L2 cache TTL (seconds)
  
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics
```

## 4. Monitoring & Alerting

See [Observability](../06-observability/README.md) for:
- Prometheus metrics configuration
- Grafana dashboard setup
- Alerting rules

## 5. Backup & Recovery

### 5.1. Database Backup

```bash
# Daily backup script
mysqldump -u admin -p feature_toggle > backup_$(date +%Y%m%d).sql
```

### 5.2. Redis Backup

Redis RDB snapshots are automatically configured:
```conf
save 900 1
save 300 10
save 60 10000
```

### 5.3. Recovery Procedure

1. Restore MySQL from backup
2. Restart ff-server (will repopulate Redis cache)
3. Verify health endpoints

## 6. Troubleshooting

### 6.1. Common Issues

| Issue | Symptoms | Solution |
| :--- | :--- | :--- |
| Redis Down | High latency, cache misses | Check Redis pod status, restart if needed |
| MySQL Connection Failed | 500 errors on flag creation | Verify DB credentials, network connectivity |
| Pub/Sub Not Working | Stale configs on some instances | Restart affected pods, check Redis logs |
| High Memory Usage | OOM kills | Increase memory limit, check for memory leaks |

### 6.2. Log Analysis

```bash
# View recent logs
kubectl logs -l app=feature-toggle-service --tail=100

# Follow logs in real-time
kubectl logs -l app=feature-toggle-service -f
```

## 7. Security

### 7.1. Access Control

- **Admin API**: Requires authentication (JWT/OAuth2 - to be implemented)
- **Client API**: Requires valid `appKey`
- **Internal APIs**: Restricted to cluster network

### 7.2. Secrets Management

- Database credentials stored in Kubernetes Secrets
- Never commit secrets to Git
- Rotate credentials periodically

---

For testing strategies, see [Testing Guide](../08-testing/README.md).
