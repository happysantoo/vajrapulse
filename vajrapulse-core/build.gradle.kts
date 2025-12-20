dependencies {
    api(project(":vajrapulse-api"))
    
    // Use BOM for dependency management
    implementation(platform(project(":vajrapulse-bom")))
    
    implementation("io.micrometer:micrometer-core:1.12.0")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.yaml:snakeyaml:2.2")
    // Minimal OpenTelemetry tracing dependencies
    implementation("io.opentelemetry:opentelemetry-api:1.41.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.41.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.41.0")
    implementation("io.opentelemetry:opentelemetry-semconv:1.26.0-alpha")
    
    // Vortex micro-batching library for task submission optimization
    implementation("com.vajrapulse:vortex")
    
    testImplementation("org.spockframework:spock-core:2.4-M4-groovy-4.0")
    testImplementation("org.slf4j:slf4j-simple:2.0.9")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testImplementation("org.awaitility:awaitility-groovy:4.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
