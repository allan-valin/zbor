package mk.zbor.engines

import android.content.Context
import java.io.File

/**
 * The offline model pack: downloaded once, or copied over USB into the same folder
 * (Android/data/mk.zbor/files/models). Engines read straight from these files.
 */
class ModelPack(val dir: File) {
    val whisper = File(dir, "ggml-small-q5_1.bin")
    val nllbEncoder = File(dir, "nllb-encoder.ort")
    val nllbDecoder = File(dir, "nllb-decoder.ort")
    val nllbTokenizer = File(dir, "nllb-tokenizer.bin")
    val espeakData = File(dir, "espeak-ng-data")

    companion object {
        fun location(context: Context) = ModelPack(File(context.getExternalFilesDir(null), "models"))
    }
}
