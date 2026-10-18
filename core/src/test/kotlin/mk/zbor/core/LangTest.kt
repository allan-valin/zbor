package mk.zbor.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LangTest {
    @Test fun `four supported languages`() {
        assertEquals(4, Lang.entries.size)
    }
    @Test fun `utterance keeps pcm and rate`() {
        val u = Utterance(shortArrayOf(1, 2, 3), 16_000)
        assertEquals(16_000, u.sampleRate)
        assertEquals(3, u.pcm.size)
    }
}
