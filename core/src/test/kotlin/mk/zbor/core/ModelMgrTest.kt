package mk.zbor.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val GIB = 1L shl 30

/** A model that counts how often it was loaded and whether it is closed. */
private class Counted : AutoCloseable {
    var closed = false; private set
    override fun close() { closed = true }
}

private class Slots {
    var loads = mutableMapOf<ModelKind, Int>()
    val made = mutableListOf<Counted>()
    fun slot(kind: ModelKind) = ModelSlot(kind) {
        loads[kind] = (loads[kind] ?: 0) + 1
        Counted().also { made += it }
    }
    val stt = slot(ModelKind.STT); val mt = slot(ModelKind.MT); val tts = slot(ModelKind.TTS)
}

class ResidencyPolicyTest {
    @Test fun `policy follows device ram`() {
        assertEquals(ResidencyPolicy.HOT, ResidencyPolicy.forRam(8 * GIB))
        assertEquals(ResidencyPolicy.HOT, ResidencyPolicy.forRam(6 * GIB))
        assertEquals(ResidencyPolicy.WARM, ResidencyPolicy.forRam(5 * GIB))
        assertEquals(ResidencyPolicy.COLD, ResidencyPolicy.forRam(3 * GIB))
    }

    @Test fun `colder steps down and bottoms out at COLD`() {
        assertEquals(ResidencyPolicy.WARM, ResidencyPolicy.HOT.colder())
        assertEquals(ResidencyPolicy.COLD, ResidencyPolicy.WARM.colder())
        assertEquals(ResidencyPolicy.COLD, ResidencyPolicy.COLD.colder())
    }
}

class ModelMgrTest {
    @Test fun `HOT keeps every model resident across turns (the neural voice too)`() {
        val s = Slots(); val m = ModelMgr(ResidencyPolicy.HOT)
        repeat(3) { m.use(s.stt) {}; m.use(s.mt) {}; m.use(s.tts) {} }
        assertEquals(1, s.loads[ModelKind.STT])
        assertEquals(1, s.loads[ModelKind.MT])
        assertEquals(1, s.loads[ModelKind.TTS])
    }

    @Test fun `WARM reloads tts every turn`() {
        val s = Slots(); val m = ModelMgr(ResidencyPolicy.WARM)
        repeat(2) { m.use(s.tts) {} }
        assertEquals(2, s.loads[ModelKind.TTS])
        assertFalse(m.isResident(ModelKind.TTS))
    }

    @Test fun `WARM keeps only stt`() {
        val s = Slots(); val m = ModelMgr(ResidencyPolicy.WARM)
        repeat(2) { m.use(s.stt) {}; m.use(s.mt) {} }
        assertEquals(1, s.loads[ModelKind.STT])
        assertEquals(2, s.loads[ModelKind.MT])
    }

    @Test fun `COLD closes every model after use`() {
        val s = Slots(); val m = ModelMgr(ResidencyPolicy.COLD)
        m.use(s.stt) {}; m.use(s.mt) {}
        assertTrue(s.made.all { it.closed })
    }

    @Test fun `use hands the block the loaded model and returns its result`() {
        val m = ModelMgr(ResidencyPolicy.HOT)
        val slot = ModelSlot(ModelKind.MT) { Counted() }
        assertEquals("ok", m.use(slot) { model -> if (!model.closed) "ok" else "closed" })
    }

    @Test fun `memory pressure steps colder and evicts what is no longer kept`() {
        val s = Slots(); val changes = mutableListOf<ResidencyPolicy>()
        val m = ModelMgr(ResidencyPolicy.HOT) { changes += it }
        m.use(s.stt) {}; m.use(s.mt) {}
        m.onMemoryPressure()
        assertEquals(ResidencyPolicy.WARM, m.policy)
        assertTrue(m.isResident(ModelKind.STT))
        assertFalse(m.isResident(ModelKind.MT))
        assertTrue(s.made[1].closed) // the MT model was released
        assertEquals(listOf(ResidencyPolicy.WARM), changes)
    }

    @Test fun `out of memory on load steps colder and retries once`() {
        var attempts = 0
        val m = ModelMgr(ResidencyPolicy.HOT)
        val s = Slots(); m.use(s.stt) {}
        val flaky = ModelSlot(ModelKind.MT) {
            if (attempts++ == 0) throw OutOfMemoryError("first try") else Counted()
        }
        m.use(flaky) {}
        assertEquals(2, attempts)
        assertEquals(ResidencyPolicy.WARM, m.policy)
    }

    @Test fun `close releases everything`() {
        val s = Slots(); val m = ModelMgr(ResidencyPolicy.HOT)
        m.use(s.stt) {}; m.use(s.mt) {}
        m.close()
        assertTrue(s.made.all { it.closed })
        assertFalse(m.isResident(ModelKind.STT))
    }

    @Test fun `evict drops one resident model so the next use reloads it`() {
        val s = Slots(); val m = ModelMgr(ResidencyPolicy.HOT)
        m.use(s.tts) {}
        m.evict(ModelKind.TTS)
        assertTrue(s.made.single().closed)
        m.use(s.tts) {}
        assertEquals(2, s.loads[ModelKind.TTS])
    }
}
