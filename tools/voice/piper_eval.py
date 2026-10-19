"""Compare voices by Whisper CER on one language column of the eval set (lower = clearer).

eSpeak NG makes the IPA phonemes Piper reads (it also spells numbers out), exactly what
the app does through its espeak JNI. Needs onnxruntime (~/zbor-build/.venv).

    ~/zbor-build/.venv/bin/python tools/voice/piper_eval.py [--lang pt|en|de] ~/zbor-build/piper/pt_BR-*.onnx
    ~/zbor-build/.venv/bin/python tools/voice/piper_eval.py --goldens <voice.onnx>   # → eval/pt_piper_goldens.tsv
"""
import csv
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
import onnxruntime as ort

ROOT = Path(__file__).resolve().parents[2]
ESPEAK = Path.home() / "zbor-build/espeak/host/src/espeak-ng"
ENV = {**os.environ, "ESPEAK_DATA_PATH": str(Path.home() / "zbor-build/espeak")}
CLI = Path.home() / "zbor-build/src/whisper.cpp/build-host/bin/whisper-cli"
MODEL = Path.home() / "zbor-build/models/ggml-small-q5_1.bin"


def ipa(text: str, voice: str) -> str:
    out = subprocess.run([ESPEAK, "-q", "--ipa", "-v", voice, text], env=ENV, capture_output=True, text=True).stdout
    return " ".join(l.strip() for l in out.splitlines() if l.strip())


def piper_ids(phonemes: str, cfg: dict) -> list[int]:
    m = cfg["phoneme_id_map"]
    ids = m["^"] + m["_"]
    for ch in phonemes:
        if ch in m:
            ids += m[ch] + m["_"]
    return ids + m["$"]


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


LANGS = {"pt": "pt-br", "en": "en-us", "de": "de"}  # eval column → eSpeak voice (baseline)


def score(make, lang: str = "pt") -> float:
    e = n = 0
    for r in csv.DictReader(open(ROOT / "eval/mk_eval_sentences.tsv"), delimiter="\t"):
        with tempfile.TemporaryDirectory() as t:
            make(r[lang], f"{t}/a.wav")
            subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-i", f"{t}/a.wav", "-ar", "16000", "-ac", "1", f"{t}/b.wav"], check=True)
            hyp = subprocess.run([CLI, "-m", MODEL, "-l", lang, "-nt", "-np", "-t", "8", "-f", f"{t}/b.wav"], capture_output=True, text=True).stdout
        ref = norm(r[lang])
        e += edits(ref, norm(hyp))
        n += len(ref)
    return 100 * e / n


def espeak_for(lang: str):
    def espeak(text, out):
        subprocess.run([ESPEAK, "-v", LANGS[lang], "-w", out, text], env=ENV, check=True)
    return espeak


def goldens(path: str) -> None:
    """IPA + Piper ids per pt sentence, for the app's Kotlin engine and JNI phonemizer tests."""
    cfg = json.load(open(path + ".json"))
    with open(ROOT / "eval/pt_piper_goldens.tsv", "w") as f:
        for r in csv.DictReader(open(ROOT / "eval/mk_eval_sentences.tsv"), delimiter="\t"):
            ph = ipa(r["pt"], "pt-br")
            f.write(f"{r['pt']}\t{ph}\t{' '.join(map(str, piper_ids(ph, cfg)))}\n")


def main() -> None:
    if sys.argv[1] == "--goldens":
        goldens(sys.argv[2])
        return
    lang = "pt"
    args = sys.argv[1:]
    if args[0] == "--lang":
        lang, args = args[1], args[2:]
    print(f"eSpeak {LANGS[lang]}: {score(espeak_for(lang), lang):.1f}%")
    for path in args:
        cfg = json.load(open(path + ".json"))
        sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
        inf = cfg["inference"]

        def piper(text, out, sess=sess, cfg=cfg, inf=inf):
            import soundfile as sf
            ids = np.array([piper_ids(ipa(text, cfg["espeak"]["voice"]), cfg)], dtype=np.int64)
            wav = sess.run(None, {"input": ids, "input_lengths": np.array([ids.shape[1]], np.int64),
                                  "scales": np.array([inf["noise_scale"], inf["length_scale"], inf["noise_w"]], np.float32)})[0].squeeze()
            sf.write(out, wav / max(1e-6, np.abs(wav).max()) * 0.9, cfg["audio"]["sample_rate"])
        print(f"{Path(path).stem}: {score(piper, lang):.1f}%")


if __name__ == "__main__":
    main()
