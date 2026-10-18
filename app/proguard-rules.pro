# ONNX Runtime's JNI layer looks these classes and members up by name.
-keep class ai.onnxruntime.** { *; }
# Our own JNI entry points (app/src/main/cpp).
-keep class mk.zbor.engines.WhisperNative { *; }
-keep class mk.zbor.engines.EspeakNative { *; }
# NllbMtEngine holds the memory-mapped model files ONNX Runtime reads in place;
# stripping that field let the GC unmap them mid-translation (SIGSEGV on the Redmi).
-keepclassmembers class mk.zbor.mt.NllbMtEngine { private java.util.List mapped; }
