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
        state = try {
            val mtText = mt.translate(editedText, seam.from, seam.to)
            if (outputSeam) {
                TurnState.AwaitingOutputConfirm(editedText, mtText, seam.to)
            } else {
                TurnState.Done(editedText, mtText, tts.synthesize(mtText, seam.to))
            }
        } catch (e: EngineException) {
            TurnState.Failed(e.stage, e.message ?: "${e.stage} failed")
        }
        return state
    }

    fun confirmOutput(editedText: String): TurnState {
        val seam = state as? TurnState.AwaitingOutputConfirm
            ?: error("confirmOutput called in state $state; expected AwaitingOutputConfirm")
        state = try {
            TurnState.Done(seam.sttText, editedText, tts.synthesize(editedText, seam.to))
        } catch (e: EngineException) {
            TurnState.Failed(e.stage, e.message ?: "TTS failed")
        }
        return state
    }
}
