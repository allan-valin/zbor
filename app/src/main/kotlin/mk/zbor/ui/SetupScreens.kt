package mk.zbor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.io.File

/** Shown instead of the conversation when the model pack is incomplete (pt-BR: Allan sets the app up). */
@Composable
fun MissingPackScreen(dir: File, missing: List<File>, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).testTag("missing-pack"),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
        Text("Pacote de modelos incompleto", style = MaterialTheme.typography.headlineSmall)
        Text("O Zbor funciona sem internet, mas precisa dos modelos neste aparelho. Copie a pasta do pacote para:")
        Text(dir.path, style = MaterialTheme.typography.bodySmall)
        Text("Faltando:", style = MaterialTheme.typography.titleSmall)
        for (f in missing) Text("• ${f.relativeTo(dir)}", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onRetry, modifier = Modifier.testTag("retry")) { Text("Verificar de novo") }
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
