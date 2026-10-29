#include <jni.h>

#include "engine/MixerEngine.h"

namespace {
MixerEngine engine;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_zaiten_mixer_core_engine_NativeMixerEngine_nativeStart(JNIEnv*, jobject) {
    return engine.start();
}

JNIEXPORT void JNICALL
Java_com_zaiten_mixer_core_engine_NativeMixerEngine_nativeStop(JNIEnv*, jobject) {
    engine.stop();
}

JNIEXPORT void JNICALL
Java_com_zaiten_mixer_core_engine_NativeMixerEngine_nativeWrite(JNIEnv* env, jobject, jint uid,
                                                               jshortArray samples, jint count) {
    jshort* data = env->GetShortArrayElements(samples, nullptr);
    engine.write(uid, data, count);
    env->ReleaseShortArrayElements(samples, data, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_zaiten_mixer_core_engine_NativeMixerEngine_nativeSetGain(JNIEnv*, jobject, jint uid,
                                                                 jint percent) {
    engine.setGain(uid, percent);
}

JNIEXPORT void JNICALL
Java_com_zaiten_mixer_core_engine_NativeMixerEngine_nativeRemoveTrack(JNIEnv*, jobject, jint uid) {
    engine.removeTrack(uid);
}

}  // extern "C"
