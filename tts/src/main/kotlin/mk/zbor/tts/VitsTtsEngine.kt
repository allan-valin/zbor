package mk.zbor.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import mk.zbor.core.EngineException
import mk.zbor.core.Lang
import mk.zbor.core.Stage
import mk.zbor.core.TtsEngine
import mk.zbor.core.Utterance
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.abs

/**
 * The fine-tuned Macedonian VITS voice (v2) over ONNX Runtime. Macedonian only; the app
 * routes other languages to eSpeak. [wordsPerMinute] is the settings slider: 175 = the
 * model's natural pace (speaking_rate 1.0).
 */
class VitsTtsEngine(
    private val session: OrtSession,
    private val frontEnd: MkTextFrontEnd,
    private val vocab: VitsVocab,
    private val wordsPerMinute: () -> Int = { NATURAL_WPM },
    private val sampleRate: Int = 16_000,
) : TtsEngine, AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()

    override fun synthesize(text: String, lang: Lang): Utterance {
        if (lang != Lang.MK) throw EngineException(Stage.TTS, "neural voice is Macedonian only")
        val ids = vocab.encode(frontEnd.toModelText(text))
        if (ids.size < 3) return Utterance(ShortArray(0), sampleRate)
        val rate = (wordsPerMinute().toFloat() / NATURAL_WPM).coerceIn(0.4f, 2f)
        return try {
            OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())).use { input ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(rate)), longArrayOf(1)).use { r ->
                    session.run(mapOf("input_ids" to input, "speaking_rate" to r)).use { out ->
                        Utterance(toPcm((out.get(0) as OnnxTensor).floatBuffer), sampleRate)
                    }
                }
            }
        } catch (e: ai.onnxruntime.OrtException) {
            throw EngineException(Stage.TTS, e.message ?: "neural voice failed")
        }
    }

    /** Peak-normalised to 90 % like the eval clips, then 16-bit. */
    private fun toPcm(buf: FloatBuffer): ShortArray {
        val f = FloatArray(buf.remaining()).also(buf::get)
        val peak = f.maxOfOrNull { abs(it) }?.coerceAtLeast(1e-6f) ?: 1f
        return ShortArray(f.size) { (f[it] / peak * 0.9f * Short.MAX_VALUE).toInt().toShort() }
    }

    override fun close() = session.close()

    companion object {
        const val NATURAL_WPM = 175

        fun open(model: File, vocabFile: File, wordsPerMinute: () -> Int = { NATURAL_WPM },
                 options: OrtSession.SessionOptions = OrtSession.SessionOptions()): VitsTtsEngine {
            val vocab = VitsVocab.load(vocabFile)
            return VitsTtsEngine(OrtEnvironment.getEnvironment().createSession(model.path, options),
                MkTextFrontEnd(vocab), vocab, wordsPerMinute)
        }
    }
}
