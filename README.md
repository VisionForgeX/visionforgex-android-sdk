# VisionForgeX SDK for Android

The SDK for apps monetized with [VisionForgeX](https://apps.visionforgex.com): Google Ad Manager demand
(Google Ad Exchange) through the app's VisionForgeX account, with every decision made on the platform,
not in the app.

- **One line to start.** The app's settings come from the platform, are cached, and change without an
  app update: what each placement requests, banner refresh, pacing and caps, preloading, price floors,
  consent, experiments.
- **Every format.** Large adaptive and collapsible banners, interstitial, rewarded, rewarded
  interstitial, app open (shown by itself on return, when allowed) and native in ready layouts.
- **More shown, less waiting.** Full-screen ads are loaded in advance and replaced before Google's
  expiry; a failed load is retried after a growing wait; banners refresh only while seen.
- **Floors.** Price tiers asked highest first, each with its own key-value, then without a floor.
- **Users first.** Interstitials and app open ads are paced by the platform (gaps, per session, per
  hour, per day, a grace at the start, every Nth opportunity), never two at once.
- **Consent.** Google's User Messaging Platform where the law needs it, and the privacy options form.
- **Live revenue.** Google's value of every impression (its paid event) goes to the platform with
  requests, fills and shows, so revenue per user is seen as it happens, per placement and country.
- **Experiments.** The platform splits installs and compares revenue per daily user.
- **A debug panel** in the app, and Google's ad inspector.

What stays private is the platform; this repository holds only what runs in the app.
[docs/PROTOCOL.md](docs/PROTOCOL.md) describes everything the SDK and the platform say to each other.

## Add it

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

`app/build.gradle.kts` (minSdk 24): **one** of these two lines.

```kotlin
dependencies {
    // Google's GMA Next-Gen SDK (Google's preferred Android SDK since July 2026): recommended for new apps
    implementation("com.github.VisionForgeX.visionforgex-android-sdk:vfx-ads-nextgen:1.2.0")

    // or Google Mobile Ads 25.x (supported until 30 Jun 2027, retired 30 Jun 2028)
    // implementation("com.github.VisionForgeX.visionforgex-android-sdk:vfx-ads:1.2.0")
}
```

Each brings its Google SDK and Google's consent SDK; remove your own `play-services-ads` line. The two Google SDKs
cannot be in one app, so never add both. Your code is the same with either: moving from `vfx-ads` to
`vfx-ads-nextgen` is that one line. Next-Gen needs `compileSdk` 35 or higher. If your app uses mediation adapters
of its own (AppLovin, Meta, …), check that each has a Next-Gen version before you move; the yield groups
VisionForgeX manages for you work with both.

A WebView app on `vfx-ads-nextgen` registers its WebView with Google (the WebView API for Ads,
`com.google.android.libraries.ads.mobile.sdk.MobileAds.registerWebView(webView)`) once the SDK is ready, in the
`VfxInitListener` of `VisionForgeX.initialize`: Google's Next-Gen SDK refuses the registration before it has started
(Google Mobile Ads 25.x took it at any time). Seen on a real phone, 15 Nov 2026.

`AndroidManifest.xml`, inside `<application>`: the App ID shown on your app's page on VisionForgeX.

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-…~…"/>
```

## Start it

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        VisionForgeX.initialize(this, "vfx_…your app key…")
    }
}
```

The key is on your app's page on VisionForgeX (VisionForgeX SDK panel). Java:
`VisionForgeX.initialize(this, "vfx_…");`

## Show ads, by placement name

The names are your app's placements on VisionForgeX.

```kotlin
// at a natural pause: call at every one; the platform decides whether an ad shows now
VisionForgeX.interstitial("level_complete").show(activity)

// rewarded
VisionForgeX.rewarded("extra_life").show(activity) { type, amount -> giveReward(amount) }
watchButton.isEnabled = VisionForgeX.rewarded("extra_life").isReady()

// to know what happened
VisionForgeX.interstitial("level_complete").show(activity, object : VfxShowListener {
    override fun onDismissed() = goOn()
    override fun onNotShown(reason: String) = goOn()   // "not_ready", "interval", "grace", …
})
```

```xml
<com.visionforgex.ads.VfxBannerView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:vfxPlacement="home_banner" />

<com.visionforgex.ads.VfxNativeView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:vfxPlacement="feed_native" />
```

App open ads need no code: they show when the app comes back to the front, whenever the platform allows it.

## Privacy options

```kotlin
privacyButton.isVisible = VisionForgeX.isPrivacyOptionsRequired()
privacyButton.setOnClickListener { VisionForgeX.showPrivacyOptions(activity) }
```

Set up Google's consent messages once in Ad Manager → Privacy & messaging.

## Checking it

```kotlin
if (BuildConfig.DEBUG) VisionForgeX.openDebugPanel(context)   // settings, placements, pacing, events
VisionForgeX.openAdInspector(context)                          // Google's own
```

Test ads: switch "Test ads" on for the app on VisionForgeX, or add your device:
`VfxOptions.Builder().testDeviceIds(listOf("…")).build()`. Logcat tag: `VisionForgeX`.

## Options

| `VfxOptions.Builder()` | |
|---|---|
| `testDeviceIds(list)` | Google test devices |
| `logLevel(level)` | Logcat output (default: as the platform says) |
| `consentDebugEea(true, hashedIds)` | show the consent form as if in the EEA (testing) |
| `autoPreload(false)` | load only when asked (`VisionForgeX.preloadAll()`) |

Also: `VisionForgeX.setKeyValue("level", "12")` (your own targeting key-values), `setMuted(true)`,
`flush()` (send waiting events now).

## Building this repository

```
./gradlew :vfx-core:testDebugUnitTest :vfx-ads:testDebugUnitTest :vfx-ads-nextgen:testDebugUnitTest   # the tests
./gradlew :sample:assembleNextgenDebug -PvfxAppKey=vfx_…     # the sample app on Next-Gen (assembleGmaDebug: on 25.x)
```

Modules: `vfx-core` (settings, pacing, preloading, floors, events, views, debug panel; no ad SDK),
`vfx-ads` (the engine on Google Mobile Ads 25.x), `vfx-ads-nextgen` (the engine on Google's GMA Next-Gen SDK),
`sample` (flavours `gma` and `nextgen`). An app adds one engine; `vfx-core` finds whichever is there.

Licensed under the Apache License 2.0. Questions: support@visionforgex.com.
