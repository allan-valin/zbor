package mk.zbor.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mk.zbor.core.EngineException
import mk.zbor.core.Lang
import mk.zbor.core.Stage
import mk.zbor.core.TtsEngine
import mk.zbor.core.Utterance
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.abs

/** A Piper voice's `.onnx.json`: phoneme → ids, sample rate, default synthesis scales. */
class PiperConfig(
    val phonemeIds: Map<String, List<Long>>,
    val sampleRate: Int,
    val noiseScale: Float,
    val lengthScale: Float,
    val noiseW: Float,
    val espeakVoice: String,
) {
    /** `^ _ (phoneme _)* $`, skipping phonemes the voice does not know — as piper-phonemize. */
    fun ids(phonemes: String): LongArray {
        val pad = phonemeIds.getValue("_")
        val out = ArrayList<Long>()
        out += phonemeIds.getValue("^"); out += pad
        phonemes.codePoints().forEach { cp ->
            phonemeIds[String(Character.toChars(cp))]?.let { out += it; out += pad }
        }
        out += phonemeIds.getValue("$")
        return out.toLongArray()
    }

    companion object {
        fun load(file: File): PiperConfig {
            val o = Json.parseToJsonElement(file.readText()).jsonObject
            val inf = o.getValue("inference").jsonObject
            return PiperConfig(
                o.getValue("phoneme_id_map").jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.int.toLong() } },
                o.getValue("audio").jsonObject.getValue("sample_rate").jsonPrimitive.int,
                inf.getValue("noise_scale").jsonPrimitive.float,
                inf.getValue("length_scale").jsonPrimitive.float,
                inf.getValue("noise_w").jsonPrimitive.float,
                o.getValue("espeak").jsonObject.getValue("voice").jsonPrimitive.content,
            )
        }
    }
}

/**
 * A Piper (VITS) voice for one language. [phonemize] turns text into eSpeak IPA (the app's
 * espeak JNI; a CLI call in tests). [wordsPerMinute] is the settings slider, 175 = natural.
 */
class PiperTtsEngine(
    private val lang: Lang,
    private val session: OrtSession,
    private val config: PiperConfig,
    private val phonemize: (text: String, voice: String) -> String,
    private val wordsPerMinute: () -> Int = { VitsTtsEngine.NATURAL_WPM },
) : TtsEngine, AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()

    override fun synthesize(text: String, lang: Lang): Utterance {
        if (lang != this.lang) throw EngineException(Stage.TTS, "this voice speaks ${this.lang} only")
        val ids = config.ids(phonemize(text, config.espeakVoice))
        if (ids.size < 4) return Utterance(ShortArray(0), config.sampleRate)
        val rate = (wordsPerMinute().toFloat() / VitsTtsEngine.NATURAL_WPM).coerceIn(0.4f, 2f)
        val scales = floatArrayOf(config.noiseScale, config.lengthScale / rate, config.noiseW)
        return try {
            OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())).use { input ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(ids.size.toLong())), longArrayOf(1)).use { len ->
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(scales), longArrayOf(3)).use { sc ->
                        session.run(mapOf("input" to input, "input_lengths" to len, "scales" to sc)).use { out ->
                            Utterance(toPcm((out.get(0) as OnnxTensor).floatBuffer), config.sampleRate)
                        }
                    }
                }
            }
        } catch (e: ai.onnxruntime.OrtException) {
            throw EngineException(Stage.TTS, e.message ?: "Piper voice failed")
        }
    }

    private fun toPcm(buf: FloatBuffer): ShortArray {
        val f = FloatArray(buf.remaining()).also(buf::get)
        val peak = f.maxOfOrNull { abs(it) }?.coerceAtLeast(1e-6f) ?: 1f
        return ShortArray(f.size) { (f[it] / peak * 0.9f * Short.MAX_VALUE).toInt().toShort() }
    }

    override fun close() = session.close()

    companion object {
        fun open(lang: Lang, model: File, phonemize: (String, String) -> String, wordsPerMinute: () -> Int,
                 options: OrtSession.SessionOptions = OrtSession.SessionOptions()) = PiperTtsEngine(
            lang, OrtEnvironment.getEnvironment().createSession(model.path, options),
            PiperConfig.load(File(model.path + ".json")), phonemize, wordsPerMinute,
        )
    }
}
