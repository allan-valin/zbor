# Zbor v2 (SP2) — a Macedonian neural voice · Design

**Date:** 2026-10-19 · **Status:** approved direction (Allan, 2026-10-19), M1–M2 approved to run,
M3 full training waits for a priced pilot.

## Goal
Replace the robotic eSpeak NG Macedonian voice with a neural one that runs offline on the
phone, behind the existing `TtsEngine` (no pipeline change). eSpeak stays as fallback.

## Approach (decided 2026-07-19, see v1 spec "Sub-Project Split")
Fine-tune Meta's single-speaker **Bulgarian MMS-TTS** (VITS, `facebook/mms-tts-bul`,
CC-BY-NC-4.0) on **FLEURS Macedonian** (`google/fleurs` `mk_mk`, CC-BY-4.0, ~10 h,
multi-speaker). Bulgarian is the closest language with a ready single voice; starting from
it keeps one identity and most phonology. Fallback: multi-speaker VITS on the same data.
Recording an own speaker: rejected.

## Milestones
- **M1 Eval set** — ~40 fixed Macedonian sentences (everyday, numbers, health, directions,
  every Macedonian-specific letter), a baseline with today's eSpeak voice, and an offline
  rating sheet (1–5 intelligibility + naturalness, comments) that native speakers fill in
  once per batch. Every voice is judged on the same set.
- **M2 Data** — FLEURS mk download, per-clip stats (speaker, duration, level/noise), a clean
  subset, train/val manifest. Common Voice mk only if reachable without the MDC hoop.
- **M3 Fine-tune** — training env on the RTX 4050 (6 GB), text front-end (MMS-bul vocabulary
  vs Macedonian Cyrillic: ѓ ќ ј љ њ џ ѕ need a mapping or vocab extension), pilot run to
  measure s/step and hear early checkpoints, then a priced full run (hours) for Allan's go.
- **M4 Ship** — ONNX export, `pack-v2` asset (~60–150 MB), `VitsTtsEngine` in the app via
  ONNX Runtime, settings switch eSpeak ↔ neural, release.
- **M5 (optional)** — Piper/MMS voices for pt/en/de.

## Risks
- Multi-speaker FLEURS → "averaged" voice; mitigation: speaker filtering.
- MMS uses raw characters (uroman-free for Cyrillic bul); Macedonian letters missing from
  the Bulgarian vocab need mapping (e.g. ѓ→гј-like) or new embeddings.
- 6 GB VRAM: small batch, fp16; long runs in a memory-capped systemd scope.
- Native evaluation is the bottleneck → batch it (M1).
