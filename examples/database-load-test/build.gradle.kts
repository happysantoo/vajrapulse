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
    // Using BOM for version management
    implementation(platform(project(":vajrapulse-bom")))
    
    // VajraPulse modules
    implementation(project(":vajrapulse-api"))
    implementation(project(":vajrapulse-core"))
    implementation(project(":vajrapulse-exporter-console"))
    
    // Database dependencies
    // H2 for in-memory testing (lightweight, no setup required)
    implementation("com.h2database:h2:2.3.232")
    
    // HikariCP for connection pooling
    implementation("com.zaxxer:HikariCP:6.1.0")
    
    // PostgreSQL driver (optional - uncomment if using PostgreSQL)
    // implementation("org.postgresql:postgresql:42.7.4")
    
    // Logging
    runtimeOnly("ch.qos.logback:logback-classic:1.5.21")
}

application {
    mainClass.set("com.example.database.DatabaseLoadTestRunner")
}
