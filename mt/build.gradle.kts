plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}
repositories { mavenCentral() }
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
    maxHeapSize = "1g"
    // Real-model tests read from here and skip when it is absent.
    environment("ZBOR_MODELS", System.getenv("ZBOR_MODELS") ?: "${System.getProperty("user.home")}/zbor-build/models")
}
// Bytecode level Android (:app) can consume.
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
