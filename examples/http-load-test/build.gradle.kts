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
    // Reference local vajra-api module
    implementation(files("../../vajra-api/build/libs/vajra-api-1.0.0-SNAPSHOT.jar"))
}

application {
    mainClass.set("com.example.http.HttpLoadTest")
}
