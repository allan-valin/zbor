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
        val voice = natural[lang]?.takeIf { useNatural(lang) } ?: espeak
        return voice.synthesize(text, lang)
    }

    override fun close() {
        espeak.close()
        natural.values.forEach { (it as? AutoCloseable)?.close() }
    }
}
