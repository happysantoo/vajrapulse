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
    
    // Logging
    runtimeOnly("ch.qos.logback:logback-classic:1.5.21")
}

application {
    mainClass.set("com.example.cpu.CpuBoundTestRunner")
}
