plugins {
    id("java")
    id("application")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("com.vajrapulse:vajrapulse-bom:0.9.3"))
    implementation("com.vajrapulse:vajrapulse-worker")
    implementation("com.vajrapulse:vajrapulse-exporter-console")
}

application {
    mainClass.set("com.vajrapulse.test.AllPatternsTest")
}

