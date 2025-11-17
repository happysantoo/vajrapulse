#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-0.9.0}"
GROUP_PATH="com/vajrapulse"
MODULES=(vajrapulse-api vajrapulse-core vajrapulse-exporter-console vajrapulse-exporter-opentelemetry vajrapulse-worker)
REPO_ROOT="${HOME}/.m2/repository"

echo "[central-bundle] Preparing bundle for version ${VERSION}"

for mod in "${MODULES[@]}"; do
  dir="${REPO_ROOT}/${GROUP_PATH}/${mod}/${VERSION}"
  if [[ ! -d "${dir}" ]]; then
    echo "[central-bundle] ERROR: Missing directory ${dir}" >&2
    exit 1
  fi
  echo "[central-bundle] Checking artifacts for ${mod}";
  for base in pom module jar; do
    f="${dir}/${mod}-${VERSION}.${base}"; [[ -f "${f}" ]] || { echo "Missing ${f}"; exit 1; }
  done
  for classifier in sources javadoc; do
    f="${dir}/${mod}-${VERSION}-${classifier}.jar"; [[ -f "${f}" ]] || { echo "Missing ${f}"; exit 1; }
  done
  # Generate checksums if missing
  for artifact in \
    "${dir}/${mod}-${VERSION}.pom" \
    "${dir}/${mod}-${VERSION}.module" \
    "${dir}/${mod}-${VERSION}.jar" \
    "${dir}/${mod}-${VERSION}-sources.jar" \
    "${dir}/${mod}-${VERSION}-javadoc.jar"; do
      [[ -f "${artifact}.md5" ]] || md5 -q "${artifact}" > "${artifact}.md5"
      [[ -f "${artifact}.sha1" ]] || shasum -a 1 "${artifact}" | awk '{print $1}' > "${artifact}.sha1"
  done
done

OUT="/tmp/vajrapulse-${VERSION}-central.zip"
echo "[central-bundle] Creating zip ${OUT}";
rm -f "${OUT}"
cd "${REPO_ROOT}";
zip -r "${OUT}" "${GROUP_PATH}/vajrapulse-api/${VERSION}" "${GROUP_PATH}/vajrapulse-core/${VERSION}" "${GROUP_PATH}/vajrapulse-exporter-console/${VERSION}" "${GROUP_PATH}/vajrapulse-exporter-opentelemetry/${VERSION}" "${GROUP_PATH}/vajrapulse-worker/${VERSION}" >/dev/null
echo "[central-bundle] Bundle ready: ${OUT}"
echo "Upload command example:";
echo "curl -u \"$mavenCentralUsername:$mavenCentralPassword\" -F bundle=@${OUT} \"https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC\""
