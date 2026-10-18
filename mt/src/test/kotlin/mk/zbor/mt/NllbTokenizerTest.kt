package mk.zbor.mt

import mk.zbor.core.Lang
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class NllbTokenizerTest {
    private val goldens = javaClass.getResource("/tokenizer_goldens.tsv")!!.readText()
        .lines().filter { it.isNotBlank() }
        .map { line -> line.split("\t").let { it[0] to it[1].split(" ").map(String::toInt) } }

    private val tok by lazy { NllbTokenizer.fromTokenizerJson(Models.source("tokenizer.json")) }

    @Test fun `encode matches the HF tokenizer on every golden sentence`() {
        for ((text, ids) in goldens) assertEquals(ids, tok.encode(text, Lang.EN), text)
    }

    @Test fun `source language code leads the ids`() {
        assertEquals(256118, tok.encode("Здраво", Lang.MK).first())
        assertEquals(2, tok.encode("Здраво", Lang.MK).last())
    }

    @Test fun `decode skips specials and restores spaces`() {
        for ((text, ids) in goldens.filter { 3 !in it.second }) {
            assertEquals(text.replace(Regex(" {2,}"), " "), tok.decode(ids), text)
        }
    }

    @Test fun `compact form round-trips to identical encodings`() {
        val bytes = ByteArrayOutputStream().also { tok.writeCompact(it) }.toByteArray()
        val back = NllbTokenizer.readCompact(ByteArrayInputStream(bytes))
        for ((text, ids) in goldens) assertEquals(ids, back.encode(text, Lang.EN), text)
    }

    @Test fun `lang token ids`() {
        assertEquals(256118, tok.langId(Lang.MK))
        assertEquals(256141, tok.langId(Lang.PT))
        assertEquals(256047, tok.langId(Lang.EN))
        assertEquals(256042, tok.langId(Lang.DE))
    }
}
