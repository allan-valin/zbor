package mk.zbor.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mk.zbor.BuildConfig
import mk.zbor.engines.ModelPack
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * First run end to end: no pack → setup screen → "Baixar" → the conversation.
 * Only with a debug build pointed at a host server
 * (`-Pzbor.packUrl=http://10.0.2.2:8765/`, see docs/BUILD.md); it deletes the pack.
 */
@RunWith(AndroidJUnit4::class)
class SetupUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun downloadFromScratchReachesTheConversation() {
        assumeTrue("needs a local test pack server", "10.0.2.2" in BuildConfig.PACK_URL)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ModelPack.location(ctx).dir.deleteRecursively()

        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntil(30_000) { rule.onAllNodesWithTag("download").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("download").performClick()
            rule.waitUntil(15 * 60_000) { rule.onAllNodesWithTag("mic-BOTTOM").fetchSemanticsNodes().isNotEmpty() }
        }
        assertTrue(ModelPack.installer(ctx).status(deep = true).ready)
    }
}
