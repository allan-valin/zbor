package mk.zbor.core

/** Returns a canned transcript; records calls. */
class FakeStt(private val out: String) : SttEngine {
    var calls = 0; private set
    override fun transcribe(audio: Utterance, lang: Lang): String { calls++; return out }
}

/** Echoes "<to>:<text>"; records the exact text it was asked to translate. */
class FakeMt : MtEngine {
    var calls = 0; private set
    var lastText: String? = null; private set
    override fun translate(text: String, from: Lang, to: Lang): String {
        calls++; lastText = text; return "$to:$text"
    }
}

/** Produces a 1-sample utterance; records the exact text it was asked to speak. */
class FakeTts : TtsEngine {
    var calls = 0; private set
    var lastText: String? = null; private set
    override fun synthesize(text: String, lang: Lang): Utterance {
        calls++; lastText = text; return Utterance(shortArrayOf(text.length.toShort()), 16_000)
    }
}
