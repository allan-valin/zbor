"""V2-M3a: a trainable VITS checkpoint = Bulgarian MMS-TTS generator + a discriminator.

Meta's full Bulgarian training checkpoint (with discriminator) is not downloadable (403),
so the discriminator comes from ylacombe/mms-tts-eng-train (same VITS architecture; a
discriminator judges audio realism, not language).

    ~/zbor-build/.venv/bin/python tools/voice/make_train_checkpoint.py
"""
import sys
from pathlib import Path

import torch

V = Path.home() / "zbor-build/voice-data"
sys.path.insert(0, str(Path.home() / "zbor-build/src/finetune-hf-vits"))
from utils import VitsConfig, VitsModelForPreTraining  # noqa: E402
from transformers import AutoTokenizer  # noqa: E402

out = V / "mms-tts-bul-train"
config = VitsConfig.from_pretrained(V / "mms-tts-bul")
model = VitsModelForPreTraining.from_pretrained(V / "mms-tts-bul", config=config)  # discriminator: random init
eng = torch.load(V / "eng-train-pytorch_model.bin", map_location="cpu")
disc = {k[len("discriminator."):]: v for k, v in eng.items() if k.startswith("discriminator.")}
missing, unexpected = model.discriminator.load_state_dict(disc, strict=True), None
print(f"discriminator tensors loaded: {len(disc)}")
model.save_pretrained(out)
AutoTokenizer.from_pretrained(V / "mms-tts-bul").save_pretrained(out)
from transformers import AutoFeatureExtractor  # noqa: E402
try:
    from utils import VitsFeatureExtractor
    VitsFeatureExtractor(sampling_rate=config.sampling_rate).save_pretrained(out)
except Exception as e:  # pragma: no cover
    print("feature extractor:", e)
print("saved", out)
