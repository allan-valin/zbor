package mk.zbor.core

/** The three model families the pipeline loads. */
enum class ModelKind { STT, MT, TTS }

/**
 * Which models stay in RAM between turns. TTS is never kept: it runs once at the
 * end of a turn, while the reader is already looking at the translated text.
 */
enum class ResidencyPolicy(val keeps: Set<ModelKind>) {
    HOT(setOf(ModelKind.STT, ModelKind.MT)),
    WARM(setOf(ModelKind.STT)),
    COLD(emptySet());

    fun colder(): ResidencyPolicy = when (this) {
        HOT -> WARM
        WARM, COLD -> COLD
    }

    companion object {
        private const val GIB = 1L shl 30
        fun forRam(totalBytes: Long): ResidencyPolicy = when {
            totalBytes >= 6 * GIB -> HOT
            totalBytes >= 4 * GIB -> WARM
            else -> COLD
        }
    }
}

/** How to load one model; the loaded model is closed when evicted. */
class ModelSlot<M : AutoCloseable>(val kind: ModelKind, val load: () -> M)

/**
 * Loads models lazily and keeps or evicts them per [policy]. Under memory
 * pressure (or an OOM while loading) it steps one policy colder; the
 * [onPolicyChange] callback lets the UI show a "slower mode" notice.
 */
class ModelMgr(
    initial: ResidencyPolicy,
    private val onPolicyChange: (ResidencyPolicy) -> Unit = {},
) : AutoCloseable {
    var policy: ResidencyPolicy = initial
        private set

    private val resident = mutableMapOf<ModelKind, AutoCloseable>()

    @Synchronized fun isResident(kind: ModelKind): Boolean = kind in resident

    @Synchronized
    fun <M : AutoCloseable, T> use(slot: ModelSlot<M>, block: (M) -> T): T {
        @Suppress("UNCHECKED_CAST")
        val model = resident[slot.kind] as M? ?: load(slot)
        try {
            return block(model)
        } finally {
            if (slot.kind !in policy.keeps) evict(slot.kind)
        }
    }

    @Synchronized fun onMemoryPressure() = stepColder()

    @Synchronized
    override fun close() {
        resident.keys.toList().forEach(::evict)
    }

    private fun <M : AutoCloseable> load(slot: ModelSlot<M>): M {
        val model = try {
            slot.load()
        } catch (e: OutOfMemoryError) {
            stepColder()
            slot.load()
        }
        resident[slot.kind] = model
        return model
    }

    private fun stepColder() {
        val next = policy.colder()
        if (next == policy) return
        policy = next
        resident.keys.filter { it !in next.keeps }.forEach(::evict)
        onPolicyChange(next)
    }

    private fun evict(kind: ModelKind) {
        resident.remove(kind)?.close()
    }
}
