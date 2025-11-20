# VajraPulse Release Process

**Last Updated**: 2025-01-XX  
**Status**: Automated with validation and safety checks

---

## Overview

The VajraPulse release process is automated through a release script that handles:
- Version validation
- Testing and coverage checks
- Building and artifact preparation
- Maven Central publishing (via JReleaser)
- Git tagging

---

## Quick Start

### Standard Release (Recommended)

```bash
# 1. Dry run to validate everything
./scripts/release.sh 0.9.4 --dry-run

# 2. If dry run passes, do the actual release
./scripts/release.sh 0.9.4 --publish
```

### Release with Custom Options

```bash
# Skip tests (use with caution)
./scripts/release.sh 0.9.4 --skip-tests --publish

# Prepare release without publishing
./scripts/release.sh 0.9.4

# Publish without creating bundle (if using JReleaser only)
./scripts/release.sh 0.9.4 --publish --skip-bundle
```

---

## Prerequisites

### 1. Required Tools
- Git
- Gradle (wrapper included)
- Java 21+

### 2. Gradle Properties

Create or update `~/.gradle/gradle.properties`:

```properties
# Maven Central credentials (from Sonatype)
mavenCentralUsername=your-username
mavenCentralPassword=your-password

# GPG signing
signingKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...
signingPassword=your-gpg-passphrase
```

### 3. Git Setup
- Working directory should be clean (or you'll be prompted)
- Version tag should not already exist

---

## Release Steps

The release script automates the following steps:

### 1. Prerequisites Check
- Validates Git is installed
- Checks working directory is clean
- Verifies version tag doesn't exist
- Validates Gradle properties (if publishing)

### 2. Version Update
- Updates `build.gradle.kts` with new version
- Updates `jreleaser.yml` with new version

### 3. Testing
- Runs all tests: `./gradlew clean test`
- Validates test coverage: `./gradlew jacocoTestReport jacocoTestCoverageVerification`

### 4. Build
- Builds all modules: `./gradlew clean build -x test`

### 5. Prepare Release
- Prepares release artifacts: `./gradlew prepareRelease`

### 6. Publish to Maven Local
- Publishes to local Maven repository: `./gradlew publishToMavenLocal`

### 7. Publish to Maven Central
- Creates bundle (if not skipped): `./scripts/create-central-bundle.sh`
- Publishes via JReleaser: `./gradlew jreleaserDeploy`

### 8. Create Git Tag
- Creates annotated tag: `git tag -a v0.9.4 -m "Release v0.9.4"`

---

## Manual Release Process

If you prefer to run steps manually:

### Step 1: Update Version

```bash
# Update build.gradle.kts
sed -i '' 's/version = ".*"/version = "0.9.4"/' build.gradle.kts

# Update jreleaser.yml
sed -i '' 's/version: .*/version: 0.9.4/' jreleaser.yml
```

### Step 2: Run Tests

```bash
./gradlew clean test
./gradlew jacocoTestReport jacocoTestCoverageVerification
```

### Step 3: Build

```bash
./gradlew clean build
```

### Step 4: Prepare Release

```bash
./gradlew prepareRelease
```

### Step 5: Publish to Maven Local

```bash
./gradlew publishToMavenLocal
```

### Step 6: Create Bundle (Optional)

```bash
./scripts/create-central-bundle.sh 0.9.4
```

### Step 7: Publish to Maven Central

**Option A: Using JReleaser (Recommended)**

```bash
# Dry run first
./gradlew jreleaserDeploy --dry-run

# Actual publish
./gradlew jreleaserDeploy
```

**Option B: Manual Bundle Upload**

```bash
# Upload bundle
curl -u "$mavenCentralUsername:$mavenCentralPassword" \
  -F "bundle=@/tmp/vajrapulse-0.9.4-central.zip" \
  "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC"
```

### Step 8: Create Git Tag

```bash
git tag -a v0.9.4 -m "Release v0.9.4"
git push origin v0.9.4
```

---

## Verification

### Check Maven Central

After publishing, verify artifacts are available:

```bash
# Check BOM
curl -I https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-bom/0.9.4/vajrapulse-bom-0.9.4.pom

# Check core module
curl -I https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-core/0.9.4/vajrapulse-core-0.9.4.pom

# Check all modules
for module in bom api core exporter-console exporter-opentelemetry worker; do
  echo "Checking vajrapulse-${module}..."
  curl -I "https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-${module}/0.9.4/vajrapulse-${module}-0.9.4.pom"
done
```

### Search Maven Central

Visit: https://search.maven.org/search?q=g:com.vajrapulse

---

## Post-Release Checklist

- [ ] Verify all artifacts are on Maven Central
- [ ] Push Git tag: `git push origin v0.9.4`
- [ ] Create GitHub release (if enabled)
- [ ] Update README.md with new version
- [ ] Update CHANGELOG.md
- [ ] Announce release (if applicable)
- [ ] Bump version to next SNAPSHOT for development

---

## Troubleshooting

### Version Already Tagged

```bash
# Check existing tags
git tag -l "v*"

# Delete local tag if needed (be careful!)
git tag -d v0.9.4

# Delete remote tag if needed (be very careful!)
git push origin --delete v0.9.4
```

### Maven Central Upload Fails

1. Check credentials in `~/.gradle/gradle.properties`
2. Verify GPG key is exported to key servers
3. Check JReleaser logs: `build/jreleaser/`
4. Try manual bundle upload as fallback

### Test Failures

```bash
# Run tests with more output
./gradlew test --info

# Run specific test
./gradlew :vajrapulse-core:test --tests "*SpecificTest*"
```

### Coverage Failures

```bash
# Generate coverage report
./gradlew jacocoTestReport

# View report
open build/reports/jacoco/test/html/index.html
```

---

## Release Script Options

| Option | Description |
|--------|-------------|
| `--dry-run` | Validate release without making changes |
| `--skip-tests` | Skip test execution (use with caution) |
| `--publish` | Enable Maven Central publishing |
| `--skip-bundle` | Skip bundle creation (use JReleaser only) |

---

## Version Numbering

Follow semantic versioning:
- **Major** (1.0.0): Breaking changes
- **Minor** (0.10.0): New features, backward compatible
- **Patch** (0.9.4): Bug fixes, backward compatible

Pre-1.0: Breaking changes allowed (0.x versions)

---

## Automation Improvements

### Current Features
- ✅ Automated version updates
- ✅ Test and coverage validation
- ✅ Build and artifact preparation
- ✅ Maven Central publishing via JReleaser
- ✅ Git tagging
- ✅ Dry-run mode for validation

### Future Enhancements
- [ ] CI/CD integration (GitHub Actions)
- [ ] Automatic CHANGELOG generation
- [ ] Automatic GitHub release creation
- [ ] Version bump to next SNAPSHOT
- [ ] Release notes generation

---

## Related Documents

- `documents/integrations/MAVEN_CENTRAL_PUBLISHING.md` - Detailed publishing guide
- `documents/integrations/JRELEASER_INTEGRATION.md` - JReleaser configuration
- `scripts/create-central-bundle.sh` - Bundle creation script
- `jreleaser.yml` - JReleaser configuration

---

*This release process ensures consistent, reliable releases with proper validation and safety checks.*

