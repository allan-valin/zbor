plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}
dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    // Same ai.onnxruntime API as onnxruntime-android, which :app ships instead.
    compileOnly("com.microsoft.onnxruntime:onnxruntime:1.22.0")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.22.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}
tasks.test {
    useJUnitPlatform()
    // Goldens shared with the Python front-end (tools/voice/mk_text.py) live in eval/.
    environment("ZBOR_EVAL", rootProject.file("eval").path)
    environment("ZBOR_VOICE", System.getenv("ZBOR_VOICE") ?: "${System.getProperty("user.home")}/zbor-build/onnx")
}
// Bytecode level Android (:app) can consume.
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
