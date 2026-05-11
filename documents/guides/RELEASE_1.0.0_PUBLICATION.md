# Publishing VajraPulse 1.0.0 (tag, GitHub, Maven Central)

This guide is the **maintainer checklist** for turning the source tree at version `1.0.0` into published artifacts. It complements [`documents/releases/RELEASE_1.0.0_CHECKLIST.md`](../releases/RELEASE_1.0.0_CHECKLIST.md).

## Preconditions

1. `main` (or the release branch) passes full verification:

   ```bash
   ./gradlew check --rerun-tasks
   ```

2. Optional but recommended: refresh performance numbers in [`documents/analysis/PERFORMANCE_BASELINE.md`](../analysis/PERFORMANCE_BASELINE.md):

   ```bash
   ./gradlew :benchmarks:jmh --no-configuration-cache
   ```

   To include the long-running macro benchmark (~20+ minutes):

   ```bash
   ./gradlew :benchmarks:jmh -Pjmh.includeMacro --no-configuration-cache
   ```

3. Credentials available (see also [`documents/integrations/MAVEN_CENTRAL_PUBLISHING.md`](../integrations/MAVEN_CENTRAL_PUBLISHING.md)):

   - `mavenCentralUsername` / `mavenCentralPassword`
   - `signingKey` / `signingPassword` (GPG)
   - `JRELEASER_GITHUB_TOKEN` with `repo` scope (for GitHub release when using JReleaser)

## Git tag

Create an **annotated** tag on the commit you are releasing:

```bash
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
```

If the tag already exists remotely, do not recreate it; use a patch release instead.

## JReleaser deploy

[`jreleaser.yml`](../../jreleaser.yml) is configured for GitHub releases and Maven Central (`stage: FULL`).

Dry run:

```bash
./gradlew prepareRelease jreleaserDeploy --dry-run --no-configuration-cache
```

Publish:

```bash
./gradlew prepareRelease jreleaserDeploy --no-configuration-cache
```

## Verify Maven Central

After propagation (can take several minutes):

```bash
curl -I "https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-core/1.0.0/vajrapulse-core-1.0.0.pom"
```

## GitHub release

If JReleaser `release.github.enabled` is `true`, the release may be created automatically. Otherwise create the GitHub Release manually from tag `v1.0.0` and paste the [`CHANGELOG.md`](../../CHANGELOG.md) section for 1.0.0.

## After publication

1. Mark the publication items in [`RELEASE_1.0.0_CHECKLIST.md`](../releases/RELEASE_1.0.0_CHECKLIST.md).
2. Bump the next development version (e.g. `1.0.1-SNAPSHOT`) per your branching policy.
