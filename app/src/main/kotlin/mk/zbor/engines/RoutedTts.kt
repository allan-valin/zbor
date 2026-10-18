package mk.zbor.engines

import mk.zbor.core.Lang
import mk.zbor.core.TtsEngine
import mk.zbor.core.Utterance
import mk.zbor.tts.VitsTtsEngine

/** Macedonian → the neural voice when present and switched on; everything else → eSpeak. */
class RoutedTts(
    private val espeak: EspeakTtsEngine,
    private val neural: VitsTtsEngine?,
    private val useNeural: () -> Boolean,
) : TtsEngine, AutoCloseable {
    override fun synthesize(text: String, lang: Lang): Utterance =
        if (lang == Lang.MK && neural != null && useNeural()) neural.synthesize(text, lang)
        else espeak.synthesize(text, lang)

    override fun close() {
        espeak.close()
        neural?.close()
    }
}
