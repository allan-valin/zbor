# Zbor v2 — overnight progress (started 2026-10-19 ~00:50)

Allan approved: V2-M1 (Macedonian eval set) and V2-M2 (voice data), then (00:55) "if
finished and I didn't write anything, continue with the next milestones": so the full
M3 training, M4 (ship the voice) and M5 (pt/en/de voices) follow, still priced in the log
before each long run. Resume rule: on every wake-up, take the
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
- [ ] M3d price the full run (hours) → log
- [ ] M3e full fine-tune (memory-capped systemd scope, checkpoints, samples on the eval set)
- [ ] M3f synthesize the eval set with the new voice; package rating sheet A/B (espeak vs neural)
- [ ] M4a export VITS to ONNX, verify output = PyTorch on eval sentences
- [ ] M4b `VitsTtsEngine` in the app (ORT, mk text front-end in Kotlin), JVM/instrumented tests
- [ ] M4c settings switch eSpeak ↔ neural, pack-v2 manifest/download, emulator pass
- [ ] M4d release build, install on Allan's phone (no pack deletion), GitHub release (pack-v2 + v0.2.0)
- [ ] M5 Piper/MMS voices for pt/en/de (only if M4 done)
- [ ] END morning summary in Log

## Log
