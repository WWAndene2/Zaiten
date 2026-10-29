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
Java_com_zaiten_mixer_core_engine_NativeMixerEngine_nativeSetGains(JNIEnv*, jobject, jint uid,
                                                                  jint left, jint right) {
    engine.setGains(uid, left, right);
}

JNIEXPORT void JNICALL
Java_com_zaiten_mixer_core_engine_NativeMixerEngine_nativeSetEffects(JNIEnv* env, jobject,
                                                                    jfloatArray eqDb,
                                                                    jfloat compressorAmount,
                                                                    jboolean limiter, jboolean mono,
                                                                    jfloat spatialAmount, jint room) {
    dsp::EffectsParams params;
    jsize bands = env->GetArrayLength(eqDb);
    env->GetFloatArrayRegion(eqDb, 0, bands < dsp::kEqBands ? bands : dsp::kEqBands, params.eqDb.data());
    params.compressorAmount = compressorAmount;
    params.limiter = limiter;
    params.mono = mono;
    params.spatialAmount = spatialAmount;
    params.room = static_cast<dsp::Room>(room);
    engine.setEffects(params);
}

JNIEXPORT void JNICALL
Java_com_zaiten_mixer_core_engine_NativeMixerEngine_nativeRemoveTrack(JNIEnv*, jobject, jint uid) {
    engine.removeTrack(uid);
}

}  // extern "C"
