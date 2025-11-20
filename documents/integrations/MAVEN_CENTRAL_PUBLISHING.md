## Maven Central Publishing (JReleaser)

Use this document for all future releases (manual 0.9.0 steps archived).

### Version Lifecycle
- Current release in preparation: 0.9.1
- Previous published: 0.9.0 (manual portal bundle)
- Next planned: 0.9.2 or 0.10.0 depending on feature scope.

### Core Command Flow
```bash
./gradlew clean build
./gradlew prepareRelease
./gradlew jreleaserDeploy --dry-run --no-configuration-cache
./gradlew jreleaserDeploy --no-configuration-cache
```

### Required Properties (Gradle or Env)
```
mavenCentralUsername=***
mavenCentralPassword=***
signingKey=ASCII_ARMORED_PRIVATE_KEY
signingPassword=GPG_PASSPHRASE
JRELEASER_GITHUB_TOKEN=ghp_... (for release automation)
```

### Verification
```bash
curl -I https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-core/0.9.1/vajrapulse-core-0.9.1.pom
```

### Fallback Script
If JReleaser fails unexpectedly:
```bash
./gradlew publishToMavenLocal
scripts/create-central-bundle.sh 0.9.1 /tmp/vajrapulse-0.9.1-central.zip
curl -u "$mavenCentralUsername:$mavenCentralPassword" \
  -F "bundle=@/tmp/vajrapulse-0.9.1-central.zip" \
  "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC"
```

### Post Publish Checklist
- Tag `v0.9.1`
- GitHub Release (auto when enabled)
- Update README dependency snippet
- Start next development cycle

### Upcoming Enhancements
- CHANGELOG generation via JReleaser
- CI workflow for tag-triggered deployment
- Additional exporters integration tests
