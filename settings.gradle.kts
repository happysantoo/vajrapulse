rootProject.name = "vajrapulse"

include("vajrapulse-bom")
include("vajrapulse-api")
include("vajrapulse-core")
include("vajrapulse-exporter-console")
include("vajrapulse-exporter-opentelemetry")
include("vajrapulse-worker")

// Examples
include("examples:http-load-test")
