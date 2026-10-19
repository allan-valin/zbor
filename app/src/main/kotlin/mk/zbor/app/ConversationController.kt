package mk.zbor.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mk.zbor.core.Lang
import mk.zbor.core.PipelineOrchestrator
import mk.zbor.core.Stage
import mk.zbor.core.TurnState
import mk.zbor.core.Utterance

/** The two halves of the screen. TOP is rotated 180° to face the person across the table. */
enum class Side {
    TOP, BOTTOM;

    val other: Side get() = if (this == TOP) BOTTOM else TOP
}

enum class Phase { IDLE, RECORDING, TRANSCRIBING, EDITING, TRANSLATING, REVIEWING, SPEAKING, FAILED }

data class SideUi(val lang: Lang, val text: String = "")

data class UiError(val stage: Stage, val message: String)

/** One finished turn, kept in the conversation history (and saved across app restarts). */
data class HistoryEntry(val from: Lang, val to: Lang, val source: String, val translation: String, val time: Long)

data class ConversationUi(
    val top: SideUi = SideUi(Lang.MK),
    val bottom: SideUi = SideUi(Lang.PT),
    val phase: Phase = Phase.IDLE,
    /** Whose turn it is: the side that speaks (or types) in the current turn. */
    val speaker: Side? = null,
    /** Microphone level 0..1 while recording. */
    val level: Float = 0f,
    val error: UiError? = null,
    /** The side the last translation was spoken to, which can replay it. */
    val replaySide: Side? = null,
    val outputSeam: Boolean = false,
    /** Face-to-face voice mode: the top half is mirrored for the person opposite. Off = both halves upright. */
    val faceToFace: Boolean = false,
    /** Every finished turn, oldest first. */
    val history: List<HistoryEntry> = emptyList(),
) {
    fun side(s: Side) = if (s == Side.TOP) top else bottom
    fun canReplay(s: Side) = replaySide == s && phase != Phase.SPEAKING
    val busy get() = phase == Phase.TRANSCRIBING || phase == Phase.TRANSLATING || phase == Phase.SPEAKING

    internal fun withSide(s: Side, f: (SideUi) -> SideUi) =
        if (s == Side.TOP) copy(top = f(top)) else copy(bottom = f(bottom))
}

interface Recorder {
    fun start(onLevel: (Float) -> Unit)
    fun stop(): Utterance
}

interface Player {
    suspend fun play(audio: Utterance)
    fun stop()
}

/**
 * Turns taps on the split screen into [PipelineOrchestrator] calls. All engine
 * work runs on [work] (one thread in the app: the orchestrator is not thread-safe);
 * the UI only ever reads [state].
 */
