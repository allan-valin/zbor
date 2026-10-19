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
import mk.zbor.engines.Voices
import mk.zbor.engines.VoiceSpec
import mk.zbor.engines.ZborEngines
import mk.zbor.BuildConfig
import mk.zbor.core.ModelKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
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
    /** App-private (deleted on uninstall), one JSON object per finished turn. */
    private val historyFile = java.io.File(app.filesDir, "history.jsonl")

    private val _speechRate = MutableStateFlow(prefs.getInt(SPEECH_RATE, EspeakTtsEngine.DEFAULT_RATE))
    /** espeak-ng words per minute (settings slider). */
    val speechRate: StateFlow<Int> = _speechRate

    private val installer = ModelPack.installer(app)

    /** Natural voice per language: on/off preference (on by default once downloaded). */
    private val _natural = MutableStateFlow(Lang.entries.associateWith { naturalPref(it) })
    val natural: StateFlow<Map<Lang, Boolean>> = _natural

    /** Natural voice per language: installed / downloading / last error. */
    private val _voices = MutableStateFlow(Voices.all.associate { it.lang to VoiceState(installed = isInstalled(it)) })
    val voices: StateFlow<Map<Lang, VoiceState>> = _voices
    val voiceBytes: Map<Lang, Long> = Voices.all.associate { v ->
        v.lang to installer.manifest.files.filter { it.name in v.files }.sumOf { it.size }
    }

    private val _downloadedBytes = MutableStateFlow(0L)
    /** Everything Zbor has stored: base pack + voices. */
    val downloadedBytes: StateFlow<Long> = _downloadedBytes

    private val engines = ZborEngines.create(
        pack, ZborEngines.policyFor(app),
        onPolicyChange = { _slowMode.value = it },
        speechRate = { _speechRate.value },
        naturalVoice = { lang -> _natural.value[lang] == true },
    )

    val controller = ConversationController(
        orchestratorFor = { seam -> PipelineOrchestrator(engines.stt, engines.mt, engines.tts, seam) },
        recorder = MicRecorder(),
        player = player,
        scope = viewModelScope,
        work = work,
        faceToFace = prefs.getBoolean(FACE_TO_FACE, false),
        homeLang = homeLangFor(java.util.Locale.getDefault()),
        history = HistoryFile.load(historyFile),
        onHistory = { e -> viewModelScope.launch(Dispatchers.IO) { HistoryFile.append(historyFile, e) } },
    )

    fun clearHistory() {
        controller.clearHistory()
        viewModelScope.launch(Dispatchers.IO) { historyFile.delete() }
    }

    /** Speaks any text (history ▶) with the current voice for [lang]. */
    fun speak(text: String, lang: Lang) {
        if (controller.state.value.busy) return
        viewModelScope.launch {
            val audio = runCatching { withContext(work) { engines.tts.synthesize(text, lang) } }.getOrNull() ?: return@launch
            player.play(audio)
        }
    }

    fun setNatural(lang: Lang, on: Boolean) {
        _natural.value = _natural.value + (lang to on)
        prefs.edit().putBoolean(NATURAL + lang.name, on).apply()
    }

    /** Downloads one optional voice, then reloads TTS so it speaks from the next turn. */
    fun downloadVoice(lang: Lang) {
        val v = Voices.of(lang)
        if (_voices.value[lang]?.busy == true) return
        update(lang, VoiceState(busy = true))
        viewModelScope.launch(Dispatchers.IO) {
            val error = try {
                installer.download(BuildConfig.PACK_URL, only = v.files) { done, total ->
                    update(lang, VoiceState(busy = true, done = done, total = total))
                }
                null
            } catch (e: IOException) {
                e.message ?: "download failed"
            }
            val ok = isInstalled(v)
            if (ok) withContext(work) { engines.models.evict(ModelKind.TTS) }
            update(lang, VoiceState(installed = ok, error = if (ok) null else error))
            refreshDownloaded()
        }
    }

    fun deleteVoice(lang: Lang) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(work) { engines.models.evict(ModelKind.TTS) }  // close the session first
            installer.remove(Voices.of(lang).files)
            update(lang, VoiceState())
            refreshDownloaded()
        }
    }

    fun refreshDownloaded() {
        viewModelScope.launch(Dispatchers.IO) {
            _downloadedBytes.value = pack.dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }

    private fun update(lang: Lang, state: VoiceState) {
        _voices.value = _voices.value + (lang to state)
    }

    private fun isInstalled(v: VoiceSpec) = v.files.all(installer::isInstalled)

    /** Old builds stored the Macedonian and Portuguese switches under their own keys. */
    private fun naturalPref(lang: Lang): Boolean = prefs.getBoolean(NATURAL + lang.name,
        when (lang) {
            Lang.MK -> prefs.getBoolean("neuralVoice", true)
            Lang.PT -> prefs.getBoolean("naturalPt", true)
            else -> true
        })

    fun setSpeechRate(wpm: Int) {
        _speechRate.value = wpm
        prefs.edit().putInt(SPEECH_RATE, wpm).apply()
    }

    /** Speaks [lang]'s example sentence with whatever voice it uses now, at the current rate. */
    fun previewVoice(lang: Lang = Lang.MK) {
        if (controller.state.value.busy) return
        viewModelScope.launch {
            val audio = runCatching {
                withContext(work) { engines.tts.synthesize(Voices.of(lang).example, lang) }
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
        refreshDownloaded()
    }

    fun onTrimMemory() {
        viewModelScope.launch(work) { engines.models.onMemoryPressure() }
    }

    private companion object {
        const val FACE_TO_FACE = "faceToFace"
        const val SPEECH_RATE = "speechRate"
        const val NATURAL = "natural_"
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

/** history.jsonl: one turn per line, org.json (no extra dependency). Unreadable lines are skipped. */
object HistoryFile {
    fun load(f: java.io.File): List<HistoryEntry> = if (!f.isFile) emptyList() else f.readLines().mapNotNull { line ->
        runCatching {
            val o = org.json.JSONObject(line)
            HistoryEntry(Lang.valueOf(o.getString("from")), Lang.valueOf(o.getString("to")),
                o.getString("source"), o.getString("translation"), o.getLong("time"))
        }.getOrNull()
    }

    fun append(f: java.io.File, e: HistoryEntry) {
        val o = org.json.JSONObject().put("from", e.from.name).put("to", e.to.name)
            .put("source", e.source).put("translation", e.translation).put("time", e.time)
        f.appendText(o.toString() + "\n")
    }
}
