# Zbor v2 — overnight progress (started 2026-10-19 ~00:50)

Allan approved: V2-M1 (Macedonian eval set) and V2-M2 (voice data), then (00:55) "if
finished and I didn't write anything, continue with the next milestones": so the full
M3 training, M4 (ship the voice) and M5 (pt/en/de voices) follow, still priced in the log
before each long run. Resume rule: on every wake-up, take the
first unchecked item and keep going until all are checked or Allan says he woke up.

## Checklist
- [x] P0 V2 spec + plan written (docs/superpowers/specs, plans)
- [x] M1a eval sentence set (~40, phrase types + all Macedonian letters) → eval/mk_eval_sentences.tsv
- [x] M1b baseline: synthesize the set with today's espeak voice → eval/baseline-espeak/*.wav
- [x] M1c rating sheet for native speakers (HTML, offline, per-sentence 1–5 + comment, export) → eval/
- [x] M2a check availability + licences: FLEURS mk, Common Voice mk, MMS-TTS bul
- [x] M2b download FLEURS mk (~2.6 GB) into ~/zbor-build/voice-data (resumable, background)
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
- 01:10 P0+M1 done. 41 sentences (eval/mk_eval_sentences.tsv; written by Claude, need a native
  check). Baseline sheet eval/rating-baseline.html (1.7 MB, self-contained): eSpeak at 175 and
  140 wpm as blind voices А/Б, so testers' ratings also answer the speed question.
- 01:10 M2a/b: FLEURS mk 2.0 GB (CC-BY-4.0) and MMS-TTS bul 139 MB (CC-BY-NC-4.0) downloaded to
  ~/zbor-build/voice-data. Common Voice mk skipped (Mozilla Data Collective sign-up).