class ConversationController(
    private val orchestratorFor: (outputSeam: Boolean) -> PipelineOrchestrator,
    private val recorder: Recorder,
    private val player: Player,
    private val scope: CoroutineScope,
    private val work: CoroutineDispatcher,
    outputSeam: Boolean = false,
    faceToFace: Boolean = false,
    /** The phone owner's language, shown on the bottom half; Macedonian sits on top. */
    homeLang: Lang = Lang.PT,
    history: List<HistoryEntry> = emptyList(),
    /** Called with each new history entry (the app appends it to a file). */
    private val onHistory: (HistoryEntry) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(
        ConversationUi(bottom = SideUi(homeLang), outputSeam = outputSeam, faceToFace = faceToFace, history = history),
    )

    /** What the screen showed before the current turn started, for [cancel]. */
    private var beforeTurn: ConversationUi? = null
    val state: StateFlow<ConversationUi> = _state

    private var orchestrator = orchestratorFor(outputSeam)
    private var lastAudio: Utterance? = null

    fun tapMic(side: Side) {
        val s = _state.value
        when {
            s.phase == Phase.RECORDING && s.speaker == side -> stopRecording(side)
            s.phase == Phase.RECORDING || s.busy -> Unit // one turn at a time
            else -> startRecording(side)
        }
    }

    fun startTyping(side: Side) {
        val s = _state.value
        if (s.busy || s.phase == Phase.RECORDING) return
        if (s.phase == Phase.EDITING && s.speaker == side) return
        if (s.phase == Phase.REVIEWING && s.speaker == side.other) return // listener is correcting
        orchestrator.startTyped(langOf(side), langOf(side.other))
        beforeTurn = _state.value
        _state.update {
            it.copy(phase = Phase.EDITING, speaker = side, error = null).withSide(side) { t -> t.copy(text = "") }
        }
    }

    fun edit(side: Side, text: String) {
        val s = _state.value
        val editable = (s.phase == Phase.EDITING && s.speaker == side) ||
            (s.phase == Phase.REVIEWING && s.speaker == side.other)
        if (editable) _state.update { it.withSide(side) { t -> t.copy(text = text) } }
    }

    fun confirm(side: Side) {
        val s = _state.value
        val text = s.side(side).text
        if (text.isBlank()) return
        when {
            s.phase == Phase.EDITING && s.speaker == side -> {
                _state.update { it.copy(phase = Phase.TRANSLATING, error = null) }
                runStep(Stage.MT) { orchestrator.confirmInput(text.trim()) }
            }
            s.phase == Phase.REVIEWING && s.speaker == side.other -> {
                _state.update { it.copy(phase = Phase.SPEAKING) }
                runStep(Stage.TTS) { orchestrator.confirmOutput(text.trim()) }
            }
        }
    }

    /** Leaves editing (or a failed turn) and puts back what the screen showed before it. */
    fun cancel() {
        val s = _state.value
        if (s.phase != Phase.EDITING && s.phase != Phase.REVIEWING && s.phase != Phase.FAILED) return
        val before = beforeTurn ?: return
        _state.update { before.copy(phase = Phase.IDLE, speaker = null, error = null, history = it.history,
            outputSeam = it.outputSeam, faceToFace = it.faceToFace) }
    }

    fun clearHistory() {
        _state.update { it.copy(history = emptyList()) }
    }

    private fun remember(speaker: Side, translation: String) {
        val s = _state.value
        val entry = HistoryEntry(s.side(speaker).lang, s.side(speaker.other).lang, s.side(speaker).text, translation, clock())
        _state.update { it.copy(history = it.history + entry) }
        onHistory(entry)
    }

    fun replay(side: Side) {
        val audio = lastAudio ?: return
        if (!_state.value.canReplay(side) || _state.value.busy) return
        scope.launch { speakOut(audio) }
    }

    fun setLang(side: Side, lang: Lang) {
        if (_state.value.busy || _state.value.phase == Phase.RECORDING) return
        _state.update { it.withSide(side) { t -> t.copy(lang = lang) } }
    }

    fun setOutputSeam(on: Boolean) {
        if (_state.value.busy || _state.value.phase == Phase.RECORDING) return
        orchestrator = orchestratorFor(on)
        _state.update { it.copy(outputSeam = on, phase = Phase.IDLE, speaker = null) }
    }

    /** One-sided (non-mirrored) use: flip the translation direction, keeping what is on screen. */
    fun swapLangs() {
        if (_state.value.busy || _state.value.phase == Phase.RECORDING) return
        _state.update {
            it.copy(
                top = it.bottom, bottom = it.top, phase = Phase.IDLE, speaker = null,
                replaySide = it.replaySide?.other,
            )
        }
    }

    fun setFaceToFace(on: Boolean) {
        _state.update { it.copy(faceToFace = on) }
    }

    private fun startRecording(side: Side) {
        player.stop()
        orchestrator.startTurn(langOf(side), langOf(side.other))
        beforeTurn = _state.value
        _state.update {
            it.copy(phase = Phase.RECORDING, speaker = side, level = 0f, error = null)
                .withSide(side) { t -> t.copy(text = "") }
        }
        recorder.start { level -> _state.update { it.copy(level = level) } }
    }

    private fun stopRecording(side: Side) {
        val audio = recorder.stop()
        _state.update { it.copy(phase = Phase.TRANSCRIBING, level = 0f) }
        runStep(Stage.STT) { orchestrator.submitAudio(audio) }
    }

    /** Runs one orchestrator step off the main thread and folds its result into [state]. */
    private fun runStep(stage: Stage, step: () -> TurnState) {
        scope.launch {
            val result = try {
                withContext(work) { step() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Native/runtime failures must surface, never kill the conversation.
                TurnState.Failed(stage, e.message ?: e.javaClass.simpleName)
            }
            apply(result)
        }
    }

    private suspend fun apply(result: TurnState) {
        val speaker = _state.value.speaker ?: return
        val listener = speaker.other
        when (result) {
            is TurnState.AwaitingInputConfirm -> _state.update {
                it.copy(phase = Phase.EDITING).withSide(speaker) { t -> t.copy(text = result.sttText.trim()) }
            }
            is TurnState.AwaitingOutputConfirm -> _state.update {
                it.copy(phase = Phase.REVIEWING).withSide(listener) { t -> t.copy(text = result.mtText) }
            }
            is TurnState.Done -> {
                lastAudio = result.audio
                _state.update {
                    it.copy(replaySide = listener).withSide(listener) { t -> t.copy(text = result.mtText) }
                }
                remember(speaker, result.mtText)
                speakOut(result.audio)
            }
            is TurnState.Failed -> {
                _state.update { s ->
                    val shown = result.mtText?.let { mt -> s.withSide(listener) { t -> t.copy(text = mt) } } ?: s
                    shown.copy(phase = Phase.FAILED, error = UiError(result.stage, result.reason))
                }
                result.mtText?.let { remember(speaker, it) }
            }
            TurnState.Idle, is TurnState.Recording -> Unit
        }
    }

    private suspend fun speakOut(audio: Utterance) {
        _state.update { it.copy(phase = Phase.SPEAKING) }
        try {
            player.play(audio)
            _state.update { it.copy(phase = Phase.IDLE) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.update { it.copy(phase = Phase.FAILED, error = UiError(Stage.TTS, e.message ?: "playback failed")) }
        }
    }

    private fun langOf(side: Side) = _state.value.side(side).lang
}

/** The bottom half's default language from the phone's locale (English when it is none of ours). */
fun homeLangFor(locale: java.util.Locale): Lang = when (locale.language) {
    "pt" -> Lang.PT
    "de" -> Lang.DE
    else -> Lang.EN
}
