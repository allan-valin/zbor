package mk.zbor.engines

import mk.zbor.core.EngineException
import mk.zbor.core.Lang
import mk.zbor.core.SttEngine
import mk.zbor.core.Stage
import mk.zbor.core.Utterance
import java.io.File

/** whisper.cpp speech-to-text; one loaded model serves all four languages. */
class WhisperSttEngine private constructor(
    private val ctx: Long,
    private val threads: Int,
) : SttEngine, AutoCloseable {

    override fun transcribe(audio: Utterance, lang: Lang): String {
        if (audio.sampleRate != SAMPLE_RATE) {
            throw EngineException(Stage.STT, "whisper needs $SAMPLE_RATE Hz, got ${audio.sampleRate}")
        }
        val pcm = FloatArray(audio.pcm.size) { audio.pcm[it] / 32768f }
        val text = synchronized(this) { WhisperNative.transcribe(ctx, pcm, lang.whisperCode, threads) }
            ?: throw EngineException(Stage.STT, "whisper failed")
        return text.trim()
    }

    override fun close() = WhisperNative.free(ctx)

    companion object {
        const val SAMPLE_RATE = 16_000

        fun open(model: File, threads: Int = defaultThreads()): WhisperSttEngine {
            val ctx = WhisperNative.init(model.path)
            if (ctx == 0L) throw EngineException(Stage.STT, "could not load ${model.name}")
            return WhisperSttEngine(ctx, threads)
        }

        private fun defaultThreads() = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}

internal val Lang.whisperCode: String
    get() = when (this) {
        Lang.MK -> "mk"; Lang.PT -> "pt"; Lang.EN -> "en"; Lang.DE -> "de"
    }
