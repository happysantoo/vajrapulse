# Docker & Grafana Setup for HTTP Load Test Example

This guide helps you set up and troubleshoot the observability stack (OTel Collector, Prometheus, Grafana) for the HTTP load test example.

## Quick Start

### 1. Start the Observability Stack

```bash
cd examples/http-load-test
docker-compose up -d
```

Wait for all containers to be healthy:
```bash
docker-compose ps
```

Expected output:
```
NAME                COMMAND             STATUS
otel-collector      --config=...        Up (healthy)
prometheus          --config.file=...   Up (healthy)
grafana             /run.sh              Up (healthy)
```

### 2. Verify Services

- **OTel Collector**: http://localhost:4317 (gRPC), http://localhost:4318 (HTTP)
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin / vajrapulse)

### 3. Run Load Test

```bash
# Terminal 1: Start load test
./gradlew :examples:http-load-test:runOtel

# Terminal 2: Monitor in Grafana
# Open browser: http://localhost:3000
# Navigate to: Dashboards → VajraPulse
# Select a Run ID from dropdown at top of dashboard
```

### 4. Understanding the Dashboard

The dashboard is **run_id driven**:
- **Run ID Selector**: Dropdown at top filters all panels to show metrics for a specific load test run
- Select **"All"** to see aggregated metrics across all runs
- Select a **specific run_id** (e.g., `uuid-1234...`) to isolate that run's metrics
- Each panel automatically updates when you change the run_id

## Troubleshooting

### Run ID Variable Not Showing Values

**Symptom**: Run ID dropdown shows "All" but no actual run IDs available.

**Root Cause**: Either no load test has run yet, or Prometheus hasn't scraped the metrics.

**Solution**:

1. **Start a load test**:
   ```bash
   ./gradlew :examples:http-load-test:runOtel
   ```
   Wait for it to export at least a few metrics (10-15s).

2. **Verify metrics in Prometheus**:
   - Open: http://localhost:9090/graph
   - Query: `label_values(vajrapulse_execution_count_total, run_id)`
   - Should return a list of run IDs
   - If empty, check OTel Collector logs

3. **Refresh Grafana variable**:
   - In dashboard: click the refresh icon next to "Run ID" dropdown
   - Should populate with available run IDs

### Panels Show No Data After Selecting Run ID

**Symptom**: Run ID dropdown works but panels remain empty when selecting a run.

**Root Cause**: Query syntax issue or metrics don't have `run_id` label.

**Solution**:

1. **Verify run_id label exists**:
   ```bash
   curl -s http://localhost:9090/api/v1/query?query='vajrapulse_execution_count_total{run_id!=""}' | jq '.data.result[0].metric'
   ```
   Should show `run_id` label in output.

2. **Test variable interpolation**:
   - Open dashboard in edit mode
   - Click any panel to edit
   - Check the Query field
   - Should show: `run_id=~"$run_id"` or similar variable reference
   - If not, apply dashboard updates

3. **Check dashboard JSON**:
   ```bash
   grep -o '"run_id=~"$run_id"' grafana/dashboards/vajrapulse-dashboard.json | head -5
   ```
   Should find 8 instances (one per panel).

### All Panels Empty (No Data)

**Symptom**: All panels show "No data" even after running load test.

**Root Cause**: No load test has been run yet, or metrics aren't being exported.

**Solution**:

1. **Run a load test**:
   ```bash
   cd /Users/santhoshkuppusamy/IdeaProjects/vajrapulse
   ./gradlew :examples:http-load-test:runOtel
   ```
   This will run a 30-second load test and export metrics.

2. **Verify metrics in Prometheus**:
   ```bash
   curl -s 'http://localhost:9090/api/v1/label/__name__/values' | grep vajrapulse
   ```
   Should return list of metric names.

3. **Check OTel Collector is receiving metrics**:
   ```bash
   docker logs otel-collector --tail 50
   ```
   Look for "Metric" or "ResourceMetrics" entries.

4. **Wait for metrics to appear**:
   - Metrics export every 10 seconds
   - Prometheus scrapes every 15 seconds
   - Allow up to 30 seconds for first data point

### Dashboards Not Appearing in Grafana

**Symptom**: Grafana loads but no dashboards visible under VajraPulse folder.

**Root Cause**: Dashboard provisioning requires proper volume mounting and Grafana configuration.

**Solution**:

1. **Verify volume mounts**:
   ```bash
   docker inspect grafana | grep -A 5 "Mounts"
   ```
   Should show:
   ```
   "Source": "/.../examples/http-load-test/grafana/provisioning",
   "Destination": "/etc/grafana/provisioning"
   
   "Source": "/.../examples/http-load-test/grafana/dashboards",
   "Destination": "/var/lib/grafana/dashboards"
   ```

