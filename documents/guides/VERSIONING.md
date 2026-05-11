# VajraPulse Versioning Strategy

## Semantic Versioning

VajraPulse follows [Semantic Versioning 2.0.0](https://semver.org/):

```
MAJOR.MINOR.PATCH
```

- **MAJOR**: Breaking changes to public API
- **MINOR**: New features, backwards compatible
- **PATCH**: Bug fixes, backwards compatible

## Pre-1.0 Rules (historical)

Before **1.0.0**, breaking changes were allowed in minor versions. That phase is complete for the core library API.

## Post-1.0 Rules (current)

As of **1.0.0**:

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
- Snapshots: `1.0.1-SNAPSHOT` (example; not published to Central until release)

## 1.0.0 status

**1.0.0** is the current production line. Criteria below are tracked in [`documents/releases/RELEASE_1.0.0_CHECKLIST.md`](../releases/RELEASE_1.0.0_CHECKLIST.md) and [`documents/analysis/PERFORMANCE_BASELINE.md`](../analysis/PERFORMANCE_BASELINE.md).

### 1.0.0 criteria (reference)

- [x] Public APIs reviewed and frozen (see API freeze document)
- [x] JavaDoc coverage enforced on `vajrapulse-api`
- [x] Performance baseline numbers recorded (JMH; macro optional)
- [x] CI/CD pipeline operational
- [x] Security scanning in place
- [x] Quick start guide validated
- [x] Migration guide complete

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
