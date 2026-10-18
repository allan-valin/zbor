package mk.zbor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import mk.zbor.R
import mk.zbor.app.ConversationUi
import mk.zbor.app.Phase
import mk.zbor.app.Side
import mk.zbor.core.Lang

/** Everything a half can ask the controller to do. */
interface ConversationActions {
    fun tapMic(side: Side)
    fun startTyping(side: Side)
    fun edit(side: Side, text: String)
    fun confirm(side: Side)
    fun replay(side: Side)
    fun setLang(side: Side, lang: Lang)
    fun setFaceToFace(on: Boolean)
    fun swapLangs()
    fun openSettings()
}

private val Recording = Color(0xFFE53935)
private val Confirm = Color(0xFF43A047)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConversationScreen(
    ui: ConversationUi,
    micAllowed: Boolean,
    slowMode: Boolean,
    actions: ConversationActions,
) {
    // Edge-to-edge (targetSdk 36): keep clear of the system bars and of the keyboard,
    // and while one side types give it most of what the keyboard leaves.
    val typing = WindowInsets.isImeVisible
    val typingSide = if (typing) ui.speaker?.let { s ->
        if (ui.phase == Phase.REVIEWING) s.other else s
    } else null
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Face-to-face mode mirrors the top half for the person across the table.
        val topCompact = typingSide == Side.BOTTOM
        val bottomCompact = typingSide == Side.TOP
        // Without face-to-face only the bottom half takes input; the top shows the translation.
        SideHalf(Side.TOP, ui, micAllowed, actions, topCompact, controls = ui.faceToFace,
            (if (topCompact) Modifier else Modifier.weight(1f)).rotate(if (ui.faceToFace) 180f else 0f))
        MiddleBar(ui, slowMode, actions)
        SideHalf(Side.BOTTOM, ui, micAllowed, actions, bottomCompact, controls = true,
            if (bottomCompact) Modifier else Modifier.weight(1f))
    }
}

@Composable
private fun MiddleBar(ui: ConversationUi, slowMode: Boolean, actions: ConversationActions) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column {
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("busy"))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = ui.faceToFace,
                    onClick = { actions.setFaceToFace(!ui.faceToFace) },
                    label = { Text(stringResource(R.string.face_to_face)) },
                    leadingIcon = { Icon(Icons.Default.RecordVoiceOver, null) },
                    modifier = Modifier.testTag("face-to-face"),
                )
                if (!ui.faceToFace) {
                    FilledIconButton(
                        onClick = actions::swapLangs,
                        enabled = !ui.busy && ui.phase != Phase.RECORDING,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.padding(start = 12.dp).size(60.dp).testTag("swap"),
                    ) { Icon(Icons.Default.SwapVert, contentDescription = "⇅", modifier = Modifier.size(36.dp)) }
                }
                Text(
                    if (slowMode) stringResource(R.string.slow_mode) else "",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                FilledTonalIconButton(onClick = actions::openSettings, modifier = Modifier.testTag("settings")) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        }
    }
}

