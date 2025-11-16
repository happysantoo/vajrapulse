plugins {
    `java-library`
    groovy
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // VajraPulse dependencies
    implementation(project(":vajrapulse-core"))
    
    // OpenTelemetry dependencies
    implementation("io.opentelemetry:opentelemetry-api:1.43.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.43.0")
    implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.43.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.43.0")
    implementation("io.opentelemetry.semconv:opentelemetry-semconv:1.37.0")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.13")
    
    // Testing
    testImplementation(platform("org.spockframework:spock-bom:2.4-M4-groovy-4.0"))
    testImplementation("org.spockframework:spock-core")
    testImplementation("org.apache.groovy:groovy:4.0.23")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.9")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
