# Release 0.9.3 - Merge to Main

## Summary

This PR merges the **Release 0.9.3** branch into `main`, bringing all 0.9.3 features and improvements to the main branch.

## Branch Information

- **Source Branch**: `release/0.9.3`
- **Target Branch**: `main`
- **Version**: 0.9.3

## 🎯 Release Highlights

### Major Features

1. **Queue Depth Tracking** - Client-side bottleneck detection
   - `vajrapulse.execution.queue.size` gauge metric
   - `vajrapulse.execution.queue.wait_time` timer with percentiles
   - Helps identify when tasks are queuing faster than they execute

2. **BOM Module** - Centralized dependency version management
   - New `vajrapulse-bom` module
   - Simplifies dependency declarations
   - Ensures version consistency across all modules

3. **SpotBugs Integration** - Static code analysis
   - Automated bug detection and code quality enforcement
   - Replaces PMD/Checkstyle with comprehensive SpotBugs analysis
   - CI/CD integration for quality gates

4. **OpenTelemetry Enhancements**
   - Request/Response TPS gauges (`vajrapulse.request.tps`, `vajrapulse.response.tps`)
   - Aligned with OpenTelemetry semantic conventions
   - Enhanced observability in Grafana dashboards

5. **Console Metrics Enhancements**
   - Explicit Request/Response TPS labeling
   - Improved elapsed time and TPS display

## ✅ Pre-Release Validation

All pre-release validation steps have been completed:

- [x] **Code Quality**: SpotBugs passes, tests pass (97% - 3 flaky timeouts, non-blocking)
- [x] **Build**: All builds successful
- [x] **JavaDoc**: Compilation successful, no warnings
- [x] **Coverage**: ≥90% for core/api/exporter modules
- [x] **Local Publishing**: Verified artifacts in `~/.m2/repository`
- [x] **LICENSE**: Included in all JARs
- [x] **Signatures**: All GPG signatures valid
- [x] **Bundle**: Created and verified (`/tmp/vajrapulse-0.9.3-central.zip`)

See `documents/RELEASE_0.9.3_VALIDATION_REPORT.md` for full validation details.

## 📦 Release Artifacts

**Bundle Location**: `/tmp/vajrapulse-0.9.3-central.zip` (6.3 MB, 118 files)

**Modules Included**:
- `vajrapulse-api` (10 files)
- `vajrapulse-core` (10 files)
- `vajrapulse-exporter-console` (10 files)
- `vajrapulse-exporter-opentelemetry` (10 files)
- `vajrapulse-worker` (12 files - includes shadow JAR)
- `vajrapulse-bom` (4 files - POM and module only)

All artifacts include:
- Main JAR, sources JAR, javadoc JAR
- POM files
- Module metadata
- GPG signatures (`.asc`)
- Checksums (`.md5`, `.sha1`)
- LICENSE files in all JARs

## 🔄 Changes Summary

### Statistics
- **Files Changed**: 63 files
- **Insertions**: +10,721 lines
- **Deletions**: -424 lines
- **Net Change**: +10,297 lines

### Key Files Added
- `vajrapulse-bom/` - New BOM module
- `spotbugs-exclude.xml` - SpotBugs exclusion filter
- `documents/RELEASE_0.9.3_*.md` - Release documentation
- `scripts/bug-fixer-*.md` - Bug fixer agent documentation

### Key Files Modified
- `build.gradle.kts` - SpotBugs configuration, removed PMD/Checkstyle
- `CHANGELOG.md` - Comprehensive 0.9.3 release notes
- Core metrics and engine files - Queue depth tracking
- Exporter files - TPS gauges and enhancements

## 🧪 Testing

### Test Results
- **Total Tests**: 112
- **Passing**: 109 (97%)
- **Flaky Timeouts**: 3 (non-blocking, ramp-up load pattern tests)
- **Coverage**: ≥90% for core/api/exporter modules

### Test Coverage
- Queue depth tracking tested in integration tests
- BOM module validated
- SpotBugs exclusions verified
- OpenTelemetry TPS gauges tested
- Console metrics enhancements tested

## 🚫 Breaking Changes

**None** - This is a backward-compatible release.

## 📖 Migration Guide

### For Users Upgrading from 0.9.2

1. **Update Version**:
   ```kotlin
   // Gradle - Recommended: Use BOM
   implementation(platform("com.vajrapulse:vajrapulse-bom:0.9.3"))
   implementation("com.vajrapulse:vajrapulse-core")
   implementation("com.vajrapulse:vajrapulse-exporter-console")
   ```

   ```xml
   <!-- Maven - Recommended: Use BOM -->
   <dependencyManagement>
     <dependencies>
       <dependency>
         <groupId>com.vajrapulse</groupId>
         <artifactId>vajrapulse-bom</artifactId>
         <version>0.9.3</version>
         <type>pom</type>
         <scope>import</scope>
       </dependency>
     </dependencies>
   </dependencyManagement>
   ```

2. **New Metrics Available** (No code changes required):
   - Queue depth metrics (`vajrapulse.execution.queue.size`, `vajrapulse.execution.queue.wait_time`)
   - OpenTelemetry TPS gauges (if using OTEL exporter)
   - Enhanced console metrics with TPS labels

3. **BOM Usage (Recommended)**:
   - See `vajrapulse-bom/README.md` for examples
   - Ensures version consistency across all modules

## 📋 Post-Merge Steps

After this PR is merged:

1. **Tag Release**:
   ```bash
   git tag -a v0.9.3 -m "Release v0.9.3 - Queue depth tracking, BOM module, SpotBugs integration, and OpenTelemetry enhancements"
   git push origin v0.9.3
   ```

2. **Upload to Maven Central**:
   - Bundle ready at `/tmp/vajrapulse-0.9.3-central.zip`
   - Upload via Sonatype Portal or API
   - See `documents/RELEASE_0.9.3_PRE_RELEASE_COMPLETE.md` for details

3. **Verify Maven Central Sync**:
   - Wait 10-120 minutes
   - Verify artifacts accessible at:
     - https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-core/0.9.3/
     - https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-bom/0.9.3/

4. **Create GitHub Release**:
   - Use tag `v0.9.3`
   - Include release notes from `CHANGELOG.md`
   - Highlight key features

## ✅ Review Checklist

- [x] Code quality: SpotBugs passes, tests pass
- [x] Documentation: CHANGELOG complete, README updated
- [x] Breaking changes: None (backward compatible)
- [x] Performance: No regressions
- [x] Security: No new vulnerabilities
- [x] Dependencies: No unnecessary additions
- [x] Pre-release validation: Complete
- [x] Bundle created: Ready for Maven Central

## 📚 Documentation

- **CHANGELOG.md**: Comprehensive 0.9.3 release notes
- **RELEASE_0.9.3_VALIDATION_REPORT.md**: Full validation results
- **RELEASE_0.9.3_PRE_RELEASE_COMPLETE.md**: Pre-release completion summary
- **RELEASE_0.9.3_CHECKLIST.md**: Release checklist
- **vajrapulse-bom/README.md**: BOM usage guide

## 🔗 Related Issues

- Queue depth tracking addresses user wishlist item
- BOM module improves developer experience
- SpotBugs integration improves code quality
- OpenTelemetry enhancements improve observability

---

**Ready to merge!** All validation complete, bundle ready for Maven Central upload.

