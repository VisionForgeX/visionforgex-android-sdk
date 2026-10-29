// The VisionForgeX SDK for Android. Two libraries and a sample app:
//   vfx-core  the SDK: settings from the platform, pacing, preloading, floors, events, the views
//   vfx-ads   the Google engine (Google Mobile Ads 25.x and Google's consent SDK); the one apps add
//   sample    an app that runs every format
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// The version comes from the tag JitPack builds (VERSION), else the one below.
allprojects {
    group = "com.github.VisionForgeX.visionforgex-android-sdk"
    version = System.getenv("VERSION")?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "1.0.0"
}
