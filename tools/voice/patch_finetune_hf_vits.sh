#!/usr/bin/env bash
# Compatibility patches for ylacombe/finetune-hf-vits with transformers 4.57 (idempotent).
set -euo pipefail
F=${1:-$HOME/zbor-build/src/finetune-hf-vits}/run_vits_finetuning.py
sed -i 's/^from transformers.utils import send_example_telemetry/send_example_telemetry = lambda *a, **k: None  # removed upstream/' "$F"
# matplotlib ≥3.10 removed FigureCanvas.tostring_rgb: take RGB from the RGBA buffer instead.
P=${1:-$HOME/zbor-build/src/finetune-hf-vits}/utils/plot.py
sed -i 's/data = np.frombuffer(fig.canvas.tostring_rgb(), dtype=np.uint8)/data = np.asarray(fig.canvas.buffer_rgba())[..., :3].copy()/' "$P"
sed -i 's/data = data.reshape(fig.canvas.get_width_height()\[::-1\] + (3,))/pass  # already (h, w, 3)/' "$P"
