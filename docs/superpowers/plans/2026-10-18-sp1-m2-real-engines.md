# Zbor SP1 · Milestone 2 — Real Engines + ModelMgr · Implementation Plan

**Goal:** Replace M1's fakes with the real offline engines behind the same
`SttEngine` / `MtEngine` / `TtsEngine` interfaces, plus the `ModelMgr` residency
policy, and prove each one on real data (desktop JVM where possible, emulator
for native code).

**Out-of-repo build inputs** (not versioned; licences all open: MIT / GPL-3 / CC-BY-NC for NLLB weights*):
- `~/zbor-build/src/whisper.cpp` (tag v1.9.4, MIT)
- `~/zbor-build/src/espeak-ng` (tag 1.52.0, GPL-3.0)
- `~/zbor-build/models/` — `ggml-small-q5_1.bin` (190 MB), `encoder_model_quantized.onnx`
  + `decoder_model_merged_quantized.onnx` + `tokenizer.json` (Xenova export of
  `facebook/nllb-200-distilled-600M`, ~850 MB)

\* NLLB weights are CC-BY-NC-4.0: fine for a free personal app, blocks any
commercial use. Noted, not a blocker (open-source-only rule is about code/deps).

## Module layout

| Module | Kind | Holds | Tested how |
|---|---|---|---|
| `:core` | JVM | orchestrator (M1), `ModelMgr` + `ResidencyPolicy` | JVM unit tests |
| `:mt` | JVM | `NllbTokenizer` (tokenizer.json), `NllbMtEngine` (greedy decode over ONNX Runtime Java API) | JVM tests against the **real** model; skipped when `ZBOR_MODELS` absent |
| `:app` | Android | `WhisperSttEngine` (JNI), `EspeakTtsEngine` (JNI), ORT-android wiring, (M3 UI) | instrumented tests on the x86_64 AVD |

`:mt` depends on `com.microsoft.onnxruntime:onnxruntime` as `compileOnly`; tests
use the desktop jar, `:app` ships `onnxruntime-android`. Same `ai.onnxruntime` API.

## Tasks

1. **ModelMgr (core).** `ResidencyPolicy.forRam(bytes)` → HOT ≥6 GB, WARM 4–6, COLD <4.
   `ModelMgr.use(kind) { model -> … }` loads lazily, keeps or evicts per policy
   (HOT keeps STT+MT, WARM keeps STT, COLD keeps none; TTS always evicted).
   `onMemoryPressure()` steps one policy colder and evicts what the new policy
   would not keep. Tests: residency per policy, step-down eviction, load-failure
   (OOM) → step colder + retry once.
2. **NLLB tokenizer (mt).** Parse `tokenizer.json` (vocab, merges/scores, normaliser,
   `▁` pre-tokeniser), encode `[lang_code] … </s>` per NLLB convention, decode
   skipping specials. Golden test: ids match the HF tokenizer output for 4 fixed
   sentences (ids dumped once from Python into a test resource).
3. **NLLB MT engine (mt).** Encoder once, merged decoder greedy loop with KV cache
   (`use_cache_branch`), forced BOS = target lang code, max 200 tokens.
   Golden test on real model: `"Where is the bus station?"` en→mk contains
   `автобуска`; mk→pt round-trip of spike sentences; maps Lang→`mkd_Cyrl`,
   `por_Latn`, `eng_Latn`, `deu_Latn`.
4. **Android `:app` skeleton.** AGP 8.12 / Kotlin 2.2 (cached from workout-app),
   minSdk 29, ABIs `arm64-v8a` + `x86_64`, CMake 3.22.1, NDK 29. Builds an empty
   activity. `:core` and `:mt` wired in.
5. **whisper.cpp JNI.** CMake `add_subdirectory` on the whisper.cpp tree, thin JNI
   (`init(path)`, `transcribe(float[], lang)`, `free`). `WhisperSttEngine` converts
   16-bit PCM → float. Instrumented test: espeak-generated "здраво, како си"
   wav → transcript non-blank Cyrillic; jfk.wav → contains "country".
6. **espeak-ng JNI.** Cross-compile espeak-ng 1.52 lib (CMake), data compiled on
   host from the same tree, shipped in the model pack. JNI `synth(text, voice)` →
   PCM via `espeak_SetSynthCallback`. Test: `"здраво"` mk → >0.3 s of non-silent
   22.05 kHz audio.
7. **Engine wiring.** `Engines.create(context, modelDir)` builds the three real
   engines behind ModelMgr with the device's policy. Instrumented end-to-end test:
   orchestrator turn with real engines mk→pt on a generated utterance.

Done = all JVM tests green, instrumented tests green on the AVD, commits per task.
