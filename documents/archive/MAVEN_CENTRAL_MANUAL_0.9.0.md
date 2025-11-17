# Archived Manual Maven Central Release Instructions (0.9.0)

(Archived on version bump to 0.9.1; replaced by JReleaser-driven process.)

## Maven Central Release 0.9.0 (Historical Record)

### Summary
Initial attempt to publish VajraPulse 0.9.0 to Maven Central (Portal) failed due to missing Gradle Module Metadata file in the uploaded bundle (`*.module`). The first bundle contained only `*.jar`, `*-sources.jar`, `*-javadoc.jar`, `*.pom` and their `*.asc` signatures plus `*.module.asc`, but excluded the raw `*.module` files. The Sonatype validator rejected the deployment with:

```
Failed to process deployment: Error on building manifests: File with path '.../vajrapulse-exporter-console-0.9.0.module' is missing
```

### Root Cause
Manual bundle creation used explicit globs that omitted the `*.module` base files:

```bash
zip -r bundle.zip com/vajrapulse/.../*.jar com/vajrapulse/.../*.pom com/vajrapulse/.../*.asc
```

Since `*.module` was not matched, the Portal could not assemble the Gradle metadata manifest.

### Resolution Steps
1. Verified presence of `*.module` and `*.module.asc` in local Maven repository (`~/.m2/repository/com/vajrapulse/<module>/0.9.0/`).
2. Created corrected bundle including all artifacts and metadata using:
   ```bash
   zip -r /tmp/vajrapulse-0.9.0-bundle-fixed.zip \
     vajrapulse-api/0.9.0/vajrapulse-api-0.9.0.* \
     vajrapulse-core/0.9.0/vajrapulse-core-0.9.0.* \
     vajrapulse-exporter-console/0.9.0/vajrapulse-exporter-console-0.9.0.* \
     vajrapulse-exporter-opentelemetry/0.9.0/vajrapulse-exporter-opentelemetry-0.9.0.* \
     vajrapulse-worker/0.9.0/vajrapulse-worker-0.9.0.*
   ```
3. Uploaded via Central Portal API (basic auth) with automatic publishing:
   ```bash
   curl -u "${MAVEN_CENTRAL_USERNAME}:${MAVEN_CENTRAL_PASSWORD}" \
     -F "bundle=@/tmp/vajrapulse-0.9.0-bundle-fixed.zip" \
     "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC"
   ```
4. Received deployment ID: `feb510a8-2452-4b1e-a997-e27948aa0592`.

### Second Validation Failure & Fix
Portal reported additional errors:
```
Sources must be provided but not found
Javadocs must be provided but not found
Missing md5/sha1 checksum for several files
File path 'vajrapulse-worker/0.9.0' is not valid ...
```
Cause: Bundle root lacked group path (`com/vajrapulse/...`) and sources/javadoc jars were excluded by an over‑restrictive glob (`artifact-<version>.*` misses `-sources` / `-javadoc`). Checksums were not included.

Fixed by:
1. Generating md5 and sha1 for each required artifact (pom, module, jar, sources.jar, javadoc.jar).
2. Rebuilding bundle from `~/.m2/repository` preserving full path:
    ```bash
    zip -r /tmp/vajrapulse-0.9.0-central.zip \
       com/vajrapulse/vajrapulse-api/0.9.0 \
       com/vajrapulse/vajrapulse-core/0.9.0 \
       com/vajrapulse/vajrapulse-exporter-console/0.9.0 \
       com/vajrapulse/vajrapulse-exporter-opentelemetry/0.9.0 \
       com/vajrapulse/vajrapulse-worker/0.9.0
    ```
3. Upload produced deployment ID: `38a1bb13-84eb-4427-8670-4450b4f849a1`.
4. Added automation script `scripts/create-central-bundle.sh` for future releases.

### Lessons / Improvements
- Manual portal bundles are fragile; prefer automation.
- Always include raw `*.module` plus signatures and checksums.
- Fallback script retained for emergency use.

### Historical Commands
```bash
./gradlew publishToMavenLocal
zip -r /tmp/vajrapulse-0.9.0-central.zip com/vajrapulse/... (full paths)
curl -u "$mavenCentralUsername:$mavenCentralPassword" -F "bundle=@/tmp/vajrapulse-0.9.0-central.zip" \
  "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC"
```

(End archived content.)
