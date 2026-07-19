# Zbor — Offline Bidirectional Conversation Translator (MVP Design)

**Date:** 2026-07-19
**Status:** Approved design, pre-implementation
**Working title:** Zbor (Macedonian *збор*, "word") — rename freely

## Problem

Google Translate can translate Macedonian and transcribe its audio, but Google
deactivated Macedonian text-to-speech (TTS) for quality reasons. In Google's
conversation mode this makes one side of a spoken exchange silent/truncated,
breaking real back-and-forth. The user needs to converse in person with a
Macedonian speaker who speaks no other language, in a location with **no working
cell reception** for the user's SIM.

Two secondary complaints with existing tools, to be improved here:
1. Speech-to-text (STT) mistakes from accents and nonsensical detected words get
   translated and spoken immediately, with no chance to correct.
2. Translation quality — idiomatic expressions rendered literally, producing
   nonsense.

## Goals

- Restore Macedonian TTS via a non-Google engine.
- Support a two-sided, in-person conversation: split-screen, tap-to-talk, the
  top half rotated to face the person across the table.
- Insert an **edit seam**: after STT and before translation/TTS, the speaker can
  correct the transcribed text. Nothing advances until confirmed.
- Work **fully offline** at the destination (no reception).
- Be **free** — no paid cloud APIs, no per-use cost.
- Bidirectional and multi-language on the user's side: **pt-br, en, de**, each
  paired with **mk**. Usable by the Macedonian-only person too.

## Non-Goals (deferred, not in MVP)

- **Online mode.** Offline-first, because the destination has no signal. Online
  (cloud quality / practice at home) is a v2 follow-up.
- **Tap-a-word-to-re-record.** v1 edit is a full editable text field. Word-level
  re-record is v2. (This deferral was the author-of-the-spec's call during
  brainstorming, accepted by the user.)
- **Better-than-NLLB idiom handling.** Using an LLM translator for idioms is a
  possible v2 upgrade; MVP uses NLLB, which already beats Google statistical MT
  on idioms.
- **Non-Android platforms.** Android/Kotlin only, targeting the user's Redmi.

## Target Device

Redmi Note 15 Pro 5G (launched Dec 2025), 8/12GB RAM, Dimensity SoC with APU.
Comfortably runs the "HOT" residency policy (below). ONNX Runtime can offload
MT+TTS via NNAPI/NPU. Per-turn latency target ~3-5s.

## Stack

- **Platform:** Native Android, Kotlin.
- **STT:** whisper.cpp (native, JNI), `small` model, GGUF q5, ~190MB. Single
  multilingual model covers mk/pt/en/de.
- **Translation (MT):** NLLB-200 distilled 600M, ONNX Runtime Android, ONNX int8,
  ~650MB. Single multilingual model covers all pairs and directions.
- **TTS:** Meta MMS-TTS (VITS), ONNX Runtime Android, one ~120MB model per
  language → mk/pt/en/de ≈ ~480MB.
- **Total model pack:** ~1.3GB.

Rationale: whisper.cpp is the best on-device STT native lib. ONNX Runtime is one
mature Android runtime shared by MT and TTS, with int8 quantization and NNAPI.
whisper + NLLB being single multilingual models makes 4 languages cost almost
the same as 1 (only per-language MMS TTS voices add size).

## Architecture

Five isolated, independently testable units plus the UI.

```
ConversationUI  (split-screen, icon-driven, per-side language)
      │  audio in / text edit / play
PipelineOrchestrator  (per-turn state machine; owns the edit pause)
      ├── STTEngine   (whisper.cpp)      depends on ModelMgr
      ├── MTEngine    (NLLB / ONNX)      depends on ModelMgr
      ├── TTSEngine   (MMS / ONNX)       depends on ModelMgr
      └── ModelMgr    (load / evict / RAM policy)
```

| Unit | Job | Depends on |
|---|---|---|
| `ConversationUI` | split-screen, tap-to-talk, editable text box, replay, language pickers | Orchestrator |
| `PipelineOrchestrator` | drives one turn's state machine, owns the edit pause | STT, MT, TTS, ModelMgr |
| `STTEngine` | audio → text | ModelMgr |
| `MTEngine` | text + src/tgt lang → translated text | ModelMgr |
| `TTSEngine` | text + lang → audio | ModelMgr |
| `ModelMgr` | load/evict models, keep RAM peak low, adapt to memory pressure | — |

