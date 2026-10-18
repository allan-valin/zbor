# ONNX Runtime's JNI layer looks these classes and members up by name.
-keep class ai.onnxruntime.** { *; }
# Our own JNI entry points (app/src/main/cpp).
-keep class mk.zbor.engines.WhisperNative { *; }
-keep class mk.zbor.engines.EspeakNative { *; }
