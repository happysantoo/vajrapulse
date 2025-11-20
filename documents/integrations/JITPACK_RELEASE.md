# JitPack Release Process

## What is JitPack?

JitPack is a **build-as-a-service** Maven repository that builds Git repositories on-demand and serves the resulting artifacts. It's useful for:

- **Pre-release testing** before Maven Central publishing
- **Snapshot builds** from any branch/commit
- **Quick sharing** of development versions
- **Backup distribution** if Maven Central has issues

**Key Difference from Maven Central:**
- Maven Central: **Manual upload** of pre-built artifacts
- JitPack: **Automatic build** from Git tags/commits

---

## Prerequisites

### 1. Public GitHub Repository

- ✅ Repository must be public: https://github.com/happysantoo/vajrapulse
- ✅ Contains buildable Gradle/Maven project
- ✅ Has Git tags for versions

### 2. JitPack Configuration (Optional)

Create `jitpack.yml` in repository root for custom build settings:

```yaml
# jitpack.yml - Optional but recommended
jdk:
  - openjdk21

before_install:
  - sdk install java 21.0.1-tem
  - sdk use java 21.0.1-tem

install:
  - ./gradlew clean build publishToMavenLocal -x test
```

**VajraPulse doesn't need this** - JitPack auto-detects Java 21 from `build.gradle.kts`.

---

## Release Process

### Step 1: Create Git Tag

```bash
# Ensure you're on the commit you want to release
git log -1

# Create annotated tag
git tag -a v0.9.0 -m "VajraPulse 0.9.0"

# Push tag to GitHub
git push origin v0.9.0
```

**✅ Already completed** (v0.9.0 tag exists)

### Step 2: Trigger JitPack Build

JitPack builds on-demand when first requested:

**Option A: Visit JitPack Website**

1. Go to https://jitpack.io/#happysantoo/vajrapulse
2. You'll see list of tags/commits
3. Click **Get it** next to `v0.9.0`
4. JitPack will build automatically

**Option B: Use Dependency (Builds Automatically)**

Add to any test project's `build.gradle`:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.happysantoo:vajrapulse:v0.9.0'
}
```

First `./gradlew build` triggers JitPack to build your project.

### Step 3: Monitor Build Progress

1. Visit: https://jitpack.io/#happysantoo/vajrapulse/v0.9.0
2. Build log appears in real-time
3. Wait for **green checkmark** or **red error**

**Build time: 2-10 minutes** (depending on project size and JitPack load)

### Step 4: Verify Artifacts

Once build succeeds:

1. **Check Build Status:**
   - Green icon at https://jitpack.io/#happysantoo/vajrapulse/v0.9.0
   - Shows "Build passing" badge

2. **Test Dependency Resolution:**
   ```bash
   mkdir test-jitpack && cd test-jitpack
   
   # Create build.gradle
   cat > build.gradle << 'EOF'
   plugins {
       id 'java'
   }
   
   repositories {
       maven { url 'https://jitpack.io' }
   }
   
   dependencies {
       implementation 'com.github.happysantoo:vajrapulse:v0.9.0'
   }
   EOF
   
   # Resolve dependencies
   gradle dependencies --configuration runtimeClasspath | grep vajrapulse
   ```

3. **Verify Multi-Module Support:**
   ```gradle
   dependencies {
       implementation 'com.github.happysantoo.vajrapulse:vajrapulse-core:v0.9.0'
       implementation 'com.github.happysantoo.vajrapulse:vajrapulse-exporter-opentelemetry:v0.9.0'
   }
   ```

---

## Using VajraPulse from JitPack

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Full project (all modules)
    implementation("com.github.happysantoo:vajrapulse:v0.9.0")
    
    // OR specific modules
    implementation("com.github.happysantoo.vajrapulse:vajrapulse-core:v0.9.0")
    implementation("com.github.happysantoo.vajrapulse:vajrapulse-exporter-console:v0.9.0")
}
```

### Gradle (Groovy DSL)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.happysantoo.vajrapulse:vajrapulse-core:v0.9.0'
    implementation 'com.github.happysantoo.vajrapulse:vajrapulse-exporter-opentelemetry:v0.9.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.happysantoo.vajrapulse</groupId>
        <artifactId>vajrapulse-core</artifactId>
        <version>v0.9.0</version>
    </dependency>
