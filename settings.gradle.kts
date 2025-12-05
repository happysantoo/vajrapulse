rootProject.name = "vajrapulse"

include("vajrapulse-bom")
include("vajrapulse-api")
include("vajrapulse-core")
include("vajrapulse-exporter-console")
include("vajrapulse-exporter-opentelemetry")
include("vajrapulse-exporter-report")
include("vajrapulse-worker")

// Examples
include("examples:http-load-test")
include("examples:adaptive-load-test")
include("examples:client-metrics-http")
include("examples:adaptive-with-warmup")
include("examples:assertion-framework")
