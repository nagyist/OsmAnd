-dontobfuscate

# Gson relies on generic signatures for fields like List<AssetEntry>.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Optional dependency surfaces referenced by bundled libraries but not available on Android.
-dontwarn java.beans.**
-dontwarn javax.ws.rs.**
-dontwarn org.immutables.value.**
-dontwarn org.kxml2.io.**