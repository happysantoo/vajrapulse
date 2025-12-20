# Release 0.9.9 Process

**Date**: 2025-12-14  
**Version**: 0.9.9  
**Status**: In Progress

---

## ✅ Completed Steps

1. ✅ **Version Updated**: `build.gradle.kts` already at 0.9.9
2. ✅ **JReleaser Config Updated**: `jreleaser.yml` already at 0.9.9
3. ✅ **Git Tag Created**: `v0.9.9` tag created locally
4. ✅ **Release Notes Created**: `RELEASE_0.9.9_NOTES.md` ready
5. ✅ **Build Successful**: All modules build successfully

---

## ⏳ Pending Steps

### 1. Push Git Tag

```bash
git push origin v0.9.9
```

**If authentication issues:**
- Use SSH: `git remote set-url origin git@github.com:YOUR_USERNAME/vajrapulse.git`
- Or configure credentials: `git config credential.helper store`

### 2. Create GitHub Release

**Option A: Using GitHub CLI**
```bash
gh release create v0.9.9 \
  --title "Release 0.9.9: Code Quality Improvements and Refactoring" \
  --notes-file RELEASE_0.9.9_NOTES.md
```

**Option B: Using GitHub Web Interface**
1. Go to: https://github.com/happysantoo/vajrapulse/releases/new
2. Select tag: `v0.9.9`
3. Title: `Release 0.9.9: Code Quality Improvements and Refactoring`
4. Description: Copy content from `RELEASE_0.9.9_NOTES.md`
5. Click "Publish release"

### 3. Publish to Maven Central

**Option A: Using JReleaser (if configured)**

First, check if JReleaser plugin is applied:
```bash
./gradlew tasks --all | grep jreleaser
```

If JReleaser tasks are available:
```bash
# Dry run first
./gradlew prepareRelease jreleaserDeploy --dry-run

# Actual publish
./gradlew prepareRelease jreleaserDeploy
```

**Option B: Using Bundle Script (Fallback)**

```bash
# Create bundle
./scripts/create-central-bundle.sh 0.9.9 /tmp/vajrapulse-0.9.9-central.zip

# Upload to Maven Central Portal
curl -u "$mavenCentralUsername:$mavenCentralPassword" \
  -F "bundle=@/tmp/vajrapulse-0.9.9-central.zip" \
  "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC"
```

**Required Properties** (in `~/.gradle/gradle.properties`):
```properties
mavenCentralUsername=your-username
mavenCentralPassword=your-password
signingKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...
signingPassword=your-gpg-passphrase
```

---

## 📋 Release Checklist

- [x] Version updated in build.gradle.kts (0.9.9)
- [x] Version updated in jreleaser.yml (0.9.9)
- [x] CHANGELOG.md updated
- [x] README.md updated
- [x] All tests pass
- [x] Build successful
- [x] Git tag created (v0.9.9)
- [ ] Git tag pushed to remote
- [ ] GitHub release created
- [ ] Maven Central published
- [ ] Release verified on Maven Central

---

## 🔍 Verification

After publishing, verify artifacts:

```bash
# Check BOM
curl -I https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-bom/0.9.9/vajrapulse-bom-0.9.9.pom

# Check core module
curl -I https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-core/0.9.9/vajrapulse-core-0.9.9.pom

# Check all modules
for module in bom api core exporter-console exporter-opentelemetry exporter-report worker; do
  curl -I "https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-${module}/0.9.9/vajrapulse-${module}-0.9.9.pom"
done
```

**Maven Central Search:**
https://search.maven.org/search?q=g:com.vajrapulse%20AND%20v:0.9.9

---

## 📝 Release Summary

**Version**: 0.9.9  
**Tag**: v0.9.9  
**Release Date**: 2025-12-14  
**Type**: Minor Release with Breaking Changes

**Key Improvements**:
- 23.5% code reduction in AdaptiveLoadPattern
- 3.4% code reduction in ExecutionEngine
- 100% test timeout coverage
- 0% test flakiness
- Polymorphism over type checking

---

**Status**: Ready for tag push, GitHub release, and Maven Central publishing
