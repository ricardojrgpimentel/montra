# OpenShelf — R8 rules.
#
# Most of this is about kotlinx.serialization: the compiler plugin generates
# serializers as synthetic classes that R8 sees no direct references to, and a
# store that silently fails to parse its own catalogue is worse than useless.

-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations

# --- kotlinx.serialization ---------------------------------------------------
-keepclassmembers class dev.openshelf.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class dev.openshelf.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class dev.openshelf.data.model.**$$serializer { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# --- OkHttp / Okio ----------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- app --------------------------------------------------------------------
# The installer receiver is instantiated by the framework from the manifest.
-keep class dev.openshelf.install.InstallResultReceiver { public <init>(...); }
