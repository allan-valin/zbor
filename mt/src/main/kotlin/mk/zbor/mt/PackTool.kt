package mk.zbor.mt

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File

/**
 * Desktop steps of building the model pack (driven by tools/build_pack.sh):
 *   tokenizer <tokenizer.json> <out.bin>  — HF JSON → compact binary the phone loads
 *   ort <in.onnx> <out.ort>              — ONNX → ORT format, which the phone memory-maps
 */
fun main(args: Array<String>) {
    require(args.size == 3) { "usage: PackTool tokenizer|ort <in> <out>" }
    val (mode, input, output) = args
    when (mode) {
        "tokenizer" -> File(output).outputStream().use(NllbTokenizer.fromTokenizerJson(File(input))::writeCompact)
        "ort" -> OrtSession.SessionOptions().use { o ->
            // Basic optimisations only: the saved graph must stay valid on arm64 as well as x86_64.
            o.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            o.setOptimizedModelFilePath(output)
            o.addConfigEntry("session.save_model_format", "ORT")
            OrtEnvironment.getEnvironment().createSession(input, o).close()
        }
        else -> error("unknown mode $mode")
    }
    println("wrote $output (${File(output).length() / (1 shl 20)} MiB)")
}
