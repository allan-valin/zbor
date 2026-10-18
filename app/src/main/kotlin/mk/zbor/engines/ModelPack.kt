package mk.zbor.engines

import android.content.Context
import mk.zbor.pack.PackInstaller
import mk.zbor.pack.PackManifest
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
    /** v2: the fine-tuned Macedonian neural voice (VITS, ONNX) and its character vocabulary. */
    val mkVoice = File(dir, "mk-voice.onnx")
    val mkVoiceVocab = File(dir, "mk-voice.vocab.json")

    companion object {
        fun location(context: Context) = ModelPack(File(context.getExternalFilesDir(null), "models"))

        /** The manifest shipped in the APK (assets/pack.json, written by tools/build_pack.sh). */
        fun manifest(context: Context): PackManifest =
            PackManifest.parse(context.assets.open("pack.json").bufferedReader().use { it.readText() })

        fun installer(context: Context) = PackInstaller(location(context).dir, manifest(context))
    }
}
