plugins {
    application
    id("com.gradleup.shadow")
}

application {
    mainClass.set("com.vajra.worker.VajraWorker")
}

dependencies {
    // Use api instead of implementation to expose these to consumers
    api(project(":vajrapulse-api"))
    api(project(":vajrapulse-core"))
    // BREAKING CHANGE (Pre-1.0): Console exporter is now optional for library users
    // The worker CLI itself uses console (implementation), but doesn't expose it (api)
    // Users must explicitly add vajrapulse-exporter-console if using worker as library
    implementation(project(":vajrapulse-exporter-console"))
    
    // Expose required runtime dependencies
    api("io.micrometer:micrometer-core:1.13.0")
    api("org.slf4j:slf4j-api:2.0.13")
    
    // Worker-specific dependencies
    implementation("info.picocli:picocli:4.7.5")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    
    testImplementation("org.spockframework:spock-core:2.4-M4-groovy-4.0")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testImplementation("org.awaitility:awaitility-groovy:4.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.shadowJar {
    archiveBaseName.set("vajrapulse-worker")
    archiveClassifier.set("all")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "com.vajra.worker.VajraWorker"
    }
}
