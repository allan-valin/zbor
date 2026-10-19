package mk.zbor.pack

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class PackStatus(val ok: List<PackFile>, val missing: List<PackFile>, val bad: List<PackFile>) {
    val ready get() = missing.isEmpty() && bad.isEmpty()
}

data class ImportResult(val imported: List<String>, val rejected: List<String>, val absent: List<String>)

/**
 * Installs the model pack into [dir]: resumable download (HTTP Range, `.part` files)
 * or import from a sideloaded folder, every file checked against the manifest's
 * sha256. A `.verified/<name>` marker remembers a good hash so launching the app
 * does not re-hash 1 GB each time.
 */
class PackInstaller(val dir: File, val manifest: PackManifest) {
    private val markers = File(dir, ".verified")

    /** Readiness of the required files (optional voices never block the app). */
    fun status(deep: Boolean = false): PackStatus {
        val ok = mutableListOf<PackFile>(); val missing = mutableListOf<PackFile>(); val bad = mutableListOf<PackFile>()
        for (f in manifest.files.filterNot { it.optional }) when (check(f, deep)) {
            State.OK -> ok += f
            State.MISSING -> missing += f
            State.BAD -> bad += f
        }
        return PackStatus(ok, missing, bad)
    }

    fun isInstalled(name: String): Boolean =
        manifest.files.firstOrNull { it.name == name }?.let { check(it, deep = false) == State.OK } == true

    /**
     * Downloads from [baseUrl]: by default whatever required file is missing or bad; with
     * [only], just those files (e.g. one optional voice). [progress] gets (bytes done, total).
     */
    fun download(baseUrl: String, only: Set<String>? = null, progress: (Long, Long) -> Unit) {
        dir.mkdirs()
        val scope = manifest.files.filter { if (only == null) !it.optional else it.name in only }
        val todo = scope.filter { check(it, deep = false) != State.OK }
        val total = scope.sumOf { it.size }
        var done = total - todo.sumOf { it.size }
        for (f in todo) {
            val part = File(dir, "${f.name}.part")
            val base = done
            fetch(URL(urlOf(baseUrl, f)), part, f.size) { got -> progress(base + got, total) }
            if (part.length() < f.size) {
                // The connection ended early but cleanly: keep the bytes, the next call resumes.
                throw IOException("${f.name}: connection ended at ${part.length()} of ${f.size} bytes")
            }
            if (!install(f, part)) {
                part.delete() // never resume onto bytes that hash wrong
                throw IOException("${f.name}: checksum mismatch")
            }
            done += f.size
            progress(done, total)
        }
    }

    /** Copies pack files from a sideloaded folder; [open] returns null for a file that is not there. */
    fun import(open: (String) -> InputStream?, progress: (Long, Long) -> Unit): ImportResult {
        dir.mkdirs()
        val imported = mutableListOf<String>(); val rejected = mutableListOf<String>(); val absent = mutableListOf<String>()
        val todo = manifest.files.filter { check(it, deep = false) != State.OK }
        var done = manifest.totalBytes - todo.filterNot { it.optional }.sumOf { it.size }
        for (f in todo) {
            val input = open(f.name)
            if (input == null) { if (!f.optional) absent += f.name; continue }
            val part = File(dir, "${f.name}.part")
            val base = done
            input.use { src -> FileOutputStream(part).use { out -> copy(src, out) { got -> progress(base + got, manifest.totalBytes) } } }
            if (install(f, part)) { imported += f.name; done += f.size } else { part.delete(); rejected += f.name }
        }
        return ImportResult(imported, rejected, absent)
    }

    /** `…/download/pack-v1/` + name, or the sibling release folder when [PackFile.release] says so. */
    private fun urlOf(baseUrl: String, f: PackFile): String {
        val base = baseUrl.trimEnd('/')
        val dir = f.release?.let { base.substringBeforeLast('/') + "/" + it } ?: base
        return "$dir/${f.name}"
    }

    private enum class State { OK, MISSING, BAD }

    private fun check(f: PackFile, deep: Boolean): State {
        val marker = File(markers, f.name)
        val file = File(dir, f.name)
        if (f.unzip) {
            if (marker.isFile && marker.readText().startsWith(f.sha256)) return State.OK
            // Sideloaded as-is (USB copy of the release): install it from where it lies.
            if (file.isFile) return if (install(f, file)) State.OK else State.BAD
            return State.MISSING
        }
        if (!file.isFile) return State.MISSING
        if (file.length() != f.size) return State.BAD
        if (!deep && marker.isFile && marker.readText() == stamp(f, file)) return State.OK
        if (file.inputStream().use(::sha256) != f.sha256) return State.BAD
        mark(f, file)
        return State.OK
    }

    /** Verifies [part] and moves it into place (or extracts it). False if the hash is wrong. */
    private fun install(f: PackFile, part: File): Boolean {
        if (part.length() != f.size || part.inputStream().use(::sha256) != f.sha256) return false
        if (f.unzip) {
            unzip(part)
            part.delete()
            markers.mkdirs()
            File(markers, f.name).writeText(f.sha256)
        } else {
            val target = File(dir, f.name)
            target.delete()
            if (!part.renameTo(target)) throw IOException("cannot move ${part.name} into place")
            mark(f, target)
        }
        return true
    }

    private fun unzip(zip: File) {
        val root = dir.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val out = File(dir, e.name).canonicalFile
                if (!out.path.startsWith(root.path + File.separator)) throw SecurityException("zip entry escapes pack: ${e.name}")
                if (e.isDirectory) out.mkdirs() else {
                    out.parentFile.mkdirs()
                    FileOutputStream(out).use { z.copyTo(it) }
                }
            }
        }
    }

    private fun stamp(f: PackFile, file: File) = "${f.sha256} ${file.length()} ${file.lastModified()}"

    private fun mark(f: PackFile, file: File) {
        markers.mkdirs()
        File(markers, f.name).writeText(stamp(f, file))
    }

    private fun fetch(url: URL, part: File, size: Long, progress: (Long) -> Unit) {
        if (part.length() > size) part.delete()
        if (part.length() == size) return
        val have = part.length()
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
        try {
            val append = when (conn.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> false // server ignored Range: start over
                else -> throw IOException("${url.path}: HTTP ${conn.responseCode}")
            }
            val start = if (append) have else 0L
            conn.inputStream.use { src ->
                FileOutputStream(part, append).use { out -> copy(src, out) { got -> progress(start + got) } }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun copy(src: InputStream, out: FileOutputStream, progress: (Long) -> Unit) {
        val buf = ByteArray(1 shl 16)
        var total = 0L
        var lastReport = 0L
        while (true) {
            val n = src.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
            if (total - lastReport >= 1 shl 20) { progress(total); lastReport = total }
        }
        progress(total)
    }
}