</dependencies>
```

---

## Advanced Usage

### Use Specific Commit

```gradle
// Instead of tag, use commit hash
implementation 'com.github.happysantoo:vajrapulse:abc123def'
```

### Use Branch SNAPSHOT

```gradle
// Latest commit from branch (rebuilds on each request)
implementation 'com.github.happysantoo:vajrapulse:phase1-opentelemetry-exporter-SNAPSHOT'
```

### Use Short Commit Hash

```gradle
// First 10 characters of commit hash
implementation 'com.github.happysantoo:vajrapulse:abc123def4'
```

---

## JitPack Badge for README

Add build status badge to README.md:

```markdown
[![JitPack](https://jitpack.io/v/happysantoo/vajrapulse.svg)](https://jitpack.io/#happysantoo/vajrapulse)
```

Displays: [![JitPack](https://jitpack.io/v/happysantoo/vajrapulse.svg)](https://jitpack.io/#happysantoo/vajrapulse)

---

## Troubleshooting

### Issue: Build Fails with "Could not resolve dependencies"

**Symptoms:**
- JitPack build log shows dependency resolution errors

**Solution:**
```bash
# Ensure build.gradle.kts uses standard repositories
repositories {
    mavenCentral()
}

# Remove any proprietary/private repositories
# JitPack cannot access private repos
```

### Issue: Java Version Mismatch

**Symptoms:**
- "source release 21 requires target release 21"

**Solution:**
Create `jitpack.yml`:
```yaml
jdk:
  - openjdk21
```

### Issue: Multi-Module Not Detected

**Symptoms:**
- Only root artifact available, submodules missing

**Solution:**
- Ensure `settings.gradle.kts` includes all modules
- Verify each module has `build.gradle.kts`
- Check module names in `settings.gradle.kts` match directory names

### Issue: Build Timeout

**Symptoms:**
- Build fails after 15 minutes with timeout

**Solution:**
```yaml
# jitpack.yml - Increase timeout
install:
  - ./gradlew build -x test  # Skip tests to speed up
```

### Issue: Cached Failed Build

**Symptoms:**
- Old build failure persists after fixes

**Solution:**
1. Go to https://jitpack.io/#happysantoo/vajrapulse
2. Click **Look up** next to version
3. Build log shows **Rebuild** button
4. Click **Rebuild** to force fresh build

---

## JitPack vs Maven Central

| Feature | JitPack | Maven Central |
|---------|---------|---------------|
| **Setup** | Zero config | OSSRH account + GPG |
| **Build** | Automatic from Git | Manual upload |
| **Speed** | Instant (on tag push) | 1-2 hours sync |
| **Versioning** | Any tag/commit | Semantic versions only |
| **Snapshots** | Branch-based | Manual upload |
| **Trust** | Lower (builds from source) | Higher (official repo) |
| **Availability** | Depends on JitPack | 99.9% SLA |
| **Caching** | CDN cached | Global mirrors |
| **Best for** | Development/testing | Production releases |

---

## Recommended Strategy

### Development Workflow

1. **Development Snapshots:**
   ```gradle
   // Point to latest commit on feature branch
   implementation 'com.github.happysantoo:vajrapulse:feature-xyz-SNAPSHOT'
   ```

2. **Pre-Release Testing:**
   ```gradle
   // Use JitPack for release candidates
   implementation 'com.github.happysantoo:vajrapulse:v0.9.0-rc1'
   ```

3. **Official Releases:**
   ```gradle
   // Use Maven Central for stable versions
   implementation 'com.vajrapulse:vajrapulse-core:0.9.0'
   ```

### Documentation Approach

In README.md, provide both options:

```markdown
## Installation

### Maven Central (Recommended)
```gradle
implementation 'com.vajrapulse:vajrapulse-core:0.9.0'
```

### JitPack (Latest Development)
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.happysantoo:vajrapulse:v0.9.0'
}
```
```

---

## Quick Reference

### Publish to JitPack

```bash
# 1. Create and push tag
git tag -a v0.9.0 -m "Release 0.9.0"
git push origin v0.9.0

# 2. Visit JitPack (triggers build automatically)
open https://jitpack.io/#happysantoo/vajrapulse

# 3. Use in project
# Add to build.gradle:
# repositories { maven { url 'https://jitpack.io' } }
# implementation 'com.github.happysantoo:vajrapulse:v0.9.0'
```

### Check Build Status

```bash
# View build logs
open https://jitpack.io/#happysantoo/vajrapulse/v0.9.0

# Rebuild if needed (via UI)
# Click "Rebuild" button on build page
```

### Test Locally

```bash
# Create test project
mkdir test-jitpack && cd test-jitpack

# Add JitPack repo and dependency
cat > build.gradle << 'EOF'
plugins { id 'java' }
repositories { maven { url 'https://jitpack.io' } }
dependencies { 
    implementation 'com.github.happysantoo:vajrapulse:v0.9.0' 
}
EOF

# Verify resolution
gradle dependencies
```

---

## Current Status for VajraPulse

✅ **JitPack Ready** - No changes needed!

- Git tag `v0.9.0` already pushed
- JitPack can build automatically from existing structure
- Multi-module Gradle project fully supported
- Java 21 toolchain auto-detected

**Next Steps:**

1. Visit https://jitpack.io/#happysantoo/vajrapulse
2. Click **Get it** on v0.9.0
3. Wait for build to complete (~5 minutes)
4. Add badge to README.md
5. Test with sample project

**JitPack coordinates:**
```gradle
implementation 'com.github.happysantoo.vajrapulse:vajrapulse-core:v0.9.0'
implementation 'com.github.happysantoo.vajrapulse:vajrapulse-exporter-opentelemetry:v0.9.0'
```

**Maven Central coordinates (once published):**
```gradle
implementation 'com.vajrapulse:vajrapulse-core:0.9.0'
implementation 'com.vajrapulse:vajrapulse-exporter-opentelemetry:0.9.0'
```

---

**Recommendation:** Use **JitPack now** for testing, switch to **Maven Central** once OSSRH namespace is approved (typically 1-3 business days for domain verification).
