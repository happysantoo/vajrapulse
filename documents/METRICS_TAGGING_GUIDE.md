# Metrics Tagging & Task Identity

## Overview
`TaskIdentity` decouples descriptive naming from `Task` execution logic. It enables consistent tagging of metrics, logs, and (future) traces without expanding the minimal `Task` interface.

## Design Principles
- Keep `Task` pure: execution only.
- Identity is immutable and low-cardinality.
- Tags must be stable across a test run.
- Namespaced under `task.*` to avoid collisions with generic resource attributes.

## Record Definition
```java
public record TaskIdentity(String name, Map<String,String> tags) { ... }
```
- `name`: required, non-blank.
- `tags`: optional, copied defensively.

## OpenTelemetry Export Mapping
When provided to `OpenTelemetryExporter.Builder.taskIdentity(identity)`:
| Identity Element | Resource Attribute Key | Notes |
|------------------|------------------------|-------|
| name             | `task.name`            | Primary grouping label |
| tags.entry(k,v)  | `task.<k>`             | Each tag key prefixed |

Example attributes emitted:
```
task.name=checkout-flow
task.scenario=high-tps
task.component=payments
```

## Cardinality Guidance
| Tag Type          | Allowed? | Guidance |
|-------------------|----------|----------|
| Scenario / Phase  | ✅       | Good for grouping |
| Dataset / Profile | ✅       | Ensure limited set |
| User ID / UUID    | ❌       | Explodes cardinality |
| Timestamp         | ❌       | Not stable |
| Random token      | ❌       | Avoid entirely |

Max recommended distinct values per tag key: **≤ 10** per test run.

## Usage Pattern
```java
TaskIdentity identity = new TaskIdentity(
    "checkout-flow",
    Map.of(
        "component", "payments",
        "scenario", "high-tps",
        "dataset", "prod-sample"
    )
);

OpenTelemetryExporter exporter = OpenTelemetryExporter.builder()
    .endpoint("http://localhost:4317")
    .taskIdentity(identity)
    .resourceAttributes(Map.of(
        "service.name", "vajrapulse-http-example",
        "environment", "dev"
    ))
    .build();
```

## Future Extensions (Post 1.0)
- `group` / `phase` explicit fields
- Tag cardinality enforcement warnings
- Multi-task pipelines with per-metric task labels (instead of resource-level)
- Trace correlation: `task.name` applied to spans

## Anti-Patterns
| Pattern | Problem | Alternative |
|---------|---------|-------------|
| Putting name into `Task` interface | Expands hot path & API surface | `TaskIdentity` record |
| High-cardinality tags (UUIDs) | Storage & query cost | Aggregate before tagging |
| Dynamic changing identity mid-run | Inconsistent time series | Create separate runs |

## Validation Checklist
- [ ] Name non-blank
- [ ] Tags map size ≤ 10
- [ ] No personally identifiable information
- [ ] No per-execution mutation
- [ ] Resource attributes kept distinct (service vs task)

---
Pre-1.0 Rule: Refactor identity model boldly if better grouping semantics emerge.
