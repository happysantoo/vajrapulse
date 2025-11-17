# VajraPulse Observability - Quick Start 🚀

Get VajraPulse metrics visualized in Grafana in under 2 minutes!

## 1. Start the Stack

```bash
cd examples/http-load-test
docker-compose up -d
```

Wait ~30 seconds for all services to start.

## 2. Verify Everything is Running

```bash
./verify-stack.sh
```

Expected output:
```
✓ Docker containers are running
✓ OTEL Collector (gRPC port 4317) is listening
✓ Prometheus is responding
✓ Grafana is responding
✓ VajraPulse dashboard found
✓ Stack is fully operational!
```

## 3. Run a Load Test

```bash
# From project root
./gradlew :examples:http-load-test:runOtel
```

This will:
- Run 100 TPS for 30 seconds
- Export metrics to OTLP collector
- Tag metrics with task identity

## 4. View the Dashboard

Open in browser: **http://localhost:3000**

- Username: `admin`
- Password: `vajrapulse`

Navigate to **Dashboards → VajraPulse → VajraPulse Load Test Dashboard**

Or direct link (after first login):
```
http://localhost:3000/dashboards/f/ff4b4xbhzepkwf/vajrapulse
```

## What You'll See

The dashboard auto-refreshes every 5 seconds and shows:

### 📊 Execution Metrics
- **Execution Rate**: Requests per minute (total, success, failure)
- **Success Rate**: Percentage gauge with color coding
- **Execution Counts**: Cumulative totals

### ⏱️ Latency Metrics
- **Success Latency**: P50, P90, P95, P99 percentiles
- **Failure Latency**: Same percentiles for failed requests

### 🏷️ Task Metadata
- Task name, scenario, component tags
- Service information and environment

## Customization

### Change Task Identity

Edit `HttpLoadTestOtelRunner.java`:

```java
TaskIdentity identity = new TaskIdentity(
    "my-custom-test",
    Map.of(
        "scenario", "production-load",
        "component", "checkout"
    )
);
```

### Add More Percentiles

```java
.withPercentiles(0.5, 0.75, 0.90, 0.95, 0.99, 0.999)
```

### Adjust Load Pattern

```java
// Ramp up from 0 to 200 TPS over 1 minute
new RampUpLoad(200.0, Duration.ofMinutes(1))

// Or sustain at 500 TPS for 5 minutes
new StaticLoad(500.0, Duration.ofMinutes(5))
```

## Access Other Services

| Service | URL | Purpose |
|---------|-----|---------|
| **Grafana** | http://localhost:3000 | Dashboards & visualization |
| **Prometheus** | http://localhost:9090 | Query metrics directly |
| **OTEL Collector** | http://localhost:8889/metrics | Raw Prometheus metrics |

## Troubleshooting

### No Metrics Appearing?

1. Check OTEL collector logs:
   ```bash
   docker-compose logs otel-collector
   ```

2. Verify Prometheus targets:
   ```
   http://localhost:9090/targets
   ```
   
   Should show `otel-collector` as **UP**

3. Check if metrics exist:
   ```bash
   curl http://localhost:8889/metrics | grep vajrapulse
   ```

### Dashboard Not Loading?

Manually import:
1. Go to http://localhost:3000/dashboards
2. Click **New → Import**
3. Upload: `grafana/dashboards/vajrapulse-dashboard.json`

### Port Conflicts?

Kill processes or change ports in `docker-compose.yml`:
```bash
# Check what's using the port
lsof -ti:3000  # Grafana
lsof -ti:9090  # Prometheus
```

## Stop the Stack

```bash
# Stop without removing data
docker-compose stop

# Stop and remove all data
docker-compose down -v
```

## Next Steps

- 📖 Read full documentation: [OBSERVABILITY-STACK.md](./OBSERVABILITY-STACK.md)
- 🎯 Explore Prometheus queries: http://localhost:9090/graph
- 🎨 Customize Grafana dashboards
- 🔔 Set up alerts for SLA violations
- 📊 Add custom panels for your metrics

---

**Need help?** Check the detailed guide in [OBSERVABILITY-STACK.md](./OBSERVABILITY-STACK.md)
