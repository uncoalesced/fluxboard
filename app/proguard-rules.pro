# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Room generated database implementations ---------------------------------
#
# Room instantiates its generated implementation reflectively:
#     Class.forName(<DatabaseClass>.getCanonicalName() + "_Impl")
#          .getDeclaredConstructor()
#          .newInstance()
#
# Nothing in the compiled code calls that no-arg constructor directly, so R8 has
# no reachability evidence for it. Room 2.6.1's own consumer rule is only
#
#     -keep class * extends androidx.room.RoomDatabase
#
# which keeps the class and its name but says nothing about its members. Under
# R8 full mode -- the default since AGP 8, and not overridden in
# gradle.properties -- "-keep class X" no longer implies keeping X's default
# constructor, so the constructor is stripped while the class survives. Startup
# then dies at Application.onCreate with:
#
#     java.lang.NoSuchMethodException:
#         com.uncoalesced.stickykeys.stickercore.data.local.StickyKeysDatabase_Impl.<init> []
#
# Scoped to the no-arg constructor of RoomDatabase subclasses only: one member,
# selected by supertype rather than by package. Both StickyKeysDatabase_Impl and
# KeyboardDatabase_Impl had it removed (verified in mapping/release/usage.txt),
# so naming a single class would leave the second one as a latent crash.
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
