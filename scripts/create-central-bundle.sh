#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-0.9.0}"
GROUP_PATH="com/vajrapulse"
MODULES=(vajrapulse-bom vajrapulse-api vajrapulse-core vajrapulse-exporter-console vajrapulse-exporter-opentelemetry vajrapulse-exporter-report vajrapulse-worker)
REPO_ROOT="${HOME}/.m2/repository"

echo "[central-bundle] Preparing bundle for version ${VERSION}"

for mod in "${MODULES[@]}"; do
  dir="${REPO_ROOT}/${GROUP_PATH}/${mod}/${VERSION}"
  if [[ ! -d "${dir}" ]]; then
    echo "[central-bundle] ERROR: Missing directory ${dir}" >&2
    exit 1
  fi
  echo "[central-bundle] Checking artifacts for ${mod}";
  
  # BOM module only has POM, not JAR
  if [[ "${mod}" == "vajrapulse-bom" ]]; then
    for base in pom module; do
      f="${dir}/${mod}-${VERSION}.${base}"; [[ -f "${f}" ]] || { echo "Missing ${f}"; exit 1; }
    done
  else
    for base in pom module jar; do
      f="${dir}/${mod}-${VERSION}.${base}"; [[ -f "${f}" ]] || { echo "Missing ${f}"; exit 1; }
    done
    for classifier in sources javadoc; do
      f="${dir}/${mod}-${VERSION}-${classifier}.jar"; [[ -f "${f}" ]] || { echo "Missing ${f}"; exit 1; }
    done
  fi
  # Generate checksums if missing
  if [[ "${mod}" == "vajrapulse-bom" ]]; then
    # BOM only has POM and module files
    for artifact in \
      "${dir}/${mod}-${VERSION}.pom" \
      "${dir}/${mod}-${VERSION}.module"; do
        [[ -f "${artifact}.md5" ]] || md5 -q "${artifact}" > "${artifact}.md5"
        [[ -f "${artifact}.sha1" ]] || shasum -a 1 "${artifact}" | awk '{print $1}' > "${artifact}.sha1"
    done
  else
    # Regular modules have JARs
    for artifact in \
      "${dir}/${mod}-${VERSION}.pom" \
      "${dir}/${mod}-${VERSION}.module" \
      "${dir}/${mod}-${VERSION}.jar" \
      "${dir}/${mod}-${VERSION}-sources.jar" \
      "${dir}/${mod}-${VERSION}-javadoc.jar"; do
        [[ -f "${artifact}.md5" ]] || md5 -q "${artifact}" > "${artifact}.md5"
        [[ -f "${artifact}.sha1" ]] || shasum -a 1 "${artifact}" | awk '{print $1}' > "${artifact}.sha1"
    done
  fi

  # Also generate checksums for any additional JARs (e.g., shadow "-all.jar")
  # Skip ones we already handled above; guard with existence checks.
  for extra in "${dir}"/*.jar; do
    [[ -f "${extra}" ]] || continue
    # Known standard artifacts already covered
    case "${extra}" in
      "${dir}/${mod}-${VERSION}.jar"|"${dir}/${mod}-${VERSION}-sources.jar"|"${dir}/${mod}-${VERSION}-javadoc.jar")
        continue ;;
    esac
    [[ -f "${extra}.md5" ]] || md5 -q "${extra}" > "${extra}.md5"
    [[ -f "${extra}.sha1" ]] || shasum -a 1 "${extra}" | awk '{print $1}' > "${extra}.sha1"
  done
done

OUT="/tmp/vajrapulse-${VERSION}-central.zip"
echo "[central-bundle] Creating zip ${OUT}";
rm -f "${OUT}"
cd "${REPO_ROOT}";

# Build zip command dynamically for all modules
ZIP_ARGS=()
for mod in "${MODULES[@]}"; do
  ZIP_ARGS+=("${GROUP_PATH}/${mod}/${VERSION}")
done

zip -r "${OUT}" "${ZIP_ARGS[@]}" >/dev/null
echo "[central-bundle] Bundle ready: ${OUT}"
echo "Upload command example:";
echo "curl -u \"$mavenCentralUsername:$mavenCentralPassword\" -F bundle=@${OUT} \"https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC\""
