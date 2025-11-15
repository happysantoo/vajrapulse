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
    // Project dependencies - transitively includes all required modules
    implementation(project(":vajrapulse-api"))
    implementation(project(":vajrapulse-core"))
    implementation(project(":vajrapulse-exporter-console"))
}

application {
    mainClass.set("com.example.http.HttpLoadTestRunner")
}
