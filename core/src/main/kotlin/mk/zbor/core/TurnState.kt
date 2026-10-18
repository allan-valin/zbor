package mk.zbor.core

/** Every observable state of a single conversation turn. */
sealed interface TurnState {
    data object Idle : TurnState
    data class Recording(val from: Lang, val to: Lang) : TurnState
    data class AwaitingInputConfirm(val sttText: String, val from: Lang, val to: Lang) : TurnState
    data class AwaitingOutputConfirm(val mtText: String, val to: Lang) : TurnState
    data class Done(val sttText: String, val mtText: String, val audio: Utterance) : TurnState
    data class Failed(val stage: Stage, val reason: String) : TurnState
}
