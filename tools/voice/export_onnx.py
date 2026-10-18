"""V2-M4a: export a fine-tuned VITS (HF VitsModel) to ONNX for the app.

Inputs: input_ids int64 [1, T] (already blank-interspersed), speaking_rate float32 [1].
Output: waveform float32 [1, N] at the model's sampling rate (16 kHz).
Also writes vocab.json next to it and checks ONNX vs PyTorch on the eval sentences.

    ~/zbor-build/.venv/bin/python tools/voice/export_onnx.py <model-dir> <out.onnx>
"""
import csv
import json
import shutil
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
import torch
from transformers import AutoTokenizer, VitsModel

sys.path.insert(0, str(Path(__file__).parent))
from mk_text import to_model_text  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
model_dir, out = Path(sys.argv[1]), Path(sys.argv[2])
model = VitsModel.from_pretrained(model_dir).eval()
tok = AutoTokenizer.from_pretrained(model_dir)


class Wrapper(torch.nn.Module):
    def __init__(self, m):
        super().__init__()
        self.m = m

    def forward(self, input_ids, speaking_rate):
        self.m.speaking_rate = speaking_rate  # VitsModel divides 1.0 by it: stays a graph input
        return self.m(input_ids=input_ids).waveform


w = Wrapper(model)
ids = tok("здраво како си", return_tensors="pt").input_ids
torch.onnx.export(w, (ids, torch.tensor([1.0])), out, input_names=["input_ids", "speaking_rate"],
                  output_names=["waveform"],
                  opset_version=17, dynamo=False, dynamic_axes={"input_ids": {1: "T"}, "waveform": {1: "N"}})
shutil.copy(model_dir / "vocab.json", out.with_suffix(".vocab.json"))

# Verification on a noise-free copy: there ONNX must equal PyTorch, and rate 0.8 must
# give 1.25x the length. (The shipped model keeps the noise, which makes speech natural.)
model.noise_scale = 0.0  # VitsModel copies these from the config at load time
model.noise_scale_duration = 0.0
tmp = out.with_suffix(".check.onnx")
torch.onnx.export(Wrapper(model), (ids, torch.tensor([1.0])), tmp, input_names=["input_ids", "speaking_rate"],
                  output_names=["waveform"],
                  opset_version=17, dynamo=False, dynamic_axes={"input_ids": {1: "T"}, "waveform": {1: "N"}})
model.eval()  # torch.onnx.export leaves the module in training mode (dropout on)
sess = ort.InferenceSession(str(tmp), providers=["CPUExecutionProvider"])
worst, rate_err = 0.0, 0.0
for r in list(csv.DictReader(open(ROOT / "eval/mk_eval_sentences.tsv"), delimiter="\t"))[:8]:
    x = tok(to_model_text(r["mk"]), return_tensors="pt").input_ids
    model.speaking_rate = 1.0
    with torch.no_grad():
        pt = model(input_ids=x).waveform[0].numpy()
    ox = sess.run(None, {"input_ids": x.numpy(), "speaking_rate": np.array([1.0], np.float32)})[0][0]
    # One frame of rounding in a duration shifts every later sample, so compare the
    # length and the 10 ms loudness envelope instead of raw samples.
    env = lambda a: np.sqrt((a[: len(a) // 160 * 160].reshape(-1, 160) ** 2).mean(1))
    ea, eb = env(pt), env(ox)
    n = min(len(ea), len(eb))
    corr = float(np.corrcoef(ea[:n], eb[:n])[0, 1])
    worst = max(worst, 1 - corr, abs(len(pt) - len(ox)) / len(pt))
    slow = sess.run(None, {"input_ids": x.numpy(), "speaking_rate": np.array([0.8], np.float32)})[0][0]
    rate_err = max(rate_err, abs(len(slow) / len(ox) - 1.25))
tmp.unlink()
print(f"{out}: {out.stat().st_size >> 20} MiB; noise-free worst (1-envelope corr | length diff) {worst:.3f}, rate-0.8 length error {rate_err:.3f}")
assert worst < 0.05 and rate_err < 0.1, "ONNX export does not match PyTorch"
