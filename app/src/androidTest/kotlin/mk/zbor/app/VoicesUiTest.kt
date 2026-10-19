package mk.zbor.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mk.zbor.BuildConfig
import mk.zbor.engines.ModelPack
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/** Settings → natural voices: download one, it becomes a switch; delete it, Download is back. */
@RunWith(AndroidJUnit4::class)
class VoicesUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun needs() {
        assumeTrue("needs a local test pack server", "10.0.2.2" in BuildConfig.PACK_URL)
        assumeTrue(ModelPack.installer(ctx).status().ready)
    }

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test fun downloadThenDeleteThePortugueseVoice() {
        ModelPack.installer(ctx).remove(setOf("pt-br-voice.onnx", "pt-br-voice.onnx.json"))
        rule.onNodeWithTag("settings").performClick()
        rule.onNodeWithTag("download-PT").performScrollTo().performClick()
        rule.waitUntil(120_000) { exists("natural-PT") }
        assertTrue(File(ModelPack.location(ctx).dir, "pt-br-voice.onnx").isFile)
        rule.onNodeWithTag("delete-PT").performScrollTo().performClick()
        rule.waitUntil(30_000) { exists("download-PT") }
        assertTrue(!File(ModelPack.location(ctx).dir, "pt-br-voice.onnx").exists())
    }
}
