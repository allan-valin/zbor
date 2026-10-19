"""V2-M3 variant: short phrase clips cut from one speaker's long FLEURS recordings.

The voice trained on 5–32 s clips garbles short sentences, which are most of what the
app says. Whisper word timestamps (whisper.cpp, -ml 1) locate pauses; words are aligned
to the official transcript, and clips are cut in the middle of pauses ≥ 200 ms so every
phrase keeps its true text. Output: a dataset with the original clips plus the phrases.

    ~/zbor-build/.venv/bin/python tools/voice/split_phrases.py [speaker=10] [pfix]
"""
import csv
import difflib
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

import soundfile as sf

sys.path.insert(0, str(Path(__file__).parent))
from mk_text import to_model_text  # noqa: E402

SRC = Path.home() / "zbor-build/voice-data/fleurs-mk"
CLI = Path.home() / "zbor-build/src/whisper.cpp/build-host/bin/whisper-cli"
MODEL = Path.home() / "zbor-build/models/ggml-small-q5_1.bin"
spk = sys.argv[1] if len(sys.argv) > 1 else "10"
p_fix = "pfix" in sys.argv
BASE = Path.home() / f"zbor-build/voice-data/train-spk{spk}{'-pfix' if p_fix else ''}"
OUT = Path.home() / f"zbor-build/voice-data/train-spk{spk}{'-pfix' if p_fix else ''}-short"
CACHE = Path.home() / "zbor-build/voice-data/word-times"
MIN_GAP, MIN_LEN, MAX_LEN = 0.2, 1.0, 6.0


def words(s: str) -> list[str]:
    return re.sub(r"[^\w\s]", " ", s.lower()).split()


def word_times(wav: Path) -> list[tuple[str, float, float]]:
    cache = CACHE / (wav.stem + ".json")
    if not cache.exists():
        with tempfile.TemporaryDirectory() as t:
            subprocess.run([CLI, "-m", MODEL, "-l", "mk", "-ml", "1", "-sow", "-np", "-t", "6", "-oj",
                            "-of", f"{t}/o", "-f", wav], capture_output=True, check=True)
            cache.write_text(Path(f"{t}/o.json").read_text())
    segs = json.loads(cache.read_text())["transcription"]
    out = []
    for s in segs:
        for w in words(s["text"]):
            out.append((w, s["offsets"]["from"] / 1000, s["offsets"]["to"] / 1000))
    return out


def main() -> None:
    CACHE.mkdir(parents=True, exist_ok=True)
    clips = {Path(r["file"]).name: r for r in csv.DictReader(open(SRC / "clips.csv"))}
    train = [json.loads(l) for l in open(BASE / "train/metadata.jsonl")]
    (OUT / "train").mkdir(parents=True, exist_ok=True)
    meta = []
    n_phr = 0
    for i, row in enumerate(train):
        name = row["file_name"]
        src = SRC / clips[name]["file"]
        meta.append(row)  # keep the long clip
        link = OUT / "train" / name
        if not link.exists():
            link.symlink_to(src)
        ref = words(clips[name]["text"])
        hyp = word_times(src)
        sm = difflib.SequenceMatcher(a=ref, b=[w for w, _, _ in hyp], autojunk=False)
        t = {}  # ref index -> (start, end)
        for a, b, size in sm.get_matching_blocks():
            for k in range(size):
                t[a + k] = hyp[b + k][1:]
        cuts = [0.0]
        cut_words = [0]
        for k in range(len(ref) - 1):
            if k in t and k + 1 in t and t[k + 1][0] - t[k][1] >= MIN_GAP:
                cuts.append((t[k][1] + t[k + 1][0]) / 2)
                cut_words.append(k + 1)
        audio, sr = sf.read(src, dtype="float32")
        cuts.append(len(audio) / sr)
        cut_words.append(len(ref))
        for j in range(len(cuts) - 1):
            a, b = cuts[j], cuts[j + 1]
            if not (MIN_LEN <= b - a <= MAX_LEN):
                continue
            text = to_model_text(" ".join(ref[cut_words[j]:cut_words[j + 1]]), p_fix)
            if not text:
                continue
            fn = f"{Path(name).stem}_p{j}.wav"
            sf.write(OUT / "train" / fn, audio[int(a * sr):int(b * sr)], sr)
            meta.append({"file_name": fn, "text": text})
            n_phr += 1
        if i % 50 == 0:
            print(f"{i}/{len(train)} clips, {n_phr} phrases", flush=True)
    with open(OUT / "train/metadata.jsonl", "w") as f:
        for m in meta:
            f.write(json.dumps(m, ensure_ascii=False) + "\n")
    test = OUT / "test"
    if not test.exists():
        test.symlink_to(BASE / "test")
    secs = sum(sf.info(OUT / "train" / m["file_name"]).duration for m in meta[-n_phr:]) if n_phr else 0
    print(f"{OUT}: {len(meta) - n_phr} long clips + {n_phr} phrases ({secs / 60:.0f} min)")


if __name__ == "__main__":
    main()