### Data flow (one turn; mk speaks → user reads/hears pt)

```
1. mk taps their half → record audio (16kHz mono)
2. STT(whisper, lang=mk) → mk text
3. EDIT SEAM: show mk text, editable, mk person corrects → confirm (✓)   [HARD STOP]
4. MT(NLLB, mk→pt) → pt text
5. show pt text on user's half (+ optional second edit seam, default off)
6. TTS(MMS, pt) → audio → autoplay
```

Reverse direction is symmetric. The orchestrator's state machine will not leave
`AWAITING_CONFIRM` until the user confirms; STT garbage cannot silently proceed.

## Model Delivery

- Ship a small APK (~30MB). **On first launch, download the ~1.3GB model pack
  over wifi once**, into app-private storage. Offline forever after.
- Each model is a versioned asset with a checksum; download is resumable (spotty
  wifi).
- Fallback: manual sideload of the pack to a known folder; the app detects and
  imports it. No Play Store dependency.
- If the pack is missing/corrupt at runtime, block the app with a re-download
  prompt (it cannot function offline without it).

## RAM Strategy (residency policy)

Every turn uses STT + MT; TTS runs once at the end. `ModelMgr` picks a policy
from detected device RAM and can drop to a colder one live via `onTrimMemory()`.

```
≥6GB  → HOT:  keep whisper + NLLB resident; lazy-load TTS per turn, evict after.
              Peak ~2.5GB. No reload latency on the hot path. (Redmi default)
4-6GB → WARM: keep whisper resident; load/evict NLLB + TTS per turn.
              Peak ~1.5GB. +1-2s reload per turn.
<4GB  → COLD: sequential load/evict everything. Peak ~1GB. Slowest.
```

Loading from disk is the latency villain. HOT keeps the every-turn models
resident; only TTS pays a load cost, hidden because it runs while the user is
already reading the translated text.

## UX

- Split screen; top half rotated 180° for the person across the table.
- Each half: large **tap-to-talk** mic, a **flag/language chip**, an editable
  text area, a **▶ replay** button. No text menus on the hot path — the
  Macedonian-only person must operate it with zero reading of pt/en/de.
- Recording state shown with obvious color + waveform.
- Language pickers are a flag grid, one-time setup per side.

### Edit seam

- v1: full editable text field. Tap, keyboard-correct, confirm. Covers the
  common cases (fix a wrong word, delete garbage detection).
- Optional second seam on the translated (output) text, toggle in settings,
  default **off** to keep the flow fast.

## Error Handling

- Mic permission denied → clear icon prompt.
- STT empty/garbage → stay in the edit seam, don't advance (the seam is exactly
  the mitigation).
- Model pack missing/corrupt → block with re-download prompt.
- Model load OOM under pressure → `ModelMgr` drops to a colder policy, retries,
  surfaces a "slower mode" notice.
- TTS failure → still show the translated text (degraded but usable).

## Testing

- `STTEngine` / `MTEngine` / `TTSEngine`: golden-file tests (fixed audio/text in
  → assert output). Instrumented/on-device because of native libs.
- `PipelineOrchestrator`: state-machine unit tests with fake engines — assert it
  blocks at the edit seam and never skips confirm.
- `ModelMgr`: policy-transition tests under simulated memory pressure.
- Manual: the emulator can't run whisper/NNAPI well, so a **real Redmi pass is
  required each batch** (consistent with the author's existing app discipline).

## Open Questions / Risks

- MMS-TTS Macedonian voice quality — validate it is intelligible early; it is the
  whole reason for the project.
- NLLB idiom quality on mk↔pt/de specifically — spot-check; LLM translator is the
  v2 escape hatch if inadequate.
- whisper `small` accuracy on strong Macedonian accents — `medium` is a fallback
  at a latency/RAM cost.
- ONNX int8 NLLB accuracy vs fp16 — verify quantization does not degrade badly.
