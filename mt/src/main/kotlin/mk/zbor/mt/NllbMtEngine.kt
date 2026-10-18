package mk.zbor.mt

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import mk.zbor.core.EngineException
import mk.zbor.core.Lang
import mk.zbor.core.MtEngine
import mk.zbor.core.Stage
import java.io.File
import java.lang.ref.Reference
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * NLLB-200 over ONNX Runtime: encoder once, then greedy decoding with the Optimum
 * "merged" decoder (one graph, `use_cache_branch` switches between the first step
 * and the KV-cached steps). Decoding starts `</s> tgt_lang`, NLLB's forced BOS.
 */
class NllbMtEngine(
    private val encoder: OrtSession,
    private val decoder: OrtSession,
    private val tokenizer: NllbTokenizer,
    private val maxTokens: Int = 200,
    /**
     * Mapped model files the sessions read weights from directly. They must stay
     * reachable until [close]: once garbage-collected they are unmapped and ONNX
     * Runtime segfaults mid-translation (it did, in the R8 release build, when this
     * field was unused and got stripped).
     */
    private val mapped: List<ByteBuffer> = emptyList(),
) : MtEngine, AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val pastNames = decoder.inputNames.filter { it.startsWith("past_key_values.") }
    private val heads: Long
    private val headDim: Long

    init {
        val shape = (decoder.inputInfo.getValue(pastNames.first()).info as ai.onnxruntime.TensorInfo).shape
        heads = shape[1]; headDim = shape[3]
    }

    override fun translate(text: String, from: Lang, to: Lang): String {
        if (text.isBlank()) return ""
        return try {
            tokenizer.decode(generate(tokenizer.encode(text, from), tokenizer.langId(to)))
        } catch (e: ai.onnxruntime.OrtException) {
            throw EngineException(Stage.MT, e.message ?: "ONNX Runtime failed")
        }
    }

    private fun generate(src: List<Int>, tgtLang: Int): List<Int> {
        val n = src.size.toLong()
        val ids = longTensor(src.map(Int::toLong), n)
        val mask = longTensor(List(src.size) { 1L }, n)
        val out = ArrayList<Int>()
        encoder.run(mapOf("input_ids" to ids, "attention_mask" to mask)).use { enc ->
            val hidden = enc.get(0) as OnnxTensor
            var past: Map<String, OnnxTensor> = pastNames.associateWith { emptyPast() }
            var stepInput = listOf(EOS.toLong(), tgtLang.toLong())
            // The cached branch emits dummy encoder KV, so the first step's result
            // (which holds the real encoder KV) stays open for the whole decode.
            var first: OrtSession.Result? = null
            var prev: OrtSession.Result? = null
            try {
                val limit = minOf(maxTokens, 2 * src.size + 16)
                while (out.size < limit) {
                    val cached = first != null
                    val inputIds = longTensor(stepInput, stepInput.size.toLong())
                    val branch = OnnxTensor.createTensor(env, booleanArrayOf(cached))
                    val feeds = HashMap<String, OnnxTensor>(past)
                    feeds["input_ids"] = inputIds
                    feeds["encoder_attention_mask"] = mask
                    feeds["encoder_hidden_states"] = hidden
                    feeds["use_cache_branch"] = branch
                    val res = decoder.run(feeds)
                    inputIds.close(); branch.close()
                    if (cached) prev?.takeIf { it !== first }?.close()
                    else { past.values.forEach(OnnxTensor::close); first = res }
                    prev = res
                    val next = argmaxLast(res.get("logits").get() as OnnxTensor)
                    if (next == EOS) break
                    out += next
                    past = pastNames.associateWith { name ->
                        val src = if (".encoder." in name) first!! else res
                        src.get(name.replace("past_key_values.", "present.")).get() as OnnxTensor
                    }
                    stepInput = listOf(next.toLong())
                }
            } finally {
                prev?.takeIf { it !== first }?.close()
                first?.close()
                ids.close(); mask.close()
            }
        }
        return out
    }

    private fun argmaxLast(logits: OnnxTensor): Int {
        val shape = logits.info.shape // [1, seq, vocab]
        val vocab = shape[2].toInt()
        val buf: FloatBuffer = logits.floatBuffer
        val base = (shape[1].toInt() - 1) * vocab
        var best = 0
        var bestV = Float.NEGATIVE_INFINITY
        for (i in 0 until vocab) {
            val v = buf.get(base + i)
            if (v > bestV) { bestV = v; best = i }
        }
        return best
    }

    private fun longTensor(values: List<Long>, len: Long) =
        OnnxTensor.createTensor(env, LongBuffer.wrap(values.toLongArray()), longArrayOf(1, len))

    private fun emptyPast() =
        OnnxTensor.createTensor(env, FloatBuffer.allocate(0), longArrayOf(1, heads, 0, headDim))

    /** How many model files this engine has memory-mapped (also keeps [mapped] referenced). */
    val mappedFiles: Int get() = mapped.size

    override fun close() {
        encoder.close(); decoder.close()
        // Only now may the mappings go: the sessions that read them are closed.
        Reference.reachabilityFence(mapped)
    }

    companion object {
        private const val EOS = 2

        /**
         * Opens the pack's `.ort` files memory-mapped: ONNX Runtime uses the weights
         * in place, so they stay file-backed pages the OS can reclaim instead of
         * ~1 GB of copied heap. Plain `.onnx` files load the normal (copying) way.
         */
        fun open(
            encoder: File,
            decoder: File,
            tokenizer: NllbTokenizer,
            options: OrtSession.SessionOptions = OrtSession.SessionOptions(),
        ): NllbMtEngine {
            val env = OrtEnvironment.getEnvironment()
            val mapped = mutableListOf<ByteBuffer>()
            fun session(f: File): OrtSession = if (f.name.endsWith(".ort")) {
                val buf = RandomAccessFile(f, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, f.length()) }
                mapped += buf
                env.createSession(buf, options)
            } else {
                env.createSession(f.path, options)
            }
            options.addConfigEntry("session.use_ort_model_bytes_directly", "1")
            options.addConfigEntry("session.use_ort_model_bytes_for_initializers", "1")
            return NllbMtEngine(session(encoder), session(decoder), tokenizer, mapped = mapped)
        }
    }
}
