plugins {
    `java-platform`
    `maven-publish`
    signing
}

// BOM is a platform module, no Java source code needed
// Include LICENSE in the POM metadata

dependencies {
    // Define constraints for all VajraPulse modules
    constraints {
        api(project(":vajrapulse-api"))
        api(project(":vajrapulse-core"))
        api(project(":vajrapulse-exporter-console"))
        api(project(":vajrapulse-exporter-opentelemetry"))
        api(project(":vajrapulse-exporter-report"))
        api(project(":vajrapulse-worker"))
        
        // External dependencies
        api("com.vajrapulse:vortex:0.0.9")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["javaPlatform"])
            pom {
                name.set("VajraPulse BOM")
                description.set("Bill of Materials for VajraPulse - A load testing framework leveraging Java 21 virtual threads")
                url.set("https://github.com/happysantoo/vajrapulse")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                scm {
                    url.set("https://github.com/happysantoo/vajrapulse")
                    connection.set("scm:git:git://github.com/happysantoo/vajrapulse.git")
                    developerConnection.set("scm:git:ssh://github.com:happysantoo/vajrapulse.git")
                }
                developers {
                    developer {
                        id.set("happysantoo")
                        name.set("Santhosh Kuppusamy")
                        url.set("https://github.com/happysantoo")
                    }
                }
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
}

