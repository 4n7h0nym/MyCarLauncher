-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# OpenLauncher data & models
-keep class com.openlauncher.app.data.** { *; }
-keep class com.openlauncher.app.model.** { *; }
-keep class com.openlauncher.app.util.LauncherBackup { *; }

# Mapbox
-dontwarn com.mapbox.**
-keep class com.mapbox.maps.** { *; }
-keep class com.mapbox.common.** { *; }

# Room Database
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase

# Gson
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }
