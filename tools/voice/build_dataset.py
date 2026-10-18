"""V2-M2c: the training set for one FLEURS speaker, as an HF `audiofolder` dataset.

    ~/zbor-build/.venv/bin/python tools/voice/build_dataset.py [speaker=10] [held_out=20]

Drops clips whose text has digits (the voice's alphabet cannot spell them, so text and
audio would disagree). Text goes through mk_text.to_model_text.
"""
import csv
import json
import random
import re
import sys
from pathlib import Path

from mk_text import to_model_text

SRC = Path.home() / "zbor-build/voice-data/fleurs-mk"
spk = sys.argv[1] if len(sys.argv) > 1 else "10"
held = int(sys.argv[2]) if len(sys.argv) > 2 else 20
OUT = Path.home() / f"zbor-build/voice-data/train-spk{spk}"

rows = [r for r in csv.DictReader(open(SRC / "clips.csv")) if r["speaker"] == spk and not re.search(r"\d", r["text"])]
random.Random(7).shuffle(rows)
splits = {"test": rows[:held], "train": rows[held:]}
for split, rs in splits.items():
    d = OUT / split
    d.mkdir(parents=True, exist_ok=True)
    # JSON lines: datasets 3.x rejected the equivalent metadata.csv ("file_name must be present").
    with open(d / "metadata.jsonl", "w") as f:
        for r in rs:
            name = Path(r["file"]).name
            link = d / name
            if not link.exists():
                link.symlink_to(SRC / r["file"])
            f.write(json.dumps({"file_name": name, "text": to_model_text(r["text"])}, ensure_ascii=False) + "\n")
hours = sum(float(r["seconds"]) for r in splits["train"]) / 3600
print(f"{OUT}: train {len(splits['train'])} clips ({hours:.2f} h), test {len(splits['test'])}")
