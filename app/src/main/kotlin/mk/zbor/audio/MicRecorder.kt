package mk.zbor.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import mk.zbor.app.Recorder
import mk.zbor.core.Utterance
import kotlin.concurrent.thread
import kotlin.math.sqrt

/** 16 kHz mono PCM16 from the microphone, the format whisper wants. Caps a turn at 60 s. */
class MicRecorder : Recorder {
    private var record: AudioRecord? = null
    private var reader: Thread? = null
    private val chunks = ArrayList<ShortArray>()
    @Volatile private var running = false

    @SuppressLint("MissingPermission") // the UI asks for RECORD_AUDIO before the first tap reaches here
    override fun start(onLevel: (Float) -> Unit) {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE / 5 * 2))
        check(r.state == AudioRecord.STATE_INITIALIZED) { "microphone unavailable" }
        synchronized(chunks) { chunks.clear() }
        running = true
        r.startRecording()
        record = r
        reader = thread(name = "zbor-mic") {
            val buf = ShortArray(RATE / 10) // 100 ms
            var total = 0
            while (running && total < RATE * MAX_SECONDS) {
                val n = r.read(buf, 0, buf.size)
                if (n <= 0) continue
                synchronized(chunks) { chunks += buf.copyOf(n) }
                total += n
                var sum = 0.0
                for (i in 0 until n) sum += buf[i] * buf[i].toDouble()
                onLevel((sqrt(sum / n) / 6000.0).coerceIn(0.0, 1.0).toFloat())
            }
        }
    }

    override fun stop(): Utterance {
        running = false
        reader?.join(1000)
        record?.run { stop(); release() }
        record = null
        val all = synchronized(chunks) { chunks.toList() }
        val pcm = ShortArray(all.sumOf { it.size })
        var at = 0
        for (c in all) { c.copyInto(pcm, at); at += c.size }
        return Utterance(pcm, RATE)
    }

    companion object {
        const val RATE = 16_000
        const val MAX_SECONDS = 60
    }
}
