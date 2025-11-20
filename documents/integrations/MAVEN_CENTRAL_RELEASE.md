## Maven Central Release 0.9.0

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


### Status
Processing (status endpoint intermittently returns 500). Publication should finalize automatically; verify availability after sync:
```bash
curl -I https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-core/0.9.0/vajrapulse-core-0.9.0.pom
```

### Lessons / Improvements
- Prefer plugin-driven publishing (nmcp) to avoid manual bundle mistakes.
- When manual: always include `*.module` and signatures.
- Add a helper script to assemble bundle deterministically.
 - Adopt JReleaser plugin (integrated) to automate portal bundle creation & retries (supersedes manual curl approach).

### Next Actions
| Action | Owner | Priority |
|--------|-------|----------|
| Verify artifacts appear on Maven Central (HTTP HEAD) | Maintainer | High |
| Re-enable nmcp plugin (review version & config) | Maintainer | Medium |
| Automate bundle creation script | Maintainer | Medium |
| Prepare 0.9.1 with any fixes post-publish | Maintainer | Low |
| Migrate fully to JReleaser for future releases (remove legacy notes) | Maintainer | High |

### Verification Checklist
- [x] All `*.module` files included
- [x] All artifacts signed (`*.asc`)
- [x] Sources + Javadoc present
- [x] POM contains license, SCM, developer data
- [x] Deployment ID captured

### Commands Reference
```bash
# Local publish
./gradlew publishToMavenLocal

# Build bundle (fixed)
cd ~/.m2/repository/com/vajrapulse
zip -r /tmp/vajrapulse-0.9.0-bundle-fixed.zip \
  vajrapulse-api/0.9.0/vajrapulse-api-0.9.0.* \
  vajrapulse-core/0.9.0/vajrapulse-core-0.9.0.* \
  vajrapulse-exporter-console/0.9.0/vajrapulse-exporter-console-0.9.0.* \
  vajrapulse-exporter-opentelemetry/0.9.0/vajrapulse-exporter-opentelemetry-0.9.0.* \
  vajrapulse-worker/0.9.0/vajrapulse-worker-0.9.0.*

# Upload
curl -u "${MAVEN_CENTRAL_USERNAME}:${MAVEN_CENTRAL_PASSWORD}" \
  -F "bundle=@/tmp/vajrapulse-0.9.0-bundle-fixed.zip" \
  "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC"

# Check one artifact after sync
curl -I https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-api/0.9.0/vajrapulse-api-0.9.0.pom
```
# Maven Central Release Process

## Prerequisites

### 1. OSSRH Account & Namespace Approval

- ✅ JIRA account created at https://issues.sonatype.org
- ✅ New Project ticket submitted for `com.vajrapulse` namespace
- ✅ Domain ownership verification completed (DNS TXT or GitHub repo)
- ✅ Ticket approved by Sonatype team
- ✅ Credentials ready (JIRA username/password or user token)

### 2. GPG Signing Key

Generate if you don't have one:

```bash
# Generate key (use same email as Git commits)
gpg --gen-key

# List keys to find your key ID
gpg --list-secret-keys --keyid-format=long

# Example output:
# sec   rsa3072/ABCD1234EFGH5678 2025-11-16
#       ^^^^^^^^^^^^^^^^
#       This is your KEY_ID

# Export public key to key servers
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
gpg --keyserver pgp.mit.edu --send-keys YOUR_KEY_ID

# Export private key for Gradle (ASCII-armored, single line)
gpg --armor --export-secret-keys YOUR_KEY_ID | grep -v "^-" | tr -d '\n' > private-key.txt
```

### 3. Environment Variables

Set these before publishing:

```bash
# GPG Signing
export SIGNING_KEY=$(cat private-key.txt)
export SIGNING_PASSWORD="your-gpg-passphrase"

# OSSRH Credentials (from JIRA or User Token)
export OSSRH_USERNAME="your-jira-username"
export OSSRH_PASSWORD="your-jira-password"
```

**Alternative: Use Gradle properties**

Create `~/.gradle/gradle.properties`:

```properties
signing.key=<paste ASCII-armored key here>
signing.password=your-gpg-passphrase
ossrhUsername=your-jira-username
ossrhPassword=your-jira-password
```

---

## Release Process

### Step 1: Prepare Release

Ensure you're on the correct branch and version:

```bash
# Checkout main/release branch
git checkout main
git pull origin main

# Verify version in build.gradle.kts
grep 'version = ' build.gradle.kts
# Should show: version = "0.9.0" (no -SNAPSHOT)

# Verify all tests pass
./gradlew clean test

# Verify coverage meets threshold
./gradlew jacocoTestReport
# Check build/reports/jacoco/test/html/index.html
```

### Step 2: Build and Publish to OSSRH Staging

