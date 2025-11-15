dependencies {
    implementation(project(":vajrapulse-api"))
    implementation(project(":vajrapulse-core"))
    
    implementation("org.slf4j:slf4j-api:2.0.9")
    
    testImplementation("org.spockframework:spock-core:2.4-M4-groovy-4.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
