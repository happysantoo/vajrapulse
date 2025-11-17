# LICENSE File Publishing

## Status: ✅ Implemented (v0.9.2+)

### Background
Prior to v0.9.2, VajraPulse published artifacts to Maven Central with license metadata in the POM file but without the physical LICENSE file embedded in the JARs.

### Implementation
As of build configuration update (November 17, 2025), all published JARs now include:
1. Apache License 2.0 file in `META-INF/LICENSE`
2. GPG signatures (`.asc` files) for all artifacts

### Configuration

#### LICENSE Inclusion
```kotlin
// build.gradle.kts - Applied to all publishable modules
tasks.withType<Jar> {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}
```

#### Artifact Signing
```kotlin
// build.gradle.kts - Applied to all publishable modules
signing {
    // Use in-memory ASCII-armored key from gradle.properties
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
}
```

**Required in `gradle.properties`:**
```properties
signingKey=<ASCII-armored GPG private key>
signingPassword=<GPG key passphrase>
```

This applies to:
- Main JARs (`.jar`)
- Sources JARs (`-sources.jar`)
- Javadoc JARs (`-javadoc.jar`)

### Verification
Check LICENSE inclusion in any published JAR:

```bash
unzip -l vajrapulse-core-0.9.2.jar | grep LICENSE
# Output: META-INF/LICENSE
```

Or extract and read:
```bash
unzip -p vajrapulse-core-0.9.2.jar META-INF/LICENSE
```

Verify GPG signatures:
```bash
# List all signature files
ls ~/.m2/repository/com/vajrapulse/vajrapulse-core/0.9.2/*.asc

# Verify a signature
gpg --verify vajrapulse-core-0.9.2.jar.asc vajrapulse-core-0.9.2.jar
```

Each artifact has 5 signature files:
- `vajrapulse-MODULE-VERSION.jar.asc`
- `vajrapulse-MODULE-VERSION-sources.jar.asc`
- `vajrapulse-MODULE-VERSION-javadoc.jar.asc`
- `vajrapulse-MODULE-VERSION.pom.asc`
- `vajrapulse-MODULE-VERSION.module.asc`

### Maven Central Bundles
The LICENSE file is automatically included in Maven Central upload bundles since it's embedded in the JARs. No additional bundle script changes required.

### Affected Modules
- `vajrapulse-api`
- `vajrapulse-core`
- `vajrapulse-exporter-console`
- `vajrapulse-exporter-opentelemetry`
- `vajrapulse-worker`

### Future Releases
All future releases (0.9.2+) will include the LICENSE file in all published artifacts.

### Compliance
This ensures full Apache License 2.0 compliance by distributing the license text with the binary distribution, as required by the license terms.
