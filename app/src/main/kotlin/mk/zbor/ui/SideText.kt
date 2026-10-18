package mk.zbor.ui

import mk.zbor.core.Lang

/**
 * Words shown on each half, in that half's language: the Macedonian speaker must
 * never need to read pt/en/de. App chrome (setup, settings) is pt-BR.
 */
data class SideText(
    val idle: String,
    val recording: String,
    val transcribing: String,
    val edit: String,
    val translating: String,
    val review: String,
    val speaking: String,
    val otherTurn: String,
    val failed: String,
    val noMic: String,
    val typeHere: String,
)

val Lang.flag: String
    get() = when (this) {
        Lang.MK -> "🇲🇰"; Lang.PT -> "🇧🇷"; Lang.EN -> "🇬🇧"; Lang.DE -> "🇩🇪"
    }

val Lang.nativeName: String
    get() = when (this) {
        Lang.MK -> "Македонски"; Lang.PT -> "Português"; Lang.EN -> "English"; Lang.DE -> "Deutsch"
    }

val Lang.text: SideText
    get() = when (this) {
        Lang.MK -> SideText(
            idle = "Притиснете го микрофонот или пишувајте",
            recording = "Снимам… притиснете пак за крај",
            transcribing = "Препознавам говор…",
            edit = "Поправете го текстот, па притиснете ✓",
            translating = "Преведувам…",
            review = "Поправете го преводот, па притиснете ✓",
            speaking = "Зборувам…",
            otherTurn = "Другиот зборува…",
            failed = "Грешка",
            noMic = "Нема дозвола за микрофон",
            typeHere = "Пишувајте тука",
        )
        Lang.PT -> SideText(
            idle = "Toque no microfone ou digite",
            recording = "Gravando… toque de novo para parar",
            transcribing = "Reconhecendo a fala…",
            edit = "Corrija o texto e toque em ✓",
            translating = "Traduzindo…",
            review = "Corrija a tradução e toque em ✓",
            speaking = "Falando…",
            otherTurn = "A outra pessoa está falando…",
            failed = "Erro",
            noMic = "Sem permissão para o microfone",
            typeHere = "Digite aqui",
        )
        Lang.EN -> SideText(
            idle = "Tap the microphone or type",
            recording = "Recording… tap again to stop",
            transcribing = "Recognising speech…",
            edit = "Fix the text, then tap ✓",
            translating = "Translating…",
            review = "Fix the translation, then tap ✓",
            speaking = "Speaking…",
            otherTurn = "The other person is talking…",
            failed = "Error",
            noMic = "No microphone permission",
            typeHere = "Type here",
        )
        Lang.DE -> SideText(
            idle = "Mikrofon antippen oder tippen",
            recording = "Aufnahme… zum Beenden erneut tippen",
            transcribing = "Spracherkennung…",
            edit = "Text korrigieren, dann ✓ antippen",
            translating = "Übersetze…",
            review = "Übersetzung korrigieren, dann ✓ antippen",
            speaking = "Spreche…",
            otherTurn = "Die andere Person spricht…",
            failed = "Fehler",
            noMic = "Keine Mikrofonberechtigung",
            typeHere = "Hier tippen",
        )
    }
