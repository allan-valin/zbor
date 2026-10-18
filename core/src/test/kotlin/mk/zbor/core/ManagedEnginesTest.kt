package mk.zbor.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class ClosableMt : MtEngine, AutoCloseable {
    var closed = false
    override fun translate(text: String, from: Lang, to: Lang) = "$to:$text"
    override fun close() { closed = true }
}

private class ClosableStt : SttEngine, AutoCloseable {
    override fun transcribe(audio: Utterance, lang: Lang) = "heard"
    override fun close() {}
}

private class ClosableTts : TtsEngine, AutoCloseable {
    var closed = false
    override fun synthesize(text: String, lang: Lang) = Utterance(shortArrayOf(1), 22_050)
    override fun close() { closed = true }
}

class ManagedEnginesTest {
    @Test fun `managed engines load through ModelMgr and obey its policy`() {
        val mgr = ModelMgr(ResidencyPolicy.HOT)
        val mts = mutableListOf<ClosableMt>(); val ttss = mutableListOf<ClosableTts>()
        val mt = ManagedMt(mgr, ModelSlot(ModelKind.MT) { ClosableMt().also { mts += it } })
        val tts = ManagedTts(mgr, ModelSlot(ModelKind.TTS) { ClosableTts().also { ttss += it } })
        val stt = ManagedStt(mgr, ModelSlot(ModelKind.STT) { ClosableStt() })

        assertEquals("heard", stt.transcribe(Utterance(shortArrayOf(0), 16_000), Lang.MK))
        assertEquals("PT:a", mt.translate("a", Lang.MK, Lang.PT))
        assertEquals("PT:b", mt.translate("b", Lang.MK, Lang.PT))
        tts.synthesize("x", Lang.PT); tts.synthesize("y", Lang.PT)
        assertEquals(1, mts.size)                 // MT stayed resident (HOT)
        assertTrue(!ttss.single().closed)         // so did TTS (HOT keeps the neural voice)
        assertTrue(mgr.isResident(ModelKind.STT))
    }
}
