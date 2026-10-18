# Zbor v2 — overnight progress (started 2026-10-19 ~00:50)

Allan approved: V2-M1 (Macedonian eval set) and V2-M2 (voice data). M3 (fine-tune
Bulgarian MMS-TTS → Macedonian) only groundwork + a short timed pilot, so the full
training can be priced in hours before it runs. Resume rule: on every wake-up, take the
first unchecked item and keep going until all are checked or Allan says he woke up.

## Checklist
- [ ] P0 V2 spec + plan written (docs/superpowers/specs, plans)
- [ ] M1a eval sentence set (~40, phrase types + all Macedonian letters) → eval/mk_eval_sentences.tsv
- [ ] M1b baseline: synthesize the set with today's espeak voice → eval/baseline-espeak/*.wav
- [ ] M1c rating sheet for native speakers (HTML, offline, per-sentence 1–5 + comment, export) → eval/
- [ ] M2a check availability + licences: FLEURS mk, Common Voice mk, MMS-TTS bul
- [ ] M2b download FLEURS mk (~2.6 GB) into ~/zbor-build/voice-data (resumable, background)
- [ ] M2c clip stats: speakers, durations, SNR estimate; pick clean subset; manifest
- [ ] M3a training environment (venv in ~/zbor-build, CPU/GPU check, MMS bul loads + speaks)
- [ ] M3b data prep: espeak mk phonemes/text → MMS char set mapping; train/val split
- [ ] M3c pilot fine-tune (≤30 min, capped scope): s/step, VRAM, samples at checkpoints
- [ ] M3d price the full run (hours) + recommendation → this file + morning summary

## Log
