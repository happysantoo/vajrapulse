#!/usr/bin/env bash
# Verify Maven Central sync for VajraPulse 0.9.3

set -euo pipefail

VERSION="0.9.3"
BASE_URL="https://repo1.maven.org/maven2/com/vajrapulse"

echo "=== Verifying Maven Central Sync for VajraPulse ${VERSION} ==="
echo ""

MODULES=(
  "vajrapulse-api"
  "vajrapulse-core"
  "vajrapulse-exporter-console"
  "vajrapulse-exporter-opentelemetry"
  "vajrapulse-worker"
  "vajrapulse-bom"
)

SUCCESS=0
FAILED=0

for module in "${MODULES[@]}"; do
  echo -n "Checking ${module}... "
  
  if [[ "${module}" == "vajrapulse-bom" ]]; then
    # BOM only has POM, no JAR
    URL="${BASE_URL}/${module}/${VERSION}/${module}-${VERSION}.pom"
  else
    # Regular modules have JAR
    URL="${BASE_URL}/${module}/${VERSION}/${module}-${VERSION}.pom"
  fi
  
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${URL}")
  
  if [[ "${HTTP_CODE}" == "200" ]]; then
    echo "✅ OK (HTTP ${HTTP_CODE})"
    ((SUCCESS++))
  else
    echo "❌ FAILED (HTTP ${HTTP_CODE})"
    ((FAILED++))
  fi
done

echo ""
echo "=== Summary ==="
echo "✅ Successful: ${SUCCESS}/${#MODULES[@]}"
echo "❌ Failed: ${FAILED}/${#MODULES[@]}"

if [[ ${FAILED} -eq 0 ]]; then
  echo ""
  echo "🎉 All artifacts are available on Maven Central!"
  echo ""
  echo "You can now use:"
  echo "  implementation(platform(\"com.vajrapulse:vajrapulse-bom:${VERSION}\"))"
  exit 0
else
  echo ""
  echo "⏳ Some artifacts are not yet available. Please wait and try again."
  echo "   Sync typically takes 10-120 minutes."
  exit 1
fi