```bash
# Set environment variables
export SIGNING_KEY=$(cat private-key.txt)
export SIGNING_PASSWORD="your-gpg-passphrase"
export OSSRH_USERNAME="your-jira-username"
export OSSRH_PASSWORD="your-jira-password"

# Build and publish all modules
./gradlew clean publish

# Expected output:
# > Task :vajrapulse-api:publishMavenPublicationToOssrhRepository
# > Task :vajrapulse-core:publishMavenPublicationToOssrhRepository
# > Task :vajrapulse-exporter-console:publishMavenPublicationToOssrhRepository
# > Task :vajrapulse-exporter-opentelemetry:publishMavenPublicationToOssrhRepository
# > Task :vajrapulse-worker:publishMavenPublicationToOssrhRepository
```

### Step 3: Verify Staging Repository

1. **Log into OSSRH Nexus:**
   - URL: https://s01.oss.sonatype.org/
   - Credentials: Same as OSSRH_USERNAME/PASSWORD

2. **Navigate to Staging Repositories:**
   - Click **Staging Repositories** in left sidebar
   - Find your repository: `comvajrapulse-XXXX` (auto-generated ID)

3. **Verify Artifacts:**
   - Click on the staging repository
   - Browse to `com/vajrapulse/`
   - Check all 5 modules are present:
     - `vajrapulse-api/0.9.0/`
     - `vajrapulse-core/0.9.0/`
     - `vajrapulse-exporter-console/0.9.0/`
     - `vajrapulse-exporter-opentelemetry/0.9.0/`
     - `vajrapulse-worker/0.9.0/`

4. **Verify Each Artifact Contains:**
   - `.jar` - Main artifact
   - `-sources.jar` - Source code
   - `-javadoc.jar` - JavaDoc documentation
   - `.pom` - Maven POM file
   - `.asc` files - GPG signatures for each above file
   - `.md5` and `.sha1` - Checksums

### Step 4: Close Staging Repository

1. **Select Repository:**
   - Check the box next to `comvajrapulse-XXXX`

2. **Click "Close" Button:**
   - Nexus runs validation rules:
     - ✅ Valid POM structure
     - ✅ GPG signatures present and valid
     - ✅ Sources and JavaDoc JARs included
     - ✅ No snapshot dependencies

3. **Wait for Validation:**
   - Check **Activity** tab for progress
   - Validation usually takes 1-5 minutes
   - If errors occur, **Drop** the repository and fix issues

### Step 5: Release to Maven Central

1. **Select Closed Repository:**
   - Ensure status shows **closed** (not open)

2. **Click "Release" Button:**
   - Confirms artifact promotion to Maven Central
   - Repository will be automatically dropped after release

3. **Confirmation:**
   - Repository disappears from staging repositories list
   - Artifacts are now queued for Maven Central sync

### Step 6: Verify Maven Central Sync

**Sync time: 10 minutes to 2 hours (usually ~30 minutes)**

1. **Check Maven Central Search:**
   - URL: https://search.maven.org/
   - Search: `g:com.vajrapulse`
   - Verify all 5 artifacts appear with version 0.9.0

2. **Direct Repository Check:**
   - URL: https://repo1.maven.org/maven2/com/vajrapulse/
   - Browse to each module and verify files

3. **Test Dependency Resolution:**
   ```bash
   # Create test project
   mkdir test-vajrapulse && cd test-vajrapulse
   gradle init --type java-application
   
   # Add dependency to build.gradle
   echo 'dependencies { implementation "com.vajrapulse:vajrapulse-core:0.9.0" }' >> build.gradle
   
   # Resolve dependencies
   gradle dependencies
   ```

---

## Post-Release Tasks

### 1. Tag Release on GitHub

```bash
# Create annotated tag
git tag -a v0.9.0 -m "VajraPulse 0.9.0 Release"

# Push tag to GitHub
git push origin v0.9.0
```

**✅ Already completed** (tag pushed earlier)

### 2. Create GitHub Release

1. Go to https://github.com/happysantoo/vajrapulse/releases
2. Click **Draft a new release**
3. Select tag: `v0.9.0`
4. Title: `VajraPulse 0.9.0`
5. Description: Copy content from CHANGELOG.md
6. Attach artifacts (optional):
   - `vajrapulse-worker/build/libs/vajrapulse-worker-0.9.0-all.jar`
7. Click **Publish release**

### 3. Update Documentation

```bash
# Update README.md with Maven Central badge
# Update installation instructions to reference published version
# Remove -SNAPSHOT notes

# Commit changes
git add README.md
git commit -m "docs: update README with Maven Central 0.9.0 release"
git push origin main
```

### 4. Prepare Next Development Version

```bash
# Bump version to next SNAPSHOT
# In build.gradle.kts: version = "0.10.0-SNAPSHOT"

git add build.gradle.kts
git commit -m "chore: bump version to 0.10.0-SNAPSHOT"
git push origin main
```

### 5. Announce Release

- Update project website (if applicable)
- Post to social media / forums
- Notify early adopters
- Update dependency examples

