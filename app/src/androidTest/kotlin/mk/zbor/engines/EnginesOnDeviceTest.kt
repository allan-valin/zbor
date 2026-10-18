package mk.zbor.engines

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mk.zbor.core.Lang
import mk.zbor.core.ModelKind
import mk.zbor.core.PipelineOrchestrator
import mk.zbor.core.ResidencyPolicy
import mk.zbor.core.TurnState
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Real engines on the device. Needs the model pack pushed to
 * Android/data/mk.zbor/files/models (see docs/BUILD.md); skips without it.
 */
@RunWith(AndroidJUnit4::class)
class EnginesOnDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val pack = ModelPack.location(ctx)

    private fun wav(name: String) = readWav(assets.open(name).use { it.readBytes() })
    private fun log(msg: String) = android.util.Log.i("ZborTest", msg)

    @Before fun needsPack() = assumeTrue("model pack missing in ${pack.dir}", pack.whisper.isFile)

    @Test fun whisperTranscribesEnglish() {
        WhisperSttEngine.open(pack.whisper).use { stt ->
            val t0 = System.nanoTime()
            val text = stt.transcribe(wav("jfk.wav"), Lang.EN)
            log("whisper en ${(System.nanoTime() - t0) / 1_000_000} ms: $text")
            assertContains(text.lowercase(), "country")
        }
    }

    @Test fun whisperTranscribesMacedonianInCyrillic() {
        WhisperSttEngine.open(pack.whisper).use { stt ->
            val text = stt.transcribe(wav("mk_espeak.wav"), Lang.MK)
            log("whisper mk: $text")
            assertTrue(text.any { it in 'А'..'ш' || it in "ѓќљњџѕј" }, text)
        }
    }

    @Test fun espeakSpeaksMacedonian() {
        EspeakTtsEngine.open(pack.dir).use { tts ->
            val u = tts.synthesize("Здраво, како си?", Lang.MK)
            log("espeak mk: ${u.pcm.size} samples @ ${u.sampleRate}")
            assertTrue(u.pcm.size > u.sampleRate * 0.3, "too short: ${u.pcm.size}")
            assertTrue(u.pcm.maxOf { abs(it.toInt()) } > 1000, "silent")
        }
    }

    @Test fun espeakSpeaksEveryLanguage() {
        EspeakTtsEngine.open(pack.dir).use { tts ->
            for ((lang, text) in listOf(Lang.PT to "Olá", Lang.EN to "Hello", Lang.DE to "Hallo")) {
                assertTrue(tts.synthesize(text, lang).pcm.isNotEmpty(), "$lang")
            }
        }
    }

    @Test fun fullTurnWithRealEngines() {
        ZborEngines.create(pack, ResidencyPolicy.COLD).use { e ->
            val o = PipelineOrchestrator(e.stt, e.mt, e.tts)
            o.startTurn(Lang.MK, Lang.PT)
            val seam = o.submitAudio(wav("mk_espeak.wav"))
            assertIs<TurnState.AwaitingInputConfirm>(seam)
            log("turn stt: ${seam.sttText}")
            val t0 = System.nanoTime()
            val done = o.confirmInput("Каде е автобуската станица?") // the edit seam: speaker fixes the text
            log("turn mt+tts ${(System.nanoTime() - t0) / 1_000_000} ms: $done")
            assertIs<TurnState.Done>(done)
            assertTrue("ônibus" in done.mtText || "autocarro" in done.mtText, done.mtText)
            assertTrue(done.audio.pcm.isNotEmpty())
            assertTrue(!e.models.isResident(ModelKind.MT)) // COLD evicted it
        }
    }
}
