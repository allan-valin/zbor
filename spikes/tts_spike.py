"""MMS-TTS Macedonian spike. Synthesize mk sentences -> WAV.
User judges: audio quality + that words are actually spoken (cannot verify mk meaning)."""
import os, numpy as np, torch
from scipy.io.wavfile import write as wavwrite
from transformers import VitsModel, AutoTokenizer

OUT = os.path.join(os.path.dirname(__file__), "tts_out")
os.makedirs(OUT, exist_ok=True)

MODEL = "facebook/mms-tts-mkd"
print("loading", MODEL)
tok = AutoTokenizer.from_pretrained(MODEL)
model = VitsModel.from_pretrained(MODEL)
model.eval()
sr = model.config.sampling_rate
print("sampling_rate", sr)

# (filename, macedonian text, english gloss for labeling only)
SENTENCES = [
    ("01_greeting",   "Здраво, како си?",                                  "Hello, how are you?"),
    ("02_dontspeak",  "Јас не зборувам македонски добро.",                 "I don't speak Macedonian well."),
    ("03_toilet",     "Каде е тоалетот?",                                  "Where is the toilet?"),
    ("04_thanks",     "Многу ти благодарам за помошта.",                   "Thank you very much for the help."),
    ("05_numbers",    "Еден, два, три, четири, пет.",                      "One, two, three, four, five."),
    ("06_long",       "Утре ќе патувам во Скопје со автобус во осум часот.", "Tomorrow I travel to Skopje by bus at eight."),
]

manifest = []
for name, mk, gloss in SENTENCES:
    inp = tok(mk, return_tensors="pt")
    with torch.no_grad():
        wav = model(**inp).waveform[0].cpu().numpy()
    wav = wav / (np.max(np.abs(wav)) + 1e-9)          # normalize
    pcm = (wav * 32767).astype(np.int16)
    path = os.path.join(OUT, name + ".wav")
    wavwrite(path, sr, pcm)
    dur = len(pcm) / sr
    manifest.append(f"{name}.wav | {dur:4.1f}s | {mk}  ||  ({gloss})")
    print("wrote", path, f"{dur:.1f}s")

with open(os.path.join(OUT, "MANIFEST.txt"), "w") as f:
    f.write("MMS-TTS Macedonian spike output\n")
    f.write("file | duration | macedonian  ||  (english gloss)\n\n")
    f.write("\n".join(manifest) + "\n")
print("\nMANIFEST:\n" + "\n".join(manifest))
