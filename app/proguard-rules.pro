-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# OpenLauncher data & models
-keep class com.openlauncher.app.data.** { *; }
-keep class com.openlauncher.app.model.** { *; }
-keep class com.openlauncher.app.util.LauncherBackup { *; }

# Mapbox

# Mapbox JNI/reflective entry points are covered by its bundled consumer rules.


# Room Database
# Room runtime is covered by its bundled consumer rules.
-keep class * extends androidx.room.RoomDatabase

# Gson
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Gson runtime is covered by its bundled consumer rules.

# Release: remove diagnostic logs that may include media or region names.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Mapbox generated Directions models retain AutoValue annotation metadata.
# These two CLASS-retained compiler annotations are not runtime dependencies.
# See https://github.com/google/auto/blob/main/value/userguide/index.md
# Limit this exception to the two annotation classes; do not suppress SDK packages.
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.auto.value.AutoValue$Builder
