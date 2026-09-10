#!/usr/bin/env bash
#
# Downloads the sherpa-onnx speech-to-text model into app/src/main/assets/.
#
# The model is ~130 MB, so it is deliberately not committed; run this once after
# cloning, and again whenever the files below change. Without it the app builds
# fine but TranscriptionWorker fails with ModelNotInstalledException.
#
# Model: a Zipformer transducer (English), int8-quantized encoder + fp32
# decoder/joiner - see https://huggingface.co/yfyeung/icefall-asr-multidataset-pruned_transducer_stateless7-2023-05-04
# (Apache-2.0, ungated). This is the same model sherpa-onnx's own Android example
# references as model type 1 in OfflineRecognizer.kt's getOfflineModelConfig().
#
# Usage: scripts/fetch-sherpa-model.sh [--force]

set -euo pipefail

MODEL_REPO="yfyeung/icefall-asr-multidataset-pruned_transducer_stateless7-2023-05-04"
MODEL_BASE_URL="https://huggingface.co/${MODEL_REPO}/resolve/main"

# Maps each destination filename (what SherpaOnnxTranscriber expects) to its path
# in the HF repo. Parallel arrays rather than an associative array - the latter
# needs Bash 4+, which isn't what /usr/bin/env bash resolves to on macOS by default.
DEST_NAMES=("encoder.onnx" "decoder.onnx" "joiner.onnx" "tokens.txt")
SRC_PATHS=(
  "exp/encoder-epoch-30-avg-4.int8.onnx"
  "exp/decoder-epoch-30-avg-4.onnx"
  "exp/joiner-epoch-30-avg-4.onnx"
  "data/lang_bpe_500/tokens.txt"
)

# Must match SherpaOnnxTranscriber.MODEL_ASSET_DIR.
ASSET_DIR_NAME="sherpa-onnx-en"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS_DIR="${REPO_ROOT}/app/src/main/assets"
TARGET_DIR="${ASSETS_DIR}/${ASSET_DIR_NAME}"

force=false
if [[ "${1:-}" == "--force" ]]; then
  force=true
fi

if [[ -f "${TARGET_DIR}/tokens.txt" ]] && [[ "${force}" == false ]]; then
  echo "✓ Model already present in ${TARGET_DIR#"${REPO_ROOT}/"}"
  echo "  Re-run with --force to download it again."
  exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "error: curl is required but not installed" >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

echo "Downloading sherpa-onnx model (~130 MB total)..."
for i in "${!DEST_NAMES[@]}"; do
  dest_name="${DEST_NAMES[$i]}"
  src_path="${SRC_PATHS[$i]}"
  echo "  ${dest_name}"
  curl -fL --progress-bar -o "${tmp_dir}/${dest_name}" "${MODEL_BASE_URL}/${src_path}"
done

mkdir -p "${ASSETS_DIR}"
rm -rf "${TARGET_DIR}"
mkdir -p "${TARGET_DIR}"
mv "${tmp_dir}"/* "${TARGET_DIR}/"

echo "✓ Installed to ${TARGET_DIR#"${REPO_ROOT}/"} ($(du -sh "${TARGET_DIR}" | cut -f1))"
