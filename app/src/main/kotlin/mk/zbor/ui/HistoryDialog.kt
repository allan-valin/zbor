package mk.zbor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mk.zbor.R
import mk.zbor.app.HistoryEntry
import mk.zbor.core.Lang
import java.text.DateFormat
import java.util.Date

/** The conversation so far: each turn's original and translation, selectable, ▶ speaks it again. */
@Composable
fun HistoryDialog(history: List<HistoryEntry>, onSpeak: (String, Lang) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(history.size) { if (history.isNotEmpty()) list.scrollToItem(history.lastIndex) }
    ZborAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            TextButton(onClick = onClear, enabled = history.isNotEmpty(), modifier = Modifier.testTag("clear-history")) {
                Text(stringResource(R.string.clear_history))
            }
        },
        text = {
            if (history.isEmpty()) {
                Text(stringResource(R.string.history_empty))
            } else {
                SelectionContainer {
                    LazyColumn(Modifier.heightIn(max = 520.dp).testTag("history-list"), state = list,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(history) { e ->
                            Column(Modifier.fillMaxWidth()) {
                                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.time)),
                                    style = MaterialTheme.typography.labelSmall)
                                Text("${e.from.flag} ${e.source}", style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${e.to.flag} ${e.translation}", style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f))
                                    IconButton(onClick = { onSpeak(e.translation, e.to) }) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "▶")
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
    )
}
