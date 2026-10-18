package mk.zbor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mk.zbor.app.SetupState
import mk.zbor.app.SetupViewModel.Companion.mb
import java.io.File

interface SetupActions {
    fun download(confirmedMetered: Boolean)
    fun pause()
    fun importFolder()
    fun verify()
}

/** First run (pt-BR: Allan sets the app up): get the ~1 GB offline model pack onto the phone. */
@Composable
fun SetupScreen(state: SetupState, totalBytes: Long, packDir: File, actions: SetupActions) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()).testTag("setup"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Preparar o Zbor", style = MaterialTheme.typography.headlineMedium)
        Text(
            "O Zbor traduz sem internet, mas precisa baixar uma vez os modelos de fala e tradução " +
                "(${mb(totalBytes)}). Use Wi-Fi. Se a conexão cair, o download continua de onde parou.",
        )
        when (state) {
            is SetupState.Working -> {
                if (state.done > 0) KeepScreenOn()
                Text(state.what, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("working"))
                if (state.total > 0) {
                    LinearProgressIndicator(
                        progress = { (state.done.toFloat() / state.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${mb(state.done)} de ${mb(state.total)}")
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (state.what.startsWith("Baixando")) {
                    OutlinedButton(onClick = actions::pause, modifier = Modifier.testTag("pause")) { Text("Pausar") }
                }
            }
            is SetupState.Needed -> {
                for (f in state.status.ok) FileRow(Icons.Default.CheckCircle, f.name, "ok")
                for (f in state.status.missing) FileRow(Icons.Default.RadioButtonUnchecked, f.name, "falta")
                for (f in state.status.bad) FileRow(Icons.Default.ErrorOutline, f.name, "corrompido, será baixado de novo")
                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("setup-message"))
                }
                Button(
                    onClick = { actions.download(confirmedMetered = state.confirmMetered) },
                    modifier = Modifier.fillMaxWidth().testTag("download"),
                ) {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.padding(end = 8.dp))
                    Text(if (state.confirmMetered) "Baixar mesmo assim (rede móvel)" else "Baixar")
                }
                OutlinedButton(onClick = actions::importFolder, modifier = Modifier.fillMaxWidth().testTag("import")) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.padding(end = 8.dp))
                    Text("Importar de uma pasta…")
                }
                TextButton(onClick = actions::verify, modifier = Modifier.testTag("verify")) { Text("Conferir os arquivos de novo") }
                Text(
                    "Sem Wi-Fi? Copie os arquivos do pacote pelo cabo USB para:\n${packDir.path}\n" +
                        "ou para qualquer pasta e use “Importar”.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SetupState.Ready -> Text("Pronto.")
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
fun SettingsDialog(outputSeam: Boolean, onOutputSeam: (Boolean) -> Unit, packDir: File, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        title = { Text("Configurações") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Revisar a tradução antes de falar")
                        Text(
                            "Mostra a tradução editável do outro lado; só fala depois do ✓.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = outputSeam, onCheckedChange = onOutputSeam, modifier = Modifier.testTag("output-seam"))
                }
                Text("Modelos: ${packDir.path}", style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}
