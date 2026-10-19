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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import mk.zbor.core.Lang
import mk.zbor.engines.Voices
import mk.zbor.app.VoiceState
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
        // What is still missing, not the whole pack: an update that only adds a voice says 108 MB.
        val needed = (state as? SetupState.Needed)?.status?.let { st -> (st.missing + st.bad).sumOf { it.size } } ?: totalBytes
        Text(stringResource(R.string.setup_intro, mb(needed)))
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
    voices: Map<Lang, VoiceState>,
    natural: Map<Lang, Boolean>,
    voiceBytes: Map<Lang, Long>,
    onNatural: (Lang, Boolean) -> Unit,
    onDownload: (Lang) -> Unit,
    onDelete: (Lang) -> Unit,
    onPreview: (Lang) -> Unit,
    speechRate: Int,
    onSpeechRate: (Int) -> Unit,
    downloadedBytes: Long,
    packDir: File,
    onDismiss: () -> Unit,
) {
    var manage by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.review_translation))
                        Text(stringResource(R.string.review_translation_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = outputSeam, onCheckedChange = onOutputSeam, modifier = Modifier.testTag("output-seam"))
                }

                Text(stringResource(R.string.natural_voices), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.natural_voices_hint), style = MaterialTheme.typography.bodySmall)
                // Only languages that have a natural voice published (in the manifest) get a row.
                for (lang in Lang.entries.filter { (voiceBytes[it] ?: 0) > 0 }) {
                    val v = voices[lang] ?: VoiceState()
                    VoiceRow(lang, v, natural[lang] == true, voiceBytes[lang] ?: 0, onNatural, onDownload, onDelete, onPreview)
                }

                Text(stringResource(R.string.speech_speed), style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = speechRate.toFloat(),
                    onValueChange = { onSpeechRate(it.roundToInt()) },
                    valueRange = EspeakTtsEngine.MIN_RATE.toFloat()..EspeakTtsEngine.MAX_RATE.toFloat(),
                    steps = (EspeakTtsEngine.MAX_RATE - EspeakTtsEngine.MIN_RATE) / 10 - 1,
                    modifier = Modifier.testTag("speech-rate"),
                )
                Text(stringResource(R.string.speech_speed_value, speechRate), style = MaterialTheme.typography.bodySmall)

                Text(stringResource(R.string.downloads_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.downloads_total, mb(downloadedBytes)), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { manage = true }, modifier = Modifier.testTag("manage-voices")) {
                    Text(stringResource(R.string.manage_voices))
                }
                Text(stringResource(R.string.models_at, packDir.path), style = MaterialTheme.typography.bodySmall)
            }
        },
    )
    if (manage) ManageVoicesDialog(voices, voiceBytes, onDelete) { manage = false }
}

/** Downloaded natural voices, each deletable on its own (the base models are never offered). */
@Composable
private fun ManageVoicesDialog(voices: Map<Lang, VoiceState>, voiceBytes: Map<Lang, Long>, onDelete: (Lang) -> Unit, onDismiss: () -> Unit) {
    val installed = Lang.entries.filter { voices[it]?.installed == true }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_voices_title)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (installed.isEmpty()) Text(stringResource(R.string.no_voices))
                for (lang in installed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${lang.flag} ${lang.nativeName}")
                            Text("${Voices.of(lang).credit} · ${mb(voiceBytes[lang] ?: 0)}", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { onDelete(lang) }, modifier = Modifier.testTag("delete-$lang")) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.padding(end = 6.dp))
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
        },
    )
}

/** One language: preview ▶, then Download (size) or on/off. Deleting is in Manage voices. */
@Composable
private fun VoiceRow(
    lang: Lang, v: VoiceState, on: Boolean, bytes: Long,
    onNatural: (Lang, Boolean) -> Unit, onDownload: (Lang) -> Unit, onDelete: (Lang) -> Unit, onPreview: (Lang) -> Unit,
) {
    val spec = Voices.of(lang)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPreview(lang) }, modifier = Modifier.testTag("preview-$lang")) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.play_example))
        }
        Column(Modifier.weight(1f)) {
            Text("${lang.flag} ${lang.nativeName}")
            Text(
                when {
                    v.busy -> stringResource(R.string.mb_of_mb, mb(v.done), mb(v.total))
                    v.error != null -> stringResource(R.string.download_interrupted, v.error)
                    v.installed -> spec.credit + " · " + mb(bytes)
                    else -> stringResource(R.string.robot_voice_now)
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (v.installed) {
            Switch(checked = on, onCheckedChange = { onNatural(lang, it) }, modifier = Modifier.testTag("natural-$lang"))
        } else {
            OutlinedButton(onClick = { onDownload(lang) }, enabled = !v.busy, modifier = Modifier.testTag("download-$lang")) {
                Text(mb(bytes))
            }
        }
    }
}

private val Work.label: Int
    get() = when (this) {
        Work.CHECKING -> R.string.checking
        Work.CHECKING_DEEP -> R.string.checking_deep
        Work.DOWNLOADING -> R.string.downloading
        Work.IMPORTING -> R.string.importing
    }
