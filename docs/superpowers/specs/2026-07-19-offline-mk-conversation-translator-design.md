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
- **Better-than-NLLB idiom handling.** MVP uses NLLB. The 2026-07-19 spike showed
  NLLB handles plain sentences well but renders most idioms literally (same
  complaint as Google) — see Spike Findings. An LLM translator is the v2 upgrade
  for idioms; on-device it is large/heavy, hence deferred.
- **Text-only output (no TTS).** Explicitly rejected: TTS *is* the point of the
  app. Showing Macedonian text only is what Google Translate already does, so a
  text-only build would have no reason to exist.
- **Non-Android platforms.** Android/Kotlin only, targeting the user's Redmi.

## Target Device

Redmi Note 15 Pro 5G (launched Dec 2025), 8/12GB RAM, Dimensity SoC with APU.
Comfortably runs the "HOT" residency policy (below). ONNX Runtime can offload
MT+TTS via NNAPI/NPU. Per-turn latency target ~3-5s.

## Stack

- **Platform:** Native Android, Kotlin.
- **STT:** whisper.cpp (native, JNI), `small` model, GGUF q5, ~190MB. Single
  multilingual model covers mk/pt/en/de. Model file is path-swappable behind
  `STTEngine` — a Macedonian-tuned whisper (e.g. `Macedonian-ASR/buki-whisper`,
  `whisper-large-v3-macedonian-asr`) can be dropped in for the mk direction
  later with no interface change.
- **Translation (MT):** NLLB-200 distilled 600M, ONNX Runtime Android, ONNX int8,
  ~650MB. Single multilingual model covers all pairs and directions. Good on
  plain sentences; weak on idioms (see Spike Findings).
- **TTS (the crux):** No free offline Macedonian neural voice exists (see Spike
  Findings). Two-backend `TTSEngine`:
  - **Backend A — `espeak-ng` (mk):** formant synthesis, offline, free, tiny,
    robotic. Ships day 1 so the app is functional immediately. For pt/en/de
    output either espeak-ng or MMS-TTS (those languages DO exist in MMS).
  - **Backend B — custom-trained Macedonian neural voice (the real deliverable):**
    Piper/VITS finetuned on Macedonian audio. Replaces backend A for mk once
    trained. Same `TTSEngine` interface — swap is invisible to the pipeline.
- **Total model pack:** ~0.9GB at launch (whisper + NLLB + espeak data), +~60MB
  when the trained Piper mk voice lands.

Rationale: whisper.cpp is the best on-device STT native lib. ONNX Runtime is one
mature Android runtime shared by MT and TTS. whisper + NLLB being single
multilingual models makes 4 languages cost almost the same as 1. TTS is the
hard gear — see the dedicated sub-project below.

## Architecture

Five isolated, independently testable units plus the UI.

