#!/usr/bin/env bash
# Synthesize the Macedonian eval set with eSpeak NG (the v1 voice) → eval/audio/espeak/<id>.mp3
#   tools/eval_espeak.sh [words-per-minute] [dir-suffix]
set -euo pipefail
cd "$(dirname "$0")/.."
RATE=${1:-175}
BIN=$HOME/zbor-build/espeak/host/src/espeak-ng
OUT=eval/audio/espeak${2:-}; mkdir -p "$OUT"
tail -n +2 eval/mk_eval_sentences.tsv | while IFS=$'\t' read -r id cat mk en; do
  ESPEAK_DATA_PATH=$HOME/zbor-build/espeak "$BIN" -v mk -s "$RATE" -w /tmp/zbor-$$.wav "$mk"
  ffmpeg -nostdin -loglevel error -y -i /tmp/zbor-$$.wav -ac 1 -ar 22050 -b:a 48k "$OUT/$id.mp3"
done
rm -f /tmp/zbor-$$.wav
ls "$OUT" | wc -l
