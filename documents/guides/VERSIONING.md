# VajraPulse Versioning Strategy

## Semantic Versioning

VajraPulse follows [Semantic Versioning 2.0.0](https://semver.org/):

```
MAJOR.MINOR.PATCH
```

- **MAJOR**: Breaking changes to public API
- **MINOR**: New features, backwards compatible
- **PATCH**: Bug fixes, backwards compatible

## Pre-1.0 Rules (Current)

During pre-1.0 development:

- **Breaking changes are allowed** in MINOR versions
- API is evolving toward stabilization
- Users should pin to specific versions
- Migration guides provided for breaking changes

### Current Status: 0.9.x

- Core APIs are stabilizing
- Breaking changes are minimized but possible
- Focus on feature completeness and quality

## Post-1.0 Rules

After 1.0.0 release:

- **MAJOR**: Reserved for breaking API changes
- **MINOR**: New features, deprecations (no removals)
- **PATCH**: Bug fixes only, no API changes

### Breaking Change Policy

A breaking change is:
- Removing a public class, method, or field
- Changing method signatures
- Changing behavior in incompatible ways
- Removing configuration options

Breaking changes will:
1. Be announced in CHANGELOG
2. Include migration guide
3. Provide deprecation period when possible

## Release Cadence

- **PATCH**: As needed for critical fixes
- **MINOR**: Monthly or when features are ready
- **MAJOR**: Annually or for significant changes

## Version Tags

- Release versions: `v1.2.3`
- Pre-releases: `v1.0.0-rc1`, `v1.0.0-beta1`
- Snapshots: `0.9.12-SNAPSHOT` (not published)

## 1.0.0 Freeze Date

**Target**: When all P0 items from gap analysis are complete

### 1.0.0 Criteria

- [ ] All public APIs reviewed and frozen
- [ ] 100% JavaDoc coverage on API module
- [ ] Performance baselines established
- [ ] CI/CD pipeline operational
- [ ] Security scanning in place
- [ ] Quick start guide validated
- [ ] Migration guide complete

## Deprecation Policy

Post-1.0:

1. Mark with `@Deprecated` annotation
2. Document replacement in JavaDoc
3. Minimum 2 MINOR versions before removal
4. Announce in CHANGELOG

## Dependency Updates

- Security updates: Immediate PATCH release
- Feature updates: Bundled in next MINOR
- Breaking dependency updates: Wait for MAJOR
