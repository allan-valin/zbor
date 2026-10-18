#!/usr/bin/env bash
# Build espeak-ng 1.52 for Zbor: host build compiles the voice data (shipped in the
# model pack), NDK builds produce static libs per ABI that app/src/main/cpp links.
#   tools/build_espeak.sh [espeak-src] [out-dir]
set -euo pipefail
SRC=${1:-$HOME/zbor-build/src/espeak-ng}
OUT=${2:-$HOME/zbor-build/espeak}
NDK=${ANDROID_NDK:-$HOME/Android/Sdk/ndk/29.0.14206865}
CMAKE=${CMAKE:-$HOME/Android/Sdk/cmake/3.22.1/bin/cmake}
OFF="-DUSE_MBROLA=OFF -DUSE_LIBSONIC=OFF -DUSE_LIBPCAUDIO=OFF -DUSE_ASYNC=OFF -DUSE_SPEECHPLAYER=OFF -DBUILD_TESTING=OFF"

# 1. host: library + compiled data (phondata, *_dict)
cmake -S "$SRC" -B "$OUT/host" -DCMAKE_BUILD_TYPE=Release $OFF >/dev/null
cmake --build "$OUT/host" -j"$(nproc)" --target data >/dev/null
rm -rf "$OUT/espeak-ng-data" && cp -r "$OUT/host/espeak-ng-data" "$OUT/espeak-ng-data"

# 2. android: static libs per ABI
for ABI in arm64-v8a x86_64; do
  "$CMAKE" -S "$SRC" -B "$OUT/android-$ABI" -DCMAKE_BUILD_TYPE=Release $OFF \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-29 -DCMAKE_POSITION_INDEPENDENT_CODE=ON >/dev/null
  "$CMAKE" --build "$OUT/android-$ABI" -j"$(nproc)" --target espeak-ng >/dev/null
  mkdir -p "$OUT/lib/$ABI"
  find "$OUT/android-$ABI" -name '*.a' -exec cp {} "$OUT/lib/$ABI/" \;
done
mkdir -p "$OUT/include" && cp -r "$SRC/src/include/espeak-ng" "$OUT/include/"
ls "$OUT/lib"/* && du -sh "$OUT/espeak-ng-data"
