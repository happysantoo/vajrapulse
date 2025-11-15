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
    // Reference local vajrapulse-api module
    implementation(files("../../vajrapulse-api/build/libs/vajrapulse-api-1.0.0-SNAPSHOT.jar"))
}

application {
    mainClass.set("com.example.http.HttpLoadTest")
}
