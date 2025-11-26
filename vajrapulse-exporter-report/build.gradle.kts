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
    implementation(project(":vajrapulse-api"))
    
    // JSON serialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    
    // Micrometer for adaptive pattern metrics (optional registry access)
    implementation("io.micrometer:micrometer-core:1.12.0")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.13")
    
    // Testing
    testImplementation(platform("org.spockframework:spock-bom:2.4-M4-groovy-4.0"))
    testImplementation("org.spockframework:spock-core")
    testImplementation("org.apache.groovy:groovy:4.0.23")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.9")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

