"""Synthesize the eval set with a VITS/MMS model → eval/audio/<name>/<id>.wav (16 kHz) + .mp3.

    ~/zbor-build/.venv/bin/python tools/voice/synth_eval.py <model-dir> <name>
"""
import csv
import subprocess
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
import torch
from transformers import AutoTokenizer, VitsModel

sys.path.insert(0, str(Path(__file__).parent))
from mk_text import to_model_text  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
model_dir, name = sys.argv[1], sys.argv[2]
out = ROOT / "eval/audio" / name
out.mkdir(parents=True, exist_ok=True)
tok = AutoTokenizer.from_pretrained(model_dir)
model = VitsModel.from_pretrained(model_dir).eval()
torch.manual_seed(0)
for r in csv.DictReader(open(ROOT / "eval/mk_eval_sentences.tsv"), delimiter="\t"):
    ids = tok(to_model_text(r["mk"]), return_tensors="pt")
    with torch.no_grad():
        wav = model(**ids).waveform[0].numpy()
    wav = wav / max(1e-6, np.abs(wav).max()) * 0.9
    sf.write(out / f"{r['id']}.wav", wav, model.config.sampling_rate)
    subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-i", out / f"{r['id']}.wav",
                    "-b:a", "48k", out / f"{r['id']}.mp3"], check=True)
print(f"{name}: {len(list(out.glob('*.mp3')))} clips")
