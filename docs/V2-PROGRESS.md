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
- [x] M2c clip stats: speakers, durations, SNR estimate; pick clean subset; manifest
- [x] M3a training environment (venv in ~/zbor-build, CPU/GPU check, MMS bul loads + speaks)
- [x] M3b data prep: espeak mk phonemes/text → MMS char set mapping; train/val split
- [x] M3c pilot fine-tune (≤30 min, capped scope): s/step, VRAM, samples at checkpoints
- [x] M3d price the full run (hours) → log
- [ ] M3e full fine-tune (memory-capped systemd scope, checkpoints, samples on the eval set)
- [ ] M3f synthesize the eval set with the new voice; package rating sheet A/B (espeak vs neural)
- [x] M4a export VITS to ONNX, verify output = PyTorch on eval sentences
- [x] M4b `VitsTtsEngine` in the app (ORT, mk text front-end in Kotlin), JVM/instrumented tests
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
- 01:30 M2c: 3725 clips / 11.3 h, 11 voices found by ECAPA clustering (FLEURS has no speaker
  ids). Chosen: voice #10, female, 1.62 h, SNR 53 dB (cleanest by far; others 29–45), 100 %
  one gender. Clips with digits dropped (text/audio mismatch): train 361 clips / 1.18 h, test 20.
  Runner-up if a male voice is wanted: #8 (1.12 h, SNR 44.7). Front-end: tools/voice/mk_text.py
  (ѓ→гь ќ→кь ј→й љ→ль њ→нь џ→дж ѕ→дз), goldens eval/mk_text_goldens.tsv.
  NOTE for M4: the app must spell numbers out in Macedonian before the neural voice (NLLB
  writes digits; the voice's alphabet has none).
- 01:30 env: torch 2.14+cu130 on the RTX 4050 (6 GB) in ~/zbor-build/.venv (same Zbor venv),
  ylacombe/finetune-hf-vits (MIT) in ~/zbor-build/src, monotonic_align built.
- 01:40 M3a–c: trainable checkpoint = MMS bul generator + discriminator from ylacombe/mms-tts-eng-train
  (Meta's bul full checkpoint is 403). finetune-hf-vits needed 2 compat patches
  (tools/voice/patch_finetune_hf_vits.sh). Pilot: 200 steps in 1 min 55 s (1.74 it/s, batch 8, fp16).
  Intelligibility proxy (Whisper small, same model as the app; CER on the 41 eval sentences):
  eSpeak 175 wpm 26.6 %, eSpeak 140 wpm 20.9 %, Bulgarian voice untrained 21.8 %, pilot-200 56.0 %
  (early GAN instability, <5 epochs). Side finding: slower eSpeak is more intelligible.
- 01:40 M3d price: staged run, 8 × 1000 steps ≈ 8 × (10 min train + 3 min score) ≈ 1 h 45 min GPU,
  ~2.7 GB disk. Driver: tools/voice/train_stages.sh; table ~/zbor-build/runs/stages.tsv.
- 01:50 M3e running: stage 1 (1000 steps) CER 40.6 % (pilot-200 56 %); 8 stages ≈ until 03:30.
  Queued after it (systemd unit zbor-queue-pfix): the same 8 stages with п spelled щ, because
  the HF MMS tokenizer turns its pad token "п" into id 0 = the blank, so /p/ cannot be learned.
- 01:50 M4a: tools/voice/export_onnx.py — legacy tracer works (dynamo fails on VITS splines);
  speaking_rate is a graph input (speed slider). Verified on a noise-free copy: envelope match
  1.000, rate 0.8 → 1.25× length (±0.064 rounding). Gotcha: torch.onnx.export leaves the model
  in train mode (dropout) — the first comparisons were wrong because of that.
- 01:50 M4b: :tts module (MkNumbers, MkTextFrontEnd = Python goldens, VitsVocab reproduces the
  HF п-quirk exactly on all 41 sentences, VitsTtsEngine), 9 JVM tests green on the pilot ONNX.
  pack: per-file `release` (pack-v2 adds files, pack-v1 files stay where they are), 11 tests.
  App: RoutedTts (mk → neural if present + switched on, else eSpeak), settings switch EN/PT/DE,
  ResidencyPolicy.HOT now keeps TTS too (108 MB voice would reload every turn).
