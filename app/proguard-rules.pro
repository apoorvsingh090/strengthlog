-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.lolmax.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.lolmax.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
