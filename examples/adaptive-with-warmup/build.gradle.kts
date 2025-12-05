plugins {
    java
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(platform(project(":vajrapulse-bom")))
    implementation(project(":vajrapulse-core"))
    implementation(project(":vajrapulse-api"))
}

application {
    mainClass.set("com.vajrapulse.examples.AdaptiveWithWarmupExample")
}

