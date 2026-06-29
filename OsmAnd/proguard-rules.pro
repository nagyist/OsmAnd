-dontobfuscate

# Gson relies on generic signatures for fields like List<AssetEntry>.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Java classes called from Qt/OsmAndCore native code must keep their JNI-visible names and members.
-keep class org.qtproject.qt5.android.** { *; }
-keep class net.osmand.core.android.** { *; }
-keep class net.osmand.core.jni.** { *; }

# Optional dependency surfaces referenced by bundled libraries but not available on Android.
-dontwarn java.beans.**
-dontwarn javax.ws.rs.**
-dontwarn org.immutables.value.**
-dontwarn org.kxml2.io.**