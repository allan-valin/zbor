plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}
tasks.test { useJUnitPlatform() }
// Bytecode level Android (:app) can consume.
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

tasks.register<JavaExec>("manifest") {
    description = "Writes pack.json for a built pack; see tools/build_pack.sh"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("mk.zbor.pack.ManifestToolKt")
    args(providers.gradleProperty("manifestArgs").get().split(" "))
}
