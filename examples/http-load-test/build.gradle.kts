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
    // Reference local vajrapulse modules
    implementation(files("../../vajrapulse-api/build/libs/vajrapulse-api-1.0.0-SNAPSHOT.jar"))
    implementation(files("../../vajrapulse-core/build/libs/vajrapulse-core-1.0.0-SNAPSHOT.jar"))
    implementation(files("../../vajrapulse-worker/build/libs/vajrapulse-worker-1.0.0-SNAPSHOT.jar"))
    implementation(files("../../vajrapulse-exporter-console/build/libs/vajrapulse-exporter-console-1.0.0-SNAPSHOT.jar"))
    
    // Required transitive dependencies
    implementation("io.micrometer:micrometer-core:1.13.0")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("info.picocli:picocli:4.7.6")
}

application {
    mainClass.set("com.example.http.HttpLoadTestRunner")
}
