# Gradle → Maven: Feature Gap Analysis (VajraPulse)

This document analyzes **what you give up or make harder** if VajraPulse migrates from **Gradle (Kotlin DSL)** to **Apache Maven**, based on the **current** repository layout and `build.gradle.kts` / `settings.gradle.kts` configuration.

It is **not** a recommendation to migrate; it is a factual comparison for decision-making.

---

## Executive summary

Most **tooling outcomes** (compile Java 21 with preview, Spock tests, JaCoCo thresholds, SpotBugs, JMH, fat JAR for the worker, BOM publishing, signing) can be **replicated in Maven** using well-known plugins and a multi-module parent POM. What you **lose or dilute** is mainly **Gradle’s execution model and developer ergonomics**: configuration cache, Gradle’s build/task cache semantics, Kotlin DSL–style programmatic build logic, and some **single-place** cross-cutting configuration that today lives in one root script.

---

## 1. What VajraPulse uses Gradle for today

| Area | Gradle usage in this repo |
|------|---------------------------|
| **Multi-module** | `settings.gradle.kts` with `include(...)` including nested logical names (`examples:http-load-test`, etc.) |
| **Java** | Toolchain Java 21, `release` 21, `--enable-preview`, UTF-8, `-parameters`, deprecation lint |
| **JavaDoc** | Per-module-type doclint (`-Xdoclint:all,-missing` vs `none` for examples), preview in Javadoc |
| **Library vs app** | `java-library` + **`api` / `implementation`** separation on published modules |
| **BOM** | `java-platform` module (`vajrapulse-bom`) with `constraints` |
| **Tests** | JUnit Platform + **Groovy / Spock**, `maxParallelForks = 1`, `--enable-preview` on test JVM |
| **Coverage** | JaCoCo report + **`jacocoTestCoverageVerification`** with **90% line minimum** on selected modules only |
| **Static analysis** | SpotBugs on main sources; `spotbugsTest` disabled; shared `spotbugs-exclude.xml` |
| **Packaging** | **Shadow** (`com.gradleup.shadow`) for `vajrapulse-worker` fat JAR |
| **Benchmarks** | **JMH Gradle plugin** (`me.champeau.jmh`) with preview JVM args, conditional benchmark excludes |
| **Security** | OWASP **Dependency-Check** Gradle plugin (CI) |
| **Publishing** | `maven-publish` + **in-memory PGP** signing + rich POM metadata; JReleaser docs reference `./gradlew ...` |
| **Convenience tasks** | Root `compileExamples`, `prepareRelease`; example `runOtel` custom `JavaExec` |
| **CI / automation** | `compileExamples`, `--rerun-tasks`, Gradle cache in GitHub Actions, Dependabot `package-ecosystem: gradle` |
| **Gradle performance** | `org.gradle.parallel`, `org.gradle.caching`, **`org.gradle.configuration-cache=true`** in `gradle.properties` |
| **Secondary tree** | `internal-tests/` has its **own** `settings.gradle.kts` (composite-style workflow without the root settings) |

---

## 2. Features you would miss or weaken (relative to current Gradle setup)

### 2.1 Gradle configuration cache

**Today:** `gradle.properties` enables **`org.gradle.configuration-cache=true`**.

**With Maven:** There is **no equivalent**. Maven’s model is phase-based; some plugins add incremental behavior, but not Gradle’s whole-configuration reuse.

**Impact:** Slightly slower or less predictable local/CI iteration compared to a well-tuned Gradle 8+ project using configuration cache—especially when many plugins participate.

---

### 2.2 Gradle’s task graph, avoidance, and `--rerun-tasks`

**Today:** Documentation and habits rely on **`./gradlew ... --rerun-tasks`** to defeat stale outputs and cached test results.

**With Maven:** You typically use **`clean`**, plugin-specific flags, or delete `target/`. There is **no one flag** that maps 1:1 to “re-execute every task like Gradle’s `--rerun-tasks`.”

**Impact:** Team muscle memory and CI snippets change; risk profile for “did we actually re-run tests?” shifts unless CI always uses explicit clean steps.

---

### 2.3 Kotlin DSL + centralized imperative logic

**Today:** One root `subprojects { ... }` block applies plugins, sets compiler args, wires `check` → JaCoCo + SpotBugs, and branches on `project.path` (examples vs product modules, benchmarks exceptions).

**With Maven:** The usual pattern is a **parent POM** + **`<pluginManagement>`** + sometimes **profiles** (`<activation>` by property or JDK). Equivalent behavior is achievable but often **more XML**, or requires **small custom plugins** / **Groovy execution** in `pom.xml` for the same density of conditional rules.

**Impact:** Harder to express “if module path matches X then Y” in one place without duplication or profiles; readability trade-offs vs Kotlin.

---

### 2.4 `api` vs `implementation` (Gradle Java Library plugin)

**Today:** Published modules use Gradle’s **`api`** dependencies so consumers get the correct **transitive compile classpath** without leaking internals.

