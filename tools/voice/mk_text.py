"""Macedonian text → the character set of the Bulgarian MMS-TTS voice (V2 front-end).

The Bulgarian vocabulary lacks ѓ ѕ ј љ њ ќ џ; they are written the way Bulgarian
spells those sounds, so the pretrained letter embeddings are reused and fine-tuning
learns the Macedonian pronunciation. The app's Kotlin front-end must produce exactly
this output (checked against eval/mk_text_goldens.tsv).
"""
import json
import re
import unicodedata
from pathlib import Path

MAP = {"ѓ": "гь", "ќ": "кь", "ј": "й", "љ": "ль", "њ": "нь", "џ": "дж", "ѕ": "дз",
       "ѐ": "е", "ѝ": "и"}
VOCAB = set(json.load(open(Path.home() / "zbor-build/voice-data/mms-tts-bul/vocab.json")))


def to_model_text(text: str) -> str:
    t = unicodedata.normalize("NFC", text.lower())
    t = "".join(MAP.get(c, c) for c in t)
    t = "".join(c if c in VOCAB else " " for c in t)
    return re.sub(r"\s+", " ", t).strip()


if __name__ == "__main__":
    import csv
    root = Path(__file__).resolve().parents[2]
    rows = list(csv.DictReader(open(root / "eval/mk_eval_sentences.tsv"), delimiter="\t"))
    with open(root / "eval/mk_text_goldens.tsv", "w") as f:
        for r in rows:
            f.write(f"{r['mk']}\t{to_model_text(r['mk'])}\n")
    print("wrote eval/mk_text_goldens.tsv")
