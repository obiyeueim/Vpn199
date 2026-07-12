package com.khanhan.pingpilot.qa;

/**
 * JNI entry point for the process-local QA network emulation module.
 *
 * <p>The native hook only affects UDP sendto calls made inside this APK process.
 * It does not inject code into or modify another installed application.</p>
 */
public final class NativeBridge {

    static {
        System.loadLibrary("NetworkQA");
    }

    private NativeBridge() {
        throw new AssertionError("No instances");
    }

    public static native boolean installHooks();

    public static native void toggleLatencySim(boolean enabled);

    public static native void togglePacketDrop(boolean enabled);

    public static native void setLatencyMs(int milliseconds);

    public static native boolean isHookInstalled();
}
