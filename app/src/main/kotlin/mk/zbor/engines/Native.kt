package mk.zbor.engines

/** JNI entry points in libzbor_native (app/src/main/cpp). */
internal object WhisperNative {
    init { System.loadLibrary("zbor_native") }
    external fun init(modelPath: String): Long
    external fun transcribe(ctx: Long, pcm: FloatArray, lang: String, threads: Int): String?
    external fun free(ctx: Long)
}

internal object EspeakNative {
    init { System.loadLibrary("zbor_native") }
    /** [dataParent] is the directory that contains `espeak-ng-data`. Returns the sample rate, or ≤0. */
    external fun init(dataParent: String): Int
    external fun synth(text: String, voice: String, wordsPerMinute: Int): ShortArray?
    /** IPA phonemes (for Piper voices), clauses joined by spaces. */
    external fun phonemes(text: String, voice: String): String?
    external fun terminate()
}
