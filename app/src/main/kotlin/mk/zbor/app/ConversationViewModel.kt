package mk.zbor.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import mk.zbor.audio.MicRecorder
import mk.zbor.audio.SpeakerPlayer
import mk.zbor.core.PipelineOrchestrator
import mk.zbor.core.ResidencyPolicy
import mk.zbor.core.Lang
import mk.zbor.engines.EspeakTtsEngine
import mk.zbor.engines.ModelPack
import mk.zbor.engines.ZborEngines
import java.util.concurrent.Executors

/** Owns the engines (one set per process) and the conversation controller. */
class ConversationViewModel(app: Application) : AndroidViewModel(app) {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "zbor-engines") }
    private val work = worker.asCoroutineDispatcher()
    private val player = SpeakerPlayer()

    val pack = ModelPack.location(app)

    private val _slowMode = MutableStateFlow<ResidencyPolicy?>(null)
    /** Set when memory pressure forced a colder residency policy (spec: "slower mode" notice). */
    val slowMode: StateFlow<ResidencyPolicy?> = _slowMode

    private val prefs = app.getSharedPreferences("zbor", 0)

    private val _speechRate = MutableStateFlow(prefs.getInt(SPEECH_RATE, EspeakTtsEngine.DEFAULT_RATE))
    /** espeak-ng words per minute (settings slider). */
    val speechRate: StateFlow<Int> = _speechRate

    private val engines = ZborEngines.create(
        pack, ZborEngines.policyFor(app),
        onPolicyChange = { _slowMode.value = it },
        speechRate = { _speechRate.value },
    )

    val controller = ConversationController(
        orchestratorFor = { seam -> PipelineOrchestrator(engines.stt, engines.mt, engines.tts, seam) },
        recorder = MicRecorder(),
        player = player,
        scope = viewModelScope,
        work = work,
        faceToFace = prefs.getBoolean(FACE_TO_FACE, false),
        homeLang = homeLangFor(java.util.Locale.getDefault()),
    )

    fun setSpeechRate(wpm: Int) {
        _speechRate.value = wpm
        prefs.edit().putInt(SPEECH_RATE, wpm).apply()
    }

    /** Speaks a fixed Macedonian sentence at the current rate, for tuning the slider. */
    fun previewVoice() {
        if (controller.state.value.busy) return
        viewModelScope.launch {
            val audio = runCatching {
                kotlinx.coroutines.withContext(work) { engines.tts.synthesize(PREVIEW, Lang.MK) }
            }.getOrNull() ?: return@launch
            player.play(audio)
        }
    }

    fun setFaceToFace(on: Boolean) {
        controller.setFaceToFace(on)
        prefs.edit().putBoolean(FACE_TO_FACE, on).apply()
    }

    init {
        viewModelScope.launch(work) { runCatching { engines.warmUp() } }
    }

    fun onTrimMemory() {
        viewModelScope.launch(work) { engines.models.onMemoryPressure() }
    }

    private companion object {
        const val FACE_TO_FACE = "faceToFace"
        const val SPEECH_RATE = "speechRate"
        const val PREVIEW = "Здраво, како си? Каде е автобуската станица?"
    }

    override fun onCleared() {
        player.stop()
        worker.execute { engines.close() }
        worker.shutdown()
    }
}
