package mk.zbor.tts

/**
 * Macedonian number words, so the neural voice (whose alphabet has no digits) can say
 * what the translator writes as "250 денари" or "7:15". Masculine/default forms;
 * "и" joins the last part ("сто дваесет и пет", "две илјади и пет").
 */
object MkNumbers {
    private val ones = listOf("нула", "еден", "два", "три", "четири", "пет", "шест", "седум", "осум", "девет",
        "десет", "единаесет", "дванаесет", "тринаесет", "четиринаесет", "петнаесет", "шеснаесет",
        "седумнаесет", "осумнаесет", "деветнаесет")
    private val tens = listOf("", "", "дваесет", "триесет", "четириесет", "педесет", "шеесет", "седумдесет",
        "осумдесет", "деведесет")
    private val hundreds = listOf("", "сто", "двесте", "триста", "четиристотини", "петстотини", "шестотини",
        "седумстотини", "осумстотини", "деветстотини")

    fun words(n: Long): String {
        require(n >= 0) { "negative" }
        if (n == 0L) return ones[0]
        val parts = parts(n)
        return if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(" ") + " и " + parts.last()
    }

    private fun parts(n: Long): List<String> {
        val out = ArrayList<String>()
        val millions = n / 1_000_000
        val thousands = (n / 1000) % 1000
        val rest = (n % 1000).toInt()
        if (millions > 0) out += if (millions == 1L) "еден милион" else "${words(millions)} милиони"
        if (thousands > 0) out += when (thousands) {
            1L -> "илјада"
            2L -> "две илјади"
            else -> "${words(thousands)} илјади"
        }
        if (rest >= 100) out += hundreds[rest / 100]
        val r = rest % 100
        when {
            r == 0 -> Unit
            r < 20 -> out += ones[r]
            else -> {
                out += tens[r / 10]
                if (r % 10 > 0) out += ones[r % 10]
            }
        }
        return out
    }

    private val time = Regex("""\b(\d{1,2}):(\d{2})\b""")
    private val decimal = Regex("""(\d+)[.,](\d+)""")
    private val integer = Regex("""\d+""")

    /** Replaces times, decimals, percents and integers in [text] with words. */
    fun spellOut(text: String): String = text
        .replace(time) { m -> words(m.groupValues[1].toLong()) + " и " + words(m.groupValues[2].toLong()) }
        .replace(decimal) { m -> words(m.groupValues[1].toLong()) + " запирка " + words(m.groupValues[2].toLong()) }
        .replace("%", " проценти")
        .replace(integer) { m -> m.value.toLongOrNull()?.let(::words) ?: m.value }
        .replace(Regex(" {2,}"), " ")
        .trim()
}
