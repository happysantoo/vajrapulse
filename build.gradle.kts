plugins {
    java
    groovy
    id("com.gradleup.shadow") version "9.0.0-beta4" apply false
    // Coverage plugin (applied to subprojects explicitly)
    jacoco
    id("maven-publish")
    id("signing")
}

allprojects {
    // Artifact coordinates moved to 'com.vajrapulse' for 0.9 release alignment.
    group = "com.vajrapulse"
    version = "0.9.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    // Skip plugin/application for the aggregator ':examples' to avoid
    // creating and resolving unused configurations like annotationProcessor.
    if (project.path == ":examples") {
        // Aggregator only; no source sets or plugins applied.
        return@subprojects
    }

    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "groovy")
    apply(plugin = "jacoco")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation"))
    }

    // Configure coverage verification: enforce ≥90% for tested modules
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("compileJava"))
        // Include Groovy compilation if present (Spock tests / Groovy sources)
        tasks.findByName("compileGroovy")?.let { dependsOn(it) }
        dependsOn(tasks.named("test"))
        executionData.setFrom(files("$buildDir/jacoco/test.exec"))
        val fileFilter = listOf("**/module-info.class")
        classDirectories.setFrom(
            files(project.layout.buildDirectory.dir("classes"))
                .asFileTree.matching { exclude(fileFilter) }
        )
        sourceDirectories.setFrom(files("src/main/java", "src/main/groovy"))
        // Enforce 90% line coverage for core, api, exporter modules
        if (project.path in listOf(":vajrapulse-core", ":vajrapulse-api", ":vajrapulse-exporter-console")) {
            violationRules {
                rule {
                    element = "BUNDLE"
                    limit {
                        counter = "LINE"
                        value = "COVEREDRATIO"
                        minimum = BigDecimal("0.90")
                    }
                }
            }
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        // Generate coverage report and enforce threshold after tests
        finalizedBy(tasks.named("jacocoTestReport"), tasks.named("jacocoTestCoverageVerification"))
    }

    // Configure existing Jacoco reporting task per subproject
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        val fileFilter = listOf("**/module-info.class")
        classDirectories.setFrom(
            files(classDirectories.files.map { dir ->
                fileTree(dir) { exclude(fileFilter) }
            })
        )
        // Default source directories (Groovy may not exist in all modules)
        sourceDirectories.setFrom(files("src/main/java", "src/main/groovy"))
        executionData.setFrom(files("$buildDir/jacoco/test.exec"))
    }

    // Ensure 'check' depends on coverage verification for CI gating
    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestCoverageVerification"))
    }

    // Simplified publishing/signing for selected modules (API, core, exporters, worker)
    val publishable = setOf(
        "vajrapulse-api",
        "vajrapulse-core",
        "vajrapulse-exporter-console",
        "vajrapulse-exporter-opentelemetry",
        "vajrapulse-worker"
    )
    if (project.name in publishable) {

        java { withSourcesJar(); withJavadocJar() }

        publishing {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    pom {
                        name.set(project.name)
                        description.set("VajraPulse module ${project.name} (pre-1.0 load testing framework leveraging Java 21 virtual threads)")
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
            repositories {
                maven {
                    name = "OSSRH"
                    url = uri(if (version.toString().endsWith("SNAPSHOT"))
                        "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    else
                        "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                    credentials {
                        username = findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                        password = findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
                    }
                }
            }
        }

        signing {
            val signingKey: String? = findProperty("signingKey") as String? ?: System.getenv("SIGNING_KEY")
            val signingPassword: String? = findProperty("signingPassword") as String? ?: System.getenv("SIGNING_PASSWORD")
            if (signingKey != null && signingPassword != null) {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(publishing.publications["mavenJava"])
            } else {
                logger.warn("[publishing] Skipping signing for ${project.path}; SIGNING_KEY/SIGNING_PASSWORD not provided")
            }
        }
    }
}
