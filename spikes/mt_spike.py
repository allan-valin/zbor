"""NLLB-200 distilled 600M spike. Tests idiom handling via round-trip.
src idiom -> mk -> back to src. User judges the back-translation: did the meaning
survive, or did it go literal-nonsense (the exact complaint about Google MT)."""
import os, torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

MODEL = "facebook/nllb-200-distilled-600M"
print("loading", MODEL)
tok = AutoTokenizer.from_pretrained(MODEL)
model = AutoModelForSeq2SeqLM.from_pretrained(MODEL)
model.eval()

CODE = {"en": "eng_Latn", "pt": "por_Latn", "de": "deu_Latn", "mk": "mkd_Cyrl"}

def translate(text, src, tgt):
    tok.src_lang = CODE[src]
    inp = tok(text, return_tensors="pt")
    with torch.no_grad():
        out = model.generate(**inp,
                             forced_bos_token_id=tok.convert_tokens_to_ids(CODE[tgt]),
                             max_length=128)
    return tok.batch_decode(out, skip_special_tokens=True)[0]

# idioms — the hard case
IDIOMS = [
    ("en", "It's raining cats and dogs."),
    ("en", "Break a leg!"),
    ("en", "That costs an arm and a leg."),
    ("en", "It's a piece of cake."),
    ("pt", "Custa os olhos da cara."),        # very expensive
    ("pt", "Vou chutar o balde."),            # I give up
    ("pt", "Ele pagou o pato."),              # he took the blame
    ("de", "Ich verstehe nur Bahnhof."),      # I don't understand a thing
]
# plain sentences — baseline, should be easy
PLAIN = [
    ("en", "Where is the bus station?"),
    ("pt", "Quanto custa isto?"),
    ("en", "I am allergic to nuts."),
    ("pt", "Preciso de um medico."),
]

def run(title, items):
    print("\n" + "=" * 70 + f"\n{title}\n" + "=" * 70)
    rows = []
    for src, text in items:
        mk = translate(text, src, "mk")
        back = translate(mk, "mk", src)
        print(f"\n[{src}] {text}")
        print(f"  -> mk:   {mk}")
        print(f"  -> back: {back}")
        rows.append((src, text, mk, back))
    return rows

r1 = run("IDIOMS (round-trip: src -> mk -> src)", IDIOMS)
r2 = run("PLAIN SENTENCES (round-trip)", PLAIN)

out = os.path.join(os.path.dirname(__file__), "mt_out.txt")
with open(out, "w") as f:
    f.write("NLLB-200 distilled 600M round-trip spike\n")
    f.write("Judge the 'back' line: did the idiom meaning survive, or go literal?\n")
    for title, rows in [("IDIOMS", r1), ("PLAIN", r2)]:
        f.write(f"\n=== {title} ===\n")
        for src, text, mk, back in rows:
            f.write(f"\n[{src}] {text}\n  mk:   {mk}\n  back: {back}\n")
print("\nwrote", out)
