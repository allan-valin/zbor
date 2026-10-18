"""V2-M2c: per-clip statistics and speaker clusters for FLEURS Macedonian.

FLEURS has no speaker ids, so speakers are recovered by clustering ECAPA voice
embeddings. Writes ~/zbor-build/voice-data/fleurs-mk/clips.csv (one row per clip) and
prints a cluster summary (hours, gender, loudness, noise) to choose the training voice.

    ~/zbor-build/.venv/bin/python tools/voice/prepare_fleurs.py
"""
import csv
from pathlib import Path

import numpy as np
import soundfile as sf
import torch
from sklearn.cluster import AgglomerativeClustering
from speechbrain.inference.speaker import EncoderClassifier

DATA = Path.home() / "zbor-build/voice-data/fleurs-mk"


def level_and_snr(wav: np.ndarray, sr: int) -> tuple[float, float]:
    """RMS level (dBFS) and a crude SNR: loud frames vs quiet frames, 25 ms windows."""
    frame = int(0.025 * sr)
    n = len(wav) // frame
    e = (wav[: n * frame].reshape(n, frame) ** 2).mean(axis=1) + 1e-10
    db = 10 * np.log10(e)
    return float(10 * np.log10((wav ** 2).mean() + 1e-10)), float(np.percentile(db, 95) - np.percentile(db, 10))


def main() -> None:
    rows = []
    for split in ("train", "dev", "test"):
        for r in csv.reader(open(DATA / f"{split}.tsv"), delimiter="\t", quoting=csv.QUOTE_NONE):
            rows.append({"split": split, "file": f"{split}/{r[1]}", "text": r[3], "raw": r[2], "gender": r[6]})
    enc = EncoderClassifier.from_hparams("speechbrain/spkrec-ecapa-voxceleb",
                                         savedir=str(Path.home() / "zbor-build/models-cache/ecapa"),
                                         run_opts={"device": "cuda" if torch.cuda.is_available() else "cpu"})
    embs = []
    for i, r in enumerate(rows):
        wav, sr = sf.read(DATA / r["file"], dtype="float32")
        r["seconds"] = round(len(wav) / sr, 2)
        r["level_db"], r["snr_db"] = (round(x, 1) for x in level_and_snr(wav, sr))
        with torch.no_grad():
            e = enc.encode_batch(torch.from_numpy(wav).unsqueeze(0)).squeeze().cpu().numpy()
        embs.append(e / np.linalg.norm(e))
        if i % 500 == 0:
            print(f"{i}/{len(rows)}", flush=True)
    X = np.stack(embs)
    labels = AgglomerativeClustering(n_clusters=None, metric="cosine", linkage="average",
                                     distance_threshold=0.55).fit_predict(X)
    for r, l in zip(rows, labels):
        r["speaker"] = int(l)
    with open(DATA / "clips.csv", "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    print("\nspeaker  clips  hours  gender        level  snr")
    by = {}
    for r in rows:
        by.setdefault(r["speaker"], []).append(r)
    for spk, rs in sorted(by.items(), key=lambda kv: -sum(x["seconds"] for x in kv[1])):
        h = sum(x["seconds"] for x in rs) / 3600
        if h < 0.1:
            continue
        g = max(set(x["gender"] for x in rs), key=[x["gender"] for x in rs].count)
        print(f"{spk:7d}  {len(rs):5d}  {h:5.2f}  {g:12s}  {np.median([x['level_db'] for x in rs]):5.1f}"
              f"  {np.median([x['snr_db'] for x in rs]):4.1f}")
    print(f"total {len(rows)} clips, {sum(r['seconds'] for r in rows) / 3600:.1f} h, {len(by)} clusters")


if __name__ == "__main__":
    main()
