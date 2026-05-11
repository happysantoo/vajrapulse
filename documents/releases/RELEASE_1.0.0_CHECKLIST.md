# VajraPulse 1.0.0 Release Checklist

## Pre-Release Verification

### Code Quality
- [x] All tests pass: `./gradlew test --rerun-tasks`
- [x] Coverage ≥90%: `./gradlew jacocoTestCoverageVerification --rerun-tasks`
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
- [x] Publication guide: [RELEASE_1.0.0_PUBLICATION.md](../guides/RELEASE_1.0.0_PUBLICATION.md)

### Performance
- [x] Benchmark infrastructure in place (`./gradlew :benchmarks:jmh`)
- [x] JMH runs with Java 21 `--enable-preview` (bytecode generator + forks)
- [x] Macro benchmark optional via `-Pjmh.includeMacro` (default suite excludes it for CI/local speed)
- [x] Benchmark comparison script implemented (`scripts/compare-benchmarks.sh`)
- [x] Baseline numbers populated in PERFORMANCE_BASELINE.md (2026-04-04 local run; re-run on release hardware as needed)

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
- [x] Git annotated tag `v1.0.0` created locally (if missing: see publication guide)
- [ ] Git tag `v1.0.0` pushed to `origin` (maintainer)
- [ ] GitHub release created with notes
- [ ] Maven Central publication successful
- [ ] Artifacts available on Maven Central

## Post-Release
- [ ] Announce release (GitHub, social media)
- [ ] Update version to 1.0.1-SNAPSHOT (or next pre-release)
- [ ] Create 1.0.x maintenance branch (if using branch-per-line)
- [ ] Archive 0.9.x release documents (optional)

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
