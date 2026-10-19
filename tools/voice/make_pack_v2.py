"""V2-M4c: pack-v2 = pack-v1's files (left in their release) + the Macedonian neural voice.

    python3 tools/voice/make_pack_v2.py <voice.onnx>
Copies the voice to ~/zbor-build/pack-v2-release/ as mk-voice.onnx + mk-voice.vocab.json and
rewrites app/src/main/assets/pack.json (the manifest the app verifies against).
"""
import hashlib
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
onnx = Path(sys.argv[1])
rel = Path.home() / "zbor-build/pack-v2-release"
rel.mkdir(parents=True, exist_ok=True)
shutil.copy(onnx, rel / "mk-voice.onnx")
shutil.copy(onnx.with_suffix(".vocab.json"), rel / "mk-voice.vocab.json")

manifest_path = ROOT / "app/src/main/assets/pack.json"
m = json.loads(manifest_path.read_text())
files = [f for f in m["files"] if not f["name"].startswith("mk-voice")]
for f in files:
    f.setdefault("release", "pack-v1") if m["version"] == "pack-v1" or f.get("release") else None
    if "release" not in f:
        f["release"] = "pack-v1"
for name in ("mk-voice.onnx", "mk-voice.vocab.json"):
    data = (rel / name).read_bytes()
    files.append({"name": name, "size": len(data), "sha256": hashlib.sha256(data).hexdigest(), "release": "pack-v2"})
m = {"version": "pack-v2", "files": sorted(files, key=lambda f: f["name"])}
manifest_path.write_text(json.dumps(m, indent=4, ensure_ascii=False) + "\n")
print(f"pack-v2: {len(files)} files, +{sum(f['size'] for f in files if f['release'] == 'pack-v2') >> 20} MiB new")
