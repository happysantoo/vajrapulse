dependencies {
    api(project(":vajra-api"))
    
    implementation("io.micrometer:micrometer-core:1.12.0")
    implementation("org.slf4j:slf4j-api:2.0.9")
    
    testImplementation("org.spockframework:spock-core:2.4-M4-groovy-4.0")
    testImplementation("org.slf4j:slf4j-simple:2.0.9")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
