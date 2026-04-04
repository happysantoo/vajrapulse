plugins {
    java
    id("me.champeau.jmh") version "0.7.1"
}

import me.champeau.jmh.JmhBytecodeGeneratorTask

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Using BOM for version management
    implementation(platform(project(":vajrapulse-bom")))
    
    // VajraPulse modules
    implementation(project(":vajrapulse-api"))
    implementation(project(":vajrapulse-core"))
    
    // OpenTelemetry for Span type in benchmarks
    implementation("io.opentelemetry:opentelemetry-api:1.32.0")
    
    // JMH - add to implementation for compilation, jmh config for runtime
    implementation("org.openjdk.jmh:jmh-core:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    
    // JMH runtime dependencies
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("--enable-preview") // Enable preview for ScopedValue
}

tasks.withType<Test> {
    jvmArgs("--enable-preview") // Enable preview for tests
}

jmh {
    jmhVersion.set("1.37")
    warmupIterations.set(5)
    iterations.set(10)
    fork.set(1)
    threads.set(1)
    // vajrapulse-core uses preview (ScopedValue); JMH generator and forks must load those classes
    jvmArgs.set(listOf("--enable-preview"))
    // MacroScenarioBenchmark runs ~2 min per iteration; exclude unless explicitly enabled
    if (!project.hasProperty("jmh.includeMacro")) {
        excludes.add(".*MacroScenarioBenchmark.*")
    }
}

tasks.withType<JmhBytecodeGeneratorTask>().configureEach {
    jvmArgs.add("--enable-preview")
}
