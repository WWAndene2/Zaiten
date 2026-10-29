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
                                                                    jfloat spatialAmount, jint room,
                                                                    jfloat preampDb, jboolean crossfeed) {
    dsp::EffectsParams params;
    jsize bands = env->GetArrayLength(eqDb);
    env->GetFloatArrayRegion(eqDb, 0, bands < dsp::kEqBands ? bands : dsp::kEqBands, params.eqDb.data());
    params.compressorAmount = compressorAmount;
    params.limiter = limiter;
    params.mono = mono;
    params.spatialAmount = spatialAmount;
    params.room = static_cast<dsp::Room>(room);
    params.preampDb = preampDb;
    params.crossfeed = crossfeed;
    engine.setEffects(params);
}

JNIEXPORT void JNICALL
Java_com_zaiten_mixer_core_engine_NativeMixerEngine_nativeRemoveTrack(JNIEnv*, jobject, jint uid) {
    engine.removeTrack(uid);
}

JNIEXPORT jlong JNICALL
Java_com_zaiten_mixer_core_engine_NativeMixerEngine_nativeSetTapEnabled(JNIEnv*, jobject, jboolean enabled) {
    return engine.setTapEnabled(enabled);
}

JNIEXPORT jint JNICALL
Java_com_zaiten_mixer_core_engine_NativeMixerEngine_nativeReadTap(JNIEnv* env, jobject, jshortArray out) {
    jsize size = env->GetArrayLength(out);
    jshort* data = env->GetShortArrayElements(out, nullptr);
    int read = engine.readTap(data, size);
    env->ReleaseShortArrayElements(out, data, 0);
    return read;
}

JNIEXPORT void JNICALL
Java_com_zaiten_mixer_core_engine_NativeMixerEngine_nativeTapClock(JNIEnv* env, jobject, jlongArray out) {
    jlong values[2];
    int64_t timeNs, frames;
    engine.tapClock(&timeNs, &frames);
    values[0] = timeNs;
    values[1] = frames;
    env->SetLongArrayRegion(out, 0, 2, values);
}

JNIEXPORT void JNICALL
Java_com_zaiten_mixer_core_engine_NativeMixerEngine_nativeSetOutputDelayMs(JNIEnv*, jobject, jint ms) {
    engine.setOutputDelayMs(ms);
}

}  // extern "C"
