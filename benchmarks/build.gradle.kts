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
        languageVersion.set(JavaLanguageVersion.of(25))
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
}

tasks.withType<Test> {
}

jmh {
    jmhVersion.set("1.37")
    warmupIterations.set(5)
    iterations.set(10)
    fork.set(1)
    threads.set(1)
    jvmArgs.set(listOf())
    // MacroScenarioBenchmark runs ~2 min per iteration; exclude unless explicitly enabled
    if (!project.hasProperty("jmh.includeMacro")) {
        excludes.add(".*MacroScenarioBenchmark.*")
    }
}

tasks.withType<JmhBytecodeGeneratorTask>().configureEach {
}
