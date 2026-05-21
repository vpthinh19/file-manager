// Phase C-2a: JNI skeleton — placeholder cho libarchive integration.
//
// nativeVersion() trả về hardcoded string trong C-2a. Ở C-2b sẽ thay bằng
// `archive_version_string()` từ libarchive thật sau khi vendor source.
//
// Bridge này cố ý KHÔNG link tới libarchive ở C-2a. Mục đích duy nhất:
// verify toolchain (NDK download, CMake config, JNI symbol mangling,
// System.loadLibrary, ABI filter arm64-v8a) work end-to-end. Smoke test
// C-2a: Java side gọi nativeVersion() → trả về placeholder string.

#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "libarchive_bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_vpt_filemanager_archive_LibarchiveBridge_nativeVersion(
        JNIEnv* env, jobject /* thiz */) {
    const std::string placeholder = "libarchive vendor pending (Phase C-2a skeleton, arm64-v8a)";
    LOGI("%s", placeholder.c_str());
    return env->NewStringUTF(placeholder.c_str());
}
