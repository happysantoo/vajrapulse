# VajraPulse Observability Stack

Complete observability stack for VajraPulse load testing with OpenTelemetry Collector, Prometheus, and Grafana.

## 🎯 Overview

This stack provides end-to-end observability for VajraPulse load tests:

- **OpenTelemetry Collector**: Receives metrics via OTLP (gRPC/HTTP) from VajraPulse
- **Prometheus**: Scrapes and stores metrics from the collector
- **Grafana**: Visualizes metrics with pre-configured dashboards

```
VajraPulse → OTLP → OpenTelemetry Collector → Prometheus → Grafana
```

## 🚀 Quick Start

### 1. Start the Stack

```bash
cd examples/http-load-test
docker-compose up -d
```

Wait ~30 seconds for all services to be healthy.

### 2. Run a Load Test

```bash
# From project root
./gradlew :examples:http-load-test:runOtel
```

### 3. Access Grafana Dashboard

Open: **http://localhost:3000**

- **Username**: `admin`
- **Password**: `vajrapulse`
- Dashboard: **VajraPulse Load Test Dashboard** (auto-provisioned)

### 4. View Metrics

The dashboard auto-refreshes every 5 seconds and displays:
- Execution rate (per minute)
- Success rate percentage
- Execution counts (total, success, failure)
- Latency percentiles (P50, P90, P95, P99) for success and failure

## 📊 Stack Components

### OpenTelemetry Collector (Port 4317/4318)

**Version**: `0.115.1` (contrib)

**Endpoints**:
- OTLP gRPC: `http://localhost:4317`
- OTLP HTTP: `http://localhost:4318`
- Prometheus exporter: `http://localhost:8889/metrics`

**Configuration**: `otel-collector-config.yml`

**Features**:
- Receives OTLP metrics from VajraPulse
- Adds deployment metadata (environment, collector name)
- Exports to Prometheus format
- Logs metrics to JSON file for debugging

### Prometheus (Port 9090)

**Version**: `3.0.1`

**URL**: http://localhost:9090

**Configuration**: `prometheus.yml`

**Features**:
- Scrapes OTEL Collector every 5 seconds
- 7-day retention period
- Self-monitoring enabled

**Example Queries**:
```promql
# Total execution rate
rate(vajrapulse_executions_total[1m])

# Success rate percentage
vajrapulse_success_rate

# P95 latency
histogram_quantile(0.95, rate(vajrapulse_latency_success_bucket[1m]))
```

### Grafana (Port 3000)

**Version**: `11.4.0`

**URL**: http://localhost:3000

**Credentials**:
- Username: `admin`
- Password: `vajrapulse`

**Features**:
- Auto-provisioned Prometheus datasource
- Pre-configured VajraPulse dashboard
- 5-second auto-refresh
- Anonymous read-only access enabled

## 📈 Dashboard Panels

### 1. Execution Rate (Time Series)
Shows executions per minute:
- Total executions/min
- Successful executions/min
- Failed executions/min

### 2. Success Rate (Gauge)
Real-time success rate percentage with color thresholds:
- Red: < 80%
- Orange: 80-95%
- Yellow: 95-99%
- Green: ≥ 99%

### 3. Execution Counts (Stats)
Current cumulative counts:
- Total executions
- Successful executions
- Failed executions

### 4. Success Latency Percentiles (Time Series)
Success latency distribution over time:
- P50 (median)
- P90
- P95
- P99

### 5. Failure Latency Percentiles (Time Series)
Failure latency distribution over time (when failures occur)

### 6. Task Metadata (Table)
Resource attributes and task identity:
- Task name
- Service name/version
- Environment
- Task tags (scenario, component, etc.)

## 🔧 Configuration

### Task Identity

Tasks can be tagged for better grouping in Grafana:

```java
TaskIdentity identity = new TaskIdentity(
    "checkout-flow",
    Map.of(
        "scenario", "high-load",
        "component", "payments"
    )
);

OpenTelemetryExporter exporter = OpenTelemetryExporter.builder()
    .endpoint("http://localhost:4317")
    .taskIdentity(identity)
    .resourceAttributes(Map.of(
        "service.name", "my-service",
        "environment", "staging"
    ))
    .build();
```

These appear as labels in Prometheus/Grafana:
- `task_name`: from `identity.name()`
- `task_scenario`, `task_component`: from `identity.tags()`

### Resource Attributes

Resource attributes are added as metric labels:

| Attribute | Description | Example |
|-----------|-------------|---------|
| `service_name` | Service identifier | `vajrapulse-http-example` |
| `service_version` | Version | `1.0.0` |
| `environment` | Deployment env | `dev`, `staging`, `prod` |
| `task_name` | Task identifier | `http-load-test` |
| `task_*` | Task tags | `task_scenario=baseline` |

## 🛠️ Management Commands

### View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f otel-collector
docker-compose logs -f prometheus
docker-compose logs -f grafana
```

### Check Service Health

```bash
docker-compose ps
```

### Restart Services

```bash
# Restart all
docker-compose restart

