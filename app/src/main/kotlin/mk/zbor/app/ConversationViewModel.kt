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

    private val _neuralVoice = MutableStateFlow(prefs.getBoolean(NEURAL_VOICE, true))
    /** v2: natural (neural) Macedonian voice instead of eSpeak; on by default. */
    val neuralVoice: StateFlow<Boolean> = _neuralVoice

    private val _naturalPt = MutableStateFlow(prefs.getBoolean(NATURAL_PT, true))
    val naturalPt: StateFlow<Boolean> = _naturalPt

    /** Optional Brazilian Portuguese voice: null = not installed, else installed. */
    private val installer = ModelPack.installer(app)
    private val _ptVoice = MutableStateFlow(VoiceState(installed = pack.ptVoice.isFile))
    val ptVoice: StateFlow<VoiceState> = _ptVoice
    val ptVoiceBytes: Long = installer.manifest.files.filter { it.name in ModelPack.PT_VOICE_FILES }.sumOf { it.size }

    private val engines = ZborEngines.create(
        pack, ZborEngines.policyFor(app),
        onPolicyChange = { _slowMode.value = it },
        speechRate = { _speechRate.value },
        naturalVoice = { lang ->
            when (lang) {
                mk.zbor.core.Lang.MK -> _neuralVoice.value
                mk.zbor.core.Lang.PT -> _naturalPt.value
                else -> true
            }
        },
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

    fun setNaturalPt(on: Boolean) {
        _naturalPt.value = on
        prefs.edit().putBoolean(NATURAL_PT, on).apply()
    }

    /** Downloads the optional pt-BR voice, then reloads TTS so it is used from the next turn. */
    fun downloadPtVoice() {
        if (_ptVoice.value.busy) return
        _ptVoice.value = VoiceState(busy = true)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val error = try {
                installer.download(mk.zbor.BuildConfig.PACK_URL, only = ModelPack.PT_VOICE_FILES) { done, total ->
                    _ptVoice.value = VoiceState(busy = true, done = done, total = total)
                }
                null
            } catch (e: java.io.IOException) {
                e.message ?: "download failed"
            }
            val ok = ModelPack.PT_VOICE_FILES.all(installer::isInstalled)
            if (ok) kotlinx.coroutines.withContext(work) { engines.models.evict(mk.zbor.core.ModelKind.TTS) }
            _ptVoice.value = VoiceState(installed = ok, error = if (ok) null else error)
        }
    }

    fun setNeuralVoice(on: Boolean) {
        _neuralVoice.value = on
        prefs.edit().putBoolean(NEURAL_VOICE, on).apply()
    }

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
        const val NEURAL_VOICE = "neuralVoice"
        const val NATURAL_PT = "naturalPt"
        const val PREVIEW = "Здраво, како си? Каде е автобуската станица?"
    }

    override fun onCleared() {
        player.stop()
        worker.execute { engines.close() }
        worker.shutdown()
    }
}

/** An optional voice in settings: installed, or downloading (bytes), or the last error. */
data class VoiceState(
    val installed: Boolean = false,
    val busy: Boolean = false,
    val done: Long = 0,
    val total: Long = 0,
    val error: String? = null,
)
