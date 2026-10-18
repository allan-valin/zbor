package mk.zbor.pack

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PackInstallerTest {
    @TempDir lateinit var tmp: File

    private val release by lazy { File(tmp, "release").apply { mkdirs() } }
    private val device by lazy { File(tmp, "device") }

    /** A tiny pack: two binaries and a zipped data dir, plus its manifest. */
    private fun makeRelease(): PackManifest {
        File(release, "model-a.bin").writeBytes(Random(1).nextBytes(300_000))
        File(release, "model-b.bin").writeBytes(Random(2).nextBytes(50_000))
        ZipOutputStream(File(release, "voice-data.zip").outputStream()).use { z ->
            z.putNextEntry(ZipEntry("voice-data/phondata")); z.write(ByteArray(1000) { 7 }); z.closeEntry()
            z.putNextEntry(ZipEntry("voice-data/mk_dict")); z.write("здраво".toByteArray()); z.closeEntry()
        }
        return PackManifest.build("test-v1", release, unzip = setOf("voice-data.zip"))
    }

    @Test fun `manifest lists size and sha256 of every release file`() {
        val m = makeRelease()
        assertEquals(listOf("model-a.bin", "model-b.bin", "voice-data.zip"), m.files.map { it.name })
        assertEquals(300_000, m.files[0].size)
        assertEquals(64, m.files[0].sha256.length)
        assertEquals(m, PackManifest.parse(m.toJson()))
    }

    @Test fun `empty device reports everything missing`() {
        val p = PackInstaller(device, makeRelease())
        assertEquals(3, p.status().missing.size)
        assertTrue(!p.status().ready)
    }

    @Test fun `download installs, unzips and verifies the pack`() {
        val m = makeRelease()
        TestServer(release).use { srv ->
            val p = PackInstaller(device, m)
            val progress = mutableListOf<Long>()
            p.download(srv.url) { done, _ -> progress += done }
            assertTrue(p.status().ready, p.status().toString())
            assertEquals(File(release, "model-a.bin").readBytes().toList(), File(device, "model-a.bin").readBytes().toList())
            assertEquals("здраво", File(device, "voice-data/mk_dict").readText())
            assertEquals(m.totalBytes, progress.last())
        }
    }

    @Test fun `a dropped connection resumes from where it stopped`() {
        val m = makeRelease()
        TestServer(release).use { srv ->
            srv.cutAfter = 120_000
            val p = PackInstaller(device, m)
            assertFailsWith<java.io.IOException> { p.download(srv.url) { _, _ -> } }
            val partLen = File(device, "model-a.bin.part").length()
            assertTrue(partLen in 1..120_000, "part=$partLen")
            p.download(srv.url) { _, _ -> }
            assertTrue(p.status().ready)
            assertTrue(srv.rangeRequests.any { it.startsWith("bytes=") && it != "bytes=0-" }, srv.rangeRequests.toString())
        }
    }

    @Test fun `server that ignores Range restarts the file instead of corrupting it`() {
        val m = makeRelease()
        TestServer(release).use { srv ->
            srv.cutAfter = 120_000
            val p = PackInstaller(device, m)
            runCatching { p.download(srv.url) { _, _ -> } }
            srv.ignoreRange = true
            p.download(srv.url) { _, _ -> }
            assertTrue(p.status().ready)
        }
    }

    @Test fun `corrupt file is detected and replaced on the next download`() {
        val m = makeRelease()
        TestServer(release).use { srv ->
            val p = PackInstaller(device, m)
            p.download(srv.url) { _, _ -> }
            File(device, "model-b.bin").writeBytes(ByteArray(50_000))
            assertEquals(listOf("model-b.bin"), p.status(deep = true).bad.map { it.name })
            p.download(srv.url) { _, _ -> }
            assertTrue(p.status(deep = true).ready)
        }
    }

    @Test fun `quick status trusts a verified marker but notices size changes`() {
        val m = makeRelease()
        TestServer(release).use { srv -> PackInstaller(device, m).download(srv.url) { _, _ -> } }
        val p = PackInstaller(device, m)
        assertTrue(p.status().ready)
        File(device, "model-a.bin").appendBytes(byteArrayOf(1))
        assertEquals(listOf("model-a.bin"), p.status().bad.map { it.name })
    }

    @Test fun `import copies a sideloaded folder and rejects a wrong file`() {
        val m = makeRelease()
        val p = PackInstaller(device, m)
        p.import({ name -> File(release, name).takeIf { it.isFile }?.inputStream() }) { _, _ -> }
        assertTrue(p.status(deep = true).ready)

        val other = PackInstaller(File(tmp, "device2"), m)
        File(release, "model-b.bin").writeBytes(ByteArray(10))
        val result = other.import({ name -> File(release, name).takeIf { it.isFile }?.inputStream() }) { _, _ -> }
        assertEquals(listOf("model-b.bin"), result.rejected)
        assertEquals(listOf("model-b.bin"), other.status().missing.map { it.name })
    }

    @Test fun `zip entries cannot escape the pack directory`() {
        File(release, "evil.zip").also { f ->
            ZipOutputStream(f.outputStream()).use { z -> z.putNextEntry(ZipEntry("../outside")); z.write(1); z.closeEntry() }
        }
        val m = PackManifest.build("t", release, unzip = setOf("evil.zip"))
        TestServer(release).use { srv ->
            assertFailsWith<SecurityException> { PackInstaller(device, m).download(srv.url) { _, _ -> } }
        }
        assertTrue(!File(tmp, "outside").exists())
    }

    @Test fun `release files copied straight into the pack dir are detected, zip extracted`() {
        val m = makeRelease()
        device.mkdirs()
        release.listFiles()!!.forEach { it.copyTo(File(device, it.name)) }
        val p = PackInstaller(device, m)
        assertTrue(p.status().ready, p.status().toString())
        assertEquals("здраво", File(device, "voice-data/mk_dict").readText())
        assertTrue(!File(device, "voice-data.zip").exists())
    }
}
