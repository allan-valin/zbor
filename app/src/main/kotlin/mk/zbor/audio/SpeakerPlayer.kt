package mk.zbor.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.delay
import mk.zbor.app.Player
import mk.zbor.core.Utterance

/** Plays a whole utterance on the loudspeaker and returns when it has finished. */
class SpeakerPlayer : Player {
    @Volatile private var track: AudioTrack? = null

    override suspend fun play(audio: Utterance) {
        if (audio.pcm.isEmpty()) return
        stop()
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(audio.sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(audio.pcm.size * 2)
            .build()
        t.write(audio.pcm, 0, audio.pcm.size)
        track = t
        t.play()
        try {
            while (track === t && t.playbackHeadPosition < audio.pcm.size) delay(50)
        } finally {
            if (track === t) stop()
        }
    }

    override fun stop() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }
}
