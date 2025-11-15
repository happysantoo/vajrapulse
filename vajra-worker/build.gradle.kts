plugins {
    application
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":vajra-api"))
    implementation(project(":vajra-core"))
    implementation(project(":vajra-exporter-console"))
    
    implementation("info.picocli:picocli:4.7.5")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    
    testImplementation("org.spockframework:spock-core:2.4-M4-groovy-4.0")
}

application {
    mainClass.set("com.vajra.worker.VajraWorker")
}

tasks.shadowJar {
    archiveBaseName.set("vajra-worker")
    archiveClassifier.set("all")
    mergeServiceFiles()
}
