package mk.zbor.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TurnStateTest {
    @Test fun `states carry their data`() {
        val a = TurnState.AwaitingInputConfirm("здраво", Lang.MK, Lang.PT)
        assertEquals(Lang.PT, a.to)
        val f = TurnState.Failed(Stage.STT, "empty")
        assertEquals(Stage.STT, f.stage)
    }
}
