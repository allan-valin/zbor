package mk.zbor.core

/** An [SttEngine] whose model is loaded, kept and evicted by [ModelMgr]. */
class ManagedStt<M>(private val mgr: ModelMgr, private val slot: ModelSlot<M>) : SttEngine
        where M : SttEngine, M : AutoCloseable {
    override fun transcribe(audio: Utterance, lang: Lang) = mgr.use(slot) { it.transcribe(audio, lang) }
}

/** An [MtEngine] whose model is loaded, kept and evicted by [ModelMgr]. */
class ManagedMt<M>(private val mgr: ModelMgr, private val slot: ModelSlot<M>) : MtEngine
        where M : MtEngine, M : AutoCloseable {
    override fun translate(text: String, from: Lang, to: Lang) = mgr.use(slot) { it.translate(text, from, to) }
}

/** A [TtsEngine] whose model is loaded, kept and evicted by [ModelMgr]. */
class ManagedTts<M>(private val mgr: ModelMgr, private val slot: ModelSlot<M>) : TtsEngine
        where M : TtsEngine, M : AutoCloseable {
    override fun synthesize(text: String, lang: Lang) = mgr.use(slot) { it.synthesize(text, lang) }
}
