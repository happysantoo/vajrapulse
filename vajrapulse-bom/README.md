# VajraPulse BOM (Bill of Materials)

The VajraPulse BOM provides a centralized way to manage dependency versions across all VajraPulse modules.

## What is a BOM?

A Bill of Materials (BOM) is a special POM file that groups dependency versions together. When you import the BOM, you get all the dependency versions defined in it, ensuring consistency across your project.

## Benefits

- ✅ **Single version to manage** - Import the BOM and use VajraPulse modules without specifying versions
- ✅ **No version conflicts** - All modules are guaranteed to use compatible versions
- ✅ **Industry standard** - Same pattern used by Spring Boot, Micronaut, Quarkus, etc.
- ✅ **Easier upgrades** - Update one version number to upgrade all modules

## Usage

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    // Import BOM
    implementation(platform("com.vajrapulse:vajrapulse-bom:0.9.3"))
    
    // Use VajraPulse modules without versions
    implementation("com.vajrapulse:vajrapulse-core")
    implementation("com.vajrapulse:vajrapulse-exporter-console")
    implementation("com.vajrapulse:vajrapulse-exporter-opentelemetry")
    implementation("com.vajrapulse:vajrapulse-worker")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    // Import BOM
    implementation platform('com.vajrapulse:vajrapulse-bom:0.9.3')
    
    // Use VajraPulse modules without versions
    implementation 'com.vajrapulse:vajrapulse-core'
    implementation 'com.vajrapulse:vajrapulse-exporter-console'
    implementation 'com.vajrapulse:vajrapulse-exporter-opentelemetry'
    implementation 'com.vajrapulse:vajrapulse-worker'
}
```

### Maven

```xml
<dependencyManagement>
    <dependencies>
        <!-- Import BOM -->
        <dependency>
            <groupId>com.vajrapulse</groupId>
            <artifactId>vajrapulse-bom</artifactId>
            <version>0.9.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Use VajraPulse modules without versions -->
    <dependency>
        <groupId>com.vajrapulse</groupId>
        <artifactId>vajrapulse-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.vajrapulse</groupId>
        <artifactId>vajrapulse-exporter-console</artifactId>
    </dependency>
    <dependency>
        <groupId>com.vajrapulse</groupId>
        <artifactId>vajrapulse-exporter-opentelemetry</artifactId>
    </dependency>
    <dependency>
        <groupId>com.vajrapulse</groupId>
        <artifactId>vajrapulse-worker</artifactId>
    </dependency>
</dependencies>
```

## Modules Included

The BOM includes the following VajraPulse modules:

- `vajrapulse-api` - Public API (zero dependencies)
- `vajrapulse-core` - Execution engine
- `vajrapulse-exporter-console` - Console metrics exporter
- `vajrapulse-exporter-opentelemetry` - OpenTelemetry exporter
- `vajrapulse-worker` - CLI application

## Version Compatibility

All modules in the BOM are guaranteed to be compatible with each other. When you import the BOM, you get:

- Consistent versions across all modules
- No transitive dependency conflicts
- Tested combinations

## Upgrading

To upgrade to a new version, simply update the BOM version:

```kotlin
// Before
implementation(platform("com.vajrapulse:vajrapulse-bom:0.9.2"))

// After
implementation(platform("com.vajrapulse:vajrapulse-bom:0.9.3"))
```

All modules will automatically use the new version.

## Without BOM (Traditional Approach)

If you don't use the BOM, you need to specify versions for each module:

```kotlin
dependencies {
    implementation("com.vajrapulse:vajrapulse-core:0.9.3")
    implementation("com.vajrapulse:vajrapulse-exporter-console:0.9.3")
    implementation("com.vajrapulse:vajrapulse-exporter-opentelemetry:0.9.3")
    // ... need to update version in multiple places
}
```

## Best Practices

1. **Always use the BOM** - It ensures version consistency
2. **Import in dependencyManagement** - For Maven, use `<scope>import</scope>`
3. **Use platform() in Gradle** - For Gradle, use `platform()` dependency
4. **Don't override versions** - Let the BOM manage versions unless you have a specific need

## Example: Complete Project Setup

```kotlin
// build.gradle.kts
plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    // Import BOM
    implementation(platform("com.vajrapulse:vajrapulse-bom:0.9.3"))
    
    // Use modules without versions
    implementation("com.vajrapulse:vajrapulse-core")
    implementation("com.vajrapulse:vajrapulse-exporter-console")
    
    // Your application dependencies
    implementation("org.slf4j:slf4j-api")
}
```

## Troubleshooting

### Version Conflicts

If you encounter version conflicts, ensure you're using the BOM:

```kotlin
// ✅ Correct
implementation(platform("com.vajrapulse:vajrapulse-bom:0.9.3"))
implementation("com.vajrapulse:vajrapulse-core")

// ❌ Wrong - version specified
implementation("com.vajrapulse:vajrapulse-core:0.9.3")
```

### Transitive Dependencies

The BOM only manages VajraPulse module versions. For other dependencies (e.g., Micrometer, OpenTelemetry), you may need to manage versions separately or use their respective BOMs.

## See Also

- [VajraPulse README](../README.md) - Main project documentation
- [Maven Central](https://search.maven.org/search?q=g:com.vajrapulse) - Published artifacts

