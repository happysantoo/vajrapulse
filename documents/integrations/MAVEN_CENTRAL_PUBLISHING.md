## Maven Central Publishing (JReleaser)

Use this document for releases **1.0.0+**. Older manual steps for 0.9.0 are archived under `documents/archive/`.

### Version lifecycle

- **Current release line**: 1.0.0 (see root `build.gradle.kts` and `jreleaser.yml`).
- **Next**: patch (`1.0.1`) or minor (`1.1.0`) per [Semantic Versioning](https://semver.org/) and [VERSIONING.md](../guides/VERSIONING.md).

### Maintainer checklist

See **[RELEASE_1.0.0_PUBLICATION.md](../guides/RELEASE_1.0.0_PUBLICATION.md)** for tag + JReleaser + verification commands.

### Core command flow

```bash
./gradlew check --rerun-tasks
./gradlew prepareRelease
./gradlew jreleaserDeploy --dry-run --no-configuration-cache
./gradlew jreleaserDeploy --no-configuration-cache
```

### Required properties (Gradle or environment)

```
mavenCentralUsername=***
mavenCentralPassword=***
signingKey=ASCII_ARMORED_PRIVATE_KEY
signingPassword=GPG_PASSPHRASE
JRELEASER_GITHUB_TOKEN=ghp_...   # if GitHub release is automated
```

### Verification

```bash
curl -I "https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-core/1.0.0/vajrapulse-core-1.0.0.pom"
```

(Replace version after publishing newer artifacts.)

### Fallback bundle upload

If JReleaser fails unexpectedly, see archived manual flow in `documents/archive/MAVEN_CENTRAL_MANUAL_0.9.0.md` and adapt the version in the bundle script.

### Post-publish

- Push annotated tag `v<version>`
- Confirm GitHub Release notes
- Update README dependency snippets if needed
- Begin next development cycle (`x.y.(z+1)-SNAPSHOT` or patch branch policy)
