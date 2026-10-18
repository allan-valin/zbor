package mk.zbor.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import mk.zbor.BuildConfig
import mk.zbor.R
import mk.zbor.engines.ModelPack
import mk.zbor.pack.PackStatus
import java.io.IOException

sealed interface SetupState {
    data object Ready : SetupState
    data class Needed(
        val status: PackStatus,
        /** Last action's outcome, shown so every tap visibly answers. */
        val message: String? = null,
        /** On mobile data: the next Download tap must confirm. */
        val confirmMetered: Boolean = false,
    ) : SetupState
    data class Working(val what: Work, val done: Long = 0, val total: Long = 0) : SetupState
}

enum class Work { CHECKING, CHECKING_DEEP, DOWNLOADING, IMPORTING }

/** Gets the model pack onto the phone: check, download (resumable), or import a sideloaded folder. */
class SetupViewModel(app: Application) : AndroidViewModel(app) {
    private val installer = ModelPack.installer(app)
    private val _state = MutableStateFlow<SetupState>(SetupState.Working(Work.CHECKING))
    val state: StateFlow<SetupState> = _state
    @Volatile private var paused = false

    val totalBytes get() = installer.manifest.totalBytes

    init { check() }

    fun check(deep: Boolean = false, message: String? = null) {
        _state.value = SetupState.Working(if (deep) Work.CHECKING_DEEP else Work.CHECKING)
        viewModelScope.launch(Dispatchers.IO) {
            val status = installer.status(deep)
            _state.value = if (status.ready) SetupState.Ready else SetupState.Needed(status, message)
        }
    }

    fun download(confirmedMetered: Boolean) {
        val now = _state.value as? SetupState.Needed ?: return
        if (isMetered() && !confirmedMetered) {
            _state.value = now.copy(confirmMetered = true, message = str(R.string.metered_warning, mb(totalBytes)))
            return
        }
        paused = false
        _state.value = SetupState.Working(Work.DOWNLOADING, 0, totalBytes)
        viewModelScope.launch(Dispatchers.IO) {
            val msg = try {
                installer.download(BuildConfig.PACK_URL) { done, total ->
                    if (paused) throw IOException("paused")
                    _state.value = SetupState.Working(Work.DOWNLOADING, done, total)
                }
                null
            } catch (e: IOException) {
                if (paused) str(R.string.download_paused)
                else str(R.string.download_interrupted, e.message.orEmpty())
            }
            check(message = msg)
        }
    }

    fun pause() { paused = true }

    fun importFrom(tree: Uri) {
        _state.value = SetupState.Working(Work.IMPORTING, 0, totalBytes)
        viewModelScope.launch(Dispatchers.IO) {
            val msg = try {
                val children = listChildren(tree)
                val r = installer.import({ name ->
                    children[name]?.let { getApplication<Application>().contentResolver.openInputStream(it) }
                }) { done, total -> _state.value = SetupState.Working(Work.IMPORTING, done, total) }
                buildString {
                    append(str(R.string.import_result, r.imported.size))
                    if (r.rejected.isNotEmpty()) append(str(R.string.import_rejected, r.rejected.joinToString()))
                    if (r.absent.isNotEmpty()) append(str(R.string.import_absent, r.absent.joinToString()))
                }
            } catch (e: Exception) {
                str(R.string.import_failed, e.message.orEmpty())
            }
            check(message = msg)
        }
    }

    /** Display name → document URI for the files directly inside a picked folder. */
    private fun listChildren(tree: Uri): Map<String, Uri> {
        val resolver = getApplication<Application>().contentResolver
        val parent = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val out = HashMap<String, Uri>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) out[c.getString(1)] = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
        }
        return out
    }

    private fun str(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    private fun isMetered() =
        getApplication<Application>().getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true

    companion object {
        fun mb(bytes: Long) = "%,d MB".format(bytes / 1_000_000)
    }
}
