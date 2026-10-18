package mk.zbor.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun audio() = Utterance(shortArrayOf(0, 0, 0), 16_000)

class PipelineOrchestratorTest {
    @Test fun `submitAudio transcribes and halts at input seam without translating`() {
        val mt = FakeMt()
        val o = PipelineOrchestrator(FakeStt("здраво"), mt, FakeTts())
        o.startTurn(Lang.MK, Lang.PT)
        val s = o.submitAudio(audio())
        assertIs<TurnState.AwaitingInputConfirm>(s)
        assertEquals("здраво", s.sttText)
        assertEquals(0, mt.calls) // seam holds: no translation yet
    }

    @Test fun `stt failure yields Failed(STT)`() {
        val stt = object : SttEngine {
            override fun transcribe(audio: Utterance, lang: Lang) =
                throw EngineException(Stage.STT, "boom")
        }
        val o = PipelineOrchestrator(stt, FakeMt(), FakeTts())
        o.startTurn(Lang.MK, Lang.PT)
        val s = o.submitAudio(audio())
        assertIs<TurnState.Failed>(s)
        assertEquals(Stage.STT, s.stage)
    }

    @Test fun `blank transcript stays at seam for manual entry`() {
        val o = PipelineOrchestrator(FakeStt("   "), FakeMt(), FakeTts())
        o.startTurn(Lang.MK, Lang.PT)
        val s = o.submitAudio(audio())
        assertIs<TurnState.AwaitingInputConfirm>(s)
        assertTrue(s.sttText.isBlank())
    }
}

class PipelineConfirmTest {
    @Test fun `confirmInput translates the EDITED text, then speaks it`() {
        val mt = FakeMt(); val tts = FakeTts()
        val o = PipelineOrchestrator(FakeStt("здравo-typo"), mt, tts) // outputSeam=false
        o.startTurn(Lang.MK, Lang.PT)
        o.submitAudio(Utterance(shortArrayOf(0), 16_000))
        val s = o.confirmInput("здраво") // user corrected the transcript
        assertIs<TurnState.Done>(s)
        assertEquals("здраво", mt.lastText)   // edited text was translated
        assertEquals("PT:здраво", tts.lastText) // confirmed translation was spoken
        assertEquals("PT:здраво", s.mtText)
    }

    @Test fun `output seam halts before TTS until confirmOutput`() {
        val tts = FakeTts()
        val o = PipelineOrchestrator(FakeStt("здраво"), FakeMt(), tts, outputSeam = true)
        o.startTurn(Lang.MK, Lang.PT)
        o.submitAudio(Utterance(shortArrayOf(0), 16_000))
        val mid = o.confirmInput("здраво")
        assertIs<TurnState.AwaitingOutputConfirm>(mid)
        assertEquals(0, tts.calls)            // not spoken yet
        val done = o.confirmOutput("PT:здраво editado")
        assertIs<TurnState.Done>(done)
        assertEquals("PT:здраво editado", tts.lastText)
    }

    @Test fun `output seam keeps the confirmed input text in Done`() {
        val o = PipelineOrchestrator(FakeStt("здравo-typo"), FakeMt(), FakeTts(), outputSeam = true)
        o.startTurn(Lang.MK, Lang.PT)
        o.submitAudio(Utterance(shortArrayOf(0), 16_000))
        o.confirmInput("здраво")
        val done = o.confirmOutput("olá")
        assertIs<TurnState.Done>(done)
        assertEquals("здраво", done.sttText) // the speaker's words, not the translation
        assertEquals("olá", done.mtText)
    }

    @Test fun `mt failure yields Failed(MT)`() {
        val mt = object : MtEngine {
            override fun translate(text: String, from: Lang, to: Lang) =
                throw EngineException(Stage.MT, "nope")
        }
        val o = PipelineOrchestrator(FakeStt("здраво"), mt, FakeTts())
        o.startTurn(Lang.MK, Lang.PT)
        o.submitAudio(Utterance(shortArrayOf(0), 16_000))
        val s = o.confirmInput("здраво")
        assertIs<TurnState.Failed>(s)
        assertEquals(Stage.MT, s.stage)
    }
}
