"""Dump NLLB token ids for fixed sentences, as golden data for NllbTokenizerTest.

Run with any Python that has `tokenizers` installed:
    python tools/dump_tokenizer_goldens.py ~/zbor-build/models/tokenizer.json > mt/src/test/resources/tokenizer_goldens.tsv
"""
import sys
from tokenizers import Tokenizer

SENTENCES = [
    "Where is the bus station?",
    "Quanto custa isto?",
    "Preciso de um médico.",
    "Ich bin allergisch gegen Nüsse.",
    "Здраво, како си?",
    "Каде е автобуската станица?",
    "It's raining cats and dogs!",
    "Não  sei,   obrigado.",
    "Ова чини 25 денари, ве молам.",
    "Fußgängerübergang — Ärger über Öl.",
]

tok = Tokenizer.from_file(sys.argv[1])
for s in SENTENCES:
    ids = tok.encode(s).ids  # post-processor adds eng_Latn ... </s>
    print(s + "\t" + " ".join(map(str, ids)))
