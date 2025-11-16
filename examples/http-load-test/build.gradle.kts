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
    // Project dependencies - transitively includes all required modules
    implementation(project(":vajrapulse-api"))
    implementation(project(":vajrapulse-core"))
    implementation(project(":vajrapulse-exporter-console"))
    implementation(project(":vajrapulse-exporter-opentelemetry"))
    implementation(project(":vajrapulse-worker"))
    
    // Logback for logging (replaces slf4j-simple)
    runtimeOnly("ch.qos.logback:logback-classic:1.5.21")
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
