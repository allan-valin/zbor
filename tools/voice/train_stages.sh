#!/usr/bin/env bash
# V2-M3e: fine-tune in stages of STEPS, scoring every stage with the ASR round-trip.
#   tools/voice/train_stages.sh <first-stage> <last-stage> [steps=1000] [lr=2e-5]
# Stage N starts from stage N-1 (stage 1 from the pilot base); results → ~/zbor-build/runs/stages.tsv
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
R=$HOME/zbor-build/runs; PY=$HOME/zbor-build/.venv/bin/python
FIRST=$1; LAST=$2; STEPS=${3:-1000}; LR=${4:-2e-5}
NAME=${NAME:-stage}                                  # run prefix (e.g. stage-pfix)
DATASET=${DATASET:-$HOME/zbor-build/voice-data/train-spk10}
for N in $(seq "$FIRST" "$LAST"); do
  PREV=$([ "$N" = 1 ] && echo "$HOME/zbor-build/voice-data/mms-tts-bul-train" || echo "$R/$NAME-$((N-1))")
  CFG=$R/$NAME-$N.json
  "$PY" - "$REPO/tools/voice/configs/pilot.json" "$CFG" "$PREV" "$R/$NAME-$N" "$STEPS" "$LR" "$N" "$DATASET" <<'PYEOF'
import json, sys
c = json.load(open(sys.argv[1]))
c.update(model_name_or_path=sys.argv[3], output_dir=sys.argv[4], max_steps=int(sys.argv[5]),
         learning_rate=float(sys.argv[6]), eval_steps=10**9, save_steps=10**9, seed=456 + int(sys.argv[7]),
         dataset_name=sys.argv[8])
json.dump(c, open(sys.argv[2], "w"), indent=1)
PYEOF
  ( cd "$HOME/zbor-build/src/finetune-hf-vits" && "$HOME/zbor-build/.venv/bin/accelerate" launch --num_processes 1 \
      --mixed_precision fp16 run_vits_finetuning.py "$CFG" > "$R/$NAME-$N.log" 2>&1 )
  "$PY" "$REPO/tools/voice/synth_eval.py" "$R/$NAME-$N" "$NAME-$N" > /dev/null 2>&1
  CER=$(python3 "$REPO/tools/voice/asr_cer.py" "$NAME-$N" | grep -oE "[0-9.]+%")
  echo -e "$NAME\t$N\t$((N*STEPS))\t$LR\t$CER\t$(date +%H:%M)" | tee -a "$R/stages.tsv"
  rm -rf "$R/$NAME-$N/runs"  # tensorboard events
done
echo "$NAME-DONE" >> "$R/stages.tsv"
