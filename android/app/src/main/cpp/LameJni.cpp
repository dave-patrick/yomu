#include <jni.h>
#include <cstdint>
#include <vector>
#include "lame.h"

extern "C" JNIEXPORT jlong JNICALL
Java_dev_yomu_app_export_LameEncoder_nativeCreate(JNIEnv *, jobject, jint sampleRate, jint channels, jint bitRateKbps) {
    lame_t encoder = lame_init();
    if (encoder == nullptr) return 0;
    lame_set_in_samplerate(encoder, sampleRate);
    lame_set_out_samplerate(encoder, sampleRate);
    lame_set_num_channels(encoder, channels);
    lame_set_mode(encoder, channels == 1 ? MONO : JOINT_STEREO);
    lame_set_brate(encoder, bitRateKbps);
    lame_set_quality(encoder, 3);
    if (lame_init_params(encoder) < 0) {
        lame_close(encoder);
        return 0;
    }
    return reinterpret_cast<jlong>(encoder);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_yomu_app_export_LameEncoder_nativeEncode(JNIEnv *env, jobject, jlong handle, jshortArray pcm, jint sampleCount) {
    auto *encoder = reinterpret_cast<lame_t>(handle);
    if (encoder == nullptr || pcm == nullptr || sampleCount <= 0) return nullptr;
    const jsize available = env->GetArrayLength(pcm);
    if (sampleCount > available || (lame_get_num_channels(encoder) == 2 && sampleCount % 2 != 0)) return nullptr;
    std::vector<jshort> input(static_cast<size_t>(sampleCount));
    env->GetShortArrayRegion(pcm, 0, sampleCount, input.data());
    if (env->ExceptionCheck()) return nullptr;
    std::vector<unsigned char> encoded(static_cast<size_t>(1.25 * sampleCount + 7200));
    const int bytes = lame_get_num_channels(encoder) == 1
        ? lame_encode_buffer(encoder, input.data(), nullptr, sampleCount, encoded.data(), static_cast<int>(encoded.size()))
        : lame_encode_buffer_interleaved(encoder, input.data(), sampleCount / 2, encoded.data(), static_cast<int>(encoded.size()));
    if (bytes < 0) return nullptr;
    jbyteArray result = env->NewByteArray(bytes);
    if (bytes > 0) env->SetByteArrayRegion(result, 0, bytes, reinterpret_cast<const jbyte *>(encoded.data()));
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_yomu_app_export_LameEncoder_nativeFlush(JNIEnv *env, jobject, jlong handle) {
    auto *encoder = reinterpret_cast<lame_t>(handle);
    if (encoder == nullptr) return nullptr;
    std::vector<unsigned char> encoded(7200);
    const int bytes = lame_encode_flush(encoder, encoded.data(), static_cast<int>(encoded.size()));
    if (bytes < 0) return nullptr;
    jbyteArray result = env->NewByteArray(bytes);
    if (bytes > 0) env->SetByteArrayRegion(result, 0, bytes, reinterpret_cast<const jbyte *>(encoded.data()));
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_yomu_app_export_LameEncoder_nativeDestroy(JNIEnv *, jobject, jlong handle) {
    auto *encoder = reinterpret_cast<lame_t>(handle);
    if (encoder != nullptr) lame_close(encoder);
}
