# Keep rules for the release build's R8 shrinking. Libraries ship most of their own rules
# (Room, kotlinx.serialization, Compose, PdfBox-Android); these cover the rest.

# PdfBox-Android and Bouncy Castle mention desktop-only Java classes they never use on Android.
-dontwarn java.awt.**
-dontwarn javax.naming.**
-dontwarn org.bouncycastle.**
-dontwarn com.gemalto.jp2.**

# Crash reports name the code where things went wrong; keep line numbers so they stay useful.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
