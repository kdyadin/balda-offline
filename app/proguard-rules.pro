# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.kdyadin.balda.**$$serializer { *; }
-keepclassmembers class com.kdyadin.balda.** { *** Companion; }
-keepclasseswithmembers class com.kdyadin.balda.** { kotlinx.serialization.KSerializer serializer(...); }