```
ConversationUI  (split-screen, icon-driven, per-side language)
      │  audio in / text edit / play
PipelineOrchestrator  (per-turn state machine; owns the edit pause)
      ├── STTEngine   (whisper.cpp)               depends on ModelMgr
      ├── MTEngine    (NLLB / ONNX)               depends on ModelMgr
      ├── TTSEngine   (backend: espeak-ng | Piper) depends on ModelMgr
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

## Spike Findings (2026-07-19)

Ran two desktop spikes before building. Two earlier assumptions in this spec
were wrong and are corrected here.

1. **No free offline Macedonian neural TTS exists.** Confirmed three ways:
   `facebook/mms-tts-mkd` absent on Hugging Face (org returns 401 for missing
   repos; `eng`/`por` return 200); no Piper/VITS/Coqui Macedonian voice in HF
   search; Meta's own CDN returns 403 for `mms/tts/mkd.tar.gz` while `eng.tar.gz`
   returns 200. **Correction:** an earlier version of this spec assumed MMS-TTS
   covered Macedonian. It does not. Consequence: the two-backend TTS strategy
   (espeak-ng now, trained Piper voice later) in the Stack section.

2. **NLLB: strong on plain sentences, weak on idioms.** Round-trip test
   (`src → mk → src`): plain sentences ("Where is the bus station?",
   "Quanto custa isto?", "Preciso de um médico") came back correct; most idioms
   went literal ("raining cats and dogs" → "Дождува мачки и кучиња";
   "break a leg" → "Скрши си нога"; "piece of cake" → "парче торта"). One pt
   idiom survived ("custa os olhos da cara" → "многу скапо"). One safety-ish
   miss: "allergic to nuts" fuzzed to "food". **Correction:** an earlier version
   claimed NLLB beats Google on idioms — it does not, reliably. It is fine for
   the plain speech that dominates travel/survival conversation; idiom fidelity
   waits for the v2 LLM translator.

3. **Bonus — Macedonian STT is well served.** Dedicated mk-tuned models exist
   (`Macedonian-ASR/buki-whisper`, `whisper-large-v3-macedonian-asr`, mk
   wav2vec2). STT is the strongest gear, upgradable behind `STTEngine`.

## Sub-Project Split

This work decomposes into two independently plannable pieces:

- **SP1 — The App.** Android/Kotlin: UI, orchestrator + edit seam, STT (whisper),
  MT (NLLB), `TTSEngine` with the **espeak-ng backend**, ModelMgr, delivery.
  Fully shippable and useful on its own (robotic mk voice). This is the MVP.
- **SP2 — The Macedonian Voice.** Train a Piper/VITS mk voice and drop it in
  behind the existing `TTSEngine`. This is the project's true payoff. Runs
  after/parallel to SP1 because the interface is already stubbed by the espeak
  backend. Piper phonemizes via espeak-ng (mk phonemes exist), so SP1's espeak
  work feeds SP2.

  **Data availability (spiked 2026-07-19):** There is **no single-speaker mk TTS
  corpus** off the shelf (no LJSpeech-equivalent; OpenSLR has no Macedonian). The
  only meaningful free mk speech data is **FLEURS mk (~3725 clips, ~10h, clean,
  transcribed, but multi-speaker)** — the `shunyalabs/macedonian-speech-dataset`
  repo is a byte-identical repackage of it. Common Voice mk exists but is now
  behind Mozilla Data Collective (extra access hoop) and is small/multi-speaker.
  `Speech-data/Macedonian-Speech-Dataset` is a single 41-second promo clip
  (useless). Ranked training paths:
  - **A — finetune single-speaker Bulgarian MMS-TTS on FLEURS mk (+ Common Voice
    mk).** bg VITS is single-voice; adapt phonetics to mk. One consistent
    identity, no recording. Preferred: closest existing single voice, and Allan's
    "quality > size" thesis suggests a curated subset may beat all ~10h. Chosen
    primary.
  - **B — multi-speaker VITS on FLEURS mk + Common Voice mk.** More data, but
    "averaged"/muddy voice, quality capped by multi-speaker material. Fallback.
  - **C — espeak-ng** remains the guaranteed floor shipped by SP1.
  - **REJECTED — record your own single speaker.** Would give the cleanest voice,
    but ruled out by Allan: 2-5h scripted reading is too big an ask, a willing
    native is low-probability and would drag on months/years, and without pro
    mics the realistic quality ≈ FLEURS/CV anyway. Not worth the dependency.

  Training data (~2.6GB FLEURS) lives on the dev machine; only the ~60MB trained
  voice ships to the phone. **Native-speaker evaluation is the bottleneck** given
  rare access — batch a fixed eval sentence set, collect judgments in one pass.

Brainstorm/plan SP1 first; SP2 gets its own spec → plan cycle.

## Fallback Matrix (Plan B/C/D per gear)

Locked now because Macedonian native-speaker access is rare — we cannot re-test
gears on demand, so alternatives are pre-decided.

| Gear | Plan A (chosen) | Plan B | Plan C | Plan D |
|---|---|---|---|---|
| **mk TTS** | espeak-ng (ship) → trained Piper mk (goal) | Finetune Bulgarian MMS-TTS on mk data | Azure mk-MK (online only, home/practice) | ElevenLabs multilingual (online, paid) |
| **mk STT** | whisper.cpp `small` | mk-tuned whisper (buki / large-v3-mk) | whisper `medium` (accents) | wav2vec2 mk |
| **Translate** | NLLB-600M int8 | NLLB-1.3B (better, heavier) | On-device small LLM (idioms, heavy) | Cloud LLM (online, best idioms) |
| **MT runtime** | ONNX Runtime + NNAPI | ONNX Runtime CPU only | ctranslate2 | TF Lite |
| **RAM policy** | HOT (Redmi 8GB+) | WARM | COLD | reduce model tiers |
| **Model delivery** | first-run wifi download | manual sideload import | split asset packs | bundle-in-APK (if small enough) |

## Open Questions / Risks

- **espeak-ng mk intelligibility** — validate the robotic voice is at least
  understandable to a Macedonian; it is the day-1 voice until SP2 lands.
- **SP2 data availability** — RESOLVED (2026-07-19): no off-shelf single-speaker
  mk corpus; only ~10h multi-speaker FLEURS + small Common Voice mk. Recording
  your own speaker was rejected. Primary path = finetune Bulgarian MMS-TTS on
  FLEURS/CV mk; multi-speaker VITS is the fallback.
- **Model hosting** — the ~0.9GB pack ships via **GitHub Releases** (Allan's
  portfolio, user `allan-valin`; assets ≤2GB each, free CDN, checksummed). App
  downloads once over home wifi; manual sideload is the fallback.
- **Native eval logistics** — design the batched eval set early so rare mk
  access is used efficiently (covers both espeak sanity and trained-voice
  quality).
- **NLLB idiom coping** — UX should nudge plain phrasing; consider a phrasebook
  of pre-verified common sentences to sidestep MT entirely for critical needs
  (e.g. "I'm allergic to X").
- whisper `small` accuracy on strong Macedonian accents — mk-tuned whisper is
  the Plan B.
- ONNX int8 NLLB accuracy vs fp16 — verify quantization does not degrade badly.
