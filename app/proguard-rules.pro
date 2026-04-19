# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Kotlin
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# AndroidX Lifecycle
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# WebView JS Bridge
-keep class com.rotein.ebookreader.reader.EpubBridge { *; }
-keep class com.rotein.ebookreader.reader.PdfBridge { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
