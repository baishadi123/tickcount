# Add project specific ProGuard rules here.
# For more details, see https://developer.android.com/guide/developing/tools/proguard.html

# Compose and AndroidX ship their own consumer rules; nothing extra is needed
# for this app. Keep line numbers so crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# osmdroid has no consumer rules and leans on reflection in its tile
# pipeline, so the map mode would break silently if R8 stripped it.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
