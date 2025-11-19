# VajraPulse 0.9.3 Release Checklist

## Pre-Release Verification

### 1. Version Confirmation
- [x] Version updated to `0.9.3` in `build.gradle.kts`
- [x] All modules using correct version
- [x] CHANGELOG.md updated with comprehensive 0.9.3 release notes

### 2. Code Quality Checks
```bash
./gradlew clean build
./gradlew test
./gradlew spotbugsMain
./gradlew javadoc
```
- [x] All builds successful
- [x] All tests passing
- [x] SpotBugs analysis passes (all violations resolved or excluded)
- [x] No JavaDoc warnings (except examples which have relaxed rules)
- [x] No compiler warnings (except javadoc)

### 3. Static Analysis
- [x] SpotBugs configured and passing for all modules
- [x] Exclusion filter (`spotbugs-exclude.xml`) properly configured
- [x] All legitimate issues fixed
- [x] Acceptable patterns documented in exclusions

### 4. Feature Verification
- [x] Queue depth tracking implemented and tested
  - [x] `vajrapulse.execution.queue.size` gauge metric working
  - [x] `vajrapulse.execution.queue.wait_time` timer working
  - [x] Queue metrics appear in `AggregatedMetrics`
  - [x] Queue metrics exported via console exporter
  - [x] Queue metrics exported via OpenTelemetry exporter
- [x] BOM module created and published
  - [x] BOM includes all VajraPulse modules
  - [x] BOM usage documented in README
- [x] OpenTelemetry TPS gauges working
  - [x] Request TPS gauges (`vajrapulse.request.tps`)
  - [x] Response TPS gauges (`vajrapulse.response.tps`)
  - [x] TPS accessors exposed for testing
- [x] Console metrics enhancements working
  - [x] Request/Response TPS labels displayed
  - [x] Elapsed time and TPS shown in console

### 5. Documentation
- [x] CHANGELOG.md updated with all 0.9.3 features
- [x] README.md updated (if needed)
- [x] BOM usage examples in README
- [x] Release documentation complete

### 6. Local Publishing Test
```bash
./gradlew clean publishToMavenLocal
```

Verify artifacts in `~/.m2/repository/com/vajrapulse/`:
- [ ] All 6 modules published (api, core, exporter-console, exporter-opentelemetry, worker, bom)
- [ ] Each module has 20 files (5 artifacts × 4 files: jar, .asc, .md5, .sha1)
- [ ] Signature files present for: `.jar`, `-sources.jar`, `-javadoc.jar`, `.pom`, `.module`
- [ ] BOM module published correctly

### 7. Verify LICENSE Inclusion
```bash
unzip -l vajrapulse-core/build/libs/vajrapulse-core-0.9.3.jar | grep LICENSE
unzip -l vajrapulse-core/build/libs/vajrapulse-core-0.9.3-sources.jar | grep LICENSE
unzip -l vajrapulse-core/build/libs/vajrapulse-core-0.9.3-javadoc.jar | grep LICENSE
```
- [ ] `META-INF/LICENSE` present in main JAR
- [ ] `META-INF/LICENSE` present in sources JAR
- [ ] `META-INF/LICENSE` present in javadoc JAR

### 8. Verify Signatures
```bash
gpg --verify ~/.m2/repository/com/vajrapulse/vajrapulse-core/0.9.3/vajrapulse-core-0.9.3.jar.asc \
             ~/.m2/repository/com/vajrapulse/vajrapulse-core/0.9.3/vajrapulse-core-0.9.3.jar
```
- [ ] All signatures valid
- [ ] "Good signature" message displayed

## Release Process

### 9. Merge Feature Branch to Release Branch
```bash
# Ensure we're on release/0.9.3 branch
git checkout release/0.9.3

# Merge feature branch
git merge feature/queue-depth-tracking

# Resolve any conflicts if needed
# Test after merge
./gradlew clean build test
```

- [ ] Feature branch merged to `release/0.9.3`
- [ ] All conflicts resolved
- [ ] Build and tests pass after merge

### 10. Create Bundle
```bash
./scripts/create-central-bundle.sh 0.9.3
```
- [ ] Bundle created at `/tmp/vajrapulse-0.9.3-central.zip`
- [ ] No errors during bundle creation
- [ ] BOM module included in bundle

### 11. Upload to Maven Central
```bash
# Set token (if not in gradle.properties)
export MAVEN_CENTRAL_TOKEN="your-token-here"

curl --request POST \
  --verbose \
  --header "Authorization: Bearer $MAVEN_CENTRAL_TOKEN" \
  --form bundle=@/tmp/vajrapulse-0.9.3-central.zip \
  "https://central.sonatype.com/api/v1/publisher/upload?name=vajrapulse-0.9.3&publishingType=AUTOMATIC"
```
- [ ] Upload successful (HTTP 201)
- [ ] Deployment ID received
- [ ] Save deployment ID: `__________________`

