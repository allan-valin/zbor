package mk.zbor.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mk.zbor.core.EngineException
import mk.zbor.core.Lang
import mk.zbor.core.MtEngine
import mk.zbor.core.PipelineOrchestrator
import mk.zbor.core.SttEngine
import mk.zbor.core.Stage
import mk.zbor.core.TtsEngine
import mk.zbor.core.Utterance
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeRecorder : Recorder {
    var recording = false
    var level: ((Float) -> Unit)? = null
    override fun start(onLevel: (Float) -> Unit) { recording = true; level = onLevel }
    override fun stop(): Utterance { recording = false; return Utterance(shortArrayOf(1, 2), 16_000) }
}

private class FakePlayer : Player {
    val played = mutableListOf<Utterance>()
    override suspend fun play(audio: Utterance) { played += audio }
    override fun stop() {}
}

private class Stt(var out: String = "здраво") : SttEngine {
    override fun transcribe(audio: Utterance, lang: Lang) = out
}

private class Mt(var fail: Boolean = false) : MtEngine {
    val seen = mutableListOf<Triple<String, Lang, Lang>>()
    override fun translate(text: String, from: Lang, to: Lang): String {
        if (fail) throw EngineException(Stage.MT, "mt down")
        seen += Triple(text, from, to); return "$to:$text"
    }
}

