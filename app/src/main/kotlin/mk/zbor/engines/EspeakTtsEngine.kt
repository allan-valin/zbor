package mk.zbor.engines

import mk.zbor.core.EngineException
import mk.zbor.core.Lang
import mk.zbor.core.Stage
import mk.zbor.core.TtsEngine
import mk.zbor.core.Utterance
import java.io.File

/**
 * espeak-ng formant voice: robotic but offline and tiny. The SP1 voice for every
 * language until the trained Macedonian voice (SP2) replaces it behind [TtsEngine].
 */
class EspeakTtsEngine private constructor(
    private val sampleRate: Int,
    /** Words per minute, read at every call so the settings slider applies immediately. */
    private val rate: () -> Int,
) : TtsEngine, AutoCloseable {

    override fun synthesize(text: String, lang: Lang): Utterance {
        val pcm = synchronized(EspeakNative) { EspeakNative.synth(text, lang.espeakVoice, rate().coerceIn(MIN_RATE, MAX_RATE)) }
            ?: throw EngineException(Stage.TTS, "espeak-ng could not speak ${lang.espeakVoice}")
        return Utterance(pcm, sampleRate)
    }

    override fun close() = synchronized(EspeakNative) { EspeakNative.terminate() }

    companion object {
        const val DEFAULT_RATE = 175 // espeak-ng's own default
        const val MIN_RATE = 80
        const val MAX_RATE = 260

        /** [packDir] holds `espeak-ng-data/`. */
        fun open(packDir: File, rate: () -> Int = { DEFAULT_RATE }): EspeakTtsEngine {
            val sampleRate = synchronized(EspeakNative) { EspeakNative.init(packDir.path) }
            if (sampleRate <= 0) throw EngineException(Stage.TTS, "espeak-ng data missing in $packDir")
            return EspeakTtsEngine(sampleRate, rate)
        }
    }
}

internal val Lang.espeakVoice: String
    get() = when (this) {
        Lang.MK -> "mk"; Lang.PT -> "pt-br"; Lang.EN -> "en-us"; Lang.DE -> "de"
    }
