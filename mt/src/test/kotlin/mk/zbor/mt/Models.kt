package mk.zbor.mt

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File

/** The real model pack on the dev machine; tests that need it skip without it. */
object Models {
    val dir = File(System.getenv("ZBOR_MODELS") ?: "")
    fun file(name: String): File = File(dir, name).also {
        assumeTrue(it.isFile, "model file $it missing; set ZBOR_MODELS")
    }
}
