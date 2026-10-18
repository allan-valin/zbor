package mk.zbor.app

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import mk.zbor.core.Lang
import mk.zbor.engines.ModelPack
import mk.zbor.ui.ConversationActions
import mk.zbor.ui.ConversationScreen
import mk.zbor.ui.MissingPackScreen
import mk.zbor.ui.SettingsDialog
import mk.zbor.ui.ZborTheme

class MainActivity : ComponentActivity() {
    private var micAllowed by mutableStateOf(false)
    private var pendingMic: Side? = null
    private var packMissing by mutableStateOf(emptyList<java.io.File>())
    private var settingsOpen by mutableStateOf(false)

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micAllowed = granted
        val side = pendingMic
        pendingMic = null
        if (granted && side != null) vm.controller.tapMic(side)
    }

    private val vm: ConversationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pack = ModelPack.location(this)
        packMissing = pack.missing()
        micAllowed = hasMic()

        val actions = object : ConversationActions {
            override fun tapMic(side: Side) {
                if (hasMic()) vm.controller.tapMic(side)
                else { pendingMic = side; askMic.launch(Manifest.permission.RECORD_AUDIO) }
            }
            override fun startTyping(side: Side) = vm.controller.startTyping(side)
            override fun edit(side: Side, text: String) = vm.controller.edit(side, text)
            override fun confirm(side: Side) = vm.controller.confirm(side)
            override fun replay(side: Side) = vm.controller.replay(side)
            override fun setLang(side: Side, lang: Lang) = vm.controller.setLang(side, lang)
            override fun openSettings() { settingsOpen = true }
        }

        setContent {
            ZborTheme {
                if (packMissing.isNotEmpty()) {
                    MissingPackScreen(pack.dir, packMissing) { packMissing = pack.missing() }
                } else {
                    val ui by vm.controller.state.collectAsState()
                    val slow by vm.slowMode.collectAsState()
                    ConversationScreen(ui, micAllowed, slow != null, actions)
                    if (settingsOpen) {
                        SettingsDialog(ui.outputSeam, vm.controller::setOutputSeam, pack.dir) { settingsOpen = false }
                    }
                }
            }
        }
    }

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @Deprecated("Deprecated in Java")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) vm.onTrimMemory()
    }
}
