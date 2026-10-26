#include <jni.h>
#include <algorithm>
#include <memory>
#include <stdexcept>
#include <vector>
#include "runtime.h"
#include "diar_pipeline.h"

namespace {
ggml_runtime::Params params(int threads) {
    ggml_runtime::Params value;
    value.use_gpu = false;
    value.n_threads = std::clamp(threads, 1, 4);
    return value;
}
struct Job {
    ggml_runtime::BackendManager backend;
    nemo_speech::asr::DiarModel model;
    nemo_speech::asr::DiarStream stream;
    bool finished = false;
    Job(const char* path, int threads)
        : backend(params(threads)), model(backend, path),
          stream(model, nemo_speech::asr::DiarGeometry::v3_offline()) {
        if (model.cfg().num_speakers != 8 || !model.cfg().is_v3())
            throw std::runtime_error("Expected Nemotron 3 eight-speaker model");
    }
};
void error(JNIEnv* env, const char* message) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/io/IOException"), message);
}
Job& job(jlong handle) {
    if (!handle) throw std::runtime_error("Diarizer closed");
    return *reinterpret_cast<Job*>(handle);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_pocketllama_voice_NemotronDiarizer_open(JNIEnv* env, jclass, jstring path, jint threads) {
    if (!path) { error(env, "Missing model path"); return 0; }
    const char* text = env->GetStringUTFChars(path, nullptr);
    if (!text) return 0;
    try {
        auto result = std::make_unique<Job>(text, threads);
        env->ReleaseStringUTFChars(path, text);
        return reinterpret_cast<jlong>(result.release());
    } catch (const std::exception& e) {
        env->ReleaseStringUTFChars(path, text); error(env, e.what());
    } catch (...) {
        env->ReleaseStringUTFChars(path, text); error(env, "Nemotron initialization failed");
    }
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_pocketllama_voice_NemotronDiarizer_push(JNIEnv* env, jclass, jlong handle, jfloatArray audio) {
    try {
        auto& value = job(handle);
        if (value.finished || !audio) throw std::runtime_error("Invalid diarization stream");
        jsize count = env->GetArrayLength(audio);
        if (count < 1 || count > 16000) throw std::runtime_error("Invalid diarization block");
        std::vector<float> samples(count);
        env->GetFloatArrayRegion(audio, 0, count, samples.data());
        if (env->ExceptionCheck()) return;
        try { value.stream.feed_audio(samples.data(), samples.size()); }
        catch (...) { std::fill(samples.begin(), samples.end(), 0.f); throw; }
        std::fill(samples.begin(), samples.end(), 0.f);
    } catch (const std::exception& e) { error(env, e.what()); }
      catch (...) { error(env, "Nemotron inference failed"); }
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_dev_pocketllama_voice_NemotronDiarizer_finish(JNIEnv* env, jclass, jlong handle) {
    try {
        auto& value = job(handle);
        if (!value.finished) { value.stream.finish(); value.finished = true; }
        auto segments = value.stream.segments();
        if (segments.size() > 1000000) throw std::runtime_error("Too many speaker segments");
        std::vector<double> result;
        result.reserve(segments.size()*3);
        for (const auto& s : segments) {
            result.push_back(s.t0); result.push_back(s.t1);
            result.push_back(s.speaker); // ZERO-based C++ channel; Java adds one exactly once.
        }
        jdoubleArray out = env->NewDoubleArray(static_cast<jsize>(result.size()));
        if (out) env->SetDoubleArrayRegion(out, 0, static_cast<jsize>(result.size()), result.data());
        return out;
    } catch (const std::exception& e) { error(env, e.what()); }
      catch (...) { error(env, "Nemotron finalization failed"); }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_pocketllama_voice_NemotronDiarizer_destroy(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<Job*>(handle);
}
