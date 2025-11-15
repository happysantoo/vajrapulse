plugins {
    application
    id("com.gradleup.shadow")
}

application {
    mainClass.set("com.vajra.worker.VajraWorker")
}

dependencies {
    implementation(project(":vajra-api"))
    implementation(project(":vajra-exporter-console"))
    implementation(project(":vajra-core"))
    
    implementation("info.picocli:picocli:4.7.5")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    
    testImplementation("org.spockframework:spock-core:2.4-M4-groovy-4.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.shadowJar {
    archiveBaseName.set("vajra-worker")
    archiveClassifier.set("all")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "com.vajra.worker.VajraWorker"
    }
}
