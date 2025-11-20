# VajraPulse 0.9.2 Release Checklist

## Pre-Release Verification

### 1. Version Confirmation
- [ ] Version updated to `0.9.2` in `build.gradle.kts`
- [ ] All modules using correct version

### 2. Build & Test
```bash
./gradlew clean build
./gradlew test
```
- [ ] All builds successful
- [ ] All tests passing
- [ ] No compiler warnings (except javadoc)

### 3. Local Publishing Test
```bash
./gradlew clean publishToMavenLocal
```

Verify artifacts in `~/.m2/repository/com/vajrapulse/`:
- [ ] All 5 modules published (api, core, exporter-console, exporter-opentelemetry, worker)
- [ ] Each module has 20 files (5 artifacts × 4 files: jar, .asc, .md5, .sha1)
- [ ] Signature files present for: `.jar`, `-sources.jar`, `-javadoc.jar`, `.pom`, `.module`

### 4. Verify LICENSE Inclusion
```bash
unzip -l vajrapulse-core/build/libs/vajrapulse-core-0.9.2.jar | grep LICENSE
unzip -l vajrapulse-core/build/libs/vajrapulse-core-0.9.2-sources.jar | grep LICENSE
unzip -l vajrapulse-core/build/libs/vajrapulse-core-0.9.2-javadoc.jar | grep LICENSE
```
- [ ] `META-INF/LICENSE` present in main JAR
- [ ] `META-INF/LICENSE` present in sources JAR
- [ ] `META-INF/LICENSE` present in javadoc JAR

### 5. Verify Signatures
```bash
gpg --verify ~/.m2/repository/com/vajrapulse/vajrapulse-core/0.9.2/vajrapulse-core-0.9.2.jar.asc \
             ~/.m2/repository/com/vajrapulse/vajrapulse-core/0.9.2/vajrapulse-core-0.9.2.jar
```
- [ ] All signatures valid
- [ ] "Good signature" message displayed

## Release Process

### 6. Create Bundle
```bash
./scripts/create-central-bundle.sh 0.9.2
```
- [ ] Bundle created at `/tmp/vajrapulse-0.9.2-central.zip`
- [ ] No errors during bundle creation

### 7. Upload to Maven Central
```bash
# Set token (if not in gradle.properties)
export MAVEN_CENTRAL_TOKEN="your-token-here"

curl --request POST \
  --verbose \
  --header "Authorization: Bearer $MAVEN_CENTRAL_TOKEN" \
  --form bundle=@/tmp/vajrapulse-0.9.2-central.zip \
  "https://central.sonatype.com/api/v1/publisher/upload?name=vajrapulse-0.9.2&publishingType=AUTOMATIC"
```
- [ ] Upload successful (HTTP 201)
- [ ] Deployment ID received
- [ ] Save deployment ID: `__________________`

### 8. Git Tagging
```bash
git tag -a v0.9.2 -m "Release v0.9.2 - Maven Central compliance fixes (signatures + LICENSE)"
git push origin v0.9.2
```
- [ ] Tag created
- [ ] Tag pushed to GitHub

### 9. Update Documentation
- [ ] Update `README.md` with version 0.9.2
- [ ] Update Maven Central badge (if needed)
- [ ] Update installation examples

### 10. Verify Maven Central Sync
Wait 10-120 minutes, then check:
```bash
curl -I https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-core/0.9.2/vajrapulse-core-0.9.2.pom
```
- [ ] HTTP 200 response
- [ ] All artifacts accessible
- [ ] Signatures visible on Maven Central

## Post-Release

### 11. GitHub Release
- [ ] Create GitHub release from tag v0.9.2
- [ ] Include release notes from `RELEASE_0.9.2_CHANGES.md`
- [ ] Attach shadow JAR (optional)

### 12. Announcements
- [ ] Update project README with latest version
- [ ] Notify users (if applicable)
- [ ] Update examples to use 0.9.2

### 13. Prepare for Next Version
```bash
# Update version in build.gradle.kts to next snapshot
# Example: 0.9.3-SNAPSHOT or 1.0.0-SNAPSHOT
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

### Sync Delays
- Normal sync time: 10-120 minutes
- Check status on Maven Central Portal
- Verify deployment ID in portal
- Contact Sonatype support if > 2 hours

## Required Files per Module

Each module must have (20 files total):

**Artifacts (5):**
1. `vajrapulse-MODULE-0.9.2.jar`
2. `vajrapulse-MODULE-0.9.2-sources.jar`
3. `vajrapulse-MODULE-0.9.2-javadoc.jar`
4. `vajrapulse-MODULE-0.9.2.pom`
5. `vajrapulse-MODULE-0.9.2.module`

**Signatures (5):**
1. `vajrapulse-MODULE-0.9.2.jar.asc`
2. `vajrapulse-MODULE-0.9.2-sources.jar.asc`
3. `vajrapulse-MODULE-0.9.2-javadoc.jar.asc`
4. `vajrapulse-MODULE-0.9.2.pom.asc`
5. `vajrapulse-MODULE-0.9.2.module.asc`

**MD5 Checksums (5):**
1-5. Same as artifacts with `.md5` suffix

**SHA1 Checksums (5):**
1-5. Same as artifacts with `.sha1` suffix

## Sign-Off

**Released by:** ___________________  
**Date:** ___________________  
**Deployment ID:** ___________________  
**Maven Central URL:** https://central.sonatype.com/artifact/com.vajrapulse/vajrapulse-core/0.9.2
