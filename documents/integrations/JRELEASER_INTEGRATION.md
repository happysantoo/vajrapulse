# JReleaser Integration (Maven Central Portal)

## Goal
Replace ad‑hoc manual bundle construction & curl uploads with a declarative, repeatable release pipeline using JReleaser's Maven Central Portal support (introduced in JReleaser 1.12.0).

## Why JReleaser
- Automates bundle assembly (sources, javadoc, checksums, signatures).
- Handles portal upload + publish (Stage.FULL).
- Provides retry logic & status tracking (deployment states: PENDING → VALIDATING → VALIDATED → PUBLISHING → PUBLISHED/FAILED).
- Centralizes release metadata (authors, license, links).

## Configuration Summary
Added to `build.gradle.kts`:
```kotlin
plugins { id("org.jreleaser") version "1.12.0" }

jreleaser {
  signing { active.set(org.jreleaser.model.Active.ALWAYS); armored.set(true) }
  project {
    description.set("VajraPulse distributed load testing framework (pre-1.0, Java 21 virtual threads)")
    authors.set(listOf("Santhosh Kuppusamy"))
    license.set("Apache-2.0")
    links { homepage.set("https://github.com/happysantoo/vajrapulse"); scm.set("https://github.com/happysantoo/vajrapulse"); documentation.set("https://github.com/happysantoo/vajrapulse") }
  }
  deploy { maven { mavenCentral { stage.set(org.jreleaser.model.api.deploy.maven.MavenCentralMavenDeployer.Stage.FULL) } } }
}
```

### Required Gradle Properties (in `~/.gradle/gradle.properties`)
```
mavenCentralUsername=YOUR_USERNAME
mavenCentralPassword=YOUR_PASSWORD
signingKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...
signingPassword=YOUR_KEY_PASSPHRASE
```

## Release Workflow
### 1. Prepare
```bash
./gradlew clean prepareRelease
```
### 2. Dry Run (validate bundle assembly without uploading)
```bash
./gradlew jreleaserDeploy --dry-run
```
Checks: output directory `build/jreleaser` contains staged artifacts & generated checksums.

### 3. Full Release
```bash
./gradlew jreleaserDeploy
```
This performs upload + publish (Stage.FULL). If you want to separate:
- `stage = UPLOAD` then later `stage = PUBLISH` (edit build or use environment overrides).

### 4. Verify (after portal finishes)
```bash
curl -I https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-core/0.9.0/vajrapulse-core-0.9.0.pom
```

## Troubleshooting
| Symptom | Cause | Fix |
|---------|-------|-----|
| Missing sources/javadoc error | Not built or not attached | Ensure each publishable module applies `withSourcesJar()` & `withJavadocJar()` (already configured). |
| Signature mismatch | Wrong secret key/passphrase | Re-export key; confirm ASCII armored; update `signingKey` & `signingPassword`. |
| 401 Unauthorized | Bad credentials | Verify `mavenCentralUsername/password` match Portal account. |
| Portal validation fails (checksums) | JReleaser auto-generates; manual interference | Remove manual checksum generation scripts from release flow. |
| Deployment stuck VALIDATING | Portal transient issues | Re-run deploy with increased `retryDelay` / `maxRetries` or inspect portal dashboard. |

## Adjusting Retry Logic
Inside `jreleaser { deploy { maven { mavenCentral { ... }}}}`:
```kotlin
retryDelay.set(20)  // seconds between polls
maxRetries.set(15)  // total attempts
```

## Advanced: Separate Upload & Publish
Set `stage` to `UPLOAD` for initial bundle push:
```kotlin
stage.set(org.jreleaser.model.api.deploy.maven.MavenCentralMavenDeployer.Stage.UPLOAD)
```
Then publish later:
```bash
./gradlew jreleaserDeploy -PmavenCentralDeploymentId=DEPLOYMENT_ID -PmavenCentralStage=PUBLISH
```
(You can expose these as custom properties; JReleaser reads deploymentId & stage.)

## Migration Notes
- Removed manual `repositories { maven { ... } }` from per-module publishing; JReleaser now owns upload.
- Removed per-module `signing {}` blocks; JReleaser performs signing centrally.
- Manual scripts (`create-central-bundle.sh`) kept for emergency fallback.

## Next Enhancements
- Add GitHub release + changelog announcement via JReleaser (post 0.9.0 stabilization).
- Integrate SBOM & cataloging features (`jreleaser.catalog`) for security posture.
- Enable SLSA provenance once build pipeline stabilized.

## Quick Commands Cheat Sheet
```bash
# Dry run
./gradlew jreleaserDeploy --dry-run
# Full deploy
./gradlew jreleaserDeploy
# Show tasks
./gradlew tasks --all | grep jreleaser
# Clean previous release state
./gradlew jreleaserClean
```

## Verification Checklist
- [ ] JReleaser tasks visible (`jreleaserDeploy`, `jreleaserFullRelease`)
- [ ] Dry run produces artifacts under `build/jreleaser`
- [ ] Full deploy returns deployment id without errors
- [ ] Artifacts appear on Maven Central

---
Pre-1.0 stance: Bold refactor acceptable; JReleaser integration supersedes earlier manual curl approach.
