// Thin JNI over espeak-ng: synchronous synthesis into a 16-bit PCM buffer.
// espeak-ng keeps global state, so the Kotlin side serialises every call.
#include <jni.h>
#include <vector>
#include <cstring>
#include <espeak-ng/speak_lib.h>

static std::vector<short> *g_out = nullptr;

static int collect(short *wav, int n, espeak_EVENT *) {
    if (wav && n > 0 && g_out) g_out->insert(g_out->end(), wav, wav + n);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_mk_zbor_engines_EspeakNative_init(JNIEnv *env, jobject, jstring dataParent) {
    const char *p = env->GetStringUTFChars(dataParent, nullptr);
    int rate = espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, 0, p, espeakINITIALIZE_DONT_EXIT);
    env->ReleaseStringUTFChars(dataParent, p);
    if (rate > 0) espeak_SetSynthCallback(collect);
    return rate;
}

extern "C" JNIEXPORT jshortArray JNICALL
Java_mk_zbor_engines_EspeakNative_synth(JNIEnv *env, jobject, jstring text, jstring voice, jint wordsPerMinute) {
    const char *v = env->GetStringUTFChars(voice, nullptr);
    espeak_ERROR vr = espeak_SetVoiceByName(v);
    env->ReleaseStringUTFChars(voice, v);
    if (vr != EE_OK) return nullptr;
    espeak_SetParameter(espeakRATE, wordsPerMinute, 0); // after the voice: setting a voice resets the rate

    const char *t = env->GetStringUTFChars(text, nullptr);
    std::vector<short> out;
    g_out = &out;
    espeak_ERROR sr = espeak_Synth(t, strlen(t) + 1, 0, POS_CHARACTER, 0, espeakCHARS_UTF8, nullptr, nullptr);
    espeak_Synchronize();
    g_out = nullptr;
    env->ReleaseStringUTFChars(text, t);
    if (sr != EE_OK) return nullptr;

    jshortArray arr = env->NewShortArray(static_cast<jsize>(out.size()));
    env->SetShortArrayRegion(arr, 0, static_cast<jsize>(out.size()), out.data());
    return arr;
}

extern "C" JNIEXPORT void JNICALL
Java_mk_zbor_engines_EspeakNative_terminate(JNIEnv *, jobject) {
    espeak_Terminate();
}
