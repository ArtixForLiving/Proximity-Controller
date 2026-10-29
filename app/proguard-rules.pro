# ProGuard rules for ProximityController
# Keep only what is strictly needed — minimize APK and RAM footprint

# Keep application entry points
-keep public class com.custom.proximitywake.MainActivity
-keep public class com.custom.proximitywake.ProximityService
-keep public class com.custom.proximitywake.ProximityAdminReceiver

# Keep Device Admin receiver (required by system for admin binding)
-keep class * extends android.app.admin.DeviceAdminReceiver

# Keep BroadcastReceiver subclasses (system callbacks)
-keep class * extends android.content.BroadcastReceiver

# Keep Service subclasses
-keep class * extends android.app.Service

# Remove all logging in release to save RAM/CPU
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Keep native attributes used by XML inflation
-keepattributes *Annotation*
-keepattributes Signature

# Suppress warnings for unused Android internals
-dontwarn android.support.**
-dontnote android.**
