# VajraPulse Configuration Guide

## Overview

VajraPulse uses a layered configuration system:

1. **Default values** - Sensible defaults for all settings
2. **Configuration file** - YAML or JSON file
3. **Environment variables** - Override any setting

Later layers override earlier ones.

## Configuration File

### Locations (searched in order)

1. `./vajrapulse.conf.yml` - Current directory
2. `~/.vajrapulse/vajrapulse.conf.yml` - User home
3. `/etc/vajrapulse/vajrapulse.conf.yml` - System-wide

Also supports `.json` extension.

### Example Configuration

```yaml
execution:
  drainTimeout: 5s
  forceTimeout: 10s
  defaultThreadPool: virtual
  platformThreadPoolSize: -1   # -1 = auto-detect

observability:
  tracingEnabled: false
  metricsEnabled: true
  structuredLogging: true
  otlpEndpoint: "http://localhost:4318"
  tracingSampleRate: 0.05
```

## Configuration Reference

### Execution Settings

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `drainTimeout` | Duration | `5s` | Time to wait for in-flight tasks to complete |
| `forceTimeout` | Duration | `10s` | Time before forceful shutdown |
| `defaultThreadPool` | Enum | `virtual` | Thread strategy: `virtual`, `platform`, `auto` |
| `platformThreadPoolSize` | int | `-1` | Platform thread pool size (-1 = CPU count) |

### Observability Settings

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `tracingEnabled` | boolean | `false` | Enable OpenTelemetry tracing |
| `metricsEnabled` | boolean | `true` | Enable metrics collection |
| `structuredLogging` | boolean | `true` | Enable JSON structured logs |
| `otlpEndpoint` | String | `http://localhost:4318` | OTLP exporter endpoint |
| `tracingSampleRate` | double | `0.05` | Trace sampling rate (0.0-1.0) |

## Environment Variables

Override any setting with environment variables using prefix `VAJRAPULSE_`:

```bash
# Execution settings
export VAJRAPULSE_EXECUTION_DRAIN_TIMEOUT=10s
export VAJRAPULSE_EXECUTION_FORCE_TIMEOUT=20s
export VAJRAPULSE_EXECUTION_DEFAULT_THREAD_POOL=platform
export VAJRAPULSE_EXECUTION_PLATFORM_THREAD_POOL_SIZE=8

# Observability settings
export VAJRAPULSE_OBSERVABILITY_TRACING_ENABLED=true
export VAJRAPULSE_OBSERVABILITY_METRICS_ENABLED=true
export VAJRAPULSE_OBSERVABILITY_STRUCTURED_LOGGING=true
export VAJRAPULSE_OBSERVABILITY_OTLP_ENDPOINT=http://otel:4318
export VAJRAPULSE_OBSERVABILITY_TRACING_SAMPLE_RATE=0.1
```

## Duration Format

Durations support these formats:
- Milliseconds: `500ms`
- Seconds: `5s`
- Minutes: `10m`
- Hours: `1h`

## Programmatic Configuration

```java
VajraPulseConfig config = new VajraPulseConfig(
    new VajraPulseConfig.ExecutionConfig(
        Duration.ofSeconds(5),
        Duration.ofSeconds(10),
        VajraPulseConfig.ThreadPoolStrategy.VIRTUAL,
        -1
    ),
    new VajraPulseConfig.ObservabilityConfig(
        true,   // tracing
        true,   // metrics
        true,   // structured logging
        "http://localhost:4318",
        0.1     // 10% sampling
    )
);
```

## Loading Configuration

```java
// Load from default locations with env overrides
VajraPulseConfig config = ConfigLoader.load();

// Load from specific file
VajraPulseConfig config = ConfigLoader.load(Path.of("my-config.yml"));

// Use defaults directly
VajraPulseConfig config = VajraPulseConfig.defaults();
```

## Validation

Configuration is validated on load:
- `drainTimeout` must be positive
- `forceTimeout` must be positive and >= `drainTimeout`
- `platformThreadPoolSize` must be -1 or positive
- `tracingSampleRate` must be between 0.0 and 1.0

Invalid configuration throws `ConfigurationException`.
