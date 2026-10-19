package mk.zbor.engines

import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.content.Context
import mk.zbor.core.ManagedMt
import mk.zbor.core.ManagedStt
import mk.zbor.core.ManagedTts
import mk.zbor.core.ModelKind
import mk.zbor.core.ModelMgr
import mk.zbor.core.ModelSlot
import mk.zbor.core.MtEngine
import mk.zbor.core.ResidencyPolicy
import mk.zbor.core.SttEngine
import mk.zbor.core.TtsEngine
import mk.zbor.mt.NllbMtEngine
import mk.zbor.mt.NllbTokenizer
import mk.zbor.core.Lang
import mk.zbor.tts.PiperTtsEngine
import mk.zbor.tts.VitsTtsEngine

/** The three real engines, sharing one [ModelMgr]. */
class ZborEngines(
    val stt: SttEngine,
    val mt: MtEngine,
    val tts: TtsEngine,
    val models: ModelMgr,
    private val slots: List<ModelSlot<*>>,
) : AutoCloseable {
    /** Loads what the policy keeps resident, so the first turn does not pay for it. */
    fun warmUp() {
        for (slot in slots) if (slot.kind in models.policy.keeps) models.use(slot) {}
    }

    override fun close() = models.close()

    companion object {
        fun create(
            pack: ModelPack,
            policy: ResidencyPolicy,
            onPolicyChange: (ResidencyPolicy) -> Unit = {},
            speechRate: () -> Int = { EspeakTtsEngine.DEFAULT_RATE },
            naturalVoice: (Lang) -> Boolean = { true },
        ): ZborEngines {
            val mgr = ModelMgr(policy, onPolicyChange)
            val stt = ModelSlot(ModelKind.STT) { WhisperSttEngine.open(pack.whisper) }
            val mt = ModelSlot(ModelKind.MT) {
                // CPU provider: NNAPI does not run NLLB's dynamic-int8 MatMuls (fallback matrix, Plan B).
                val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }
                val tokenizer = pack.nllbTokenizer.inputStream().use(NllbTokenizer::readCompact)
                NllbMtEngine.open(pack.nllbEncoder, pack.nllbDecoder, tokenizer, opts)
            }
            val tts = ModelSlot(ModelKind.TTS) {
                val espeak = EspeakTtsEngine.open(pack.dir, speechRate) // also initialises the phonemizer
                val natural = HashMap<Lang, mk.zbor.core.TtsEngine>()
                val opts = { OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) } }
                for (v in Voices.all) {
                    val model = java.io.File(pack.dir, v.model)
                    if (!v.files.all { java.io.File(pack.dir, it).isFile }) continue
                    natural[v.lang] = when (v.kind) {
                        VoiceKind.MMS -> VitsTtsEngine.open(model, java.io.File(pack.dir, v.model.removeSuffix(".onnx") + ".vocab.json"), speechRate, opts())
                        VoiceKind.PIPER -> PiperTtsEngine.open(v.lang, model, ::phonemes, speechRate, opts())
                    }
                }
                RoutedTts(espeak, natural, naturalVoice)
            }
            return ZborEngines(ManagedStt(mgr, stt), ManagedMt(mgr, mt), ManagedTts(mgr, tts), mgr, listOf(stt, mt))
        }

        private fun phonemes(text: String, voice: String): String =
            synchronized(EspeakNative) { EspeakNative.phonemes(text, voice) }.orEmpty()

        fun policyFor(context: Context): ResidencyPolicy {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            return ResidencyPolicy.forRam(info.totalMem)
        }
    }
}
