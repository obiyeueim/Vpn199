#include <jni.h>

#include <android/log.h>
#include <dlfcn.h>
#include <sys/socket.h>

#include <atomic>
#include <chrono>
#include <cstddef>
#include <mutex>
#include <thread>

#include "dobby.h"

namespace {

constexpr const char *kLogTag = "NetworkQA";
constexpr int kDefaultLatencyMs = 300;
constexpr int kMinimumLatencyMs = 0;
constexpr int kMaximumLatencyMs = 5'000;

using SendToFunction = ssize_t (*)(
        int,
        const void *,
        size_t,
        int,
        const sockaddr *,
        socklen_t
);

std::atomic_bool gLatencyEnabled{false};
std::atomic_bool gPacketDropEnabled{false};
std::atomic_int gLatencyMs{kDefaultLatencyMs};
std::atomic_bool gHookInstalled{false};
std::mutex gHookMutex;
SendToFunction gOriginalSendTo = nullptr;

bool IsUdpSocket(const int socketFd) {
    int socketType = 0;
    socklen_t optionLength = sizeof(socketType);

    return getsockopt(
            socketFd,
            SOL_SOCKET,
            SO_TYPE,
            &socketType,
            &optionLength
    ) == 0 && socketType == SOCK_DGRAM;
}

ssize_t EmulatedSendTo(
        const int socketFd,
        const void *buffer,
        const size_t length,
        const int flags,
        const sockaddr *destinationAddress,
        const socklen_t addressLength
) {
    const SendToFunction original = gOriginalSendTo;
    if (original == nullptr) {
        return -1;
    }

    // This module is intentionally process-local and only modifies UDP calls
    // made by the APK process that loaded libNetworkQA.so.
    if (!IsUdpSocket(socketFd)) {
        return original(
                socketFd,
                buffer,
                length,
                flags,
                destinationAddress,
                addressLength
        );
    }

    // Silent success most closely models a packet that left the application
    // but was lost later in the network path.
    if (gPacketDropEnabled.load(std::memory_order_relaxed)) {
        return static_cast<ssize_t>(length);
    }

    if (gLatencyEnabled.load(std::memory_order_relaxed)) {
        const int delayMs = gLatencyMs.load(std::memory_order_relaxed);
        if (delayMs > 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(delayMs));
        }
    }

    return original(
            socketFd,
            buffer,
            length,
            flags,
            destinationAddress,
            addressLength
    );
}

bool InstallSendToHook() {
    std::lock_guard<std::mutex> lock(gHookMutex);

    if (gHookInstalled.load(std::memory_order_acquire)) {
        return true;
    }

    void *sendToAddress = DobbySymbolResolver("libc.so", "sendto");
    if (sendToAddress == nullptr) {
        sendToAddress = dlsym(RTLD_DEFAULT, "sendto");
    }

    if (sendToAddress == nullptr) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                kLogTag,
                "Unable to resolve libc sendto"
        );
        return false;
    }

    void *originalAddress = nullptr;
    const int hookResult = DobbyHook(
            sendToAddress,
            reinterpret_cast<void *>(EmulatedSendTo),
            &originalAddress
    );

    if (hookResult != 0 || originalAddress == nullptr) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                kLogTag,
                "DobbyHook(sendto) failed: %d",
                hookResult
        );
        return false;
    }

    gOriginalSendTo = reinterpret_cast<SendToFunction>(originalAddress);
    gHookInstalled.store(true, std::memory_order_release);

    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "Process-local sendto hook installed with Dobby %s",
            DobbyGetVersion()
    );

    return true;
}

jboolean NativeInstallHooks(JNIEnv *, jclass) {
    return InstallSendToHook() ? JNI_TRUE : JNI_FALSE;
}

void NativeToggleLatencySimulation(JNIEnv *, jclass, const jboolean enabled) {
    gLatencyEnabled.store(enabled == JNI_TRUE, std::memory_order_relaxed);

    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "Latency simulation: %s (%d ms)",
            enabled == JNI_TRUE ? "enabled" : "disabled",
            gLatencyMs.load(std::memory_order_relaxed)
    );
}

void NativeTogglePacketDrop(JNIEnv *, jclass, const jboolean enabled) {
    gPacketDropEnabled.store(enabled == JNI_TRUE, std::memory_order_relaxed);

    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "Packet-drop simulation: %s",
            enabled == JNI_TRUE ? "enabled" : "disabled"
    );
}

void NativeSetLatencyMs(JNIEnv *, jclass, const jint milliseconds) {
    const int clamped = milliseconds < kMinimumLatencyMs
                        ? kMinimumLatencyMs
                        : milliseconds > kMaximumLatencyMs
                          ? kMaximumLatencyMs
                          : milliseconds;

    gLatencyMs.store(clamped, std::memory_order_relaxed);
}

jboolean NativeIsHookInstalled(JNIEnv *, jclass) {
    return gHookInstalled.load(std::memory_order_acquire) ? JNI_TRUE : JNI_FALSE;
}

constexpr JNINativeMethod kNativeMethods[] = {
        {
                const_cast<char *>("installHooks"),
                const_cast<char *>("()Z"),
                reinterpret_cast<void *>(NativeInstallHooks)
        },
        {
                const_cast<char *>("toggleLatencySim"),
                const_cast<char *>("(Z)V"),
                reinterpret_cast<void *>(NativeToggleLatencySimulation)
        },
        {
                const_cast<char *>("togglePacketDrop"),
                const_cast<char *>("(Z)V"),
                reinterpret_cast<void *>(NativeTogglePacketDrop)
        },
        {
                const_cast<char *>("setLatencyMs"),
                const_cast<char *>("(I)V"),
                reinterpret_cast<void *>(NativeSetLatencyMs)
        },
        {
                const_cast<char *>("isHookInstalled"),
                const_cast<char *>("()Z"),
                reinterpret_cast<void *>(NativeIsHookInstalled)
        }
};

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *javaVm, void *) {
    JNIEnv *environment = nullptr;

    if (javaVm->GetEnv(
            reinterpret_cast<void **>(&environment),
            JNI_VERSION_1_6
    ) != JNI_OK || environment == nullptr) {
        return JNI_ERR;
    }

    jclass nativeBridgeClass = environment->FindClass(
            "com/khanhan/pingpilot/qa/NativeBridge"
    );

    if (nativeBridgeClass == nullptr) {
        environment->ExceptionClear();
        __android_log_print(
                ANDROID_LOG_ERROR,
                kLogTag,
                "NativeBridge class was not found"
        );
        return JNI_ERR;
    }

    const jint registrationResult = environment->RegisterNatives(
            nativeBridgeClass,
            kNativeMethods,
            static_cast<jint>(sizeof(kNativeMethods) / sizeof(kNativeMethods[0]))
    );

    environment->DeleteLocalRef(nativeBridgeClass);

    if (registrationResult != JNI_OK) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                kLogTag,
                "RegisterNatives failed: %d",
                registrationResult
        );
        return JNI_ERR;
    }

    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "libNetworkQA.so loaded"
    );

    return JNI_VERSION_1_6;
}
