# HTTP Load Test Example

> **📚 User Example**: This is a production-ready example for end users learning VajraPulse.  
> For internal framework testing, see [`internal-tests/`](../../internal-tests/).

Demonstrates the `HttpLoadTest` task with multiple load patterns, console & OpenTelemetry export, run ID correlation, and Java 21 virtual threads.

This example shows how to:
- Create a real-world HTTP load test
- Set up a complete observability stack (Docker, Grafana, OpenTelemetry)
- Use all load patterns in practice
- Integrate with monitoring and visualization tools

## Patterns Supported

Programmatic runner (`HttpLoadTestRunner`):
```bash
./gradlew :examples:http-load-test:run --args="static"
./gradlew :examples:http-load-test:run --args="step"
./gradlew :examples:http-load-test:run --args="sine"
./gradlew :examples:http-load-test:run --args="spike"
```
Defaults to `static` if omitted.

Worker CLI (after `./gradlew :vajrapulse-worker:shadowJar`):
```bash
# Static
java -jar ../../vajrapulse-worker/build/libs/vajrapulse-worker-*-all.jar \
  com.example.http.HttpLoadTest --mode static --tps 120 --duration 45s

# Ramp then sustain
java -jar ../../vajrapulse-worker/build/libs/vajrapulse-worker-*-all.jar \
  com.example.http.HttpLoadTest --mode ramp-sustain --tps 300 --ramp-duration 30s --duration 2m

# Step
java -jar ../../vajrapulse-worker/build/libs/vajrapulse-worker-*-all.jar \
  com.example.http.HttpLoadTest --mode step --steps "50:15s,150:15s,300:30s"

# Sine
java -jar ../../vajrapulse-worker/build/libs/vajrapulse-worker-*-all.jar \
  com.example.http.HttpLoadTest --mode sine --mean-rate 200 --amplitude 100 --period 20s --duration 2m

# Spike
java -jar ../../vajrapulse-worker/build/libs/vajrapulse-worker-*-all.jar \
  com.example.http.HttpLoadTest --mode spike --base-rate 120 --spike-rate 600 --spike-interval 15s --spike-duration 3s --duration 1m
```

## Run ID
- Auto-generated if `--run-id` not provided (UUID)
- Appears as metric tag `run_id` and resource attribute (OTel exporter)
- Provide custom value for traceability across runs

## OpenTelemetry Export
Run with OTLP exporter:
```bash
./gradlew :examples:http-load-test:runOtel
```
Collector expected at `http://localhost:4317` (gRPC). Adjust endpoint in `HttpLoadTestOtelRunner`.

Import `documents/grafana-dashboard-runid-simple.json` in Grafana to visualize latency percentiles & success rate by `run_id`.

## Configuration Overrides
Environment vars (e.g. `VAJRAPULSE_EXECUTION_DRAIN_TIMEOUT=5s`) or worker CLI `--config path/to/conf.yml` modify execution semantics.

## Pattern Cheat Sheet
| Pattern | Key Args | Behavior |
|---------|----------|----------|
| static | `--tps`, `--duration` | Constant TPS for full duration |
| ramp | `--tps`, `--ramp-duration` | Linear 0→TPS then stop at end of ramp |
| ramp-sustain | `--tps`, `--ramp-duration`, `--duration` | Linear 0→TPS then sustain remainder |
| step | `--steps` | Discrete TPS steps (rate:duration segments) |
| sine | `--mean-rate`, `--amplitude`, `--period`, `--duration` | Smooth oscillation around mean |
| spike | `--base-rate`, `--spike-rate`, `--spike-interval`, `--spike-duration`, `--duration` | Periodic short spikes |

See `documents/LOAD_PATTERNS.md` for deeper design notes.

## Example Programmatic Runner Selection
In `HttpLoadTestRunner` pass the pattern name as first arg; steps, sine, spike examples are hard-coded for quick experimentation.

## Next Exploration Ideas
- Tune spike frequency to reveal autoscaler lag
- Use sine pattern to find latency resonance windows
- Combine step pattern segments mimicking release ramp-ups

## Observability Metrics (OTel)
Exported counters & histograms include `run_id`, `task.name`, and pattern tags; use them in Grafana for comparative panels.

## Performance Note
Virtual threads keep memory low even with thousands of concurrent requests; use the worker CLI with high TPS to stress outbound latency.

## Troubleshooting
- Missing metrics: verify collector port (4317 gRPC) and protocol setting
- Invalid steps format: ensure `rate:duration` pairs comma-separated (e.g. `"50:10s,150:20s"`)
- Spike validation error: spike duration must be strictly < interval

## License & Status
Pre-1.0: breaking changes expected; this example evolves with core API refinements.
