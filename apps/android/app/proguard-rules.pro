# R8 rules for the release build (#845).
#
# The release build type runs R8 in full mode with `proguard-android-optimize.txt`
# (shrink + obfuscate + optimize) and the resource shrinker. The rules below are
# deliberately short: every third-party library this app depends on that needs
# reflection already ships its own consumer rules inside its AAR/JAR, and R8
# merges those automatically. Re-declaring them here would only widen the keep
# radius and undo the shrinking. Verified against the resolved artifacts:
#
#   app.rive:rive-android            proguard.txt              -keep class app.rive.** { *; }
#   io.ktor:ktor-utils               META-INF/proguard/ktor.pro
#   org.jetbrains.kotlinx:…-serialization-core
#                                    META-INF/com.android.tools/r8/*.pro (full-mode rules)
#   org.jetbrains.kotlinx:…-coroutines-core / -android
#                                    META-INF/com.android.tools/r8*/coroutines.pro
#   io.coil-kt.coil3:coil-core       proguard.txt
#   com.squareup.okhttp3:okhttp      META-INF/proguard/okhttp3.pro
#
# Two dependencies ship no consumer rules and need none:
#   io.github.jan-tennert.supabase:* — no Class.forName / getDeclaredClasses /
#     ServiceLoader anywhere in its classes; every model is @Serializable, which
#     kotlinx-serialization's own rules already cover.
#   com.caverock:androidsvg-aar, com.google.zxing:core — no reflection at all
#     (the QR path uses QRCodeWriter directly, not MultiFormatWriter).
#
# Before adding a rule here, check the library's artifact for a bundled
# proguard.txt / META-INF/proguard/*.pro first.

# Readable crash reports. R8 strips SourceFile and LineNumberTable by default in
# full mode, which turns every Play Console stack trace into bare frame counts.
# Keeping them plus uploading the mapping file (android-release.yml) is what makes
# a deobfuscated trace possible; -renamesourcefileattribute replaces the real file
# names with a constant so nothing leaks while the line numbers stay usable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