### 12. Git Tagging
```bash
git tag -a v0.9.3 -m "Release v0.9.3 - Queue depth tracking, BOM module, SpotBugs integration, and OpenTelemetry enhancements"
git push origin v0.9.3
```
- [ ] Tag created
- [ ] Tag pushed to GitHub

### 13. Merge Release Branch to Main
```bash
# Switch to main branch
git checkout main

# Merge release branch
git merge release/0.9.3

# Push to main
git push origin main
```
- [ ] Release branch merged to `main`
- [ ] All conflicts resolved
- [ ] Main branch updated

### 14. Update Documentation
- [ ] Update `README.md` with version 0.9.3 (if needed)
- [ ] Update Maven Central badge (if needed)
- [ ] Update installation examples
- [ ] Verify CHANGELOG.md is accurate

### 15. Verify Maven Central Sync
Wait 10-120 minutes, then check:
```bash
curl -I https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-core/0.9.3/vajrapulse-core-0.9.3.pom
curl -I https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-bom/0.9.3/vajrapulse-bom-0.9.3.pom
```
- [ ] HTTP 200 response for core module
- [ ] HTTP 200 response for BOM module
- [ ] All artifacts accessible
- [ ] Signatures visible on Maven Central

## Post-Release

### 16. GitHub Release
- [ ] Create GitHub release from tag v0.9.3
- [ ] Include release notes from `CHANGELOG.md`
- [ ] Highlight key features:
  - Queue depth tracking for bottleneck detection
  - BOM module for simplified dependency management
  - SpotBugs static analysis integration
  - OpenTelemetry TPS gauges
- [ ] Attach shadow JAR (optional)

### 17. Announcements
- [ ] Update project README with latest version
- [ ] Notify users (if applicable)
- [ ] Update examples to use 0.9.3
- [ ] Update test samples to use 0.9.3

### 18. Prepare for Next Version
```bash
# Update version in build.gradle.kts to next snapshot
# Example: 0.9.4-SNAPSHOT or 1.0.0-SNAPSHOT
```
- [ ] Version bumped for next development cycle
- [ ] Branch created for next phase (if needed)

## Troubleshooting

### Missing Signatures
If Maven Central shows "Missing signature" errors:
- Verify `signing` plugin is applied
- Check `signingKey` and `signingPassword` in `gradle.properties`
- Rebuild with `./gradlew clean publishToMavenLocal`
- Verify `.asc` files exist in `~/.m2/repository`

### Failed Bundle Upload
- Check Maven Central token validity
- Verify bundle size < 500MB
- Ensure all required files present
- Check bundle script output for errors
- Verify BOM module is included

### Sync Delays
- Normal sync time: 10-120 minutes
- Check status on Maven Central Portal
- Verify deployment ID in portal
- Contact Sonatype support if > 2 hours

## Required Files per Module

Each module must have (20 files total):

**Artifacts (5):**
1. `vajrapulse-MODULE-0.9.3.jar`
2. `vajrapulse-MODULE-0.9.3-sources.jar`
3. `vajrapulse-MODULE-0.9.3-javadoc.jar`
4. `vajrapulse-MODULE-0.9.3.pom`
5. `vajrapulse-MODULE-0.9.3.module`

**Signatures (5):**
1. `vajrapulse-MODULE-0.9.3.jar.asc`
2. `vajrapulse-MODULE-0.9.3-sources.jar.asc`
3. `vajrapulse-MODULE-0.9.3-javadoc.jar.asc`
4. `vajrapulse-MODULE-0.9.3.pom.asc`
5. `vajrapulse-MODULE-0.9.3.module.asc`

**MD5 Checksums (5):**
1-5. Same as artifacts with `.md5` suffix

**SHA1 Checksums (5):**
1-5. Same as artifacts with `.sha1` suffix

**BOM Module:**
- `vajrapulse-bom-0.9.3.pom`
- `vajrapulse-bom-0.9.3.pom.asc`
- `vajrapulse-bom-0.9.3.pom.md5`
- `vajrapulse-bom-0.9.3.pom.sha1`

## Sign-Off

**Released by:** ___________________  
**Date:** ___________________  
**Deployment ID:** ___________________  
**Maven Central URL:** https://central.sonatype.com/artifact/com.vajrapulse/vajrapulse-core/0.9.3  
**BOM URL:** https://central.sonatype.com/artifact/com.vajrapulse/vajrapulse-bom/0.9.3

## Key Features in 0.9.3

1. **Queue Depth Tracking** - Client-side bottleneck detection
2. **BOM Module** - Centralized dependency version management
3. **SpotBugs Integration** - Static code analysis for quality assurance
4. **OpenTelemetry TPS Gauges** - Real-time throughput visibility
5. **Console Metrics Enhancements** - Improved observability
6. **Release Process Improvements** - Automated release scripts