private class Tts(var fail: Boolean = false) : TtsEngine {
    val spoken = mutableListOf<Pair<String, Lang>>()
    override fun synthesize(text: String, lang: Lang): Utterance {
        if (fail) throw EngineException(Stage.TTS, "no voice")
        spoken += text to lang; return Utterance(shortArrayOf(text.length.toShort()), 22_050)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationControllerTest {
    private val recorder = FakeRecorder()
    private val player = FakePlayer()
    private val stt = Stt(); private val mt = Mt(); private val tts = Tts()

    private fun TestScope.controller(outputSeam: Boolean = false) = ConversationController(
        orchestratorFor = { seam -> PipelineOrchestrator(stt, mt, tts, seam) },
        recorder = recorder, player = player,
        scope = this, work = StandardTestDispatcher(testScheduler),
        outputSeam = outputSeam,
    )

    @Test fun `defaults put Macedonian on top facing the other person`() = runTest {
        val c = controller()
        assertEquals(Lang.MK, c.state.value.top.lang)
        assertEquals(Lang.PT, c.state.value.bottom.lang)
        assertEquals(Phase.IDLE, c.state.value.phase)
    }

    @Test fun `tap to talk records, second tap transcribes into the speaker's editable text`() = runTest {
        val c = controller()
        c.tapMic(Side.TOP)
        assertEquals(Phase.RECORDING, c.state.value.phase)
        assertEquals(Side.TOP, c.state.value.speaker)
        assertTrue(recorder.recording)
        recorder.level!!(0.7f)
        assertEquals(0.7f, c.state.value.level)

        c.tapMic(Side.TOP)
        assertEquals(Phase.TRANSCRIBING, c.state.value.phase)
        advanceUntilIdle()
        assertEquals(Phase.EDITING, c.state.value.phase)
        assertEquals("здраво", c.state.value.top.text)
        assertTrue(mt.seen.isEmpty()) // edit seam holds
    }

    @Test fun `confirm translates the edited text to the listener and speaks it`() = runTest {
        val c = controller()
        c.tapMic(Side.TOP); c.tapMic(Side.TOP); advanceUntilIdle()
        c.edit(Side.TOP, "Каде е автобуската станица?")
        c.confirm(Side.TOP)
        advanceUntilIdle()
        assertEquals(Triple("Каде е автобуската станица?", Lang.MK, Lang.PT), mt.seen.single())
        assertEquals("PT:Каде е автобуската станица?", c.state.value.bottom.text)
        assertEquals(1, player.played.size)
        assertEquals(Phase.IDLE, c.state.value.phase)
        assertTrue(c.state.value.canReplay(Side.BOTTOM))
    }

    @Test fun `replay plays the last translation again on the listener's side`() = runTest {
        val c = controller()
        c.tapMic(Side.BOTTOM); c.tapMic(Side.BOTTOM); advanceUntilIdle()
        c.confirm(Side.BOTTOM); advanceUntilIdle()
        c.replay(Side.TOP); advanceUntilIdle()
        assertEquals(2, player.played.size)
        c.replay(Side.BOTTOM); advanceUntilIdle() // speaker side has nothing to replay
        assertEquals(2, player.played.size)
    }

    @Test fun `the other side's mic is ignored while one side records`() = runTest {
        val c = controller()
        c.tapMic(Side.TOP)
        c.tapMic(Side.BOTTOM)
        assertEquals(Side.TOP, c.state.value.speaker)
        assertEquals(Phase.RECORDING, c.state.value.phase)
    }

    @Test fun `confirm with blank text does nothing`() = runTest {
        stt.out = "  "
        val c = controller()
        c.tapMic(Side.TOP); c.tapMic(Side.TOP); advanceUntilIdle()
        c.confirm(Side.TOP); advanceUntilIdle()
        assertEquals(Phase.EDITING, c.state.value.phase)
        assertTrue(mt.seen.isEmpty())
    }

    @Test fun `typing starts a turn without the microphone`() = runTest {
        val c = controller()
        c.startTyping(Side.BOTTOM)
        c.edit(Side.BOTTOM, "obrigado")
        c.confirm(Side.BOTTOM); advanceUntilIdle()
        assertEquals("MK:obrigado", c.state.value.top.text)
        assertEquals(listOf("MK:obrigado" to Lang.MK), tts.spoken)
    }

    @Test fun `output seam lets the listener side correct the translation before it is spoken`() = runTest {
        val c = controller(outputSeam = true)
        c.startTyping(Side.BOTTOM); c.edit(Side.BOTTOM, "oi"); c.confirm(Side.BOTTOM); advanceUntilIdle()
        assertEquals(Phase.REVIEWING, c.state.value.phase)
        assertEquals("MK:oi", c.state.value.top.text)
        assertTrue(tts.spoken.isEmpty())
        c.edit(Side.TOP, "Здраво")
        c.confirm(Side.TOP); advanceUntilIdle()
        assertEquals(listOf("Здраво" to Lang.MK), tts.spoken)
        assertEquals(Phase.IDLE, c.state.value.phase)
    }

    @Test fun `translation failure shows an error and allows a new turn`() = runTest {
        mt.fail = true
        val c = controller()
        c.startTyping(Side.BOTTOM); c.edit(Side.BOTTOM, "oi"); c.confirm(Side.BOTTOM); advanceUntilIdle()
        assertEquals(Phase.FAILED, c.state.value.phase)
        assertEquals(Stage.MT, c.state.value.error?.stage)
        c.tapMic(Side.TOP)
        assertEquals(Phase.RECORDING, c.state.value.phase)
        assertNull(c.state.value.error)
    }

    @Test fun `tts failure still shows the translation`() = runTest {
        tts.fail = true
        val c = controller()
        c.startTyping(Side.BOTTOM); c.edit(Side.BOTTOM, "oi"); c.confirm(Side.BOTTOM); advanceUntilIdle()
        assertEquals("MK:oi", c.state.value.top.text)
        assertEquals(Stage.TTS, c.state.value.error?.stage)
    }

    @Test fun `changing a side's language applies to the next turn`() = runTest {
        val c = controller()
        c.setLang(Side.BOTTOM, Lang.DE)
        c.startTyping(Side.BOTTOM); c.edit(Side.BOTTOM, "danke"); c.confirm(Side.BOTTOM); advanceUntilIdle()
        assertEquals(Triple("danke", Lang.DE, Lang.MK), mt.seen.single())
    }

    @Test fun `unexpected engine crash becomes a visible failure, not a dead app`() = runTest {
        val c = ConversationController(
            orchestratorFor = { PipelineOrchestrator(object : SttEngine {
                override fun transcribe(audio: Utterance, lang: Lang): String = error("native blew up")
            }, mt, tts) },
            recorder = recorder, player = player, scope = this, work = StandardTestDispatcher(testScheduler),
        )
        c.tapMic(Side.TOP); c.tapMic(Side.TOP); advanceUntilIdle()
        assertEquals(Phase.FAILED, c.state.value.phase)
        assertEquals(Stage.STT, c.state.value.error?.stage)
    }

    @Test fun `face-to-face mirroring is off by default and toggles`() = runTest {
        val c = controller()
        assertTrue(!c.state.value.faceToFace)
        c.setFaceToFace(true)
        assertTrue(c.state.value.faceToFace)
        c.setFaceToFace(false)
        assertTrue(!c.state.value.faceToFace)
    }
}
