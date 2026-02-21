# Supabase / Ktor / Kotlinx Serialization
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-keepattributes *Annotation*
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Bluetooth (ESC/POS printer)
-keep class android.bluetooth.** { *; }

# DTO classes (used by Supabase Postgrest serialization)
-keep class com.ayakasir.app.core.data.remote.dto.** { *; }

# Domain models (used by kotlinx.serialization for navigation)
-keep class com.ayakasir.app.core.navigation.Screen { *; }
-keep class com.ayakasir.app.core.navigation.Screen$* { *; }

# General
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**

# R8 missing JDK management classes referenced by Ktor debug detector on Android
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
