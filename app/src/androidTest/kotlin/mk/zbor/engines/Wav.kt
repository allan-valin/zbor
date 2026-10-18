package mk.zbor.engines

import mk.zbor.core.Utterance
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal PCM16 mono WAV reader for test fixtures. */
fun readWav(bytes: ByteArray): Utterance {
    val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var rate = 0
    var pos = 12
    while (pos + 8 <= bytes.size) {
        val id = String(bytes, pos, 4)
        val size = b.getInt(pos + 4)
        if (id == "fmt ") rate = b.getInt(pos + 12)
        if (id == "data") {
            val pcm = ShortArray(size / 2) { b.getShort(pos + 8 + it * 2) }
            return Utterance(pcm, rate)
        }
        pos += 8 + size + (size and 1)
    }
    error("no data chunk")
}
