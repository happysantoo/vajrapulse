# JDK 21 → JDK 25 Migration Analysis

**Document type:** Technical analysis  
**Project:** VajraPulse  
**Analysis date:** 2026-04-04  
**Scope:** Feasibility, touchpoints, risks, and a practical migration checklist for moving the build baseline from **Java 21** to **Java 25**.

---

## Executive summary

Migrating VajraPulse from JDK 21 to JDK 25 is **technically feasible** and aligns with the project’s use of virtual threads, modern language features, and `ScopedValue`. The main **mechanical** work is updating Gradle toolchains, `release` / JavaDoc source levels, CI JDK images, and marketing/docs copy that still says “Java 21.”

The largest **platform** benefit for this codebase is that **scoped values are finalized in JDK 25** ([JEP 506](https://openjdk.org/jeps/506)), which should allow **removing `--enable-preview`** from compilation, tests, JavaDoc, and JMH/benchmark JVM args—provided nothing else in the build relies on preview features.

**Notable JDK 25 API change:** `ScopedValue.orElse` **no longer accepts `null`**. VajraPulse’s `MetricsCollector` uses `isBound()` / `get()` with local fallbacks, not `orElse(null)`, so this specific breaking change **does not appear to affect current code** (still verify under JDK 25 after upgrade).

**Consumer impact:** If published artifacts are compiled with `--release 25`, **downstream applications must run on JDK 25+** (unless you adopt a more complex multi-release JAR or maintain an older bytecode baseline—out of scope for this analysis unless product requirements demand it).

---

## Current state (JDK 21) — inventory

### Central build (`build.gradle.kts`)

| Setting | Current value | Notes |
|--------|----------------|-------|
| Toolchain `languageVersion` | `21` | Applies to all non-aggregator subprojects |
| `JavaCompile.options.release` | `21` | Bytecode / API level |
| Preview | `--enable-preview` on compile, tests, Javadoc | Driven by `ScopedValue` (preview in 21) |
| Javadoc `source` | `"21"` | Must match language level |

### Subprojects with **explicit** toolchain `21` (duplicate of root unless you rely on standalone builds)

These repeat `JavaLanguageVersion.of(21)` and would need the same bump for consistency:

- `benchmarks/build.gradle.kts`
- `examples/*/build.gradle.kts` (http-load-test, adaptive-load-test, adaptive-with-warmup, assertion-framework, cpu-bound-test, database-load-test, grpc-load-test, multi-exporter)
- `internal-tests/*/build.gradle.kts` (all-patterns, mixed-results, simple-success)
- `vajrapulse-exporter-opentelemetry/build.gradle.kts`
- `vajrapulse-exporter-report/build.gradle.kts`

Subprojects that inherit from root without a local toolchain override still pick up root `subprojects { java { toolchain { ... } } }` once root is updated.

### CI / release automation

| Location | Current JDK |
|----------|-------------|
| `.github/workflows/ci.yml` | 21 |
| `.github/workflows/benchmarks.yml` | 21 |
| `.github/workflows/security.yml` | 21 |

### Benchmarks (`benchmarks/build.gradle.kts`)

- JMH / fork JVM args include `--enable-preview` for loading core classes using preview `ScopedValue`. After migration, **re-evaluate**: if preview is fully removed project-wide, these args may be **removable**.

### Application / packaging

- `vajrapulse-worker` uses Shadow JAR; runtime JDK for end users follows whatever you document and what bytecode you emit (`release`).

### Source code — JDK 21–specific APIs in use

- **`java.lang.ScopedValue`** in `vajrapulse-core` (`MetricsCollector.java`): preview on 21, **final in 25** (JEP 506).
- **Virtual threads**, **records**, **sealed types**, **pattern matching**: all remain valid; JDK 25 adds further language/library evolution you can adopt opportunistically later.

### Gradle wrapper

- **Gradle 9.2.0** (`gradle-wrapper.properties`). JDK 25 support landed in the Gradle 9.1+ line; **9.2.0 is a reasonable baseline** to pair with JDK 25, but you should still run a full `./gradlew check --rerun-tasks` after switching.

---

## Why consider JDK 25?

1. **Scoped values finalized (JEP 506)** — Removes preview flag burden and stabilizes the API story for libraries.
2. **VM / GC / JFR improvements** — Generational Shenandoah (JEP 521), ZGC/G1 work, JFR enhancements—relevant for a performance-oriented load-testing stack (validate with benchmarks, not assumptions).
3. **Language/library evolution since 21** — Primitive patterns in `switch`/`instanceof`, module import declarations, flexible constructor bodies, and other JEPs integrated between 22–25 ([OpenJDK JDK 25 JEP list](https://openjdk.org/projects/jdk/25/))—optional follow-ups after the version bump.

---

## Risks and JDK-level breaking changes

| Topic | Relevance to VajraPulse |
|--------|-------------------------|
| **JEP 506:** `ScopedValue.orElse` disallows `null` | **Low** for current tree: no `ScopedValue.orElse` usage found; `Optional`-style `.orElse` in `CompositeBackpressureProvider` is unrelated. |
| **JEP 503:** 32-bit x86 port removed | **Low** unless you ship or test on 32-bit x86 (unlikely for this project). |
| **Deprecations / removals** between 21→25 | Run **`./gradlew compileJava compileTestJava`** and **`spotbugsMain`** on JDK 25; fix any new warnings or SpotBugs findings. |
| **Plugin ecosystem** (SpotBugs, Shadow, OWASP Dependency Check, JaCoCo) | **Medium:** confirm each plugin version supports running **on** JDK 25 and analyzing **25** bytecode; upgrade plugins if needed. |
| **Downstream JDK requirement** | **High (product):** `release 25` ⇒ consumers need JDK 25+ at runtime unless you intentionally keep a lower `release` (unusual if you “move” the project to 25). |

---

## Recommended migration sequence

1. **JDK 25 on CI and developer machines** — Install Temurin / Oracle OpenJDK 25 (or your standard distro).
2. **Gradle** — Keep **Gradle 9.2+**; if issues appear, consult [Gradle release notes](https://gradle.org/releases/) for the latest 9.x patch.
3. **Bump toolchain + `release` + Javadoc `source`** — Set all to **25** in root `build.gradle.kts` and every subproject that pins `21`.
4. **Remove `--enable-preview`** — After a successful compile/test pass:
   - Remove from `JavaCompile`, `Test`, `Javadoc`, and `benchmarks` JVM args **if** no remaining preview feature is required.
   - If something still fails, identify the remaining preview feature and either remove usage or keep a **narrow** preview flag only where needed.
5. **Update GitHub Actions** — `setup-java` `java-version: '25'` (or `25.0.x` if you pin a distro version).
6. **Full verification** (per project standards):
   - `./gradlew check --rerun-tasks`
   - Review JaCoCo and SpotBugs reports if versions changed.
7. **Docs and metadata** — README badges, `jreleaser.yml`, `.cursorrules`, `.github/copilot-instructions.md`, user guides, integration docs (JitPack, Maven Central guides), and analysis docs that say “Java 21” should be updated for consistency (separate editorial pass).

---

## Optional follow-ups (not required to “be on 25”)

- Adopt new language features (e.g. primitive patterns) where they simplify hot or complex code—**only** with tests and style agreement.
- Re-baseline performance docs (e.g. `documents/analysis/PERFORMANCE_BASELINE.md`) under JDK 25 JVM defaults.
- Decide **minimum supported JDK** for the **1.0** story and document it clearly (21 vs 25 is a product decision).

---

## Checklist summary

- [ ] Root `build.gradle.kts`: toolchain `25`, `options.release.set(25)`, Javadoc `source = "25"`, preview flags removed if safe  
- [ ] All listed `build.gradle.kts` files: toolchain `25` where `21` is explicit  
- [ ] `benchmarks/build.gradle.kts`: toolchain + JMH `jvmArgs` preview cleanup  
- [ ] `.github/workflows/*.yml`: JDK 25  
- [ ] Run `./gradlew check --rerun-tasks` on JDK 25  
- [ ] Confirm SpotBugs / Shadow / JaCoCo / dependency-check versions for JDK 25  
- [ ] Update user-facing “Java 21” strings where the project now **requires** 25  

---

## References

- [JEP 506: Scoped Values](https://openjdk.org/jeps/506) (final in JDK 25)  
- [JDK 25 release notes (Oracle)](https://www.oracle.com/java/technologies/javase/25all-relnotes.html)  
- [Significant changes migrating to JDK 25](https://docs.oracle.com/en/java/javase/26/migrate/significant-changes-jdk-25.html) (Oracle migration guide; URL may track “26” docs namespace—verify current canonical link when migrating)  
- [API diff 21 → 25 (javaalmanac.io)](https://javaalmanac.io/jdk/25/apidiff/21/)  
- [OpenJDK JDK 25 project](https://openjdk.java.net/projects/jdk/25/)  

---

*This document is an analysis only; it does not change build behavior until the team implements the checklist above.*
