package mk.zbor.core

import kotlin.test.Test
import kotlin.test.assertEquals

class EnginesTest {
    @Test fun `fakes conform to interfaces`() {
        val stt: SttEngine = FakeStt("здраво")
        val mt: MtEngine = FakeMt()
        val tts: TtsEngine = FakeTts()
        assertEquals("здраво", stt.transcribe(Utterance(shortArrayOf(0), 16_000), Lang.MK))
        assertEquals("PT:здраво", mt.translate("здраво", Lang.MK, Lang.PT))
        assertEquals(1, tts.synthesize("oi", Lang.PT).pcm.size)
    }
}