2. **Check Grafana logs**:
   ```bash
   docker logs grafana
   ```
   Should contain:
   ```
   lvl=info msg="Provisioning dashboard" fileName=vajrapulse-dashboard.json
   ```

3. **Verify dashboard file exists**:
   ```bash
   docker exec grafana ls -la /var/lib/grafana/dashboards/
   ```
   Should show: `vajrapulse-dashboard.json`

4. **Restart Grafana**:
   ```bash
   docker-compose restart grafana
   ```

### Metrics Not Appearing in Prometheus

**Symptom**: Prometheus shows "no data" for metrics.

**Root Cause**: OTel Collector not receiving metrics or Prometheus not scraping.

**Solution**:

1. **Check OTel Collector logs**:
   ```bash
   docker logs otel-collector
   ```
   Should show metrics being received:
   ```
   Resource SchemaURL: ""
   Resource attributes: map[deployment.environment:test ...]
   ```

2. **Verify Prometheus scraping OTel**:
   - Open: http://localhost:9090/targets
   - Look for `otel-collector` target with state "UP"
   - If "DOWN", check OTel Collector logs for export errors

3. **Check metrics exported**:
   ```bash
   curl -s http://localhost:8889/metrics | grep vajrapulse
   ```
   Should show metrics like:
   ```
   # HELP vajrapulse_request_throughput ...
   # TYPE vajrapulse_request_throughput gauge
   vajrapulse_request_throughput{...} 100.0
   ```

4. **Verify load test is exporting**:
   - Run: `./gradlew :examples:http-load-test:runOtel`
   - Check OTel logs for export confirmation
   - Wait 10-15s for metrics to appear in Prometheus

### Datasource Connection Error

**Symptom**: "Datasource is not responding" in Grafana dashboards.

**Root Cause**: Grafana cannot reach Prometheus container.

**Solution**:

1. **Verify network connectivity**:
   ```bash
   docker exec grafana curl -v http://prometheus:9090
   ```
   Should return HTTP 200.

2. **Check provisioned datasource**:
   - In Grafana: Configuration → Data Sources → Prometheus
   - URL should be: `http://prometheus:9090`
   - Test Connection button should show "Data source is working"

3. **Restart all containers**:
   ```bash
   docker-compose down
   docker-compose up -d
   ```

## Dashboard Structure

**Location**: `grafana/dashboards/vajrapulse-dashboard.json`

**Run ID Variable**:
- Dropdown at top labeled "Run ID"
- Automatically discovers available run IDs from Prometheus
- Select "All" for all runs, or choose a specific run_id
- All 8 panels filter by this variable

**Panels** (all filtered by run_id):
1. **Execution Rate**: Total/success/failure requests per minute
2. **Success Rate**: Percentage gauge
3. **Execution Counts**: Current totals (stat)
4. **Request Throughput**: Achieved TPS (requests/sec) - shows actual rate achieved
5. **Response Throughput**: Achieved TPS by status (success/failure/all)
6. **Success Latency Percentiles**: P50, P90, P95, P99 over time
7. **Failure Latency Percentiles**: P50, P90, P95, P99 over time
8. **Task Metadata**: Resource attributes table (run_id, task_name, service info)

**Note**: Request and Response Throughput currently show the same achieved rate.
Future versions may add target/requested TPS as a separate metric.

## Metrics Available

Exported via OTLP:

- `vajrapulse.execution.count` - Total executions (counter)
- `vajrapulse.execution.duration` - Latency percentiles (gauge)
- `vajrapulse.success.rate` - Success rate % (gauge)
- `vajrapulse.request.throughput` - Target TPS (gauge)
- `vajrapulse.response.throughput` - Achieved TPS (gauge with status dimension)

All metrics include:
- `run_id` - Unique load test run identifier
- `task.name` - Task being tested
- `task.*` - Additional task tags

## Cleanup

Stop containers without removing volumes:
```bash
docker-compose stop
```

Stop and remove everything:
```bash
docker-compose down -v
```

## Advanced: Custom Collector Configuration

Edit `otel-collector-config.yml` to:
- Change export endpoints
- Add new processors (sampling, filtering)
- Export to other backends (Datadog, New Relic, etc.)

Then restart:
```bash
docker-compose restart otel-collector
```

## Reference

- [OpenTelemetry Collector](https://opentelemetry.io/docs/reference/specification/protocol/exporter/)
- [Prometheus Scraping](https://prometheus.io/docs/prometheus/latest/configuration/configuration/)
- [Grafana Provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/provisioning-grafana/)
