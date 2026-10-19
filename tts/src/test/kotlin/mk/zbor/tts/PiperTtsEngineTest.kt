package mk.zbor.tts

import mk.zbor.core.Lang
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val home = System.getProperty("user.home")
private val voice = File("$home/zbor-build/piper/pt_BR-cadu-medium.onnx")
private val eval = File(System.getenv("ZBOR_EVAL") ?: "")

/** The desktop stand-in for the app's espeak JNI: `espeak-ng -q --ipa`, clauses joined by spaces. */
private fun cliPhonemes(text: String, voice: String): String {
    val p = ProcessBuilder("$home/zbor-build/espeak/host/src/espeak-ng", "-q", "--ipa", "-v", voice, text)
        .also { it.environment()["ESPEAK_DATA_PATH"] = "$home/zbor-build/espeak" }.start()
    return p.inputStream.bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
}

class PiperTtsEngineTest {
    @Test fun `phoneme ids match the Python reference on every pt sentence`() {
        assumeTrue(voice.isFile, "no Piper voice at $voice")
        val cfg = PiperConfig.load(File(voice.path + ".json"))
        for (line in File(eval, "pt_piper_goldens.tsv").readLines().filter { it.isNotBlank() }) {
            val (_, ipa, ids) = line.split("\t")
            assertEquals(ids.split(" ").map(String::toLong), cfg.ids(ipa).toList(), ipa)
        }
    }

    @Test fun `speaks Portuguese, slower setting is longer`() {
        assumeTrue(voice.isFile)
        fun len(wpm: Int) = PiperTtsEngine.open(Lang.PT, voice, ::cliPhonemes, { wpm }).use { e ->
            val u = e.synthesize("Onde fica a farmácia mais próxima? Custa 250 reais.", Lang.PT)
            assertTrue(u.pcm.maxOf { abs(it.toInt()) } > 10_000, "silent")
            assertEquals(22_050, u.sampleRate)
            u.pcm.size
        }
        val normal = len(175)
        assertTrue(normal > 22_050 * 2, "too short: $normal")
        assertTrue(len(120) > normal * 1.2)
    }
}
