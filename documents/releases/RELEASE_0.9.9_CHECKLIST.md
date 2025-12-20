# Release 0.9.9 Checklist

**Date**: 2025-12-12  
**Version**: 0.9.9  
**Branch**: `0.9.9` or `main`  
**Target**: `main`

## Pre-Release Validation

### Code Quality
- [x] All tests passing (`./gradlew test --rerun-tasks`)
- [x] Code coverage ≥90% (`./gradlew jacocoTestCoverageVerification`)
- [x] Static analysis passing (`./gradlew spotbugsMain`)
- [x] No deprecation warnings
- [x] JavaDoc compiles without warnings

### Version Updates
- [x] `build.gradle.kts` version updated to `0.9.9`
- [x] `jreleaser.yml` version updated to `0.9.9`
- [x] `CHANGELOG.md` updated with 0.9.9 release notes

### Documentation
- [x] `CHANGELOG.md` includes comprehensive release notes
- [x] Migration guide included in CHANGELOG
- [x] Release summary document created
- [x] Examples updated to use new APIs

### Code Changes
- [x] All examples migrated to builder pattern
- [x] Worker code updated to use builder pattern
- [x] Deprecated APIs maintained for backward compatibility
- [x] All deprecation warnings fixed

## Release Steps

### 1. Final Verification
- [ ] Run full build: `./gradlew clean build --rerun-tasks`
- [ ] Verify all tests pass: `./gradlew test --rerun-tasks`
- [ ] Verify coverage: `./gradlew jacocoTestCoverageVerification`
- [ ] Verify static analysis: `./gradlew spotbugsMain`
- [ ] Review CHANGELOG.md for accuracy

### 2. Commit and Push
- [ ] Commit all changes: `git add -A && git commit -m "Prepare for 0.9.9 release"`
- [ ] Push to branch: `git push origin 0.9.9`

### 3. Create Pull Request
- [ ] Create PR from `0.9.9` to `main`
- [ ] Review PR description
- [ ] Wait for CI/CD to pass
- [ ] Get code review approval
- [ ] Merge PR to `main`

### 4. Create Git Tag
- [ ] Checkout `main` branch: `git checkout main && git pull`
- [ ] Create tag: `git tag -a v0.9.9 -m "Release 0.9.9: AdaptiveLoadPattern Architectural Refactoring"`
- [ ] Push tag: `git push origin v0.9.9`

### 5. Create GitHub Release
- [ ] Go to GitHub Releases page
- [ ] Click "Draft a new release"
- [ ] Select tag: `v0.9.9`
- [ ] Title: `Release 0.9.9: AdaptiveLoadPattern Architectural Refactoring`
- [ ] Copy release notes from CHANGELOG.md
- [ ] Mark as "Latest release" (if appropriate)
- [ ] Publish release

### 6. Publish to Maven Central
- [ ] Verify JReleaser configuration
- [ ] Run JReleaser: `./gradlew jreleaserFullRelease`
- [ ] Verify artifacts uploaded to Maven Central
- [ ] Wait for Maven Central sync (may take a few hours)
- [ ] Verify artifacts available on Maven Central

### 7. Post-Release
- [ ] Update any external documentation
- [ ] Announce release (if applicable)
- [ ] Monitor for any issues
- [ ] Close release milestone (if using GitHub milestones)

## Release Notes Template

```markdown
# Release 0.9.9: AdaptiveLoadPattern Architectural Refactoring

## 🎯 Highlights

Major architectural refactoring of `AdaptiveLoadPattern` with significant improvements to code maintainability, testability, and extensibility.

## ✨ New Features

- **Builder Pattern**: Fluent API for configuring `AdaptiveLoadPattern`
- **Event Notifications**: `AdaptivePatternListener` interface for event-driven integrations
- **Enhanced MetricsProvider**: Added `getFailureCount()` method
- **Strategy Pattern**: Phase-specific logic extracted to strategy classes
- **Decision Policies**: Pluggable `RampDecisionPolicy` for custom decision logic

## 🔄 Changes

- **State Simplification**: Split large state record into focused records
- **Phase Machine**: Simplified from 4 phases to 3 phases
- **Configuration**: Consolidated into `AdaptiveConfig` record
- **Examples**: Updated to use new builder pattern

## 📚 Migration

All changes are backward compatible. Deprecated APIs remain available.

See CHANGELOG.md for detailed migration guide.

## 📦 Artifacts

Available on Maven Central:
- `com.vajrapulse:vajrapulse-api:0.9.9`
- `com.vajrapulse:vajrapulse-core:0.9.9`
- `com.vajrapulse:vajrapulse-worker:0.9.9`
- `com.vajrapulse:vajrapulse-bom:0.9.9`
```

## Verification Commands

```bash
# Full build
./gradlew clean build --rerun-tasks

# Tests
./gradlew test --rerun-tasks

# Coverage
./gradlew jacocoTestCoverageVerification

# Static analysis
./gradlew spotbugsMain

# Check version
grep "version = " build.gradle.kts
grep "version:" jreleaser.yml
```

## Notes

- All deprecated APIs are maintained for backward compatibility
- Migration to new APIs is optional but recommended
- Full test coverage maintained at ≥90%
- No breaking changes

---

**Status**: ✅ Ready for Release  
**Last Updated**: 2025-12-XX

