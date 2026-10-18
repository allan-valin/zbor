package mk.zbor.mt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mk.zbor.core.Lang
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.Normalizer

/** NLLB's FLORES-200 code for each language Zbor speaks. */
val Lang.nllbCode: String
    get() = when (this) {
        Lang.MK -> "mkd_Cyrl"
        Lang.PT -> "por_Latn"
        Lang.EN -> "eng_Latn"
        Lang.DE -> "deu_Latn"
    }

/**
 * The NLLB SentencePiece-BPE tokenizer, reimplemented from HF `tokenizer.json`
 * (NFKC + space collapse, Metaspace `▁` words, rank-ordered BPE merges, fused unk).
 * Encodings match HF `tokenizers` exactly on the golden set.
 *
 * The JSON is 17 MB; [writeCompact] stores the same data as ~6 MB of ints so the
 * phone never parses JSON.
 */
class NllbTokenizer private constructor(
    private val vocab: Array<String>,
    private val mergePairs: IntArray, // rank r -> (left, right) at [2r], [2r+1]
    private val specials: IntArray,
) {
    private val ids = HashMap<String, Int>(vocab.size * 2).apply {
        vocab.forEachIndexed { id, tok -> if (tok.isNotEmpty()) put(tok, id) }
    }
    private val specialSet = specials.toHashSet()
    private val rankOf = LongIntMap(mergePairs.size / 2).apply {
        for (r in 0 until mergePairs.size / 2) put(pairKey(mergePairs[2 * r], mergePairs[2 * r + 1]), r)
    }
    private val mergedId = IntArray(mergePairs.size / 2) { r ->
        ids.getValue(vocab[mergePairs[2 * r]] + vocab[mergePairs[2 * r + 1]])
    }

    fun langId(lang: Lang): Int = ids.getValue(lang.nllbCode)

    /** `[src_lang] tokens… </s>`, the NLLB source convention. */
    fun encode(text: String, src: Lang): List<Int> {
        val out = ArrayList<Int>()
        out += langId(src)
        for (word in metaspaceWords(normalize(text))) bpe(word, out)
        out += EOS
        return out
    }

    fun decode(tokenIds: List<Int>): String = tokenIds
        .filter { it !in specialSet && it in vocab.indices }
        .joinToString("") { vocab[it] }
        .replace(SPACE_MARK, ' ')
        .removePrefix(" ")

    fun writeCompact(out: OutputStream) {
        val d = DataOutputStream(out.buffered())
        d.writeInt(MAGIC)
        d.writeInt(vocab.size)
        vocab.forEach(d::writeUTF)
        d.writeInt(mergePairs.size)
        mergePairs.forEach(d::writeInt)
        d.writeInt(specials.size)
        specials.forEach(d::writeInt)
        d.flush()
    }

    private fun bpe(word: String, out: MutableList<Int>) {
        // Initial symbols: one per code point; unknown runs fuse into a single <unk>.
        val sym = ArrayList<Int>()
        var i = 0
        while (i < word.length) {
            val cp = word.codePointAt(i)
            val id = ids[String(Character.toChars(cp))] ?: UNK
            if (!(id == UNK && sym.lastOrNull() == UNK)) sym += id
            i += Character.charCount(cp)
        }
        while (sym.size > 1) {
            var best = -1
            var bestRank = Int.MAX_VALUE
            for (j in 0 until sym.size - 1) {
                val r = rankOf.get(pairKey(sym[j], sym[j + 1]))
                if (r in 0 until bestRank) { bestRank = r; best = j }
            }
            if (best < 0) break
            sym[best] = mergedId[bestRank]
            sym.removeAt(best + 1)
        }
        out += sym
    }

    companion object {
        private const val MAGIC = 0x5a42544b // "ZBTK"
        private const val UNK = 3
        private const val EOS = 2
        private const val SPACE_MARK = '▁'
        private val MULTISPACE = Regex(" {2,}")
        private val WHITESPACE = Regex("\\s")

        private fun pairKey(a: Int, b: Int) = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)

        private fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace(WHITESPACE, " ")
                .replace(MULTISPACE, " ")
                .trim()

        /** `"a b"` → `["▁a", "▁b"]`: each word carries its leading space mark. */
        private fun metaspaceWords(text: String): List<String> {
            if (text.isEmpty()) return emptyList()
            return (SPACE_MARK + text.replace(' ', SPACE_MARK))
                .split(SPACE_MARK).drop(1).map { "$SPACE_MARK$it" }
        }

        fun fromTokenizerJson(file: File): NllbTokenizer {
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            val model = root.getValue("model").jsonObject
            val byId = HashMap<Int, String>()
            for ((tok, id) in model.getValue("vocab").jsonObject) byId[id.jsonPrimitive.int] = tok
            val added = root.getValue("added_tokens").jsonArray.map { it.jsonObject }
            val specials = ArrayList<Int>()
            for (a in added) {
                val id = a.getValue("id").jsonPrimitive.int
                byId[id] = a.getValue("content").jsonPrimitive.content
                if (a.getValue("special").jsonPrimitive.content == "true") specials += id
            }
            val vocab = Array(byId.keys.max() + 1) { byId[it] ?: "" }
            val index = HashMap<String, Int>(vocab.size * 2).apply {
                vocab.forEachIndexed { id, t -> if (t.isNotEmpty()) put(t, id) }
            }
            val merges = model.getValue("merges").jsonArray
            val pairs = IntArray(merges.size * 2)
            merges.forEachIndexed { r, m ->
                val (a, b) = m.jsonPrimitive.content.split(' ', limit = 2)
                pairs[2 * r] = index.getValue(a)
                pairs[2 * r + 1] = index.getValue(b)
            }
            return NllbTokenizer(vocab, pairs, specials.toIntArray())
        }

        fun readCompact(input: InputStream): NllbTokenizer {
            val d = DataInputStream(input.buffered())
            require(d.readInt() == MAGIC) { "not a Zbor tokenizer file" }
            val vocab = Array(d.readInt()) { d.readUTF() }
            val pairs = IntArray(d.readInt()) { d.readInt() }
            val specials = IntArray(d.readInt()) { d.readInt() }
            return NllbTokenizer(vocab, pairs, specials)
        }
    }
}

/** Open-addressing Long→Int map; avoids 500k boxed entries on the phone. Missing → -1. */
internal class LongIntMap(expected: Int) {
    private val cap = Integer.highestOneBit(maxOf(4, expected * 2) - 1) shl 1
    private val keys = LongArray(cap)
    private val vals = IntArray(cap) { -1 }

    fun put(key: Long, value: Int) {
        var i = slot(key)
        while (vals[i] != -1) {
            if (keys[i] == key) return // first (lowest) rank wins, as in HF
            i = (i + 1) and (cap - 1)
        }
        keys[i] = key; vals[i] = value
    }

    fun get(key: Long): Int {
        var i = slot(key)
        while (vals[i] != -1) {
            if (keys[i] == key) return vals[i]
            i = (i + 1) and (cap - 1)
        }
        return -1
    }

    private fun slot(key: Long): Int {
        var h = key * -0x61c8864680b583ebL
        h = h xor (h ushr 29)
        return h.toInt() and (cap - 1)
    }
}
