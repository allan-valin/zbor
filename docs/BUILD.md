# Building Zbor

Native sources and models live outside the repo in `~/zbor-build/` (override with
`-Pzbor.build=…`). Nothing there is versioned; this file is how to recreate it.

## 1. Toolchain
- JDK 17+ (21 used), Android SDK 36, **NDK 29.0.14206865**, **CMake 3.22.1**
  (`sdkmanager "ndk;29.0.14206865" "cmake;3.22.1"`).
- Gradle 8.13 via `./gradlew`.

## 2. Native sources
```bash
mkdir -p ~/zbor-build/src && cd ~/zbor-build/src
git clone --depth 1 --branch v1.9.4 https://github.com/ggml-org/whisper.cpp   # MIT
git clone --depth 1 --branch 1.52.0 https://github.com/espeak-ng/espeak-ng     # GPL-3.0
tools/build_espeak.sh   # host data + arm64/x86_64 static libs → ~/zbor-build/espeak
```

## 3. Model sources (`~/zbor-build/models`, ~1.1 GB)
- `ggml-small-q5_1.bin` — huggingface.co/ggerganov/whisper.cpp (MIT)
- `encoder_model_quantized.onnx`, `decoder_model_merged_quantized.onnx`, `tokenizer.json`
  — huggingface.co/Xenova/nllb-200-distilled-600M (weights **CC-BY-NC-4.0**:
  free personal use only)

## 4. Pack (`~/zbor-build/pack`, 1.1 GB)
Needs a Python with `onnx` + `numpy` (`uv venv ~/zbor-build/.venv && uv pip install --python ~/zbor-build/.venv/bin/python onnx numpy`).
```bash
tools/build_pack.sh
```
Steps: tokenizer.json → compact `nllb-tokenizer.bin`; `fix_lm_head.py` hoists the
LM head out of the decoder's If branches (the export dequantised the whole 256k×1024
embedding every step: 3.1 GB peak, 2.3 s/sentence → 1.47 GB, 0.22 s) and drops the
duplicate embedding (decoder 738 → 476 MB); both NLLB graphs → `.ort`, which the app
memory-maps; espeak-ng data copied in.

It also writes `~/zbor-build/pack-release/` (the five files to upload, espeak data as
`espeak-ng-data.zip`) and **`app/src/main/assets/pack.json`** — the manifest (sizes +
sha256) the app checks every file against. Rebuilding the pack changes the hashes, so
the APK and the uploaded release must come from the same `build_pack.sh` run.

## 5. Publishing the pack
Upload every file in `~/zbor-build/pack-release/` as assets of a GitHub Release tagged
`pack-v1` (each asset < 2 GB). The app downloads from `BuildConfig.PACK_URL`, default
`https://github.com/allan-valin/zbor/releases/download/pack-v1/`; build with
`-Pzbor.packUrl=…` for another repo or tag. Without a release, sideload instead: copy
the release files by USB into `Android/data/mk.zbor/files/models/` (the zip is
extracted on the next check) or into any folder and use *Importar de uma pasta…*.

## 6. App builds
- `./gradlew :app:assembleDebug` — both ABIs, for the emulator.
- `./gradlew :app:assembleRelease` — R8-minified, ~50 MB, signed with the debug key
  (personal sideload; no release keystore yet).

## 7. Tests
- JVM: `./gradlew :core:test :mt:test` (real-model tests skip if `~/zbor-build/{models,pack}` missing).
- Device: install the pack into `Android/data/mk.zbor/files/models/`, then
  `./gradlew :app:connectedDebugAndroidTest` or
  `adb shell am instrument -w -e class mk.zbor.engines.EnginesOnDeviceTest mk.zbor.test/androidx.test.runner.AndroidJUnitRunner`.
  On the emulator: `adb push ~/zbor-build/pack/. /sdcard/Android/data/mk.zbor/files/models/`
  then `adb shell chmod -R a+rwX /sdcard/Android/data/mk.zbor/files/models`.
- First-run download end to end (`SetupUiTest`, deletes the pack on the device):
  ```bash
  python3 -m http.server 8765 --bind 127.0.0.1 --directory ~/zbor-build/pack-release &
  ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -Pzbor.packUrl=http://10.0.2.2:8765/
  adb install -r -t app/build/outputs/apk/debug/app-debug.apk
  adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb shell am instrument -w mk.zbor.test/androidx.test.runner.AndroidJUnitRunner
  ```
  (Python's server ignores Range; the installer restarts such files. Range resume is
  covered by the `:pack` JVM tests.)
