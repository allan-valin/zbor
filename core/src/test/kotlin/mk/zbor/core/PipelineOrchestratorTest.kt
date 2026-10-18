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
