#!/usr/bin/env bash
# Assemble the Zbor offline model pack from the downloaded sources (docs/BUILD.md).
#   tools/build_pack.sh [models-dir] [pack-dir]
set -euo pipefail
cd "$(dirname "$0")/.."
SRC=${1:-$HOME/zbor-build/models}
PACK=${2:-$HOME/zbor-build/pack}
PY=${PY:-$HOME/zbor-build/.venv/bin/python}
mkdir -p "$PACK"

tool() { ./gradlew -q :mt:packTool "-PpackArgs=$*"; }

cp "$SRC/ggml-small-q5_1.bin" "$PACK/"
tool tokenizer "$SRC/tokenizer.json" "$PACK/nllb-tokenizer.bin"
"$PY" -I tools/fix_lm_head.py "$SRC/decoder_model_merged_quantized.onnx" "$SRC/decoder_lm.onnx"
tool ort "$SRC/encoder_model_quantized.onnx" "$PACK/nllb-encoder.ort"
tool ort "$SRC/decoder_lm.onnx" "$PACK/nllb-decoder.ort"
rm -rf "$PACK/espeak-ng-data" && cp -r "$HOME/zbor-build/espeak/espeak-ng-data" "$PACK/"
ls -la "$PACK" && du -sh "$PACK"
