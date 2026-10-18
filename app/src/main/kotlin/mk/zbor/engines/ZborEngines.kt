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
        ): ZborEngines {
            val mgr = ModelMgr(policy, onPolicyChange)
            val stt = ModelSlot(ModelKind.STT) { WhisperSttEngine.open(pack.whisper) }
            val mt = ModelSlot(ModelKind.MT) {
                // CPU provider: NNAPI does not run NLLB's dynamic-int8 MatMuls (fallback matrix, Plan B).
                val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }
                val tokenizer = pack.nllbTokenizer.inputStream().use(NllbTokenizer::readCompact)
                NllbMtEngine.open(pack.nllbEncoder, pack.nllbDecoder, tokenizer, opts)
            }
            val tts = ModelSlot(ModelKind.TTS) { EspeakTtsEngine.open(pack.dir, speechRate) }
            return ZborEngines(ManagedStt(mgr, stt), ManagedMt(mgr, mt), ManagedTts(mgr, tts), mgr, listOf(stt, mt))
        }

        fun policyFor(context: Context): ResidencyPolicy {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            return ResidencyPolicy.forRam(info.totalMem)
        }
    }
}
