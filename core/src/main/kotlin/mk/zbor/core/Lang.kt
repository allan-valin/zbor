package mk.zbor.core

/** The four languages Zbor handles. */
enum class Lang { MK, PT, EN, DE }

/** A chunk of mono 16-bit PCM audio. */
data class Utterance(val pcm: ShortArray, val sampleRate: Int) {
    override fun equals(other: Any?) =
        other is Utterance && sampleRate == other.sampleRate && pcm.contentEquals(other.pcm)
    override fun hashCode() = 31 * pcm.contentHashCode() + sampleRate
    override fun toString() = "Utterance(${pcm.size} samples @ $sampleRate Hz)"
}
