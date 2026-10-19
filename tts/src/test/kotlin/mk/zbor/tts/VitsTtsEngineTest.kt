package mk.zbor.tts

import mk.zbor.core.EngineException
import mk.zbor.core.Lang
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Runs the shipped voice (tools/voice/export_onnx.py → bul-raw.onnx) on the desktop runtime. */
class VitsTtsEngineTest {
    private val dir = File(System.getenv("ZBOR_VOICE") ?: "")
    private fun engine(wpm: Int = 175): VitsTtsEngine {
        val model = File(dir, "bul-raw.onnx")
        assumeTrue(model.isFile, "no exported voice at $model")
        return VitsTtsEngine.open(model, File(dir, "bul-raw.vocab.json"), { wpm })
    }

    @Test fun `speaks a Macedonian sentence with numbers`() {
        engine().use { e ->
            val u = e.synthesize("Тоа чини 250 денари.", Lang.MK)
            assertTrue(u.pcm.size > 16_000, "too short: ${u.pcm.size}")
            assertTrue(u.pcm.maxOf { abs(it.toInt()) } > 10_000, "silent")
        }
    }

    @Test fun `slower setting gives longer audio`() {
        val text = "Каде е најблиската аптека?"
        val normal = engine(175).use { it.synthesize(text, Lang.MK).pcm.size }
        val slow = engine(120).use { it.synthesize(text, Lang.MK).pcm.size }
        assertTrue(slow > normal * 1.2, "slow $slow vs normal $normal")
    }

    @Test fun `refuses other languages`() {
        engine().use { e -> assertFailsWith<EngineException> { e.synthesize("olá", Lang.PT) } }
    }
}
