# ----------------------------------------
# Sift Science
# ----------------------------------------
-keep class siftscience.android.** { *; }

# ----------------------------------------
# Jackson
# ----------------------------------------
-keep class org.codehaus.** { *; }
-keep class com.fasterxml.** { *; }
-keep class com.fasterxml.jackson.databind.ext.*
-keep @com.fasterxml.jackson.annotation.JsonIgnoreProperties class * { *; }

-dontwarn com.fasterxml.jackson.databind.ext.*

-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.JsonCreator *;
    @com.fasterxml.jackson.annotation.JsonProperty *;
}

-keepclassmembers public final enum com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility {
    public static final com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility *;
}

-keepnames class com.fasterxml.jackson.** { *; }

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