package mk.zbor.engines

import mk.zbor.core.Lang

enum class VoiceKind { MMS, PIPER }

/**
 * One optional natural voice. The base app speaks every language with eSpeak; these are
 * downloaded from settings (manifest entries marked optional) and replace eSpeak per language.
 */
data class VoiceSpec(val lang: Lang, val model: String, val kind: VoiceKind, val credit: String, val example: String) {
    val files: Set<String>
        get() = when (kind) {
            VoiceKind.MMS -> setOf(model, model.removeSuffix(".onnx") + ".vocab.json")
            VoiceKind.PIPER -> setOf(model, "$model.json")
        }
}

object Voices {
    val all = listOf(
        VoiceSpec(Lang.MK, "mk-voice.onnx", VoiceKind.MMS, "Meta MMS (Bulgarian voice)", "Здраво, како си? Каде е автобуската станица?"),
        VoiceSpec(Lang.PT, "pt-br-voice.onnx", VoiceKind.PIPER, "Piper “cadu”", "Olá, tudo bem? Onde fica a rodoviária?"),
        VoiceSpec(Lang.EN, "en-voice.onnx", VoiceKind.PIPER, "Piper", "Hello, how are you? Where is the bus station?"),
        VoiceSpec(Lang.DE, "de-voice.onnx", VoiceKind.PIPER, "Piper", "Hallo, wie geht's? Wo ist der Busbahnhof?"),
    )

    fun of(lang: Lang) = all.first { it.lang == lang }
}
