# R8 rules for the release build.

# Align's own code: services, receivers, widget and overlay are created by the system by name. The app is
# small, so keeping it intact costs almost nothing and avoids any rename/strip surprises.
-keep class com.align.app.** { *; }

# AndroidSVG (renders the illustrations / logo from assets)
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**

# Useful stack traces
-keepattributes SourceFile,LineNumberTable,*Annotation*
