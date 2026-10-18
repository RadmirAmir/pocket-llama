#include "mobile_runtime.h"
#include <jni.h>
#include <exception>
#include <string>
#include <vector>

extern "C" JNIEXPORT jint JNICALL
Java_dev_pocketllama_AndroidServerMain_run(JNIEnv * env, jclass, jobjectArray args) {
    try {
        const jsize count = env->GetArrayLength(args);
        if (count < 1 || count > 256) return 64;
        std::vector<std::string> values;
        values.reserve(count);
        for (jsize i = 0; i < count; ++i) {
            auto item = static_cast<jstring>(env->GetObjectArrayElement(args, i));
            if (env->ExceptionCheck() || item == nullptr) return 64;
            const char * text = env->GetStringUTFChars(item, nullptr);
            if (text == nullptr) { env->DeleteLocalRef(item); return 70; }
            values.emplace_back(text);
            env->ReleaseStringUTFChars(item, text);
            env->DeleteLocalRef(item);
        }
        std::vector<char *> argv;
        argv.reserve(count + 1);
        for (auto & value : values) argv.push_back(value.data());
        argv.push_back(nullptr);
        return pocket_llama_run(count, argv.data());
    } catch (const std::exception & error) {
        std::fprintf(stderr, "Pocket Llama JNI server: %s\n", error.what());
        return 70;
    } catch (...) {
        std::fprintf(stderr, "Pocket Llama JNI server failed\n");
        return 70;
    }
}
