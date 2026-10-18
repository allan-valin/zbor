package mk.zbor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import mk.zbor.engines.EspeakTtsEngine
import kotlin.math.roundToInt
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import mk.zbor.R
import mk.zbor.app.SetupState
import mk.zbor.app.Work
import mk.zbor.app.SetupViewModel.Companion.mb
import java.io.File

interface SetupActions {
    fun download(confirmedMetered: Boolean)
    fun pause()
    fun importFolder()
    fun verify()
}

/** First run: get the ~1 GB offline model pack onto the phone. */
@Composable
fun SetupScreen(state: SetupState, totalBytes: Long, packDir: File, actions: SetupActions) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)
            .verticalScroll(rememberScrollState()).testTag("setup"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.setup_intro, mb(totalBytes)))
        when (state) {
            is SetupState.Working -> {
                if (state.done > 0) KeepScreenOn()
                Text(stringResource(state.what.label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("working"))
                if (state.total > 0) {
                    LinearProgressIndicator(
                        progress = { (state.done.toFloat() / state.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.mb_of_mb, mb(state.done), mb(state.total)))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (state.what == Work.DOWNLOADING) {
                    OutlinedButton(onClick = actions::pause, modifier = Modifier.testTag("pause")) { Text(stringResource(R.string.pause)) }
                }
            }
            is SetupState.Needed -> {
                for (f in state.status.ok) FileRow(Icons.Default.CheckCircle, f.name, stringResource(R.string.file_ok))
                for (f in state.status.missing) FileRow(Icons.Default.RadioButtonUnchecked, f.name, stringResource(R.string.file_missing))
                for (f in state.status.bad) FileRow(Icons.Default.ErrorOutline, f.name, stringResource(R.string.file_bad))
                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("setup-message"))
                }
                Button(
                    onClick = { actions.download(confirmedMetered = state.confirmMetered) },
                    modifier = Modifier.fillMaxWidth().testTag("download"),
                ) {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(if (state.confirmMetered) R.string.download_metered else R.string.download))
                }
                OutlinedButton(onClick = actions::importFolder, modifier = Modifier.fillMaxWidth().testTag("import")) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.import_folder))
                }
                TextButton(onClick = actions::verify, modifier = Modifier.testTag("verify")) { Text(stringResource(R.string.verify_again)) }
                Text(stringResource(R.string.offline_hint), style = MaterialTheme.typography.bodySmall)
            }
            SetupState.Ready -> Text(stringResource(R.string.ready))
        }
    }
}

@Composable
private fun FileRow(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, what: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.padding(end = 8.dp))
        Text("$name — $what", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
fun SettingsDialog(
    outputSeam: Boolean,
    onOutputSeam: (Boolean) -> Unit,
    speechRate: Int,
    onSpeechRate: (Int) -> Unit,
    onPreviewVoice: () -> Unit,
    packDir: File,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.review_translation))
                        Text(stringResource(R.string.review_translation_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = outputSeam, onCheckedChange = onOutputSeam, modifier = Modifier.testTag("output-seam"))
                }
                Text(stringResource(R.string.speech_speed))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = speechRate.toFloat(),
                        onValueChange = { onSpeechRate(it.roundToInt()) },
                        valueRange = EspeakTtsEngine.MIN_RATE.toFloat()..EspeakTtsEngine.MAX_RATE.toFloat(),
                        steps = (EspeakTtsEngine.MAX_RATE - EspeakTtsEngine.MIN_RATE) / 10 - 1,
                        modifier = Modifier.weight(1f).testTag("speech-rate"),
                    )
                    FilledTonalIconButton(onClick = onPreviewVoice, modifier = Modifier.testTag("preview-voice")) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.play_example))
                    }
                }
                Text(stringResource(R.string.speech_speed_value, speechRate), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.models_at, packDir.path), style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

private val Work.label: Int
    get() = when (this) {
        Work.CHECKING -> R.string.checking
        Work.CHECKING_DEEP -> R.string.checking_deep
        Work.DOWNLOADING -> R.string.downloading
        Work.IMPORTING -> R.string.importing
    }
