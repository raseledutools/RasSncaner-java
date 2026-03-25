#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="${ROOT_DIR}/ort-out"

echo "==> Cleaning previous ORT output"
rm -rf "${OUT_DIR}"
mkdir -p "${OUT_DIR}"

# Remove submodule references to prevent ORT from cloning large external repos
# (opencv ~211MB, onnxruntime ~708MB+) during provenance resolution.
# The external/ directories are excluded in .ort.yml and not needed for analysis.
if [ -f "${ROOT_DIR}/.gitmodules" ]; then
  echo "==> Temporarily removing .gitmodules to skip submodule provenance resolution"
  cp "${ROOT_DIR}/.gitmodules" "${ROOT_DIR}/.gitmodules.ort-backup"
  rm "${ROOT_DIR}/.gitmodules"
  trap 'mv "${ROOT_DIR}/.gitmodules.ort-backup" "${ROOT_DIR}/.gitmodules" 2>/dev/null' EXIT
fi

echo "==> Running ORT analyze"
ort --info --stacktrace --config "${ROOT_DIR}/ort/config/config.yml" \
  analyze \
  -i "${ROOT_DIR}" \
  -o "${OUT_DIR}/analyzer"

echo "==> Running ORT scan"
# ORT returns exit code 2 when there are unresolved issues (e.g. VCS resolution warnings).
# We tolerate this as long as the scan result file was produced.
ort --info --stacktrace --config "${ROOT_DIR}/ort/config/config.yml" \
  scan \
  -i "${OUT_DIR}/analyzer/analyzer-result.yml" \
  -o "${OUT_DIR}/scan" \
  --skip-excluded \
  --package-types=PROJECT \
  || true

if [ ! -f "${OUT_DIR}/scan/scan-result.yml" ]; then
  echo "ERROR: Scan did not produce scan-result.yml"
  exit 1
fi

echo "==> Running ORT report"
ort --info --stacktrace --config "${ROOT_DIR}/ort/config/config.yml" \
  report \
  -i "${OUT_DIR}/scan/scan-result.yml" \
  -o "${OUT_DIR}/reports" \
  -f StaticHtml,WebApp,EvaluatedModel

echo "==> ORT completed"
echo "Analyzer: ${OUT_DIR}/analyzer"
echo "Scan:     ${OUT_DIR}/scan"
echo "Reports:  ${OUT_DIR}/reports"
