# Zbor — Resume Checkpoint

_Last updated: 2026-10-18_

## What Zbor is
Offline Android/Kotlin split-screen conversation translator, Macedonian ↔
pt-br/en/de, for talking in person with a Macedonian-only speaker where there is
no cell signal. **The whole point is Macedonian TTS** (Google deactivated theirs).

## Status: SP1 (the app) M1–M4 DONE 2026-10-18 — NOT yet run on the real Redmi
- **M1** `:core` pipeline + edit seam, ModelMgr (HOT/WARM/COLD), typed turns, TTS-failure keeps translation.
- **M2** real engines: whisper.cpp + espeak-ng via JNI (`app/src/main/cpp`), NLLB-600M int8 over
  ONNX Runtime (`:mt`, own tokenizer matching HF exactly). Pack graph surgery (`tools/fix_lm_head.py`):
  peak RAM 3.1 → 1.47 GB, 2.3 → 0.22 s per sentence on desktop, pack 1.3 → 1.1 GB.
- **M3** split-screen Compose UI (top half rotated), per-half language/status in that half's language,
  mic with level ring, edit seam, ✓, replay, settings (output seam), keyboard-safe layout.
- **M4** first-run setup: resumable sha256-checked download, mobile-data warning, pause, folder import,
  USB-sideload detection (`:pack`).
- Tests: JVM core 26 / mt 10 / pack 10 / app 12; instrumented 8 on the x86_64 emulator
  (engines, UI turn, wiped-pack download → conversation). R8 release smoke-tested by hand.
- Build/recreate everything: `docs/BUILD.md`. Plans: `docs/superpowers/plans/`.

## Since 2026-10-18 evening (Allan's feedback)
- Installed on the **Redmi Note 15 Pro (Android 16)** via wireless adb; pack imported
  from `Download/zbor-pack` with the in-app folder import (adb pushes into
  `Android/data` are invisible to the app on Android 16).
- Mirroring only in face-to-face mode (chip, off by default); otherwise one-sided:
  bottom = input, top = output, yellow ⇅ swaps languages.
- Interface EN (default) / DE / pt-BR from the phone locale; bottom default
  language follows the locale.
- Settings: espeak speech-rate slider + Macedonian example.
- Keyboard asks for the half's language (`hintLocales`); needs the layout added in Gboard.
- README.md written for testers and their helpers (video links verified to exist,
  not watched).

## Measured on the emulator (x86_64, no SIMD flags — the phone build has dotprod/fp16)
- whisper small: 15 s for 11 s of audio (expect far less on the Dimensity).
- MT + espeak for one sentence: ~2 s including a cold model load.

## Open — needs Allan
1. **GitHub repo name** (suggest `zbor`): nothing pushed yet; the app's default pack URL assumes
   `allan-valin/zbor`, release tag `pack-v1`. Then upload `~/zbor-build/pack-release/*`.
2. **Real Redmi**: app + pack are on Allan's phone; still to check by him: **microphone** turns and
   real latency, keyboard auto-switch after adding Macedonian to Gboard.
3. **Macedonian strings** in `app/src/main/kotlin/mk/zbor/ui/SideText.kt` were written by Claude —
   need a native check (fold into the batched native eval set).
4. Licence note: NLLB weights are CC-BY-NC-4.0 (open-weight, non-commercial) — fine for a free personal app.

## Known limits / next
- Typing on the TOP (rotated) half: the system keyboard is not rotated — voice is the primary path.
- Download runs while the setup screen is open (resumes after interruptions; no background service).
- SP2 (trained Macedonian voice) = its own spec → plan cycle; swaps in behind `TtsEngine`.
