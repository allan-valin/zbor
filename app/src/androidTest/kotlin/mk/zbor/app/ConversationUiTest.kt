package mk.zbor.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mk.zbor.engines.ModelPack
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/** The real app with the real pack: a typed pt→mk turn through the split screen. */
@RunWith(AndroidJUnit4::class)
class ConversationUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun needsPack() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(ModelPack.installer(ctx).status().ready)
    }

    private fun text(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

    @Test fun typedTurnShowsMacedonianOnTheTopHalf() {
        rule.onNodeWithTag("confirm-BOTTOM").assertIsNotEnabled()
        rule.onNodeWithTag("type-BOTTOM").performClick()
        rule.onNodeWithTag("edit-BOTTOM").performTextInput("Onde fica a estação de ônibus?")
        rule.onNodeWithTag("confirm-BOTTOM").assertIsEnabled().performClick()

        rule.waitUntil(120_000) { text("text-TOP").any { it in 'А'..'я' } }
        val mk = text("text-TOP")
        android.util.Log.i("ZborTest", "ui turn pt->mk: $mk")
        assertTrue("автобуск" in mk.lowercase(), mk)
        rule.waitUntil(30_000) { runCatching { rule.onNodeWithTag("replay-TOP").assertIsEnabled() }.isSuccess }
        screenshot("ui-turn.png")
    }

    /** Evidence for the docs: lands in Android/data/mk.zbor/files/. */
    private fun screenshot(name: String) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val bmp = inst.uiAutomation.takeScreenshot() ?: return
        java.io.File(inst.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun languagePickerChangesTheBottomLanguage() {
        rule.onNodeWithTag("lang-BOTTOM").performClick()
        rule.onNodeWithTag("pick-BOTTOM-DE").performClick()
        rule.waitForIdle()
        assertTrue("Deutsch" in text("lang-BOTTOM") || "Mikrofon" in text("status-BOTTOM"), text("status-BOTTOM"))
    }
}
