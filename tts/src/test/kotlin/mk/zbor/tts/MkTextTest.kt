package mk.zbor.tts

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

private val eval = File(System.getenv("ZBOR_EVAL") ?: "")
private val vocabFile = File(System.getenv("ZBOR_VOICE") ?: "", "pilot.vocab.json")

class MkNumbersTest {
    @Test fun `small numbers`() {
        assertEquals("нула", MkNumbers.words(0))
        assertEquals("еден", MkNumbers.words(1))
        assertEquals("петнаесет", MkNumbers.words(15))
        assertEquals("дваесет и пет", MkNumbers.words(25))
        assertEquals("седумдесет и два", MkNumbers.words(72))
    }

    @Test fun `hundreds and thousands put и before the last part`() {
        assertEquals("сто и пет", MkNumbers.words(105))
        assertEquals("сто дваесет и пет", MkNumbers.words(125))
        assertEquals("двесте и педесет", MkNumbers.words(250))
        assertEquals("триста", MkNumbers.words(300))
        assertEquals("илјада", MkNumbers.words(1000))
        assertEquals("илјада двесте и педесет", MkNumbers.words(1250))
        assertEquals("две илјади и пет", MkNumbers.words(2005))
        assertEquals("еден милион", MkNumbers.words(1_000_000))
    }

    @Test fun `numbers inside text are spelled out`() {
        assertEquals("Тоа чини двесте и педесет денари.", MkNumbers.spellOut("Тоа чини 250 денари."))
        assertEquals("седум и петнаесет", MkNumbers.spellOut("7:15"))
        assertEquals("два запирка пет проценти", MkNumbers.spellOut("2,5%"))
    }
}

class MkTextFrontEndTest {
    @Test fun `matches the Python front-end on every eval sentence`() {
        assumeTrue(vocabFile.isFile, "no voice vocab at $vocabFile")
        val fe = MkTextFrontEnd(VitsVocab.load(vocabFile))
        for (line in File(eval, "mk_text_goldens.tsv").readLines().filter { it.isNotBlank() }) {
            val (mk, expected) = line.split("\t")
            assertEquals(expected, fe.toModelText(mk), mk)
        }
    }

    @Test fun `digits become words before the alphabet filter`() {
        assumeTrue(vocabFile.isFile)
        val fe = MkTextFrontEnd(VitsVocab.load(vocabFile))
        assertEquals("тоа чини двесте и педесет денари", fe.toModelText("Тоа чини 250 денари."))
    }

    @Test fun `tokenizer interleaves blanks exactly like the HF tokenizer`() {
        assumeTrue(vocabFile.isFile)
        val vocab = VitsVocab.load(vocabFile)
        for (line in File(eval, "mk_tts_ids_goldens.tsv").readLines().filter { it.isNotBlank() }) {
            val (text, ids) = line.split("\t")
            assertEquals(ids.split(" ").map(String::toLong), vocab.encode(text).toList(), text)
        }
    }
}
