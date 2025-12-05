plugins {
    java
    groovy
    id("com.gradleup.shadow") version "9.0.0-beta4" apply false
    jacoco
    id("com.github.spotbugs") version "6.0.14" apply false
    id("maven-publish")
    signing
}

allprojects {
    // Artifact coordinates moved to 'com.vajrapulse' for 0.9 release alignment.
    group = "com.vajrapulse"
    version = "0.9.8"

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
    
    // BOM uses java-platform plugin, skip standard plugins
    if (project.path == ":vajrapulse-bom") {
        return@subprojects
    }

    apply(plugin = "java")
    apply(plugin = "java-library")
    apply(plugin = "groovy")
    apply(plugin = "jacoco")
    apply(plugin = "com.github.spotbugs")
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
        
        // Configure JavaDoc linting
        // Suppress doclint warnings for missing comments in examples (they're educational)
        if (project.path.startsWith(":examples")) {
            options.compilerArgs.add("-Xdoclint:none")
        } else {
            // For main modules, enforce JavaDoc requirements (warn on malformed, allow missing)
            options.compilerArgs.add("-Xdoclint:all")
            options.compilerArgs.add("-Xdoclint:-missing")
        }
    }
    
    // Configure JavaDoc task to check for documentation issues
    tasks.withType<Javadoc> {
        options {
            (this as StandardJavadocDocletOptions).apply {
                addStringOption("Xdoclint:all,-missing", "-quiet")
                // Suppress doclint for examples
                if (project.path.startsWith(":examples")) {
                    addStringOption("Xdoclint:none", "-quiet")
                }
            }
        }
    }

    // Configure coverage verification: enforce ≥90% for tested modules
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("compileJava"))
        // Include Groovy compilation if present (Spock tests / Groovy sources)
        tasks.findByName("compileGroovy")?.let { dependsOn(it) }
        dependsOn(tasks.named("test"))
        executionData.setFrom(files(layout.buildDirectory.dir("jacoco/test.exec")))
        val fileFilter = listOf("**/module-info.class")
        classDirectories.setFrom(
            files(project.layout.buildDirectory.dir("classes"))
                .asFileTree.matching { exclude(fileFilter) }
        )
        sourceDirectories.setFrom(files("src/main/java", "src/main/groovy"))
        // Enforce 90% line coverage for core, api, exporter modules
        if (project.path in listOf(":vajrapulse-core", ":vajrapulse-api", ":vajrapulse-exporter-console", ":vajrapulse-exporter-opentelemetry", ":vajrapulse-exporter-report")) {
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
        executionData.setFrom(files(layout.buildDirectory.dir("jacoco/test.exec")))
    }

    // Configure SpotBugs for static code analysis
    tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
        // Skip examples from static analysis
        enabled = !project.path.startsWith(":examples")
        
        // Set exclusion filter
        excludeFilter = file("${rootProject.projectDir}/spotbugs-exclude.xml")
        
        // Configure reports
        reports {
            create("html") {
                required = true
            }
        }
        
        // Don't fail build on findings - generate report for review instead
        // Developers should review and fix issues before committing
        ignoreFailures = false  // Set to true if you want to allow builds with findings
    }
    
    // Disable spotbugsTest task - we only analyze main source code
    // Test code (Spock/Groovy) has different patterns that trigger false positives
    tasks.named("spotbugsTest") {
        enabled = false
    }
    
    // Ensure 'check' depends on coverage verification and static analysis for CI gating
    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestCoverageVerification"))
        // Note: spotbugsMain will fail if issues found - this enforces code quality
        dependsOn(tasks.named("spotbugsMain"))
    }

    // Simplified publishing/signing for selected modules (BOM, API, core, exporters, worker)
    val publishable = setOf(
        "vajrapulse-bom",
        "vajrapulse-api",
        "vajrapulse-core",
        "vajrapulse-exporter-console",
        "vajrapulse-exporter-opentelemetry",
        "vajrapulse-exporter-report",
        "vajrapulse-worker"
    )
    if (project.name in publishable) {

        java { withSourcesJar(); withJavadocJar() }

        // Include LICENSE in all JAR files (main, sources, javadoc)
        tasks.withType<Jar> {
            from(rootProject.file("LICENSE")) {
                into("META-INF")
            }
        }

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
            // No repositories: JReleaser handles bundle + portal upload.
        }

        // Configure signing for Maven Central compliance
        signing {
            // Use in-memory ASCII-armored key from gradle.properties
            val signingKey: String? by project
            val signingPassword: String? by project
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(publishing.publications["mavenJava"])
        }
    }
}

// Convenience task: aggregate build before release
tasks.register("prepareRelease") {
    group = "release"
    description = "Assemble all publishable modules for Maven Central release"
    dependsOn(subprojects.filter { it.name.startsWith("vajrapulse") }.map { it.tasks.named("build") })
}

