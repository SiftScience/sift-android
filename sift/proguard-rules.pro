-keep class com.google.common.**
-dontwarn com.google.common.**

-keep class okio.*
-dontwarn okio.*

-keep class com.fasterxml.jackson.databind.ext.*
-dontwarn com.fasterxml.jackson.databind.ext.*

-keep class com.google.android.gms.dynamic.zzd

-dontnote android.net.http.*
-dontnote org.apache.commons.codec.**
-dontnote org.apache.http.**
-dontnote com.google.android.gms.internal.*
-dontnote com.google.common.**
-dontnote okhttp3.internal.platform.*

-keepclassmembers class * {
     @com.fasterxml.jackson.annotation.JsonCreator *;
     @com.fasterxml.jackson.annotation.JsonProperty *;
}
