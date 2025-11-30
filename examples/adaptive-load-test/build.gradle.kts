plugins {
    id("java")
    id("application")
}

group = "com.example"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Use BOM for version management
    implementation(platform(project(":vajrapulse-bom")))
    
    // Use modules without versions (versions managed by BOM)
    implementation(project(":vajrapulse-api"))
    implementation(project(":vajrapulse-core"))
    implementation(project(":vajrapulse-exporter-console"))
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.21")
}

application {
    mainClass = "com.example.adaptive.AdaptiveLoadTestRunner"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