**With Maven:** Standard dependency declarations end up as **compile-scope** dependencies in the POM unless you carefully model **optional** dependencies, **multi-module boundaries**, or **BOM + dependencyManagement**. You **do not** get the same first-class “compile vs runtime classpath for consumers” split as Gradle’s `api`/`implementation` without discipline in the POM design.

**Impact:** Slightly higher risk of **over-publishing** compile dependencies or needing extra modules to keep APIs clean—unless the team enforces Maven best practices strictly.

---

### 2.5 Composite / secondary builds ergonomics

**Today:** `internal-tests/` is a **separate** Gradle build (`internal-tests/settings.gradle.kts`).

**With Maven:** You would usually use a **separate reactor** (another root `pom.xml`) or a **profile**-driven module set. It works, but the “second build with its own settings” story is **less uniform** than Gradle composites unless you invest in tooling.

---

### 2.6 Custom ad hoc tasks (low ceremony)

**Examples in this repo:**

- Root **`compileExamples`** depending on a **filtered** set of subprojects.
- **`prepareRelease`** depending on `vajrapulse*` subprojects + examples compile.
- **`runOtel`** as a second **`JavaExec`** in an example module.

**With Maven:** Achievable via **Exec** plugin, **invoker** plugin, **multiple executions** of `exec-maven-plugin`, or small modules—but each adds XML or convention overhead compared to a short `tasks.register { ... }` block.

**Impact:** More boilerplate for “one-off” developer workflows unless standardized.

---

### 2.7 JMH integration ergonomics

**Today:** `benchmarks/build.gradle.kts` uses the **JMH Gradle plugin** with `jmh { ... }`, preview **JVM args**, and **property-gated** excludes (`jmh.includeMacro`).

**With Maven:** **jmh-maven-plugin** (or similar) can run benchmarks, but the **exact** ergonomics of Gradle’s dedicated configuration block and task wiring differ; conditional inclusion often becomes **Maven profiles** or **surefire-style patterns**.

**Impact:** Small friction; not a functional blocker.

---

### 2.8 Shadow / fat JAR

**Today:** `com.gradleup.shadow` with `mergeServiceFiles`, classifier `all`, manifest `Main-Class`.

**With Maven:** **maven-shade-plugin** provides the same class of feature (relocations, merging service files, main class).

**Impact:** **No fundamental loss**; different configuration surface.

---

### 2.9 Ecosystem integrations tied to Gradle today

| Integration | Gradle today | After Maven |
|-------------|--------------|-------------|
| **GitHub Actions** `cache: 'gradle'` | Used in CI workflows | Switch to **`cache: maven`** (or comparable) |
| **Dependabot** | `package-ecosystem: "gradle"` | Switch to **`maven`** |
| **Docs / scripts** | Many references to `./gradlew ...` | Bulk update to `mvn ...` / wrapper |

**Impact:** Mechanical migration cost across docs, `jreleaser.yml` comments, and automation—not a missing capability.

---

## 3. What Maven can cover (so it is *not* a “missing feature”)

These are **already solved problems** in Maven-land if you configure them deliberately:

- **Java 21 + preview**: `maven-compiler-plugin` (`release`, `--enable-preview`), **toolchains** for JDK selection.
- **Spock / Groovy tests**: **GMavenPlus** (or equivalent) + JUnit Platform.
- **JaCoCo + gate**: `jacoco-maven-plugin` with **rules**; multi-module **aggregate** reports need an extra pattern (`report-aggregate` or reporting module).
- **SpotBugs**: `spotbugs-maven-plugin` with exclude filters (same XML filter file can be reused).
- **OWASP Dependency-Check**: official **Maven** plugin.
- **BOM**: `dependencyManagement`-only module packaged as **`pom`** (Maven’s native BOM pattern).
- **Publishing + signing**: `maven-gpg-plugin`, Central publishing via **OSSRH** / **Central Portal**, or **JReleaser** consuming Maven-built artifacts (CLI or Maven plugin—your `jreleaser.yml` is largely build-tool agnostic aside from comments).

---

## 4. Net assessment for VajraPulse

| Category | Verdict |
|----------|---------|
| **Reproducible builds, CI gates (test, coverage, SpotBugs)** | Achievable in Maven with comparable plugins. |
| **BOM, multi-module, worker fat JAR, JMH** | Achievable; different plugin config. |
| **Developer UX: concise cross-cutting rules, custom tasks** | Generally **worse** than current Gradle Kotlin DSL. |
| **Build performance / caching story** | Gradle **configuration cache** + Gradle task model **not** matched by Maven. |
| **`api`/`implementation` modeling** | **Weaker first-class support** in Maven POMs; needs discipline. |

---

## 5. References in this repository

- Root build logic: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`
- BOM: `vajrapulse-bom/build.gradle.kts`
- Worker Shadow JAR: `vajrapulse-worker/build.gradle.kts`
- JMH: `benchmarks/build.gradle.kts`
- Example custom tasks: `examples/http-load-test/build.gradle.kts`
- Secondary build: `internal-tests/settings.gradle.kts`

---

*Document type: analysis / build tooling comparison.*  
*Scope: VajraPulse repository as of the analysis date.*
