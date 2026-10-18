"""Intelligibility proxy: transcribe a voice's eval clips with the app's Whisper model and
report character error rate against the sentences (lower = easier to understand).

    python3 tools/voice/asr_cer.py <voice-name> [<voice-name> ...]
"""
import csv
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLI = Path.home() / "zbor-build/src/whisper.cpp/build-host/bin/whisper-cli"
MODEL = Path.home() / "zbor-build/models/ggml-small-q5_1.bin"


def norm(s: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"[^\w\s]", " ", s.lower())).strip()


def edits(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


rows = list(csv.DictReader(open(ROOT / "eval/mk_eval_sentences.tsv"), delimiter="\t"))
for name in sys.argv[1:]:
    d = ROOT / "eval/audio" / name
    tot_e = tot_n = 0
    out = []
    for r in rows:
        src = d / f"{r['id']}.wav"
        if not src.exists():
            src = d / f"{r['id']}.mp3"
        with tempfile.NamedTemporaryFile(suffix=".wav") as t:
            subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-i", src, "-ar", "16000", "-ac", "1", t.name], check=True)
            hyp = subprocess.run([CLI, "-m", MODEL, "-l", "mk", "-nt", "-np", "-t", "8", "-f", t.name],
                                 capture_output=True, text=True).stdout.strip()
        ref, h = norm(r["mk"]), norm(hyp)
        e = edits(ref, h)
        tot_e += e
        tot_n += len(ref)
        out.append(f"{r['id']}\t{e / max(1, len(ref)):.2f}\t{h}")
    (d / "asr.tsv").write_text("\n".join(out) + "\n")
    print(f"{name}: CER {100 * tot_e / tot_n:.1f}%")
