package mk.zbor.engines

import mk.zbor.core.Lang
import mk.zbor.core.TtsEngine
import mk.zbor.core.Utterance

/** Each language → its natural voice when installed and switched on; otherwise eSpeak. */
class RoutedTts(
    private val espeak: EspeakTtsEngine,
    private val natural: Map<Lang, TtsEngine>,
    private val useNatural: (Lang) -> Boolean,
) : TtsEngine, AutoCloseable {
    override fun synthesize(text: String, lang: Lang): Utterance {
        val nat = natural[lang]?.takeIf { useNatural(lang) }
        val t0 = System.nanoTime()
        val u = (nat ?: espeak).synthesize(text, lang)
        // One line per utterance: which voice spoke (class names are minified in release builds).
        android.util.Log.i("ZborTts", "$lang ${if (nat != null) "natural" else "espeak"} ${u.pcm.size} samples @ ${u.sampleRate} Hz in ${(System.nanoTime() - t0) / 1_000_000} ms")
        return u
    }

    override fun close() {
        espeak.close()
        natural.values.forEach { (it as? AutoCloseable)?.close() }
    }
}
