#include <Windows.h>
#include <jni.h>

extern "C" JNIEXPORT jboolean JNICALL Java_net_runelite_launcher_Launcher_isProcessElevated(JNIEnv *env, jclass clazz, jlong pid) {
    HANDLE process = OpenProcess(PROCESS_QUERY_INFORMATION, FALSE, (DWORD) pid);
    if (process == nullptr) {
        return false;
    }

    BOOL ret = false;
    HANDLE hToken = nullptr;
    if (OpenProcessToken(process, TOKEN_QUERY, &hToken)) {
        TOKEN_ELEVATION elevation;
        DWORD returnLength;
        if (GetTokenInformation(hToken, TokenElevation, &elevation, sizeof(elevation), &returnLength)) {
            ret = elevation.TokenIsElevated;
        }
        CloseHandle(hToken);
    }

    CloseHandle(process);

    return (jboolean) ret;
}