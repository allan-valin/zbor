package mk.zbor.tts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.Normalizer
import java.util.Locale

/** The MMS-TTS character vocabulary. */
class VitsVocab(private val ids: Map<String, Int>) {
    operator fun contains(c: String) = c in ids

    /**
     * Reproduces the HF MMS tokenizer exactly, including its quirk: the pad token (`п` for
     * Bulgarian, id 0) is a special token, so the text is split at every `п`; each other run
     * of letters becomes `[blank, c1, blank, c2, …, blank]` and each `п` a bare id 0.
     * The voice was fine-tuned through that tokenizer, so this is what it expects.
     */
    fun encode(modelText: String): LongArray {
        val out = ArrayList<Long>()
        val run = ArrayList<Int>()
        fun flush() {
            if (run.isEmpty()) return
            out += BLANK
            for (id in run) { out += id.toLong(); out += BLANK }
            run.clear()
        }
        modelText.codePoints().forEach { cp ->
            val c = String(Character.toChars(cp))
            if (c == PAD) { flush(); out += BLANK; return@forEach }
            ids[c]?.let(run::add)
        }
        flush()
        return out.toLongArray()
    }

    companion object {
        private const val PAD = "п"
        private const val BLANK = 0L

        fun load(file: File) = VitsVocab(
            Json.parseToJsonElement(file.readText()).jsonObject.mapValues { it.value.jsonPrimitive.int },
        )
    }
}

/**
 * Macedonian text → the Bulgarian voice's alphabet. Must equal tools/voice/mk_text.py
 * (checked against eval/mk_text_goldens.tsv), plus numbers spelled out first.
 */
class MkTextFrontEnd(private val vocab: VitsVocab) {
    fun toModelText(text: String): String {
        val t = Normalizer.normalize(MkNumbers.spellOut(text).lowercase(Locale.ROOT), Normalizer.Form.NFC)
        val sb = StringBuilder()
        t.codePoints().forEach { cp ->
            val c = String(Character.toChars(cp))
            val mapped = MAP[c] ?: c
            for (m in mapped.codePoints().toArray()) {
                val s = String(Character.toChars(m))
                sb.append(if (s in vocab) s else " ")
            }
        }
        return sb.toString().replace(SPACES, " ").trim()
    }

    companion object {
        /** Macedonian letters missing from the Bulgarian vocabulary, spelled the Bulgarian way. */
        val MAP = mapOf("ѓ" to "гь", "ќ" to "кь", "ј" to "й", "љ" to "ль", "њ" to "нь", "џ" to "дж", "ѕ" to "дз",
            "ѐ" to "е", "ѝ" to "и")
        private val SPACES = Regex("\\s+")
    }
}
