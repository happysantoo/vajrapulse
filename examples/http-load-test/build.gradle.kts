plugins {
    java
    application
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Using BOM for version management (recommended approach)
    // When using published artifacts, use: implementation(platform("com.vajrapulse:vajrapulse-bom:0.9.3"))
    // For local development with project dependencies, we reference the BOM project
    implementation(platform(project(":vajrapulse-bom")))
    
    // Use modules without versions (versions managed by BOM)
    implementation(project(":vajrapulse-api"))
    implementation(project(":vajrapulse-core"))
    implementation(project(":vajrapulse-exporter-console"))
    implementation(project(":vajrapulse-exporter-opentelemetry"))
    implementation(project(":vajrapulse-exporter-report"))
    implementation(project(":vajrapulse-worker"))
    
    // Logback for logging (replaces slf4j-simple)
    runtimeOnly("ch.qos.logback:logback-classic:1.5.21")
    
    // Note: When using published artifacts from Maven Central, the dependencies would be:
    // implementation(platform("com.vajrapulse:vajrapulse-bom:0.9.3"))
    // implementation("com.vajrapulse:vajrapulse-core")
    // implementation("com.vajrapulse:vajrapulse-exporter-console")
    // implementation("com.vajrapulse:vajrapulse-exporter-opentelemetry")
}

application {
    mainClass.set("com.example.http.HttpLoadTestRunner")
}

// Additional run task for OpenTelemetry variant
tasks.register<JavaExec>("runOtel") {
    group = "application"
    description = "Runs the HTTP load test with OpenTelemetry exporter"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.example.http.HttpLoadTestOtelRunner")
    jvmArgs = listOf("-Dotel.resource.attributes=example=true")
}
