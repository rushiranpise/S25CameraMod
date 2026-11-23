# Xposed entry point is named only in assets/xposed_init.
-keep class io.github.rushiranpise.cameramod.CameraModHook { *; }

# Feature code is reached from the entry point, but keep it explicit for future R8 builds.
-keep class io.github.rushiranpise.cameramod.feature.** { *; }

# DexKit reaches its own classes through JNI.
-keep class org.luckypray.dexkit.** { *; }
-dontwarn org.luckypray.dexkit.**

-dontwarn de.robv.android.xposed.**
