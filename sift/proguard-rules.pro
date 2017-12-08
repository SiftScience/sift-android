# ----------------------------------------
# Sift Science
# ----------------------------------------
-keep class siftscience.android.** { *; }

# ----------------------------------------
# GSON
# ----------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.sift.api.representations.** { *; }

# ----------------------------------------
# Other
# ----------------------------------------
-keep class com.google.common.**
-dontwarn com.google.common.**

-keep class okio.*
-dontwarn okio.*

-keep class com.google.android.gms.dynamic.zzd

-dontnote android.net.http.*
-dontnote org.apache.commons.codec.**
-dontnote org.apache.http.**
-dontnote com.google.android.gms.internal.*
-dontnote com.google.common.**
-dontnote okhttp3.internal.platform.*
