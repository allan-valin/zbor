package mk.zbor.mt

import mk.zbor.core.Lang
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/** The real pack model (int8, LM head hoisted, .ort) on the desktop runtime; expectations come from the fp32 spike (spikes/mt_out.txt). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NllbMtEngineTest {
    private val engine by lazy {
        NllbMtEngine.open(
            Models.pack("nllb-encoder.ort"),
            Models.pack("nllb-decoder.ort"),
            Models.pack("nllb-tokenizer.bin").inputStream().use(NllbTokenizer::readCompact),
        )
    }

    @AfterAll fun close() { if (Models.pack.isDirectory) engine.close() }

    @Test fun `en to mk plain sentence`() {
        assertContains(engine.translate("Where is the bus station?", Lang.EN, Lang.MK).lowercase(), "автобуск")
    }

    @Test fun `en to mk idiom goes literal like the spike`() {
        assertContains(engine.translate("It's raining cats and dogs.", Lang.EN, Lang.MK), "мачки")
    }

    @Test fun `pt to mk keeps the one idiom the spike kept`() {
        assertContains(engine.translate("Custa os olhos da cara.", Lang.PT, Lang.MK), "скапо")
    }

    @Test fun `mk to pt and de come back in Latin script`() {
        val pt = engine.translate("Каде е автобуската станица?", Lang.MK, Lang.PT)
        val de = engine.translate("Каде е автобуската станица?", Lang.MK, Lang.DE)
        println("mk->pt: $pt\nmk->de: $de")
        assertTrue(pt.isNotBlank() && pt.none { it in 'А'..'я' }, pt)
        assertContains(de.lowercase(), "bus")
    }

    @Test fun `blank input translates to blank without running the model`() {
        assertTrue(engine.translate("   ", Lang.PT, Lang.MK).isEmpty())
    }
}
