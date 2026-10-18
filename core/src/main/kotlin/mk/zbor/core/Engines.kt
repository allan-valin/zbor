package mk.zbor.core

/** Which pipeline stage an engine failure came from. */
enum class Stage { STT, MT, TTS }

/** Speech -> text for one language. */
interface SttEngine { fun transcribe(audio: Utterance, lang: Lang): String }

/** Text -> text across a language pair. */
interface MtEngine { fun translate(text: String, from: Lang, to: Lang): String }

/** Text -> speech for one language. */
interface TtsEngine { fun synthesize(text: String, lang: Lang): Utterance }

/** Thrown by an engine; carries the stage so the orchestrator can report it. */
class EngineException(val stage: Stage, message: String) : Exception(message)
