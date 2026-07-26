#!/usr/bin/env bash
#
# Downloads the Vosk speech-to-text model into app/src/main/assets/.
#
# The model is ~41 MB, so it is deliberately not committed; run this once after
# cloning, and again whenever MODEL_NAME below changes. Without it the app builds
# fine but TranscriptionWorker fails with ModelNotInstalledException.
#
# Usage: scripts/fetch-vosk-model.sh [--force]

set -euo pipefail

MODEL_NAME="vosk-model-small-en-us-0.15"
MODEL_URL="https://alphacephei.com/vosk/models/${MODEL_NAME}.zip"

# Must match VoskTranscriber.MODEL_ASSET_DIR.
ASSET_DIR_NAME="vosk-model-en-us"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS_DIR="${REPO_ROOT}/app/src/main/assets"
TARGET_DIR="${ASSETS_DIR}/${ASSET_DIR_NAME}"

force=false
if [[ "${1:-}" == "--force" ]]; then
  force=true
fi

if [[ -f "${TARGET_DIR}/uuid" ]] && [[ "$(cat "${TARGET_DIR}/uuid")" == "${MODEL_NAME}" ]] && [[ "${force}" == false ]]; then
  echo "✓ ${MODEL_NAME} already present in ${TARGET_DIR#"${REPO_ROOT}/"}"
  echo "  Re-run with --force to download it again."
  exit 0
fi

for cmd in curl unzip; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "error: ${cmd} is required but not installed" >&2
    exit 1
  fi
done

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

echo "Downloading ${MODEL_NAME} (~41 MB)..."
curl -fL --progress-bar -o "${tmp_dir}/model.zip" "${MODEL_URL}"

echo "Extracting..."
unzip -q "${tmp_dir}/model.zip" -d "${tmp_dir}"

extracted="${tmp_dir}/${MODEL_NAME}"
if [[ ! -d "${extracted}" ]]; then
  echo "error: expected ${MODEL_NAME}/ inside the archive; contents were:" >&2
  ls -1 "${tmp_dir}" >&2
  exit 1
fi

mkdir -p "${ASSETS_DIR}"
rm -rf "${TARGET_DIR}"
mv "${extracted}" "${TARGET_DIR}"

# org.vosk.android.StorageService requires a `uuid` file alongside the model: it
# refuses to unpack without one, and compares it against the already-unpacked copy
# to decide whether to re-extract. Using the model name rather than a random value
# means a rebuild only triggers re-extraction when the model actually changes.
printf '%s\n' "${MODEL_NAME}" > "${TARGET_DIR}/uuid"

echo "✓ Installed to ${TARGET_DIR#"${REPO_ROOT}/"} ($(du -sh "${TARGET_DIR}" | cut -f1))"
