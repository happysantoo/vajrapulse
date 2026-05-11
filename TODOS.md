# TODOS.md

## Deferred from 1.0.0

- **TaskResult<T> redesign** — Genuine API redesign, needs its own design doc. Current wildcard type erasure limits type safety.
- **ServiceLoader exporter plugin mechanism** — Feature, not fix. Would allow drop-in exporter JARs on classpath.
- **Multiple exporters in CLI** — Feature, not fix. Currently one exporter per run.
- **Embed Chart.js in HTML reports** — Feature, not fix. Document as CDN-required for now.
- **Size cap on MetricsProviderAdapter history** — Edge case, not observable in normal use. 60s retention prevents unbounded growth in practice.
- **Fat JAR CLI (`vajrapulse capacity`)** — Post-1.0.0 follow-on: single command, three flags, prints capacity number.
- **JUnit 5 extension** — Post-1.0.0 follow-on: `@VajraPulseTest` annotation for declarative load tests.
- **Spring Boot starter** — Post-1.0.0 follow-on: auto-configuration for embedded load testing in Spring apps.