---

## Troubleshooting

### Issue: GPG Signature Validation Failed

**Symptoms:**
- Nexus Close operation fails with "No public key" error

**Solution:**
```bash
# Re-upload public key to multiple key servers
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
gpg --keyserver pgp.mit.edu --send-keys YOUR_KEY_ID

# Wait 10-15 minutes for propagation
# Drop failed staging repository
# Re-run ./gradlew publish
```

### Issue: Missing Sources or JavaDoc JARs

**Symptoms:**
- Nexus validation fails: "no sources jar found"

**Solution:**
```bash
# Verify build.gradle.kts has java plugin configured
# Check publishing block includes:
artifact(tasks.named("sourcesJar"))
artifact(tasks.named("javadocJar"))

# Clean and republish
./gradlew clean publish
```

### Issue: Invalid POM Structure

**Symptoms:**
- Nexus validation error: "POM missing required metadata"

**Solution:**
- Check POM has: name, description, url, licenses, developers, scm
- Verify build.gradle.kts publishing.pom {} block
- Ensure all required fields are populated

### Issue: Snapshot Dependency Found

**Symptoms:**
- "Repository does not allow snapshot artifacts"

**Solution:**
```bash
# Check all dependencies in build.gradle.kts
grep -r "SNAPSHOT" */build.gradle.kts

# Remove any -SNAPSHOT versions
# Update to stable releases only
```

### Issue: Credentials Rejected

**Symptoms:**
- "401 Unauthorized" during publish

**Solution:**
```bash
# Verify credentials with direct login
curl -u $OSSRH_USERNAME:$OSSRH_PASSWORD https://s01.oss.sonatype.org/service/local/status

# If fails, regenerate User Token:
# 1. Login to https://s01.oss.sonatype.org/
# 2. Profile → User Token
# 3. Use generated username/password
```

---

## Automated Release (Future Enhancement)

### GitHub Actions Workflow

Create `.github/workflows/publish.yml`:

```yaml
name: Publish to Maven Central

on:
  push:
    tags:
      - 'v*'

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Publish to Maven Central
        env:
          SIGNING_KEY: ${{ secrets.SIGNING_KEY }}
          SIGNING_PASSWORD: ${{ secrets.SIGNING_PASSWORD }}
          OSSRH_USERNAME: ${{ secrets.OSSRH_USERNAME }}
          OSSRH_PASSWORD: ${{ secrets.OSSRH_PASSWORD }}
        run: ./gradlew publish
```

**Setup GitHub Secrets:**
1. Go to repository Settings → Secrets and variables → Actions
2. Add secrets:
   - `SIGNING_KEY`
   - `SIGNING_PASSWORD`
   - `OSSRH_USERNAME`
   - `OSSRH_PASSWORD`

---

## Quick Reference Commands

```bash
# Generate GPG key
gpg --gen-key

# Export public key to key servers
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID

# Export private key for Gradle
gpg --armor --export-secret-keys YOUR_KEY_ID | grep -v "^-" | tr -d '\n' > private-key.txt

# Set environment variables
export SIGNING_KEY=$(cat private-key.txt)
export SIGNING_PASSWORD="your-passphrase"
export OSSRH_USERNAME="your-username"
export OSSRH_PASSWORD="your-password"

# Publish to staging
./gradlew clean publish

# Verify and release via Nexus UI
# https://s01.oss.sonatype.org/

# Verify Maven Central sync
# https://search.maven.org/ (search: g:com.vajrapulse)
```

---

## Timeline

| Step | Duration | Notes |
|------|----------|-------|
| Build & Publish | 2-5 minutes | Depends on project size |
| Staging Validation | 1-5 minutes | Automatic Nexus checks |
| Release to Central | Instant | Manual click in Nexus UI |
| Maven Central Sync | 10-120 minutes | Usually ~30 minutes |
| Search Index Update | 2-4 hours | Maven Central search UI |

**Total time from publish to usable: ~1-2 hours**

---

## Checklist

Before publishing:
- [ ] Version is non-SNAPSHOT in build.gradle.kts
- [ ] All tests pass (`./gradlew test`)
- [ ] Coverage ≥90% (`./gradlew jacocoTestReport`)
- [ ] CHANGELOG.md updated with release notes
- [ ] GPG key generated and uploaded to key servers
- [ ] OSSRH namespace approved
- [ ] Environment variables set (SIGNING_*, OSSRH_*)

After publishing:
- [ ] Staging repository closed successfully
- [ ] Staging repository released to Maven Central
- [ ] Artifacts visible on https://search.maven.org/
- [ ] GitHub release created with tag v0.9.0
- [ ] README.md updated with Maven Central links
- [ ] Version bumped to next SNAPSHOT (0.10.0-SNAPSHOT)
- [ ] Release announced

---

**Status: Ready for 0.9.0 release once OSSRH namespace is approved**