@Composable
private fun SideHalf(
    side: Side,
    ui: ConversationUi,
    micAllowed: Boolean,
    actions: ConversationActions,
    /** While the other side types: only language and status, to leave room above the keyboard. */
    compact: Boolean,
    /** Mic, keyboard and ✓; off for the output-only top half outside face-to-face mode. */
    controls: Boolean,
    modifier: Modifier,
) {
    val me = ui.side(side)
    val words = me.lang.text
    val isSpeaker = ui.speaker == side
    val editable = (ui.phase == Phase.EDITING && isSpeaker) ||
        (ui.phase == Phase.REVIEWING && ui.speaker == side.other)
    val recordingHere = ui.phase == Phase.RECORDING && isSpeaker
    val idleish = !ui.busy && ui.phase != Phase.RECORDING
    var picking by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(16.dp).testTag("half-$side")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { picking = !picking },
                enabled = idleish,
                shape = RoundedCornerShape(50),
                modifier = Modifier.testTag("lang-$side"),
            ) { Text("${me.lang.flag}  ${me.lang.nativeName}", fontSize = 16.sp) }
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(
                onClick = { actions.replay(side) },
                enabled = ui.canReplay(side),
                modifier = Modifier.size(52.dp).testTag("replay-$side"),
            ) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "▶", modifier = Modifier.size(30.dp)) }
        }

        if (picking) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (lang in Lang.entries) {
                    OutlinedButton(
                        onClick = { actions.setLang(side, lang); picking = false },
                        modifier = Modifier.testTag("pick-$side-$lang"),
                    ) { Text(lang.flag, fontSize = 28.sp) }
                }
            }
        }

        if (controls) StatusLine(side, ui, micAllowed, words)
        if (compact) return@Column

        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
            if (editable) {
                val focus = remember { FocusRequester() }
                OutlinedTextField(
                    value = me.text,
                    onValueChange = { actions.edit(side, it) },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    placeholder = { Text(words.typeHere) },
                    // Ask the keyboard for this half's language (Gboard switches if that layout is installed).
                    keyboardOptions = KeyboardOptions(hintLocales = LocaleList(me.lang.locale)),
                    modifier = Modifier.fillMaxSize().focusRequester(focus).testTag("edit-$side"),
                )
                LaunchedEffect(Unit) { if (me.text.isEmpty()) focus.requestFocus() }
            } else {
                Text(
                    me.text,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = if (ui.replaySide == side) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("text-$side"),
                )
            }
        }

        if (controls) Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = { actions.startTyping(side) },
                enabled = idleish && !editable,
                modifier = Modifier.size(56.dp).testTag("type-$side"),
            ) { Icon(Icons.Default.Keyboard, contentDescription = words.typeHere) }

            MicButton(
                recording = recordingHere,
                level = if (recordingHere) ui.level else 0f,
                enabled = recordingHere || idleish,
                onClick = { actions.tapMic(side) },
                modifier = Modifier.testTag("mic-$side"),
            )

            FilledIconButton(
                onClick = { actions.confirm(side) },
                enabled = editable && me.text.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Confirm),
                modifier = Modifier.size(56.dp).testTag("confirm-$side"),
            ) { Icon(Icons.Default.Check, contentDescription = "✓", tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
    }
}

@Composable
private fun StatusLine(side: Side, ui: ConversationUi, micAllowed: Boolean, words: SideText) {
    val isSpeaker = ui.speaker == side
    val text = when {
        !micAllowed && ui.phase == Phase.IDLE -> words.noMic
        ui.phase == Phase.FAILED -> "${words.failed}: ${ui.error?.message.orEmpty()}"
        ui.phase == Phase.RECORDING -> if (isSpeaker) words.recording else words.otherTurn
        ui.phase == Phase.TRANSCRIBING -> if (isSpeaker) words.transcribing else words.otherTurn
        ui.phase == Phase.EDITING -> if (isSpeaker) words.edit else words.otherTurn
        ui.phase == Phase.TRANSLATING -> words.translating
        ui.phase == Phase.REVIEWING -> if (isSpeaker) words.translating else words.review
        ui.phase == Phase.SPEAKING -> words.speaking
        else -> words.idle
    }
    val color = when (ui.phase) {
        Phase.FAILED -> MaterialTheme.colorScheme.error
        Phase.RECORDING -> if (isSpeaker) Recording else MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ui.phase == Phase.FAILED) Icon(Icons.Default.ErrorOutline, null, tint = color, modifier = Modifier.padding(end = 6.dp))
        if (!micAllowed && ui.phase == Phase.IDLE) Icon(Icons.Default.MicOff, null, tint = color, modifier = Modifier.padding(end = 6.dp))
        Text(text, color = color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("status-$side"))
    }
}

@Composable
private fun MicButton(recording: Boolean, level: Float, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val pulse by animateFloatAsState(if (recording) 1f + level * 0.35f else 1f, label = "mic-level")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
        if (recording) {
            Box(
                Modifier.size(88.dp).scale(pulse)
                    .border(4.dp, Recording.copy(alpha = 0.6f), CircleShape)
            )
        }
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (recording) Recording else MaterialTheme.colorScheme.primary,
            ),
            modifier = modifier.size(84.dp),
        ) {
            Icon(
                if (recording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = "mic",
                modifier = Modifier.size(44.dp),
            )
        }
    }
}
