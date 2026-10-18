# Zbor — Resume Checkpoint

_Last updated: 2026-10-18_

## What Zbor is
Offline Android/Kotlin split-screen conversation translator, Macedonian ↔
pt-br/en/de, for talking in person with a Macedonian-only speaker where there is
no cell signal. **The whole point is Macedonian TTS** (Google deactivated theirs).

## Status: SP1 · M1 DONE (2026-10-18)
- `:core` module built: `./gradlew :core:test` → 13 tests green (6 commits).
- Deviation from plan: `AwaitingOutputConfirm` carries `sttText` so `Done.sttText`
  stays the speaker's words after an output-seam edit (plan set it to the translation).
- Gradle wrapper 8.13, Kotlin 2.0.21, JDK 21.
- **Spec (approved):** `docs/superpowers/specs/2026-07-19-offline-mk-conversation-translator-design.md`
- **Plan (ready to execute):** `docs/superpowers/plans/2026-07-19-sp1-m1-pipeline-core.md`
- **Spikes (done):** `spikes/` — see findings below.

## Key findings from spikes (2026-07-19)
1. **No free offline Macedonian neural TTS exists** (MMS lacks mk, confirmed 3
   ways). → ship `espeak-ng` (robotic) behind a swappable `TtsEngine`; the real
   deliverable is a **trained mk voice (SP2)**.
2. **NLLB** translates plain sentences well, idioms literally. Good enough for
   survival talk; idiom fix = v2 LLM translator.
3. **mk voice data:** only ~10h multi-speaker FLEURS (+ small Common Voice mk).
   No single-speaker corpus. Recording our own = REJECTED (too big an ask).
   SP2 path = finetune Bulgarian MMS-TTS on FLEURS/CV mk; fallback multi-speaker VITS.

## Decisions locked
- Native Android/Kotlin. Offline-first (online = v2). Target Redmi Note 15 Pro 5G (HOT RAM policy).
- Pipeline: whisper.cpp STT → **edit seam** (correct text) → NLLB-600M → TTS → autoplay.
- Model pack hosted on **GitHub Releases** (user `allan-valin`), one-time home-wifi download, sideload fallback.
- Work split: **SP1** = the app (espeak backend, shippable MVP); **SP2** = trained mk voice.
- SP1 milestones: **M1** pipeline+edit-seam core (planned, pure-Kotlin), M2 real engines+ModelMgr, M3 UI, M4 delivery.

## Next action when you return
Write the **M2 plan** (real engines + ModelMgr, Android `:app` + native):
whisper.cpp JNI, NLLB via ONNX Runtime, espeak-ng JNI. JNI/NNAPI unknowns live
there — spike the riskiest engine first before planning the rest.

## Open items / asks for later
- Confirm a GitHub repo name for Zbor (portfolio) before first push — repo is
  local-only right now, not pushed.
- SP2: decide when to start the mk voice training (needs the FLEURS/CV data pull
  + a native-speaker batched eval set designed up front).

## Other apps (context)
- App #1: workout app (`workout-app/`).
- App #3 (deferred): speech-to-IPA — Allan already has a survey paper + the
  `github.com/allan-valin/phonetic-eval` benchmark repo.
