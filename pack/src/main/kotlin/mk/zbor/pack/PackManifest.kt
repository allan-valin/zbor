package mk.zbor.pack

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** One release asset. [unzip] assets are extracted into the pack dir and not kept. */
@Serializable
data class PackFile(val name: String, val size: Long, val sha256: String, val unzip: Boolean = false)

/** What a complete pack is: shipped inside the APK, so sideloaded files are checked too. */
@Serializable
data class PackManifest(val version: String, val files: List<PackFile>) {
    val totalBytes: Long get() = files.sumOf { it.size }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { prettyPrint = true }

        fun parse(text: String): PackManifest = json.decodeFromString(serializer(), text)

        /** Describes every file in [dir] (a release folder ready for upload). */
        fun build(version: String, dir: File, unzip: Set<String> = emptySet()): PackManifest =
            PackManifest(version, dir.listFiles()!!.filter { it.isFile }.sortedBy { it.name }.map {
                PackFile(it.name, it.length(), it.inputStream().use(::sha256), it.name in unzip)
            })
    }
}

fun sha256(input: InputStream): String {
    val md = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(1 shl 16)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        md.update(buf, 0, n)
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Desktop step of tools/build_pack.sh:
 *   ManifestTool <version> <release-dir> <out.json> [unzip-name …]
 */
fun main(args: Array<String>) {
    require(args.size >= 3) { "usage: ManifestTool <version> <release-dir> <out.json> [unzip …]" }
    val m = PackManifest.build(args[0], File(args[1]), args.drop(3).toSet())
    File(args[2]).writeText(m.toJson())
    println("wrote ${args[2]}: ${m.files.size} files, ${m.totalBytes / (1 shl 20)} MiB")
}
