# VajraPulse 1.0.0 Release Checklist

## Pre-Release Verification

### Code Quality
- [x] All tests pass: `./gradlew test --rerun-tasks`
- [x] Coverage ≥90%: `./gradlew jacocoTestCoverageVerification`
- [x] SpotBugs clean: `./gradlew spotbugsMain`
- [x] No compiler warnings
- [x] Examples compile: `./gradlew compileExamples`

### API Stability
- [x] API freeze document reviewed (API_FREEZE_0.9.11.md)
- [x] JavaDoc coverage enforced for vajrapulse-api module (`-Xdoclint:all`)
- [x] No breaking changes from 0.9.x (documented in MIGRATION_0.9_TO_1.0.md)
- [x] Deprecations documented with replacements (`Task` → `TaskLifecycle`, removal in 1.1.0)

### Documentation
- [x] README updated with 1.0.0 features
- [x] CHANGELOG updated with 1.0.0 section
- [x] documents/guides/QUICK_START.md validated (<2 min)
- [x] Migration guide complete (MIGRATION_0.9_TO_1.0.md)
- [x] Versioning strategy documented (VERSIONING.md)

### Performance
- [x] Benchmark infrastructure in place (`./gradlew :benchmarks:jmh`)
- [x] Benchmark comparison script implemented (`scripts/compare-benchmarks.sh`)
- [ ] Baseline numbers populated in PERFORMANCE_BASELINE.md (run benchmarks post-release)

### Security
- [x] OWASP dependency check configured (`.github/workflows/security.yml`)
- [x] Dependabot configured (`.github/dependabot.yml`)
- [x] Security guide available (SECURITY.md)

### CI/CD
- [x] CI pipeline committed (.github/workflows/ci.yml)
- [x] Benchmark workflow committed (.github/workflows/benchmarks.yml)
- [x] Security/OWASP workflow committed (.github/workflows/security.yml)
- [x] Quality gates enforced (coverage, SpotBugs)

### Release Process
- [x] Version set to 1.0.0 in build.gradle.kts
- [x] Version set to 1.0.0 in jreleaser.yml
- [ ] Git tag created: v1.0.0
- [ ] GitHub release created with notes
- [ ] Maven Central publication successful
- [ ] Artifacts available on Maven Central

## Post-Release
- [ ] Announce release (GitHub, social media)
- [ ] Update version to 1.0.1-SNAPSHOT
- [ ] Create 1.0.x maintenance branch
- [ ] Archive 0.9.x release documents

## Success Criteria

1. **Adoption**: First external user runs successful load test
2. **Stability**: No critical bugs reported in first week
3. **Performance**: Achieves 10K+ TPS in reference scenario
4. **Quality**: Zero regression from 0.9.11

## Rollback Plan

If critical issues discovered:

1. Mark release as pre-release on GitHub
2. Communicate issue and timeline
3. Prepare 1.0.1 hotfix
4. Re-release after validation
