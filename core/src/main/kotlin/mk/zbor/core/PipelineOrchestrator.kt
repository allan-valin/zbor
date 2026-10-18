package mk.zbor.core

/**
 * Drives one conversation turn. The edit seam is enforced structurally:
 * [submitAudio] can only reach [TurnState.AwaitingInputConfirm]; translation
 * happens exclusively in [confirmInput].
 */
class PipelineOrchestrator(
    private val stt: SttEngine,
    private val mt: MtEngine,
    private val tts: TtsEngine,
    private val outputSeam: Boolean = false,
) {
    var state: TurnState = TurnState.Idle
        private set

    fun startTurn(from: Lang, to: Lang): TurnState {
        state = TurnState.Recording(from, to)
        return state
    }

    /** Keyboard entry: skip recording and STT, straight to the (empty) input seam. */
    fun startTyped(from: Lang, to: Lang): TurnState {
        state = TurnState.AwaitingInputConfirm("", from, to)
        return state
    }

    fun submitAudio(audio: Utterance): TurnState {
        val rec = state as? TurnState.Recording
            ?: error("submitAudio called in state $state; expected Recording")
        state = try {
            val text = stt.transcribe(audio, rec.from)
            TurnState.AwaitingInputConfirm(text, rec.from, rec.to)
        } catch (e: EngineException) {
            TurnState.Failed(e.stage, e.message ?: "STT failed")
        }
        return state
    }

    fun confirmInput(editedText: String): TurnState {
        val seam = state as? TurnState.AwaitingInputConfirm
            ?: error("confirmInput called in state $state; expected AwaitingInputConfirm")
        val mtText = try {
            mt.translate(editedText, seam.from, seam.to)
        } catch (e: EngineException) {
            state = TurnState.Failed(e.stage, e.message ?: "MT failed")
            return state
        }
        state = if (outputSeam) {
            TurnState.AwaitingOutputConfirm(editedText, mtText, seam.to)
        } else {
            speak(editedText, mtText, seam.to)
        }
        return state
    }

    fun confirmOutput(editedText: String): TurnState {
        val seam = state as? TurnState.AwaitingOutputConfirm
            ?: error("confirmOutput called in state $state; expected AwaitingOutputConfirm")
        state = speak(seam.sttText, editedText, seam.to)
        return state
    }

    private fun speak(sttText: String, mtText: String, to: Lang): TurnState = try {
        TurnState.Done(sttText, mtText, tts.synthesize(mtText, to))
    } catch (e: EngineException) {
        TurnState.Failed(e.stage, e.message ?: "TTS failed", mtText)
    }
}
