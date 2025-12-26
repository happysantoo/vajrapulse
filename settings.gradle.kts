rootProject.name = "vajrapulse"

include("vajrapulse-bom")
include("vajrapulse-api")
include("vajrapulse-core")
include("vajrapulse-exporter-console")
include("vajrapulse-exporter-opentelemetry")
include("vajrapulse-exporter-report")
include("vajrapulse-worker")
include("benchmarks")

// Examples
include("examples:http-load-test")
include("examples:adaptive-load-test")
include("examples:adaptive-with-warmup")
include("examples:assertion-framework")
include("examples:database-load-test")
include("examples:cpu-bound-test")
include("examples:grpc-load-test")
include("examples:multi-exporter")
