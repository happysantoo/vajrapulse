plugins {
    java
    application
    id("com.google.protobuf") version "0.9.4"
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
    
    // gRPC dependencies
    implementation("io.grpc:grpc-netty-shaded:1.64.0")
    implementation("io.grpc:grpc-protobuf:1.64.0")
    implementation("io.grpc:grpc-stub:1.64.0")
    implementation("com.google.protobuf:protobuf-java:4.27.3")
    
    // Annotation dependency for generated code (Java 21 compatibility)
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
    
    // Logging
    runtimeOnly("ch.qos.logback:logback-classic:1.5.21")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.27.3"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.64.0"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
            }
        }
    }
}

application {
    mainClass.set("com.example.grpc.GrpcLoadTestRunner")
}
