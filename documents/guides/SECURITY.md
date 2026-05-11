# VajraPulse Security Guide

## Overview

VajraPulse follows security best practices for a load testing framework. This document outlines our security policies and practices.

## Dependency Management

### Automated Scanning

- **Dependabot**: Automatically monitors dependencies for known vulnerabilities
- **OWASP Dependency Check**: Run via `./gradlew dependencyCheckAnalyze`
- Updates are reviewed and merged promptly

### Version Policy

- Dependencies are kept current with latest stable versions
- Security patches are prioritized over feature updates
- Breaking changes in dependencies are evaluated carefully

## Secure Development Practices

### Code Review

- All changes require PR review before merging
- Security-sensitive code requires additional scrutiny
- Static analysis (SpotBugs) runs on every PR

### Input Validation

- Task inputs are validated before execution
- Configuration values are type-checked and range-validated
- External inputs (URLs, hosts) should be sanitized by user code

## Reporting Security Issues

If you discover a security vulnerability:

1. **Do NOT** open a public GitHub issue
2. Email security concerns to the maintainers
3. Provide detailed reproduction steps
4. Allow reasonable time for a fix before disclosure

## Security Checklist for Users

When using VajraPulse:

- [ ] Keep VajraPulse updated to the latest version
- [ ] Review task code for security issues (SQL injection, etc.)
- [ ] Secure credentials used in load tests
- [ ] Isolate load test environments from production
- [ ] Monitor for abnormal behavior during tests

## SBOM (Software Bill of Materials)

Generate SBOM using Gradle's built-in dependency report:

```bash
# Generate dependency report (JSON format)
./gradlew dependencies --configuration runtimeClasspath > dependencies.txt

# For structured SBOM, consider using:
# - CycloneDX plugin (if added to build)
# - OWASP Dependency Check (already integrated)
```

The OWASP Dependency Check report includes dependency information that can serve as an SBOM.

**Note**: For production SBOM generation, consider adding the CycloneDX Gradle plugin:
```kotlin
plugins {
    id("org.cyclonedx.bom") version "1.8.0"
}
```

Then run: `./gradlew cyclonedxBom`

## Compliance

VajraPulse is licensed under Apache 2.0. All dependencies are compatible with commercial use.
