# Spikes (2026-07-19)

Pre-build validation of the two risky gears.

## mt_spike.py — NLLB-200 distilled 600M
Round-trip idiom/plain test. Result: strong on plain sentences, mostly literal
on idioms. See `mt_out.txt`. Runs (needs torch CPU + transformers).

## tts_spike.py — MMS-TTS Macedonian
Did NOT run: `facebook/mms-tts-mkd` does not exist. Confirmed via HF API,
HF search, and Meta CDN (`mms/tts/mkd.tar.gz` → 403; `eng.tar.gz` → 200).
No free offline Macedonian neural TTS exists. Script kept as a template for the
pt/en/de MMS voices (those do exist) and for future trained-voice smoke tests.

## Conclusion
TTS strategy pivoted to: espeak-ng mk (ship) + trained Piper mk voice (goal).
See the design doc's Spike Findings and Fallback Matrix sections.
