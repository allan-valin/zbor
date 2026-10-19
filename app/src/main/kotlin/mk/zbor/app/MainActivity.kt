package mk.zbor.app

import android.Manifest
import android.content.ComponentCallbacks2
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import mk.zbor.ui.SetupActions
import mk.zbor.ui.SetupScreen
import mk.zbor.ui.HistoryDialog
import mk.zbor.ui.SettingsDialog
import mk.zbor.ui.ZborTheme

class MainActivity : ComponentActivity() {
    private var micAllowed by mutableStateOf(false)
    private var pendingMic: Side? = null
    private var settingsOpen by mutableStateOf(false)
    private var historyOpen by mutableStateOf(false)

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micAllowed = granted
        val side = pendingMic
        pendingMic = null
        if (granted && side != null) vm.controller.tapMic(side)
    }

    private val vm: ConversationViewModel by viewModels()
    private val setup: SetupViewModel by viewModels()

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) setup.importFrom(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val pack = ModelPack.location(this)
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
            override fun setFaceToFace(on: Boolean) = vm.setFaceToFace(on)
            override fun swapLangs() = vm.controller.swapLangs()
            override fun cancel() = vm.controller.cancel()
            override fun openHistory() { historyOpen = true }
            override fun openSettings() { settingsOpen = true }
        }
        val setupActions = object : SetupActions {
            override fun download(confirmedMetered: Boolean) = setup.download(confirmedMetered)
            override fun pause() = setup.pause()
            override fun importFolder() = pickFolder.launch(null)
            override fun verify() = setup.check(deep = true)
        }

        setContent {
            ZborTheme {
                val setupState by setup.state.collectAsState()
                if (setupState != SetupState.Ready) {
                    SetupScreen(setupState, setup.totalBytes, pack.dir, setupActions)
                } else {
                    val ui by vm.controller.state.collectAsState()
                    val slow by vm.slowMode.collectAsState()
                    ConversationScreen(ui, micAllowed, slow != null, actions)
                    if (historyOpen) {
                        HistoryDialog(ui.history, vm::speak, vm::clearHistory) { historyOpen = false }
                    }
                    if (settingsOpen) {
                        val rate by vm.speechRate.collectAsState()
                        val voices by vm.voices.collectAsState()
                        val natural by vm.natural.collectAsState()
                        val stored by vm.downloadedBytes.collectAsState()
                        SettingsDialog(
                            ui.outputSeam, vm.controller::setOutputSeam,
                            voices, natural, vm.voiceBytes, vm::setNatural, vm::downloadVoice, vm::deleteVoice, vm::previewVoice,
                            rate, vm::setSpeechRate,
                            stored, { vm.deleteAll { settingsOpen = false; setup.check() } },
                            pack.dir,
                        ) { settingsOpen = false }
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
        // Only real pressure while we are in front. UI_HIDDEN/BACKGROUND (e.g. the folder
        // picker is open) are not pressure: stepping colder there slowed a 12 GB phone.
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            vm.onTrimMemory()
        }
    }
}