# Restart specific service
docker-compose restart prometheus
```

### Stop Stack

```bash
# Stop without removing volumes
docker-compose stop

# Stop and remove everything (including data)
docker-compose down -v
```

### Access Metrics Directly

```bash
# OTEL Collector metrics
curl http://localhost:8889/metrics

# Prometheus API
curl http://localhost:9090/api/v1/query?query=vajrapulse_executions_total
```

## 📁 Directory Structure

```
examples/http-load-test/
├── docker-compose.yml              # Full observability stack
├── otel-collector-config.yml       # OTEL Collector configuration
├── prometheus.yml                  # Prometheus scrape config
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/
│   │   │   └── prometheus.yml      # Auto-provision Prometheus datasource
│   │   └── dashboards/
│   │       └── vajrapulse.yml      # Dashboard provider config
│   └── dashboards/
│       └── vajrapulse-dashboard.json  # Pre-built dashboard
└── otel-collector-data/            # Collector file exports (created at runtime)
```

## 🔍 Troubleshooting

### Metrics Not Appearing

1. **Check OTEL Collector is receiving metrics**:
   ```bash
   docker-compose logs otel-collector | grep "Metric #"
   ```

2. **Check Prometheus is scraping**:
   - Visit http://localhost:9090/targets
   - `otel-collector` target should be "UP"

3. **Check Grafana datasource**:
   - Settings → Data Sources → Prometheus
   - Click "Save & Test"

### Dashboard Not Loading

1. **Check Grafana logs**:
   ```bash
   docker-compose logs grafana
   ```

2. **Manually provision**:
   - Visit http://localhost:3000/dashboards
   - Import `grafana/dashboards/vajrapulse-dashboard.json`

### Port Conflicts

If ports are already in use:

```bash
# Find process using port
lsof -ti:3000  # Grafana
lsof -ti:9090  # Prometheus
lsof -ti:4317  # OTEL gRPC

# Change ports in docker-compose.yml
# e.g., "3001:3000" for Grafana
```

### Container Unhealthy

```bash
# Check specific container logs
docker-compose logs <service-name>

# Restart specific service
docker-compose restart <service-name>
```

## 🎓 Advanced Usage

### Custom Percentiles

Modify `HttpLoadTestOtelRunner.java`:

```java
.withPercentiles(0.5, 0.75, 0.90, 0.95, 0.99, 0.999)
```

### Multiple Tasks

Run multiple tests simultaneously with different identities:

```java
TaskIdentity checkout = new TaskIdentity("checkout-flow", Map.of("component", "checkout"));
TaskIdentity search = new TaskIdentity("search-api", Map.of("component", "search"));
```

Dashboard will show metrics grouped by `task_name`.

### Production Configuration

For production environments:

1. **Remove anonymous access** in `docker-compose.yml`:
   ```yaml
   - GF_AUTH_ANONYMOUS_ENABLED=false
   ```

2. **Change default password**:
   ```yaml
   - GF_SECURITY_ADMIN_PASSWORD=<strong-password>
   ```

3. **Enable TLS** for OTLP endpoints

4. **Add authentication** to Prometheus/Grafana

5. **Set up external storage** (e.g., Grafana Cloud, Prometheus remote write)

## 📊 Metrics Reference

| Metric Name | Type | Description |
|-------------|------|-------------|
| `vajrapulse_executions_total` | Counter | Total task executions |
| `vajrapulse_executions_success` | Counter | Successful executions |
| `vajrapulse_executions_failure` | Counter | Failed executions |
| `vajrapulse_success_rate` | Gauge | Success rate (0-100%) |
| `vajrapulse_latency_success` | Histogram | Success latency distribution |
| `vajrapulse_latency_failure` | Histogram | Failure latency distribution |

All metrics include resource attributes as labels.

## 🔗 Useful Links

- **OpenTelemetry Collector**: https://opentelemetry.io/docs/collector/
- **Prometheus**: https://prometheus.io/docs/
- **Grafana**: https://grafana.com/docs/
- **OTLP Specification**: https://opentelemetry.io/docs/specs/otlp/

## 📝 Notes

- **Data Persistence**: Prometheus and Grafana data are stored in Docker volumes
- **Auto-Refresh**: Dashboard refreshes every 5 seconds
- **Time Range**: Default view shows last 15 minutes
- **Scrape Interval**: Prometheus scrapes every 5 seconds
- **Export Interval**: VajraPulse exports every 5 seconds (configurable)

## 🎯 Next Steps

1. **Explore Metrics**: Use Prometheus query browser at http://localhost:9090
2. **Customize Dashboard**: Edit panels in Grafana (changes are saved)
3. **Add Alerts**: Configure Grafana alerts for SLA violations
4. **Remote Storage**: Configure Prometheus remote write to long-term storage
5. **Distributed Tracing**: Add trace export (future enhancement)
