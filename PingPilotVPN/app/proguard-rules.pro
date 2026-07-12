# JNI_OnLoad resolves this class by its exact package path.
-keep class com.khanhan.pingpilot.qa.NativeBridge {
    public static native <methods>;
}

-keepclasseswithmembernames class * {
    native <methods>;
}
