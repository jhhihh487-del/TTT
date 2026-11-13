#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_localimagestudio_NativeEngine_nativeBackendStatus(JNIEnv* env, jobject) {
    const std::string status =
        "الواجهة وطبقة JNI جاهزتان. محرك Stable Diffusion غير مضمّن؛ لا يوجد توليد صور في هذه النسخة.";
    return env->NewStringUTF(status.c_str());
}
