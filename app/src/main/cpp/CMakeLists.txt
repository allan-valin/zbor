cmake_minimum_required(VERSION 3.22.1)
project(zbor_native C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_BUILD_TYPE Release) # whisper/ggml are unusably slow at -O0

# --- whisper.cpp + ggml (static, CPU only) ---
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
if (ANDROID_ABI STREQUAL "arm64-v8a")
    # Dimensity (Redmi Note 15 Pro) and every arm64 phone since ~2018 has these.
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif()
add_subdirectory(${WHISPER_SRC} whisper)

# --- espeak-ng (prebuilt by tools/build_espeak.sh) ---
add_library(espeak-ng STATIC IMPORTED)
set_target_properties(espeak-ng PROPERTIES IMPORTED_LOCATION ${ESPEAK_DIR}/lib/${ANDROID_ABI}/libespeak-ng.a)
add_library(ucd STATIC IMPORTED)
set_target_properties(ucd PROPERTIES IMPORTED_LOCATION ${ESPEAK_DIR}/lib/${ANDROID_ABI}/libucd.a)

add_library(zbor_native SHARED whisper_jni.cpp espeak_jni.cpp)
target_include_directories(zbor_native PRIVATE ${ESPEAK_DIR}/include)
target_compile_options(zbor_native PRIVATE -O3)
target_link_libraries(zbor_native whisper espeak-ng ucd log android)
