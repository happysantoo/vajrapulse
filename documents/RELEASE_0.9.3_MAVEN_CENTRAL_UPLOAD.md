# Release 0.9.3 - Maven Central Upload Guide

**Date**: 2025-01-XX  
**Status**: Ready for Upload

---

## 📦 Bundle Information

- **Bundle Location**: `/tmp/vajrapulse-0.9.3-central.zip`
- **Bundle Size**: 6.3 MB
- **Total Files**: 118 files
- **Modules**: 6 (api, core, exporter-console, exporter-opentelemetry, worker, bom)

---

## 🚀 Upload Methods

### Method 1: Sonatype Portal (Recommended)

1. **Navigate to Sonatype Portal**:
   - Go to https://central.sonatype.com/
   - Sign in with your Sonatype account

2. **Upload Bundle**:
   - Navigate to "Upload" section
   - Click "Upload Bundle"
   - Select file: `/tmp/vajrapulse-0.9.3-central.zip`
   - Set publishing type: **AUTOMATIC**
   - Click "Upload"

3. **Monitor Upload**:
   - Wait for upload to complete
   - Note the deployment ID
   - Monitor status in the portal

4. **Verify Sync**:
   - Wait 10-120 minutes for sync to Maven Central
   - Verify artifacts at:
     - https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-core/0.9.3/
     - https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-bom/0.9.3/

---

### Method 2: API Upload (Alternative)

If you have a Maven Central API token:

```bash
export MAVEN_CENTRAL_TOKEN="your-token-here"

curl --request POST \
  --verbose \
  --header "Authorization: Bearer $MAVEN_CENTRAL_TOKEN" \
  --form bundle=@/tmp/vajrapulse-0.9.3-central.zip \
  "https://central.sonatype.com/api/v1/publisher/upload?name=vajrapulse-0.9.3&publishingType=AUTOMATIC"
```

**Expected Response**: HTTP 201 with deployment ID

---

## ✅ Pre-Upload Verification

Before uploading, verify:

- [x] Bundle exists: `/tmp/vajrapulse-0.9.3-central.zip`
- [x] Bundle size: 6.3 MB
- [x] All 6 modules included
- [x] All signatures present (`.asc` files)
- [x] All checksums present (`.md5`, `.sha1`)
- [x] LICENSE files included in JARs
- [x] Git tag created: `v0.9.3`
- [x] Git tag pushed to GitHub

---

## 🔍 Post-Upload Verification

### 1. Check Upload Status

In Sonatype Portal:
- View deployment status
- Check for any errors or warnings
- Note deployment ID for tracking

### 2. Verify Maven Central Sync

Wait 10-120 minutes, then verify:

```bash
# Check core module
curl -I https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-core/0.9.3/vajrapulse-core-0.9.3.pom

# Check BOM module
curl -I https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-bom/0.9.3/vajrapulse-bom-0.9.3.pom

# Check all modules
for module in api core exporter-console exporter-opentelemetry worker bom; do
  echo "Checking $module..."
  curl -I https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-$module/0.9.3/vajrapulse-$module-0.9.3.pom
done
```

**Expected**: HTTP 200 responses

### 3. Verify Artifacts

Check that all artifacts are accessible:

- Main JARs
- Sources JARs
- Javadoc JARs
- POM files
- Signatures (`.asc` files)
- Checksums (`.md5`, `.sha1`)

### 4. Test Dependency Resolution

Test that artifacts can be resolved:

```bash
# Gradle test
./gradlew dependencies --refresh-dependencies | grep vajrapulse

# Maven test
mvn dependency:tree | grep vajrapulse
```

---

## 📋 Upload Checklist

### Before Upload
- [x] Bundle created and verified
- [x] Git tag `v0.9.3` created and pushed
- [x] GitHub release created
- [x] All validations complete

### During Upload
- [ ] Bundle uploaded to Sonatype Portal
- [ ] Deployment ID noted
- [ ] Upload status confirmed

### After Upload
- [ ] Wait for sync (10-120 minutes)
- [ ] Verify artifacts on Maven Central
- [ ] Test dependency resolution
- [ ] Update documentation if needed

---

## 🔗 Maven Central Links

Once synced, artifacts will be available at:

- **Core Module**: https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-core/0.9.3/
- **BOM Module**: https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-bom/0.9.3/
- **API Module**: https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-api/0.9.3/
- **Console Exporter**: https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-exporter-console/0.9.3/
- **OpenTelemetry Exporter**: https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-exporter-opentelemetry/0.9.3/
- **Worker**: https://repo1.maven.org/maven2/com/vajrapulse/vajrapulse-worker/0.9.3/

---

## 🆘 Troubleshooting

### Upload Fails

1. **Check Bundle Size**: Must be < 500MB
2. **Verify Signatures**: All `.asc` files must be present
3. **Check Token**: If using API, verify token is valid
4. **Review Errors**: Check Sonatype Portal for specific error messages

### Sync Delays

- **Normal**: 10-120 minutes
- **If > 2 hours**: Check Sonatype Portal for issues
- **Contact Support**: If sync fails, contact Sonatype support with deployment ID

### Missing Artifacts

- Verify all modules included in bundle
- Check bundle contents: `unzip -l /tmp/vajrapulse-0.9.3-central.zip`
- Re-create bundle if needed: `./scripts/create-central-bundle.sh 0.9.3`

---

## 📝 Notes

- Bundle includes all required artifacts, signatures, and checksums
- All modules are properly signed with GPG
- LICENSE files are included in all JARs
- BOM module is included (POM-only, no JAR)

---

**Ready for upload!** 🚀

