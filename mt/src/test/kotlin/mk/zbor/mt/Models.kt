package mk.zbor.mt

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File

/**
 * Model files on the dev machine; tests that need them skip without them.
 * [sources] = downloads (tokenizer.json …), [pack] = tools/build_pack.sh output.
 */
object Models {
    val sources = File(System.getenv("ZBOR_MODELS") ?: "")
    val pack = File(System.getenv("ZBOR_PACK") ?: "")

    fun source(name: String) = need(File(sources, name))
    fun pack(name: String) = need(File(pack, name))

    private fun need(f: File) = f.also { assumeTrue(it.isFile, "model file $it missing; see docs/BUILD.md") }
}
